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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.EditAddressBookEntryFragment;
import de.schildbach.wallet.ui.PairWalletActivity;
import de.schildbach.wallet.ui.PreferencesActivity;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WelcomeFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = WelcomeFragment.class.getName();

	private static final String KEY_ADDRESS = "address";

	private String address;

	public static DialogFragment show(final FragmentManager fm)
	{	
		final FragmentTransaction ft = fm.beginTransaction();
		final Fragment prev = fm.findFragmentByTag(FRAGMENT_TAG);
		if (prev != null)
			ft.remove(prev);
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

		pairDeviceButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {					
				startActivity(new Intent(getActivity(), PairWalletActivity.class));
			}
		});
	
		newAccountButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				dismiss();
			}
		});

		return dialog.create();
	}
}
