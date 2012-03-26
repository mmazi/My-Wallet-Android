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

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.json.simple.JSONValue;

import piuk.blockchain.android.Constants;


import com.google.bitcoin.bouncycastle.util.encoders.Hex;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.core.WalletTransaction;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryBlockStore;

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

public class MyBlockChain extends BlockChain implements WebSocketEventHandler {
	final String URL = "ws://api.blockchain.info:8335/inv";
	int nfailures = 0;
	WebSocketConnection _websocket;
	MyRemoteWallet remoteWallet;
	StoredBlock latestBlock;
	boolean isConnected = false;
	boolean ignoreNextClose;
	
	public MyRemoteWallet getRemoteWallet() {
		return remoteWallet;
	}

	public static class MyBlock extends Block {
		private static final long serialVersionUID = 1L;
		
		long time;
		Sha256Hash hash;
		int blockIndex;
		
		public MyBlock(NetworkParameters params) throws ProtocolException {
			super(params);
		}

		@Override
		public long getTimeSeconds() {
			return time;
		}
		
		@Override
		public Sha256Hash getHash() {
			return hash;
		}
	}
	
	public Set<PeerEventListener> listeners = new ConcurrentSkipListSet<PeerEventListener>();
	
	@Override
	public int getBestChainHeight() {
		return getChainHead().getHeight();
	}

	@Override
	public StoredBlock getChainHead() {
		if (latestBlock != null) {
			return latestBlock;
		} else {
			return remoteWallet._multiAddrBlock;
		}
	}

	final private WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onChange(Wallet wallet)
		{
			if (remoteWallet._multiAddrBlock != null) {
				if (latestBlock == null || latestBlock.getHeight() < remoteWallet._multiAddrBlock.getHeight()) {
					latestBlock = remoteWallet._multiAddrBlock;

					for (PeerEventListener listener : listeners) {
						listener.onBlocksDownloaded(null, latestBlock.getHeader(), 0);
					}
				}
				
				//Disconnect and reconnect
				//To resubscribe
				subscribe();
			}
		}
	};
	
	
	public MyBlockChain(NetworkParameters params, MyRemoteWallet remoteWallet) throws BlockStoreException, WebSocketException, URISyntaxException {
		super(params, remoteWallet.getBitcoinJWallet(), new MemoryBlockStore(params));

		this._websocket = new WebSocketConnection(new URI(URL));

		this._websocket.setEventHandler(this);

		this.remoteWallet = remoteWallet;
	}

	public synchronized void subscribe() {
		try {
			String message = "{\"op\":\"blocks_sub\"}";

			for (Map<String, Object> key : this.remoteWallet.getKeysMap()) {				
				message += "{\"op\":\"addr_sub\", \"addr\":\""+key.get("addr")+"\"}";
			}
			
			_websocket.send(message);
			
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}
	

	public void removePeerEventListener(PeerEventListener listener) {
		listeners.remove(listener);
	}

	public void addPeerEventListener(PeerEventListener listener) {
		listeners.add(listener);
	}

	public void onClose() {
		System.out.println("onClose()");

		this.isConnected = false;

		if (ignoreNextClose)
			return;
		
		for (PeerEventListener listener : listeners) {
			listener.onPeerConnected(null, 0);
		}
		
		if (nfailures < 5) {
			try {
				++nfailures;

				_websocket.connect();
			} catch (WebSocketException e) {
				e.printStackTrace();
			}
		}
	}
	
	public boolean isConnected() {
		return this.isConnected;
	}
	
	public void stop() {
		try {
			this.ignoreNextClose = true;
			
			_websocket.close();
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
		
		remoteWallet.getBitcoinJWallet().removeEventListener(walletEventListener);
	}
	
	public void start() {
		this.ignoreNextClose = false;

		try {
			_websocket.connect();
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
		
		remoteWallet.getBitcoinJWallet().addEventListener(walletEventListener);
	}

	@SuppressWarnings("unchecked")
	public void onMessage(WebSocketMessage wmessage) {
		
		System.out.println("OnMessage()");
		
		try {
			String message = wmessage.getText();

			System.out.println("Websocket() onMessage() " + message);

			Map<String, Object> top = (Map<String, Object>) JSONValue.parse(message);

			
			if (top == null)
				return;
			
			String op = (String) top.get("op");

			Map<String, Object> x = (Map<String, Object>) top.get("x");

					
			if (op.equals("block")) {
				Sha256Hash hash = new Sha256Hash(Hex.decode((String)x.get("hash")));
				int blockIndex = ((Number)x.get("blockIndex")).intValue();
				int blockHeight = ((Number)x.get("height")).intValue();
				long time = ((Number)x.get("time")).longValue();
				
				MyBlock block = new MyBlock(Constants.NETWORK_PARAMETERS);
				block.hash = hash;
				block.blockIndex = blockIndex;
				block.time = time;
				
				this.latestBlock = new StoredBlock(block, BigInteger.ZERO, blockHeight);

				List<MyTransaction> transactions = remoteWallet.getMyTransactions();
				List<Number> txIndexes = (List<Number>) x.get("txIndexes");
				for (Number txIndex : txIndexes) {
					for (MyTransaction tx : transactions) {
						
						MyTransactionConfidence confidence = (MyTransactionConfidence) tx.getConfidence();
						
						if (tx.txIndex == txIndex.intValue() && confidence.height != blockHeight) {
							confidence.height = blockHeight;
							confidence.runListeners(); 
						}
					}
				}

				for (PeerEventListener listener : listeners) {
					listener.onBlocksDownloaded(null, block, 0);
				}
				
			} else if (op.equals("utx")) {
				WalletTransaction tx = MyTransaction.fromJSONDict(x);
				
				BigInteger result = BigInteger.ZERO;

				BigInteger previousBalance = remoteWallet.getBitcoinJWallet().final_balance;

				for (TransactionInput input : tx.getTransaction().getInputs()) {
					//if the input is from me subtract the value
					MyTransactionInput myinput = (MyTransactionInput) input;

					if (remoteWallet.isAddressMine(input.getFromAddress().toString())) {
						result = result.subtract(myinput.value);

						remoteWallet.getBitcoinJWallet().final_balance = remoteWallet.getBitcoinJWallet().final_balance.subtract(myinput.value);
						remoteWallet.getBitcoinJWallet().total_sent = remoteWallet.getBitcoinJWallet().total_sent.add(myinput.value);
					}
				}

				for (TransactionOutput output : tx.getTransaction().getOutputs()) {
					//if the input is from me subtract the value
					MyTransactionOutput myoutput = (MyTransactionOutput) output;

					if (remoteWallet.isAddressMine(myoutput.getToAddress().toString())) {
						result = result.add(myoutput.getValue());

						remoteWallet.getBitcoinJWallet().final_balance = remoteWallet.getBitcoinJWallet().final_balance.add(myoutput.getValue());
						remoteWallet.getBitcoinJWallet().total_received = remoteWallet.getBitcoinJWallet().total_sent.add(myoutput.getValue());
					}
				}

				MyTransaction mytx = (MyTransaction) tx.getTransaction();

				mytx.result = result;

				remoteWallet.getBitcoinJWallet().addWalletTransaction(tx);

				if (result.compareTo(BigInteger.ZERO) >= 0) {
					System.out.println("On Received");
					
					remoteWallet.getBitcoinJWallet().invokeOnCoinsReceived(tx.getTransaction(), previousBalance, remoteWallet.getBitcoinJWallet().final_balance);
				} else {
					remoteWallet.getBitcoinJWallet().invokeOnCoinsSent(tx.getTransaction(), previousBalance, remoteWallet.getBitcoinJWallet().final_balance);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onOpen() {
		System.out.println("onOpen()");

		this.isConnected = true;
		
		subscribe();
		
		for (PeerEventListener listener : listeners) {
			listener.onPeerConnected(null, 1);
		}
		
		nfailures = 0;
	}
}
