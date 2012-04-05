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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import piuk.MyECKey;
import piuk.MyWallet;
import piuk.blockchain.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.util.ActionBarFragment;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_ADDRESS = "address";
	private static final String INTENT_EXTRA_QUERY = "query";
	private static final int DIALOG_HELP = 0;
	final Map<String, ECKey> temporaryPrivateKeys = new HashMap<String, ECKey>();
	private static final int REQUEST_CODE_SCAN = 0;
	public String scanPrivateKeyAddress = null;

	@Override
	protected void onResume() {
		super.onResume();

		getWalletApplication().connect();
	}

	@Override
	protected void onPause() {
		super.onPause();

		getWalletApplication().diconnectSoon();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.send_coins_content);

		final ActionBarFragment actionBar = getActionBar();

		actionBar.setPrimaryTitle(R.string.send_coins_activity_title);

		actionBar.setBack(new OnClickListener()
		{
			public void onClick(final View v)
			{
				finish();
			}
		});

		actionBar.addButton(R.drawable.ic_action_qr).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showQRReader();
			}
		});
		actionBar.addButton(R.drawable.ic_action_help).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showDialog(DIALOG_HELP);
			}
		});

		handleIntent(getIntent());
	}

	public void showQRReader() {
		if (getPackageManager().resolveActivity(Constants.INTENT_QR_SCANNER, 0) != null)
		{
			startActivityForResult(Constants.INTENT_QR_SCANNER, REQUEST_CODE_SCAN);
		}
		else
		{
			showMarketPage(Constants.PACKAGE_NAME_ZXING);
			longToast(R.string.send_coins_install_qr_scanner_msg);
		}
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		webView.loadUrl("file:///android_asset/help_send_coins" + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && "QR_CODE".equals(intent.getStringExtra("SCAN_RESULT_FORMAT")))
		{
			final String contents = intent.getStringExtra("SCAN_RESULT");
			if (contents.matches("[a-zA-Z0-9]*"))
			{
				System.out.println("Scan address " + scanPrivateKeyAddress);

				if (scanPrivateKeyAddress != null) {

					try {
						
						byte[] privBytes = Base58.decode(contents);
												
						//Prppend a zero byte to make the biginteger unsigned
						byte[] appendZeroByte = ArrayUtils.addAll(new byte[1], privBytes);

						ECKey ecKey = new ECKey(new BigInteger(appendZeroByte));
						
						if (ecKey.toAddress(Constants.NETWORK_PARAMETERS).toString().equals(scanPrivateKeyAddress)) {
							temporaryPrivateKeys.put(scanPrivateKeyAddress, ecKey);
						} else {							
							longToast(getString(R.string.wrong_private_key, ecKey.toAddress(Constants.NETWORK_PARAMETERS).toString()));
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}

				} else {
					updateSendCoinsFragment(contents, null);					
				}
			}
			else
			{
					try
					{

						final BitcoinURI bitcoinUri = new BitcoinURI(Constants.NETWORK_PARAMETERS, contents);
						final Address address = bitcoinUri.getAddress();


						updateSendCoinsFragment(address != null ? address.toString() : null, bitcoinUri.getAmount());

					}
					catch (final BitcoinURIParseException x)
					{
						errorDialog(R.string.send_coins_uri_parse_error_title, contents);
					} 
				}
		}
		

		synchronized (temporaryPrivateKeys) {
			temporaryPrivateKeys.notify();
		}
		
		scanPrivateKeyAddress = null;
	}

	private void handleIntent(final Intent intent)
	{
		final String action = intent.getAction();
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		final String address;
		final BigInteger amount;

		if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null && "bitcoin".equals(scheme))
		{
			try
			{
				final BitcoinURI bitcoinUri = new BitcoinURI(Constants.NETWORK_PARAMETERS, intentUri.toString());
				address = bitcoinUri.getAddress().toString();
				amount = bitcoinUri.getAmount();
			}
			catch (final BitcoinURIParseException x)
			{
				errorDialog(R.string.send_coins_uri_parse_error_title, intentUri.toString());
				return;
			}
		}
		else if (Intent.ACTION_WEB_SEARCH.equals(action) && intent.hasExtra(INTENT_EXTRA_QUERY))
		{
			try
			{
				final BitcoinURI bitcoinUri = new BitcoinURI(Constants.NETWORK_PARAMETERS, intent.getStringExtra(INTENT_EXTRA_QUERY));
				address = bitcoinUri.getAddress().toString();
				amount = bitcoinUri.getAmount();
			}
			catch (final BitcoinURIParseException x)
			{
				errorDialog(R.string.send_coins_uri_parse_error_title, intentUri.toString());
				return;
			}
		}
		else if (intent.hasExtra(INTENT_EXTRA_ADDRESS))
		{
			address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
			amount = null;
		}
		else
		{
			return;
		}

		if (address != null || amount != null)
			updateSendCoinsFragment(address, amount);
		else
			longToast(R.string.send_coins_parse_address_error_msg);
	}

	private void updateSendCoinsFragment(final String address, final BigInteger amount)
	{
		final SendCoinsFragment sendCoinsFragment = (SendCoinsFragment) getSupportFragmentManager().findFragmentById(R.id.send_coins_fragment);

		sendCoinsFragment.update(address, amount);
	}
}
