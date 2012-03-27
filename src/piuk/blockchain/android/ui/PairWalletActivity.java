package piuk.blockchain.android.ui;

import java.util.regex.Pattern;

import piuk.blockchain.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.ActionBarFragment;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class PairWalletActivity extends AbstractWalletActivity {
	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.pair_wallet_content);

		final ActionBarFragment actionBar = getActionBar();

		actionBar.setPrimaryTitle(R.string.pair_wallet_title);
		
		//showQRReader();	
		
		actionBar.setBack(new OnClickListener()
		{
			public void onClick(final View v)
			{
				finish();
			}
		});

		final Button pairDeviceButton = (Button) getWindow().findViewById(R.id.pair_qr_button);

		pairDeviceButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {	
				showQRReader();
			}
		});
	}
	
	
	
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
			} else {
				errorDialog(R.string.error_pairing_wallet, "Error saving preferences");
			}
		}

		finish();
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
}
