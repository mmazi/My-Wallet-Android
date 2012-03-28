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

package piuk.blockchain.android.ui;

import java.math.BigInteger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet.BalanceType;

import piuk.MyRemoteWallet;
import piuk.MyRemoteWallet.SendProgress;
import piuk.blockchain.R;
import piuk.blockchain.android.AddressBookProvider;
import piuk.blockchain.android.BlockchainService;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.ui.CurrencyAmountView.Listener;
import piuk.blockchain.android.ui.SecondPasswordFragment.SuccessCallback;
import piuk.blockchain.android.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment
{
	private WalletApplication application;
	private final Handler handler = new Handler();
	private Runnable sentRunnable;

	private AutoCompleteTextView receivingAddressView;
	private View receivingAddressErrorView;
	private CurrencyAmountView amountView;
	private Button viewGo;
	private Button viewCancel;

	private State state = State.INPUT;

	private enum State
	{
		INPUT, SENDING, SENT
	}

	private final TextWatcher textWatcher = new TextWatcher()
	{
		public void afterTextChanged(final Editable s)
		{
			updateView();
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}
	};

	private final Listener listener = new Listener()
	{
		public void changed()
		{
			updateView();
		}

		public void done()
		{
			viewGo.requestFocusFromTouch();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();

		application = (WalletApplication) activity.getApplication();

	}



	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final AbstractWalletActivity activity = (AbstractWalletActivity) getActivity();

		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		final BigInteger estimated = application.getWallet().getBalance(BalanceType.ESTIMATED);
		final BigInteger available = application.getWallet().getBalance(BalanceType.AVAILABLE);
		final BigInteger pending = estimated.subtract(available);
	
		
		Button instantDepositButton = (Button) view.findViewById(R.id.instant_deposit);
		
		instantDepositButton.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				WalletApplication application = (WalletApplication) activity.getApplication();
				
				//Don't allow deposits into new wallets
				if (application.getRemoteWallet().isNew())
					return;
				
				ECKey key = application.getWallet().keychain.get(0);
				
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/deposit?address="+key.toAddress(Constants.NETWORK_PARAMETERS)));
				
				startActivity(browserIntent);
			}
		});
		
		// TODO subscribe to wallet changes

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressView.setAdapter(new AutoCompleteAdapter(activity, null));
		receivingAddressView.addTextChangedListener(textWatcher);

		receivingAddressErrorView = view.findViewById(R.id.send_coins_receiving_address_error);

		final CurrencyAmountView availableView = (CurrencyAmountView) view.findViewById(R.id.send_coins_available);
		availableView.setAmount(available);

		final TextView pendingView = (TextView) view.findViewById(R.id.send_coins_pending);
		pendingView.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		pendingView.setText(getString(R.string.send_coins_fragment_pending, WalletUtils.formatValue(pending)));

		amountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount);
		amountView.setListener(listener);
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				final FragmentTransaction ft = getFragmentManager().beginTransaction();
				final Fragment prev = getFragmentManager().findFragmentByTag(AmountCalculatorFragment.FRAGMENT_TAG);
				if (prev != null)
					ft.remove(prev);
				ft.addToBackStack(null);
				final DialogFragment newFragment = new AmountCalculatorFragment(new AmountCalculatorFragment.Listener()
				{
					public void use(final BigInteger amount)
					{
						amountView.setAmount(amount);
					}
				});
				newFragment.show(ft, AmountCalculatorFragment.FRAGMENT_TAG);
			}
		});

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{			
			final SendProgress progress = new SendProgress() {
				public void onSend(Transaction tx, final String message) {						
					handler.post(new Runnable() {
						public void run() {
							state = State.SENT;

							activity.longToast(message);

							activity.setResult(Activity.RESULT_OK);

							updateView();
						}
					});

					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					application.syncWithMyWallet();
				}

				public void onError(final String message) {
					handler.post(new Runnable() {
						public void run() {

							System.out.println("On Error");
							
							state = State.INPUT;

							activity.longToast(message);

							updateView();
						}
					});							
				}

				public void onProgress(final String message) {
					handler.post(new Runnable() {
						public void run() {
							state = State.SENDING;
							
							updateView();
						}
					});
				}

				public boolean onReady(Transaction tx, BigInteger fee, long priority) {			
										
					if ((priority < 57600000L || tx.bitcoinSerialize().length > 1024) && fee.compareTo(BigInteger.ZERO) == 0) {		

						handler.post(new Runnable() {
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
								builder.setMessage(R.string.ask_for_fee)
								.setCancelable(false)
								.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {												
										send(BigInteger.valueOf(500000)); //0.005 BTC fee
									}
								})
								.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										dialog.cancel();
									}
								});
								
								AlertDialog alert = builder.create();
								
								alert.show();
							}
						});
						
						handler.post(new Runnable() {
							public void run() {
								state = State.INPUT;
								updateView();
							}
						});
						
						return false;
					}

					return true;
				}
			};

			public void send(BigInteger fee) {
				Address receivingAddress;
				
				try {
					receivingAddress = new Address(Constants.NETWORK_PARAMETERS, receivingAddressView.getText().toString().trim());
				} catch (AddressFormatException e) {
					e.printStackTrace();
					
					return;
				}
				
				final BigInteger amount = amountView.getAmount();
				final WalletApplication application = (WalletApplication) getActivity().getApplication();
				
				application.getRemoteWallet().sendCoinsAsync(receivingAddress.toString(), amount, fee, progress);
			}
			
			public void makeTransaction() {
				try {
					final BigInteger fee = BigInteger.ZERO;

					send(fee);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			public void onClick(final View v)
			{
								
				MyRemoteWallet remoteWallet = application.getRemoteWallet();

				if (remoteWallet.isDoubleEncrypted() == false) {
					makeTransaction();
				} else {
					if (remoteWallet.temporySecondPassword == null) {
						SecondPasswordFragment.show(getFragmentManager(), new SuccessCallback() {

							public void onSuccess() {
								makeTransaction();
							}

							public void onFail() {
								Toast.makeText(application, R.string.send_no_password_error, Toast.LENGTH_LONG).show();
							} 
						});
					} else {
						makeTransaction();
					}
				}
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				activity.setResult(Activity.RESULT_CANCELED);

				activity.finish();
			}
		});

		updateView();

		return view;
	}

	protected void onServiceBound()
	{
		System.out.println("service bound");
	}

	protected void onServiceUnbound()
	{
		System.out.println("service unbound");
	}

	@Override
	public void onDestroyView()
	{
		handler.removeCallbacks(sentRunnable);

		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	public class AutoCompleteAdapter extends CursorAdapter
	{
		public AutoCompleteAdapter(final Context context, final Cursor c)
		{
			super(context, c);
		}

		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent)
		{
			final LayoutInflater inflater = LayoutInflater.from(context);
			return inflater.inflate(R.layout.simple_dropdown_item_2line, parent, false);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			final ViewGroup viewGroup = (ViewGroup) view;
			((TextView) viewGroup.findViewById(android.R.id.text1)).setText(cursor.getString(cursor
					.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL)));
			((TextView) viewGroup.findViewById(android.R.id.text2)).setText(cursor.getString(cursor
					.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS)));
		}

		@Override
		public CharSequence convertToString(final Cursor cursor)
		{
			return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
		}

		@Override
		public Cursor runQueryOnBackgroundThread(final CharSequence constraint)
		{
			final Cursor cursor = getActivity().managedQuery(AddressBookProvider.CONTENT_URI, null, AddressBookProvider.SELECTION_QUERY,
					new String[] { constraint.toString() }, null);
			return cursor;
		}
	}

	private void updateView()
	{
		boolean validAddress = false;
		try
		{
			final String address = receivingAddressView.getText().toString().trim();
			if (address.length() > 0)
			{
				new Address(Constants.NETWORK_PARAMETERS, address);
				validAddress = true;
			}
			receivingAddressErrorView.setVisibility(View.GONE);
		}
		catch (final Exception x)
		{
			receivingAddressErrorView.setVisibility(View.VISIBLE);
		}

		final BigInteger amount = amountView.getAmount();
		final boolean validAmount = amount != null && amount.signum() > 0;


		receivingAddressView.setEnabled(state == State.INPUT);

		amountView.setEnabled(state == State.INPUT);

		viewGo.setEnabled(state == State.INPUT && validAddress && validAmount);
		if (state == State.INPUT)
			viewGo.setText(R.string.send_coins_fragment_button_send);
		else if (state == State.SENDING)
			viewGo.setText(R.string.send_coins_sending_msg);
		else if (state == State.SENT)
			viewGo.setText(R.string.send_coins_sent_msg);

		viewCancel.setEnabled(state != State.SENDING);
		viewCancel.setText(state != State.SENT ? R.string.button_cancel : R.string.send_coins_fragment_button_back);
	}

	public void update(final String receivingAddress, final BigInteger amount)
	{
		receivingAddressView.setText(receivingAddress);
		flashReceivingAddress();

		if (amount != null)
			amountView.setAmount(amount);

		if (receivingAddress != null && amount == null)
			amountView.requestFocus();

		updateView();
	}

	private void showAddAddressDialog(final String address)
	{
		final Activity activity = getActivity();
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.send_coins_add_address_dialog_title);
		builder.setPositiveButton(R.string.send_coins_add_address_dialog_button_add, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}
		});
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			receivingAddressView.setTextColor(Color.parseColor("#888888"));
		}
	};

	public void flashReceivingAddress()
	{
		receivingAddressView.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}
}
