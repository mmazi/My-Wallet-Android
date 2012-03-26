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

package piuk.blockchain.android;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import piuk.MyRemoteWallet;
import piuk.blockchain.R;
import piuk.blockchain.android.util.ErrorReporter;

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
					localSaveWallet();
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
		super.onCreate();

		ErrorReporter.getInstance().init(this);

		//Need to save session cookie for kaptcha
		CookieManager cookieManager = new CookieManager();

		CookieHandler.setDefault(cookieManager);

		//If the User has a saved GUID then we can restore the wallet
		if (getGUID() != null) {

			//Try and read the wallet from the local cache
			if (!readLocalWallet()) {
				System.out.println("Loading remote");

				Toast.makeText(WalletApplication.this, R.string.toast_downloading_wallet, Toast.LENGTH_LONG).show();

				loadRemoteWallet();
			} else { 
				System.out.println("Read local");
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

	public synchronized void writeMultiAddrCache(String repsonse) {
		try {
			FileOutputStream file = openFileOutput(remoteWallet.getGUID() + Constants.MULTIADDR_FILENAME, Constants.WALLET_MODE);

			file.write(repsonse.getBytes());

			file.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public synchronized void syncWithMyWallet() {			
		new Thread(new Runnable() {
			public void run() {
				try {					

					//Can't sync a new wallet
					if (remoteWallet.isNew())
						return;

					String payload = null; 

					//Retry 3 times
					for (int ii = 0; ii < 3; ++ii) {
						try {
							payload = MyRemoteWallet.getWalletPayload(getGUID(), getSharedKey(), remoteWallet.getChecksum());

							break;

						} catch (Exception e) {
							e.printStackTrace();

							handler.post(new Runnable() {
								public void run() {
									Toast.makeText(WalletApplication.this, R.string.toast_wallet_download_failed, Toast.LENGTH_SHORT).show();
								}
							});

							try {
								Thread.sleep(10000);
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}
						}
					}

					if (payload != null) {
						try {
							remoteWallet.setPayload(payload);
						} catch (Exception e) {
							e.printStackTrace();

							handler.post(new Runnable()
							{
								public void run() {
									Toast.makeText(WalletApplication.this, R.string.toast_wallet_decryption_failed, Toast.LENGTH_LONG).show();
								}
							});

							return;
						}

						try {
							//Payload will return null when not modified
							FileOutputStream file = openFileOutput(Constants.WALLET_FILENAME, Constants.WALLET_MODE);
							file.write(payload.getBytes("UTF-8"));
							file.close();

							//Copy our labels into the address book
							if (remoteWallet.getLabelMap() != null) {
								for(Entry<String, String> labelObj : remoteWallet.getLabelMap().entrySet()) {
									AddressBookProvider.setLabel(getContentResolver(), labelObj.getKey(), labelObj.getValue());
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					writeMultiAddrCache(remoteWallet.doMultiAddr());

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


	public synchronized void loadRemoteWallet() {			
		new Thread(new Runnable() {
			public void run() {
				String payload = null; 

				//Retry 3 times
				for (int ii = 0; ii < 3; ++ii) {
					try {
						payload = MyRemoteWallet.getWalletPayload(getGUID(), getSharedKey());

						break;
					} catch (Exception e) {
						e.printStackTrace();

						handler.post(new Runnable() {
							public void run() {
								Toast.makeText(WalletApplication.this, R.string.toast_wallet_download_failed, Toast.LENGTH_SHORT).show();
							}
						});

						try {
							Thread.sleep(10000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				}

				if (payload == null)
					return;

				try {

					FileOutputStream file = openFileOutput(Constants.WALLET_FILENAME, Constants.WALLET_MODE);
					file.write(payload.getBytes("UTF-8"));
					file.close();

					MyRemoteWallet newRemoteWallet = new MyRemoteWallet(payload, getPassword());

					MyRemoteWallet oldRemoteWallet = remoteWallet;

					remoteWallet = newRemoteWallet;

					//Copy the event listeners
					if (oldRemoteWallet != null) {						
						newRemoteWallet.getBitcoinJWallet().eventListeners.addAll(oldRemoteWallet.getBitcoinJWallet().eventListeners);

						oldRemoteWallet.getBitcoinJWallet().invokeOnChange();

						oldRemoteWallet.getBitcoinJWallet().eventListeners.clear();
					}

					handler.post(new Runnable()
					{
						public void run()
						{
							notifyWidgets();
						}
					});

					localSaveWallet();

					//Copy our labels into the address book
					if (remoteWallet.getLabelMap() != null) {
						for(Entry<String, String> labelObj : remoteWallet.getLabelMap().entrySet()) {
							AddressBookProvider.setLabel(getContentResolver(), labelObj.getKey(), labelObj.getValue());
						}
					}

					new Thread(new Runnable() {
						public void run() {
							try {					
								handler.post(new Runnable()
								{
									public void run()
									{
										notifyWidgets();
									}
								});

								writeMultiAddrCache(remoteWallet.doMultiAddr());

								handler.post(new Runnable()
								{
									public void run()
									{
										notifyWidgets();
									}
								});
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

			new Thread(){
				@Override
				public void run() {
					try {
						remoteWallet.remoteSave();
						
						handler.post(new Runnable()
						{
							public void run()
							{
								notifyWidgets();
							}
						});
						
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
			}.start();

		} catch (Exception e) {
			e.printStackTrace();
		}

		localSaveWallet();
	}

	public void setAddressLabel(String address, String label) {
		try {
			remoteWallet.addLabel(address, label);

			new Thread(){
				@Override
				public void run() {
					try {
						remoteWallet.remoteSave();
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
			}.start();
		} catch (Exception e) {
			e.printStackTrace();

			Toast.makeText(WalletApplication.this, R.string.error_setting_label, Toast.LENGTH_LONG).show();
		}
	}

	public boolean readLocalWallet() {
		try {

			//Read the wallet from local file
			FileInputStream file = openFileInput(Constants.WALLET_FILENAME);

			String payload = null;

			payload = IOUtils.toString(file, "UTF-8");

			this.remoteWallet = new MyRemoteWallet(payload, getPassword());

			try {
				//Restore the multi address cache
				FileInputStream multiaddrCacheFile = openFileInput(remoteWallet.getGUID() +  Constants.MULTIADDR_FILENAME);

				String multiAddr =  IOUtils.toString(multiaddrCacheFile);

				remoteWallet.parseMultiAddr(multiAddr);

			} catch (Exception e) {
				e.printStackTrace();
			}

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

	public void localSaveWallet()
	{
		try {
			FileOutputStream file = openFileOutput(Constants.LOCAL_WALLET_FILENAME, Constants.WALLET_MODE);

			file.write(remoteWallet.getPayload().getBytes());

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
