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

package de.schildbach.wallet;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

import piuk.MyRemoteWallet;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet.util.StrictModeWrapper;

/**
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application
{
	private MyRemoteWallet remoteWallet;

	private final Handler handler = new Handler();

	final private WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onChange(Wallet wallet)
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					saveWallet();
				}
			});
		}
	};

	public boolean isNewWallet() {
		return remoteWallet.isNew();
	}

	@Override
	public void onCreate()
	{
		try
		{
			StrictModeWrapper.init();
		}
		catch (final Error x)
		{
			System.out.println("StrictMode not available");
		}

		super.onCreate();

		ErrorReporter.getInstance().init(this);

		//If the User has a saved GUID then we can restore the wallet
		if (getGUID() != null) {
			
			//Try and read the wallet from the local cache
			if (readLocalWallet()) {
				syncWithMyWallet();
			} else {
				
				System.out.println("Loading remote");
				
				Toast.makeText(WalletApplication.this, R.string.toast_downloading_wallet, Toast.LENGTH_LONG).show();

				loadRemoteWallet();
			}
		}

		//Otherwise wither first load or an error
		if (remoteWallet == null) {
			try {
				this.remoteWallet = new MyRemoteWallet();
				
				Toast.makeText(WalletApplication.this, R.string.toast_generated_new_wallet, Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				throw new Error("Could not create wallet ", e);
			}
		}

		getWallet().addEventListener(walletEventListener);
	}

	public Wallet getWallet() {
		return remoteWallet.getBitcoinJWallet();
	}
	
	public MyRemoteWallet getRemoteWallet() {
		return remoteWallet;
	}

	public String getPassword() {
		return PreferenceManager.getDefaultSharedPreferences(this).getString("password", null);  
	}

	public String getGUID() {
		return PreferenceManager.getDefaultSharedPreferences(this).getString("guid", null);  
	}

	public String getSharedKey() {
		return PreferenceManager.getDefaultSharedPreferences(this).getString("sharedKey", null);  
	}

	public void notifyWidgets()
	{
		final Context context = getApplicationContext();

		// notify widgets
		final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		for (final AppWidgetProviderInfo providerInfo : appWidgetManager.getInstalledProviders())
		{
			// limit to own widgets
			if (providerInfo.provider.getPackageName().equals(context.getPackageName()))
			{
				final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetManager.getAppWidgetIds(providerInfo.provider));
				context.sendBroadcast(intent);
			}
		}
	}
	private void syncWithMyWallet() {			
		new Thread(new Runnable() {
			public void run() {
				try {					
					remoteWallet.sync();

					remoteWallet.doMultiAddr();

				} catch (Exception e) {
					e.printStackTrace();
					
					handler.post(new Runnable()
					{
						public void run()
						{
							Toast.makeText(WalletApplication.this, R.string.toast_error_syncing_wallet, Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		}).start();
	}

	public void loadRemoteWallet() {			
		new Thread(new Runnable() {
			public void run() {
				try {
					remoteWallet = MyRemoteWallet.getWallet(getGUID(), getSharedKey(), getPassword());
					saveWallet();

					for(Entry<String, String> labelObj : remoteWallet.getLabelMap().entrySet()) {
						AddressBookProvider.setLabel(getContentResolver(), labelObj.getKey(), labelObj.getValue());
					}
					
					new Thread(new Runnable() {
						public void run() {
							try {					
								remoteWallet.doMultiAddr();		
							} catch (Exception e) {
								e.printStackTrace();
								
								handler.post(new Runnable()
								{
									public void run()
									{
										Toast.makeText(WalletApplication.this, R.string.toast_error_downloading_transactions, Toast.LENGTH_LONG).show();
									}
								});
							}
						}
					}).start();

				} catch (Exception e) {
					e.printStackTrace();

					handler.post(new Runnable()
					{
						public void run()
						{
							Toast.makeText(WalletApplication.this, R.string.toast_wallet_download_failed, Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		}).start();
	}

	public void addNewKeyToWallet()
	{
		try {
			remoteWallet.addKey(new ECKey(), null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		saveWallet();
	}

	public boolean readLocalWallet() {
		try {
			FileInputStream file = openFileInput(Constants.WALLET_FILENAME);

			String payload =  IOUtils.toString(file, "UTF-8");

			this.remoteWallet = new MyRemoteWallet(payload, getPassword());

			return true;

		} catch (Exception e) {
			e.printStackTrace();

			handler.post(new Runnable()
			{
				public void run()
				{
					Toast.makeText(WalletApplication.this, R.string.toast_wallet_decrypt_failed, Toast.LENGTH_LONG).show();
				}
			});
		}

		return false;
	}


	public void saveWallet()
	{
		System.out.println("Save Wallet");

		try {
			FileOutputStream file = openFileOutput(Constants.WALLET_FILENAME, Constants.WALLET_MODE);

			byte[] payload = remoteWallet.getPayload(getPassword()).getBytes("UTF-8");

			file.write(payload);

			file.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public Address determineSelectedAddress()
	{
		final ArrayList<ECKey> keychain = getWallet().keychain;

		if (keychain.size() == 0)
			return null;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String defaultAddress = keychain.get(0).toAddress(Constants.NETWORK_PARAMETERS).toString();
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, defaultAddress);

		// sanity check
		for (final ECKey key : keychain)
		{
			final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
			if (address.toString().equals(selectedAddress))
				return address;
		}

		throw new IllegalStateException("address not in keychain: " + selectedAddress);
	}

	public final int applicationVersionCode()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException x)
		{
			return 0;
		}
	}

	public final String applicationVersionName()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException x)
		{
			return "unknown";
		}
	}
}
