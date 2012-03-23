package piuk;

import java.math.BigInteger;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutPoint;

public class MyTransactionInput extends TransactionInput {
	private static final long serialVersionUID = 1L;
	
	Address address;
	BigInteger value;
	
	public MyTransactionInput(NetworkParameters params,
			Transaction parentTransaction, byte[] scriptBytes,
			TransactionOutPoint outpoint) {
		super(params, parentTransaction, scriptBytes, outpoint);
	}

	@Override
	public Address getFromAddress() {
		return address;
	}
	
	public BigInteger getValue() {
		return value;
	}

	public void setValue(BigInteger value) {
		this.value = value;
	}
}