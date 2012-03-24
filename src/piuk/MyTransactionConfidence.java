package piuk;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;

public class MyTransactionConfidence extends TransactionConfidence {
	private static final long serialVersionUID = 1L;
	
	int height;
	boolean double_spend;
	
	public MyTransactionConfidence(Transaction tx, int height, boolean double_spend) {
		super(tx);
		
		this.height = height;
		this.double_spend = double_spend;
	}
	
    @Override
	public synchronized int getAppearedAtChainHeight() {
    	return height;
    }

    @Override
	public synchronized void setAppearedAtChainHeight(int appearedAtChainHeight) {
    	this.height = appearedAtChainHeight;
    }
    
    @Override
	public synchronized ConfidenceType getConfidenceType() {
        if (height == 0)
        	return ConfidenceType.NOT_SEEN_IN_CHAIN;
        else if (double_spend)
	        	return ConfidenceType.OVERRIDDEN_BY_DOUBLE_SPEND;
        else if (height > 0)
        	return ConfidenceType.BUILDING;
        else
        	return ConfidenceType.UNKNOWN;
    }
}