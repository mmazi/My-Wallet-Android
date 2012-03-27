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

import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import piuk.MyBlockChain;
import piuk.MyTransactionOutput;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import com.google.bitcoin.core.AbstractPeerEventListener;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import piuk.blockchain.R;
import piuk.blockchain.android.ui.WalletActivity;
import piuk.blockchain.android.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public class BlockchainService extends android.app.Service
{
	public static final String ACTION_PEER_STATE = BlockchainService.class.getName() + ".peer_state";
	public static final String ACTION_PEER_STATE_NUM_PEERS = "num_peers";

	public static final String ACTION_BLOCKCHAIN_STATE = BlockchainService.class.getName() + ".blockchain_state";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE = "best_chain_date";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT = "best_chain_height";
	public static final String ACTION_BLOCKCHAIN_STATE_DOWNLOAD = "download";
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK = 0;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM = 1;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM = 2;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM = 4;

	private WalletApplication application;

	private MyBlockChain blockChain;

	private final Handler handler = new Handler();
	private Handler websocketHandler = new Handler();

	private final Handler delayHandler = new Handler();

	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;
	private static final int NOTIFICATION_ID_COINS_SENT = 3;

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			System.out.println("onCoinsSent()");

			handler.post(new Runnable()
			{
				public void run()
				{
					try {
						final MyTransactionOutput output = (MyTransactionOutput) tx.getOutputs().get(0);
						final Address to = output.getToAddress();
						final BigInteger amount = tx.getValue(wallet);

						notifyCoinsSent(to, amount);

						notifyWidgets();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}

		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			try {
				System.out.println("onCoinsReceived()");

				final TransactionInput input = tx.getInputs().get(0);
				final Address from = input.getFromAddress();
				final BigInteger amount = tx.getValue(wallet);

				handler.post(new Runnable()
				{
					public void run()
					{
						notifyCoinsReceived(from, amount);

						notifyWidgets();

					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	private void notifyCoinsSent(final Address to, final BigInteger amount)
	{
		System.out.println("Notify ");

		BigInteger notificationAccumulatedAmount = BigInteger.ZERO;

		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);

		final List<Address> notificationAddresses = new LinkedList<Address>();

		if (to != null && !notificationAddresses.contains(to))
			notificationAddresses.add(to);

		final String tickerMsg = getString(R.string.notification_coins_sent_msg, WalletUtils.formatValue(amount))
				+ (Constants.TEST ? " [testnet]" : "");

		final String msg = getString(R.string.notification_coins_sent_msg, WalletUtils.formatValue(notificationAccumulatedAmount))
				+ (Constants.TEST ? " [testnet]" : "");

		final StringBuilder text = new StringBuilder();
		for (final Address address : notificationAddresses)
		{
			if (text.length() > 0)
				text.append(", ");
			text.append(address.toString());
		}

		if (text.length() == 0)
			text.append("unknown");

		text.insert(0, "To ");

		final Notification notification = new Notification(R.drawable.stat_notify_received, tickerMsg, System.currentTimeMillis());
		notification.setLatestEventInfo(BlockchainService.this, msg, text,
				PendingIntent.getActivity(BlockchainService.this, 0, new Intent(BlockchainService.this, WalletActivity.class), 0));

		notification.number = 0;
		notification.sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alert);

		nm.notify(NOTIFICATION_ID_COINS_SENT, notification);

		Toast.makeText(application, tickerMsg, Toast.LENGTH_LONG).show();
	}


	private void notifyCoinsReceived(final Address from, final BigInteger amount)
	{
		System.out.println("Notify ");

		BigInteger notificationAccumulatedAmount = BigInteger.ZERO;

		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);

		final List<Address> notificationAddresses = new LinkedList<Address>();

		if (from != null && !notificationAddresses.contains(from))
			notificationAddresses.add(from);

		final String tickerMsg = getString(R.string.notification_coins_received_msg, WalletUtils.formatValue(amount))
				+ (Constants.TEST ? " [testnet]" : "");

		final String msg = getString(R.string.notification_coins_received_msg, WalletUtils.formatValue(notificationAccumulatedAmount))
				+ (Constants.TEST ? " [testnet]" : "");

		final StringBuilder text = new StringBuilder();
		for (final Address address : notificationAddresses)
		{
			if (text.length() > 0)
				text.append(", ");
			text.append(address.toString());
		}

		if (text.length() == 0)
			text.append("unknown");

		text.insert(0, "From ");

		final Notification notification = new Notification(R.drawable.stat_notify_received, tickerMsg, System.currentTimeMillis());
		notification.setLatestEventInfo(BlockchainService.this, msg, text,
				PendingIntent.getActivity(BlockchainService.this, 0, new Intent(BlockchainService.this, WalletActivity.class), 0));

		notification.number = 0;
		notification.sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alert);

		nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification);

		Toast.makeText(application, tickerMsg, Toast.LENGTH_LONG).show();
	}

	private final PeerEventListener peerEventListener = new AbstractPeerEventListener()
	{
		@Override
		public void onPeerConnected(final Peer peer, final int peerCount)
		{
			changed(peerCount);
		}

		@Override
		public void onPeerDisconnected(final Peer peer, final int peerCount)
		{
			changed(peerCount);
		}

		private void changed(final int numPeers)
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					if (numPeers == 0)
					{
						nm.cancel(NOTIFICATION_ID_CONNECTED);
					}
					else
					{
						final String msg = getString(R.string.notification_peers_connected_msg, numPeers);
						System.out.println("Peer connected, " + msg);

						final Notification notification = new Notification(R.drawable.stat_sys_peers, null, 0);
						notification.flags |= Notification.FLAG_ONGOING_EVENT;
						notification.iconLevel = numPeers > 4 ? 4 : numPeers;
						notification.setLatestEventInfo(BlockchainService.this, getString(R.string.app_name) + (Constants.TEST ? " [testnet]" : ""),
								msg,
								PendingIntent.getActivity(BlockchainService.this, 0, new Intent(BlockchainService.this, WalletActivity.class), 0));
						nm.notify(NOTIFICATION_ID_CONNECTED, notification);
					}
				}
			});
		}
	};

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		private boolean hasConnectivity;

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final String action = intent.getAction();

			handler.post(new Runnable() {
				public void run() {
					if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action))
					{
						hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
						final String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
						// final boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);
						System.out.println("network is " + (hasConnectivity ? "up" : "down") + (reason != null ? ": " + reason : ""));

						if (hasConnectivity) {
							start();
						}
					}	
				}
			});
		}
	};

	public class LocalBinder extends Binder
	{
		public BlockchainService getService()
		{
			return BlockchainService.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(final Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onCreate()
	{
		System.out.println("service onCreate()");

		super.onCreate();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		application = (WalletApplication) getApplication();

		application.getWallet().addEventListener(walletEventListener);

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
		registerReceiver(broadcastReceiver, intentFilter);

		try {
			blockChain = new MyBlockChain(Constants.NETWORK_PARAMETERS, application.getRemoteWallet());

			blockChain.addPeerEventListener(peerEventListener);

		} catch (Exception e) {
			e.printStackTrace();
		}	

		new Thread() {
			public void run() {

				Looper.prepare();

				websocketHandler = new Handler();

				blockChain.start();
			}
		}.start();
	}

	public void start()
	{
		if (!blockChain.getRemoteWallet().isUptoDate(Constants.MultiAddrTimeThreshold)) {
			application.syncWithMyWallet();
		}

		if (websocketHandler != null && !blockChain.isConnected()) {
			websocketHandler.post(new Runnable() {
				public void run() {
					blockChain.start();
				}
			});
		}
	}

	public void stop() {
		try {
			System.out.println("Stop");

			blockChain.stop();		

			websocketHandler = null;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy()
	{
		System.out.println("service onDestroy()");

		application.getWallet().removeEventListener(walletEventListener);
		blockChain.removePeerEventListener(peerEventListener);

		stop();
		
		unregisterReceiver(broadcastReceiver);

		delayHandler.removeCallbacksAndMessages(null);

		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				nm.cancel(NOTIFICATION_ID_CONNECTED);
			}
		}, Constants.SHUTDOWN_REMOVE_NOTIFICATION_DELAY);

		super.onDestroy();
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
}
