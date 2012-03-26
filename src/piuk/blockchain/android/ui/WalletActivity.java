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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Pattern;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import piuk.blockchain.R;
import piuk.blockchain.android.BlockchainService;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.ActionBarFragment;
import piuk.blockchain.android.util.ErrorReporter;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity
{
	private static final int REQUEST_CODE_SCAN = 0;
	private static final int DIALOG_HELP = 0;

	private BlockchainService service;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainService.LocalBinder) binder).getService();
		}

		public void onServiceDisconnected(final ComponentName name)
		{
		}
	};
	
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && "QR_CODE".equals(intent.getStringExtra("SCAN_RESULT_FORMAT")))
		{
			final String contents = intent.getStringExtra("SCAN_RESULT");

			String[] components = contents.split("\\|", Pattern.LITERAL);

			System.out.println(components.length);

			if (components.length < 3) {
				errorDialog(R.string.error_pairing_wallet, "Invalid Pairing QR Code");
				return;
			}

			String guid = components[0];
			String sharedKey = components[1];
			String password = components[2];

			if (guid == null || guid.length() == 0) {
				errorDialog(R.string.error_pairing_wallet, "Invalid GUID");
				return;
			}

			if (sharedKey == null || sharedKey.length() == 0) {
				errorDialog(R.string.error_pairing_wallet, "Invalid sharedKey");
				return;
			}

			if (password == null || password.length() <= 10) {
				errorDialog(R.string.error_pairing_wallet, "Password must be greater than 10 characters in length");
				return;
			}


			Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();

			edit.putString("guid", guid);
			edit.putString("sharedKey", sharedKey);
			edit.putString("password", password);

			if (edit.commit()) {
				final WalletApplication application = (WalletApplication) getApplication();

				new Thread() {
					@Override
					public void run() {
						try {
							application.loadRemoteWallet();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
				
				startActivity(new Intent(this, WalletActivity.class));
			} else {
				errorDialog(R.string.error_pairing_wallet, "Error saving preferences");
			}
		}
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
	protected void onCreate(final Bundle savedInstanceState)
	{
		
		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = getActionBar();

		actionBar.setPrimaryTitle(R.string.app_name);

		actionBar.getPrimaryTitleView().setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				WelcomeFragment.show(getSupportFragmentManager());
			}
		});
		
		actionBar.addButton(R.drawable.ic_action_send).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(WalletActivity.this, SendCoinsActivity.class));
			}
		});

		actionBar.addButton(R.drawable.ic_action_receive).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(WalletActivity.this, RequestCoinsActivity.class));
			}
		});

		
		actionBar.addButton(R.drawable.ic_action_info).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				
				WalletApplication application = (WalletApplication) getApplication();
				
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/wallet/iphone-view?guid="+application.getRemoteWallet().getGUID()+"&sharedKey="+application.getRemoteWallet().getSharedKey()));
				
				startActivity(browserIntent);
			}
		});

		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
		{
			actionBar.addButton(R.drawable.ic_action_address_book).setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					AddressBookActivity.start(WalletActivity.this, true);
				}
			});
		}

		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE)
		{
			final FragmentManager fm = getSupportFragmentManager();
			final FragmentTransaction ft = fm.beginTransaction();
			ft.hide(fm.findFragmentById(R.id.exchange_rates_fragment));
			ft.commit();
		}

		checkVersionAndTimeskewAlert();
		
		WalletApplication application = (WalletApplication) getApplication();
				
		if (application.isNewWallet())
			WelcomeFragment.show(getSupportFragmentManager());
		
		
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		bindService(new Intent(this, BlockchainService.class), serviceConnection, Context.BIND_AUTO_CREATE);

		checkLowStorageAlert();
	}

	@Override
	protected void onPause()
	{
		unbindService(serviceConnection);

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.wallet_options, menu);
		menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_address_book:
				AddressBookActivity.start(WalletActivity.this, true);
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;

			case R.id.wallet_options_donate:
				final Intent intent = new Intent(this, SendCoinsActivity.class);
				intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS, Constants.DONATION_ADDRESS);
				startActivity(intent);
				return true;

			case R.id.wallet_options_help:
				showDialog(DIALOG_HELP);
				return true;
		}

		return false;
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		if (id == DIALOG_HELP)
			webView.loadUrl("file:///android_asset/help" + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(WalletActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.wallet_low_storage_dialog_title);
			builder.setMessage(R.string.wallet_low_storage_dialog_msg);
			builder.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
					finish();
				}
			});
			builder.setNegativeButton(R.string.button_dismiss, null);
			builder.show();
		}
	}

	private void checkVersionAndTimeskewAlert()
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					final int versionCode = getWalletApplication().applicationVersionCode();
					final URLConnection connection = new URL(Constants.VERSION_URL + "?current=" + versionCode).openConnection();
					connection.connect();
					final long serverTime = connection.getHeaderFieldDate("Date", 0);
					final InputStream is = connection.getInputStream();
					final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
					// final String version = reader.readLine();
					reader.close();

					if (serverTime > 0)
					{
						final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / 1000 / 60);

						if (diffMinutes >= 60)
						{
							runOnUiThread(new Runnable()
							{
								public void run()
								{
									if (!isFinishing())
										timeskewAlert(diffMinutes);
								}
							});
						}
					}
				}
				catch (final Exception x)
				{
					x.printStackTrace();
				}
			}
		}.start();
	}

	private void timeskewAlert(final long diffMinutes)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_timeskew_dialog_title);
		builder.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));
		builder.setPositiveButton(R.string.wallet_timeskew_dialog_button_settings, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));
				finish();
			}
		});
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}
}