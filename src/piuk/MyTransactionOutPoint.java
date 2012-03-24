package piuk;

import java.math.BigInteger;


import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.TransactionOutPoint;
import com.google.bitcoin.core.TransactionOutput;

//Very messy
//Needed to convert DBOutput blockchain.info uses internal to the BitcoinJ TransactionOutPoint needed to sign inputs
public class MyTransactionOutPoint extends TransactionOutPoint {
	private static final long serialVersionUID = 1L;
	byte[] scriptBytes;	
	int txOutputN;
	Sha256Hash txHash;
	BigInteger value;
	int confirmations;
	
	public MyTransactionOutPoint(Sha256Hash txHash, int txOutputN, BigInteger value, byte[] scriptBytes) throws ProtocolException {
		super(NetworkParameters.prodNet(), txOutputN, new Sha256Hash(txHash.getBytes()));
		this.scriptBytes = scriptBytes;
		this.value = value;
		this.txOutputN = txOutputN;
		this.txHash = txHash;
	}

	public int getConfirmations() {
		return confirmations;
	}

	public byte[] getScriptBytes() {
		return scriptBytes;
	}

	public int getTxOutputN() {
		return txOutputN;
	}

	public Sha256Hash getTxHash() {
		return txHash;
	}

	public BigInteger getValue() {
		return value;
	}

	public void setConfirmations(int confirmations) {
		this.confirmations = confirmations;
	}

	@Override
	public TransactionOutput getConnectedOutput() {		       
		return new TransactionOutput(params, null, value, scriptBytes);
	}

	@Override
	public byte[] getConnectedPubKeyScript() {
		return scriptBytes;
	}
}