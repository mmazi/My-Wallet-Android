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
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONValue;

import piuk.MyBlockChain.MyBlock;
import piuk.blockchain.android.Constants;

import com.google.bitcoin.bouncycastle.util.encoders.Hex;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletTransaction;
import com.google.bitcoin.core.Transaction.SigHash;


@SuppressWarnings("unchecked")
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

		System.out.println("Modified base64 " + base64Payload);

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
		if (_wallet != null) {
			_wallet.addKey(key);
			
			_wallet.invokeOnChange();
		}

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

	public void parseMultiAddr(String response) throws Exception {

		_wallet.clearTransactions(0);
		
		BigInteger previousBalance = _wallet.final_balance;
		
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

		if (transactions != null) {
			for (Map<String, Object> transactionDict : transactions) {
				_wallet.addWalletTransaction(MyTransaction.fromJSONDict(transactionDict));
			}
		}

		BigInteger newBalance = _wallet.final_balance;

		if (_wallet.getTransactionsByTime() != null && _wallet.getTransactionsByTime().size() > 0) {
			if (newBalance.compareTo(previousBalance) > 0)
				_wallet.invokeOnCoinsReceived(_wallet.getTransactionsByTime().get(0), previousBalance, newBalance);
			else if (newBalance.compareTo(previousBalance) < 0)
				_wallet.invokeOnCoinsSent(_wallet.getTransactionsByTime().get(0), previousBalance, newBalance);
		}
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

	public interface SendProgress {
		public void onSend(Transaction tx, String message);
		public void onError(String message);
		public void onProgress(String message);
	}

	public void sendCoinsAsync(final String toAddress, final BigInteger amount, final BigInteger fee, final SendProgress progress) {

		new Thread() {
			@Override
			public void run() {			
				try {
					//Construct a new transaction
					progress.onProgress("Getting Unspent Outputs");

					List<MyTransactionOutPoint> unspent = getUnspentOutputPoints();

					progress.onProgress("Constructing Transaction");

					Transaction tx = makeTransaction(unspent, toAddress, amount, fee);

					progress.onProgress("Signing Inputs");

					//Now sign the inputs						
					tx.signInputs(SigHash.ALL, getBitcoinJWallet());

					progress.onProgress("Broadcasting Transaction");

					String response = pushTx(tx);

					progress.onSend(tx, response);

				} catch (Exception e) {
					e.printStackTrace();

					progress.onError(e.getLocalizedMessage());
				}
			}
		}.start();
	}

	//Rerutns response message
	public String pushTx(Transaction tx) throws Exception {

		String hexString = new String(Hex.encode(tx.bitcoinSerialize()));

		if (hexString.length() > 16384)
			throw new Exception("My wallet cannot handle transactions over 16kb in size. Please try splitting your transaction");

		String response = postURL(WebROOT + "pushtx", "tx="+hexString);

		return response;
	}

	//You must sign the inputs
	public Transaction makeTransaction(List<MyTransactionOutPoint> unspent, String toAddress, BigInteger amount, BigInteger fee) throws Exception {

		if (unspent == null || unspent.size() == 0)
			throw new Exception("No free outputs to spend");

		if (fee == null)
			fee = BigInteger.ZERO;

		if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0)
			throw new Exception("You must provide an amount");

		//Construct a new transaction
		Transaction tx = new Transaction(params);

		//Add the output
		BitcoinScript script = BitcoinScript.createSimpleOutBitoinScript(new BitcoinAddress(toAddress));

		TransactionOutput output = new TransactionOutput(params, null, amount, script.getProgram());

		tx.addOutput(output);

		//Now select the appropriate inputs
		BigInteger valueSelected = BigInteger.ZERO;
		BigInteger valueNeeded =  amount.add(fee);
		MyTransactionOutPoint firstOutPoint = null;

		for (MyTransactionOutPoint outPoint : unspent) {

			MyTransactionInput input = new MyTransactionInput(params, null, new byte[0], outPoint);

			input.outpoint = outPoint;

			tx.addInput(input);

			valueSelected = valueSelected.add(outPoint.value);

			if (firstOutPoint == null) 
				firstOutPoint = outPoint;

			if (valueSelected.compareTo(valueNeeded) >= 0)
				break;
		}

		//Check the amount we have selected is greater than the amount we need
		if (valueSelected.compareTo(valueNeeded) < 0) {
			throw new Exception("Insufficient Funds");
		}

		BigInteger change = valueSelected.subtract(amount);

		//Now add the change if there is any
		if (change.compareTo(BigInteger.ZERO) > 0) {						
			BitcoinScript inputScript = new BitcoinScript(firstOutPoint.getConnectedPubKeyScript());

			//Return change to the first address
			BitcoinScript change_script = BitcoinScript.createSimpleOutBitoinScript(inputScript.getAddress());

			TransactionOutput change_output = new TransactionOutput(params, null, change, change_script.getProgram());

			tx.addOutput(change_output);
		}

		return tx;
	}

	public List<MyTransactionOutPoint> getUnspentOutputPoints() throws Exception {

		StringBuffer buffer =  new StringBuffer(WebROOT + "unspent?");

		for (Map<String, Object> map : this.getKeysMap()) {
			String addr = (String) map.get("addr");

			//Don't include watch only addresses
			if (map.get("priv") == null)
				continue;

			buffer.append("&addr[]="+addr);
		}

		List<MyTransactionOutPoint> outputs = new ArrayList<MyTransactionOutPoint>();

		String response = fetchURL(buffer.toString());

		Map<String, Object> root = (Map<String, Object>) JSONValue.parse(response);

		List<Map<String, Object>> outputsRoot = (List<Map<String, Object>>) root.get("unspent_outputs");

		for (Map<String, Object> outDict : outputsRoot) {

			byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));

			ArrayUtils.reverse(hashBytes);

			Sha256Hash txHash = new Sha256Hash(hashBytes);

			int txOutputN = ((Number)outDict.get("tx_output_n")).intValue();
			BigInteger value = BigInteger.valueOf(((Number)outDict.get("value")).longValue());
			byte[] scriptBytes = Hex.decode((String)outDict.get("script"));
			int confirmations = ((Number)outDict.get("confirmations")).intValue();

			//Contrstuct the output
			MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes);

			outPoint.setConfirmations(confirmations);

			outputs.add(outPoint);
		}

		return outputs;
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

		postURL(WebROOT + "wallet", "guid="+URLEncoder.encode(this.getGUID(), "utf-8")+"&sharedKey="+URLEncoder.encode(this.getSharedKey(), "utf-8")+"&payload="+urlEncodedPayload+"&method="+method+"&length="+(payload.length())+"&checksum="+URLEncoder.encode(_checksum, "utf-8")+"&kaptcha="+kaptcha);
		
		_isNew = false;
		
		return true;
	}
	
	public void remoteDownload() {
		
	}

	public String getChecksum() {
		return _checksum;
	}

	public synchronized String setPayload(String payload) throws Exception {

		MyRemoteWallet tempWallet = new MyRemoteWallet(payload, temporyPassword);

		this.root = tempWallet.root;

		this.temporySecondPassword = null;

		this._checksum = tempWallet._checksum;

		addKeysTobitoinJWallet(_wallet);

		_isNew = false;
		
		return payload;
	}

	public static String getWalletPayload(String guid, String sharedKey, String checkSumString) throws Exception {
		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+guid+"&sharedKey="+sharedKey+"&checksum="+checkSumString);

		if (payload == null) {
			throw new Exception("Error downloading wallet");
		}

		if (payload.equals("Not modified")) {
			return null;
		}

		return payload;
	}

	public static String getWalletPayload(String guid, String sharedKey) throws Exception {
		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+guid+"&sharedKey="+sharedKey);

		if (payload == null) {
			throw new Exception("Error downloading wallet");
		}

		return payload;
	}

}
