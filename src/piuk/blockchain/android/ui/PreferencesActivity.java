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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import piuk.blockchain.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public final class PreferencesActivity extends PreferenceActivity
{
	private static final String KEY_ABOUT_VERSION = "about_version";
	private static final String KEY_ABOUT_LICENSE = "about_license";
	private static final String KEY_ABOUT_SOURCE = "about_source";
	private static final String KEY_ABOUT_PIVACY = "about_privacy_policy";
	private static final String KEY_ABOUT_DISCLAIMER = "about_disclaimer";

	private static final String KEY_ABOUT_CREDITS_BITCOINJ = "about_credits_bitcoinj";
	private static final String KEY_ABOUT_CREDITS_ZXING = "about_credits_zxing";
	private static final String KEY_ABOUT_CREDITS_ICON = "about_credits_icon";
	private static final String KEY_ABOUT_AUTHOR_TWITTER = "about_author_twitter";
	private static final String KEY_ABOUT_AUTHOR_GOOGLEPLUS = "about_author_googleplus";
	private static final String KEY_ABOUT_BITCOIN_WALLET_FOR_ANDROID = "about_credits_bitcoin_wallet_android";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		findPreference(KEY_ABOUT_VERSION).setSummary(((WalletApplication) getApplication()).applicationVersionName());
		findPreference(KEY_ABOUT_LICENSE).setSummary(Constants.LICENSE_URL);
		findPreference(KEY_ABOUT_SOURCE).setSummary(Constants.SOURCE_URL);
		findPreference(KEY_ABOUT_CREDITS_BITCOINJ).setSummary(Constants.CREDITS_BITCOINJ_URL);
		findPreference(KEY_ABOUT_CREDITS_ZXING).setSummary(Constants.CREDITS_ZXING_URL);
		findPreference(KEY_ABOUT_CREDITS_ICON).setSummary(Constants.CREDITS_ICON_URL);
		findPreference(KEY_ABOUT_PIVACY).setSummary(Constants.PRIVACY_POLICY);
		findPreference(KEY_ABOUT_DISCLAIMER).setSummary(Constants.DISCLAIMER);
		findPreference(KEY_ABOUT_BITCOIN_WALLET_FOR_ANDROID).setSummary(Constants.CREDITS_BITCON_WALLET_ANDROID);

	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();
		if (KEY_ABOUT_LICENSE.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.LICENSE_URL)));
			finish();
		}
		else if (KEY_ABOUT_SOURCE.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.SOURCE_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_BITCOINJ.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_BITCOINJ_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_ZXING.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ZXING_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_ICON.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ICON_URL)));
			finish();
		}
		else if (KEY_ABOUT_AUTHOR_TWITTER.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.AUTHOR_TWITTER_URL)));
			finish();
		}
		else if (KEY_ABOUT_AUTHOR_GOOGLEPLUS.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.AUTHOR_GOOGLEPLUS_URL)));
			finish();
		}
		else if (KEY_ABOUT_DISCLAIMER.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DISCLAIMER)));
			finish();
		}
		else if (KEY_ABOUT_PIVACY.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.PRIVACY_POLICY)));
			finish();
		}
		else if (KEY_ABOUT_BITCOIN_WALLET_FOR_ANDROID.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_BITCON_WALLET_ANDROID)));
			finish();
		}


		return false;
	}
}
