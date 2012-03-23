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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import de.schildbach.wallet.ui.PairWalletActivity;
import piuk.blockchain.R;
import piuk.blockchain.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public final class WelcomeFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = WelcomeFragment.class.getName();

	public static DialogFragment show(final FragmentManager fm)
	{	
		
		
		final DialogFragment prev = (DialogFragment) fm.findFragmentById(R.layout.welcome_dialog);
		
		final FragmentTransaction ft = fm.beginTransaction();

		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		
		ft.addToBackStack(null);
		
		final DialogFragment newFragment = instance();

		newFragment.show(ft, FRAGMENT_TAG);

		return newFragment;
	}

	private static WelcomeFragment instance()
	{
		final WelcomeFragment fragment = new WelcomeFragment();

		return fragment;
	}


	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(R.string.welcome_title);

		final View view = inflater.inflate(R.layout.welcome_dialog, null);

		dialog.setView(view);

		final Button pairDeviceButton = (Button) view.findViewById(R.id.pair_device_button);
		final Button newAccountButton = (Button) view.findViewById(R.id.new_account_button);
		final TextView welcomeText = (TextView) view.findViewById(R.id.welcome_text);

		final WalletApplication application = (WalletApplication) getActivity().getApplication();

		if (application.getRemoteWallet().isNew()) {
			welcomeText.setText(R.string.welcome_text_no_account);

			dialog.setCancelable(false);
		} else {
			dialog.setCancelable(true);

			welcomeText.setText(R.string.welcome_text_has_account);
		}

		pairDeviceButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {	
				dismiss();

				System.out.println("Start activity");
				
				startActivity(new Intent(getActivity(), PairWalletActivity.class));
			}
		});

		if (application.getRemoteWallet().isNew()) {

			newAccountButton.setVisibility(View.VISIBLE);

			newAccountButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					try {
						dismiss();

						NewAccountFragment.show(getFragmentManager());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		} else {
			newAccountButton.setVisibility(View.GONE);
		}

		return dialog.create();
	}
}
