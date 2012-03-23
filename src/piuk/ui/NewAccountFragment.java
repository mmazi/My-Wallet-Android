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

package piuk.ui;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import piuk.blockchain.R;
import piuk.blockchain.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;

/**
 * @author Andreas Schildbach
 */
public final class NewAccountFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = NewAccountFragment.class.getName();

	public static Bitmap loadBitmap(String url) throws MalformedURLException, IOException {
		Bitmap bitmap = null;

		final byte[] data = IOUtils.toByteArray(new URL(url).openStream());
		BitmapFactory.Options options = new BitmapFactory.Options();

		bitmap = BitmapFactory.decodeByteArray(data, 0, data.length,options);

		return bitmap;
	}

	public void refreshCaptcha(View view) {
		final ImageView captchaImage = (ImageView) view.findViewById(R.id.captcha_image);

		new Thread(new Runnable() {
			public void run() {
				try {
					final Bitmap b = loadBitmap("https://blockchain.info/kaptcha.jpg");
					captchaImage.post(new Runnable() {
						public void run() {
							captchaImage.setImageBitmap(b);
						}
					});
				} catch (Exception e) {

					captchaImage.post(new Runnable() {
						public void run() {
							Toast.makeText(getActivity().getApplication(), R.string.toast_error_downloading_captcha, Toast.LENGTH_LONG).show();
						}
					});

					e.printStackTrace();
				}
			}
		}).start();
	}

	public static NewAccountFragment show(final FragmentManager fm)
	{	
		final DialogFragment prev = (DialogFragment) fm.findFragmentById(R.layout.new_account_dialog);
		
		final FragmentTransaction ft = fm.beginTransaction();

		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		
		ft.addToBackStack(null);
		
		final NewAccountFragment newFragment = instance();

		newFragment.show(ft, FRAGMENT_TAG);

		return newFragment;
	}

	static NewAccountFragment instance()
	{
		final NewAccountFragment fragment = new NewAccountFragment();

		return fragment;
	}


	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(R.string.new_account_title);

		final View view = inflater.inflate(R.layout.new_account_dialog, null);

		dialog.setView(view);

		final Button createButton = (Button) view.findViewById(R.id.create_button);
		final TextView password = (TextView) view.findViewById(R.id.password);
		final TextView password2 = (TextView) view.findViewById(R.id.password2);
		final TextView captcha = (TextView) view.findViewById(R.id.captcha);

		refreshCaptcha(view);


		createButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				final WalletApplication application = (WalletApplication) getActivity().getApplication();

				if (password.getText().length() == 0 || password.getText().length() > 255) {
					Toast.makeText(application, R.string.new_account_password_length_error, Toast.LENGTH_LONG).show();
					return;
				}


				if (!password.getText().toString().equals(password2.getText().toString())) {
					Toast.makeText(application, R.string.new_account_password_mismatch_error, Toast.LENGTH_LONG).show();
					return;
				}

				if (captcha.getText().length() == 0) {
					Toast.makeText(application, R.string.new_account_no_kaptcha_error, Toast.LENGTH_LONG).show();
					return;
				}

				final ProgressDialog progressDialog = ProgressDialog.show(getActivity(), "", getString(R.string.creating_account), true);

				
				progressDialog.show();

				final Handler handler = new Handler();

				new Thread(){
					public void run() {
						try {
							application.getRemoteWallet().setTemporyPassword(password.getText().toString());
							
							
							System.out.println(captcha.getText().toString());
							
							application.getRemoteWallet().remoteSave(captcha.getText().toString());

							handler.post(new Runnable() {
								public void run() {
									progressDialog.dismiss();

									dismiss();
									
									Toast.makeText(getActivity().getApplication(), R.string.new_account_success, Toast.LENGTH_LONG).show();
									
									Editor edit = PreferenceManager.getDefaultSharedPreferences(application.getApplicationContext()).edit();

									edit.putString("guid", application.getRemoteWallet().getGUID());
									edit.putString("sharedKey", application.getRemoteWallet().getSharedKey());
									edit.putString("password", password.getText().toString());

									if (edit.commit()) {
										application.loadRemoteWallet();
									} else {
										
										AbstractWalletActivity activity = (AbstractWalletActivity) getActivity();
										
										activity.errorDialog(R.string.error_pairing_wallet, "Error saving preferences");
									}
								}
							});
						} catch (final Exception e) {
							e.printStackTrace();

							handler.post(new Runnable() {
								public void run() {
									progressDialog.dismiss();

									refreshCaptcha(view);
									
									captcha.setText(null);
									
									Toast.makeText(getActivity().getApplication(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
								}
							});
						}
					}
				}.start();
			}
		});

		return dialog.create();
	}
}
