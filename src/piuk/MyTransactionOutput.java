package piuk;

import java.math.BigInteger;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;

public class MyTransactionOutput extends TransactionOutput {
	private static final long serialVersionUID = 1L;
	
	String address;
	NetworkParameters params;
	
	MyTransactionOutput(NetworkParameters params, Transaction parent, BigInteger value, Address to) {
		super(params, parent, value, to);
		
		this.params = params;
		this.address = to.toString();
	}
	
	public Address getToAddress() {
		try {
			return new Address(params, address);
		} catch (AddressFormatException e) {
			e.printStackTrace();
		}
		
		return null;
	}
}