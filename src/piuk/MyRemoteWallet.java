/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package piuk;

import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONValue;

import piuk.MyBlockChain.MyBlock;
import piuk.blockchain.Constants;

import com.google.bitcoin.bouncycastle.util.encoders.Hex;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletTransaction;



public class MyRemoteWallet extends MyWallet {
	public static final String WebROOT = "https://blockchain.info/";
	RemoteBitcoinJWallet _wallet;
	String _checksum;
	boolean _isNew = false;
	StoredBlock _multiAddrBlock;
	long lastMultiAddress;

	public boolean isAddressMine(String address) {		
		for (Map<String, Object> map : this.getKeysMap()) {
			String addr = (String) map.get("addr");

			if (address.equals(addr))
				return true;
		}

		return false;
	}

	public static class RemoteBitcoinJWallet extends Wallet {
		private static final long serialVersionUID = 1L;

		public RemoteBitcoinJWallet(NetworkParameters params) {
			super(params);
		}

		public BigInteger final_balance = BigInteger.ZERO;
		public BigInteger total_received = BigInteger.ZERO;
		public BigInteger total_sent = BigInteger.ZERO;
		public int n_tx = 0;

		@Override
		public synchronized BigInteger getBalance() {
			return final_balance;
		}

		@Override
		public synchronized BigInteger getBalance(BalanceType balanceType) {
			return final_balance;
		}
	}

	public boolean isNew() {
		return _isNew;
	}

	public MyRemoteWallet() throws Exception {
		super();

		this._wallet = new RemoteBitcoinJWallet(params);

		addKeysTobitoinJWallet(_wallet);

		this.temporyPassword = null;

		this._checksum  = null;

		this._isNew = true;
	}

	public MyRemoteWallet(String base64Payload, String password) throws Exception {
		super(base64Payload, password);

		this._wallet = new RemoteBitcoinJWallet(params);

		addKeysTobitoinJWallet(_wallet);

		this.temporyPassword = password;

		this._checksum  = new String(Hex.encode(MessageDigest.getInstance("SHA-256").digest(base64Payload.getBytes("UTF-8"))));

		this._isNew = false;
	}

	private static String fetchURL(String URL) throws Exception {			
		URL url = new URL(URL);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestMethod("GET"); 

			connection.setConnectTimeout(10000);

			connection.setInstanceFollowRedirects(false);

			connection.connect();

			if (connection.getResponseCode() != 200) {
				throw new Exception("Invalid HTTP Response code " + connection.getResponseCode());
			}

			String response = IOUtils.toString(connection.getInputStream(), "UTF-8");
		
			if (connection.getResponseCode() == 500)
				throw new Exception("Inavlid Response " + response);
			else
				return response;
			
		} finally {
			connection.disconnect();
		}
	}

	private static String postURL(String request, String urlParameters) throws Exception {			
		   
		URL url = new URL(request); 
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();  
		try {
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setInstanceFollowRedirects(false); 
			connection.setRequestMethod("POST"); 
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setUseCaches (false);

			connection.connect();
			
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream ());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();
			
			connection.setConnectTimeout(10000);

			connection.setInstanceFollowRedirects(false);
			
			if (connection.getResponseCode() == 500)
				throw new Exception("Invalid Response " + IOUtils.toString(connection.getErrorStream(), "UTF-8"));
			else
				return IOUtils.toString(connection.getInputStream(), "UTF-8");

		} finally {
			connection.disconnect();
		}
	}

	@Override
	public synchronized boolean addKey(ECKey key, String label) throws Exception {
		if (_wallet != null) _wallet.addKey(key);

		return super.addKey(key, label);
	}

	@Override
	public RemoteBitcoinJWallet getBitcoinJWallet() {
		return _wallet;
	}

	public List<MyTransaction> getMyTransactions() {
		List<MyTransaction> transactions = new ArrayList<MyTransaction>(_wallet.n_tx);

		for (WalletTransaction tx : _wallet.getWalletTransactions()) {
			MyTransaction mytx = (MyTransaction) tx.getTransaction();

			transactions.add(mytx);
		}

		return transactions;
	}

	@SuppressWarnings("unchecked")
	public void parseMultiAddr(String response) throws Exception {

		_wallet.clearTransactions(0);
		
		Map<String, Object> top = (Map<String, Object>) JSONValue.parse(response);

		Map<String, Object> info_obj = (Map<String, Object>) top.get("info");

		Map<String, Object> block_obj = (Map<String, Object>) info_obj.get("latest_block");

		if (block_obj != null) {
			Sha256Hash hash = new Sha256Hash(Hex.decode((String)block_obj.get("hash")));
			int blockIndex = ((Number)block_obj.get("block_index")).intValue();
			int blockHeight = ((Number)block_obj.get("height")).intValue();
			long time = ((Number)block_obj.get("time")).longValue();

			MyBlock block = new MyBlock(Constants.NETWORK_PARAMETERS);
			block.hash = hash;
			block.blockIndex = blockIndex;
			block.time = time;

			this._multiAddrBlock = new StoredBlock(block, BigInteger.ZERO, blockHeight);
		}

		Map<String, Object> wallet_obj = (Map<String, Object>) top.get("wallet");


		RemoteBitcoinJWallet _wallet = getBitcoinJWallet();

		_wallet.final_balance = BigInteger.valueOf(((Number)wallet_obj.get("final_balance")).longValue());
		_wallet.total_sent = BigInteger.valueOf(((Number)wallet_obj.get("total_sent")).longValue());
		_wallet.total_received = BigInteger.valueOf(((Number)wallet_obj.get("total_received")).longValue());
		_wallet.n_tx = ((Number)wallet_obj.get("n_tx")).intValue();

		List<Map<String, Object>> transactions = (List<Map<String, Object>>) top.get("txs");

		for (Map<String, Object> transactionDict : transactions) {
			_wallet.addWalletTransaction(MyTransaction.fromJSONDict(transactionDict));
		}

		_wallet.invokeOnChange();

	}

	public boolean isUptoDate(long time) {
		long now = System.currentTimeMillis();

		if (now - lastMultiAddress > time) {
			return false;
		} else {
			return true;
		}
	}

	public synchronized String doMultiAddr() throws Exception {

		System.out.println("Do multi addr");

		StringBuffer buffer =  new StringBuffer(WebROOT + "multiaddr?");

		for (Map<String, Object> map : this.getKeysMap()) {
			String addr = (String) map.get("addr");

			buffer.append("&addr[]="+addr);
		}

		String response = fetchURL(buffer.toString());

		parseMultiAddr(response);

		lastMultiAddress = System.currentTimeMillis();

		return response;
	}

	public synchronized boolean remoteSave() throws Exception {
		return remoteSave(null);
	}
	
	public synchronized boolean remoteSave(String kaptcha) throws Exception {
		
		String payload = this.getPayload();
		
		this._checksum  = new String(Hex.encode(MessageDigest.getInstance("SHA-256").digest(payload.getBytes("UTF-8"))));
		
		String method = _isNew ? "insert" : "update";
		
		if (!_isNew) {
			System.out.println("Not new");
		}
		
		if (kaptcha == null && _isNew)
			throw new Exception("Must provide a change to insert wallet");
		else if (kaptcha == null)
			kaptcha = "";
				
		String urlEncodedPayload = URLEncoder.encode(payload);

		System.out.println("Kaptcha " + kaptcha);
		

		String checkSumString = "";

		if (_checksum != null) 
			checkSumString = _checksum;
		
		postURL(WebROOT + "wallet", "guid="+URLEncoder.encode(this.getGUID(), "utf-8")+"&sharedKey="+URLEncoder.encode(this.getSharedKey(), "utf-8")+"&payload="+urlEncodedPayload+"&method="+method+"&length="+(payload.length())+"&checksum="+URLEncoder.encode(checkSumString, "utf-8")+"&kaptcha="+kaptcha);
		
		return true;
	}

	public synchronized void sync() throws Exception {

		String checkSumString = "";

		if (_checksum != null) 
			checkSumString = _checksum;

		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+getGUID()+"&sharedKey="+getSharedKey()+"&checksum="+checkSumString);

		if (payload.equals("Not modified")) {
			System.out.println("Not modified " + checkSumString);
			return;
		} else {
			System.out.println("Modified " + checkSumString + " " + payload.length());
		}

		MyWallet tempWallet = new MyWallet(payload, temporyPassword);

		this.root = tempWallet.root;

		this.temporySecondPassword = null;

		addKeysTobitoinJWallet(_wallet);
	}

	public static MyRemoteWallet getWallet(String guid, String sharedKey, String password) throws Exception {
		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+guid+"&sharedKey="+sharedKey);

		return new MyRemoteWallet(payload, password);
	}

}
