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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
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
import android.widget.Toast;
import piuk.blockchain.R;
import piuk.blockchain.android.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public final class SecondPasswordFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = SecondPasswordFragment.class.getName();
	private SuccessCallback callback = null;

	public interface SuccessCallback {
		public void onSuccess();
		public void onFail();
	}
	public static DialogFragment show(final FragmentManager fm, SuccessCallback callback)
	{	
		final DialogFragment prev = (DialogFragment) fm.findFragmentById(R.layout.second_password_dialog);

		final FragmentTransaction ft = fm.beginTransaction();

		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}

		ft.addToBackStack(null);

		final SecondPasswordFragment newFragment = instance();

		newFragment.show(ft, FRAGMENT_TAG);

		newFragment.callback = callback;

		return newFragment;
	}

	private static SecondPasswordFragment instance()
	{
		final SecondPasswordFragment fragment = new SecondPasswordFragment();

		return fragment;
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		callback.onFail();
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(R.string.second_password_title);

		final View view = inflater.inflate(R.layout.second_password_dialog, null);

		dialog.setView(view);

		final TextView passwordField = (TextView) view.findViewById(R.id.second_password);
		final Button continueButton = (Button) view.findViewById(R.id.second_password_continue);

		final WalletApplication application = (WalletApplication) getActivity().getApplication();

		continueButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {	

				try {
					if (passwordField.getText() == null)
						return;

					String secondPassword = passwordField.getText().toString();

					if (application.getRemoteWallet().validateSecondPassword(secondPassword)) {
						application.getRemoteWallet().setTemporySecondPassword(secondPassword);

						Toast.makeText(getActivity().getApplication(), R.string.second_password_correct, Toast.LENGTH_SHORT).show();

						dismiss();

						callback.onSuccess();
					} else {
						Toast.makeText(getActivity().getApplication(), R.string.second_password_incorrect, Toast.LENGTH_SHORT).show();
					} 
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		return dialog.create();
	}
}
