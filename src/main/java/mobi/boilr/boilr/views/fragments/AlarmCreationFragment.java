package mobi.boilr.boilr.views.fragments;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import mobi.boilr.boilr.R;
import mobi.boilr.boilr.domain.AndroidNotifier;
import mobi.boilr.boilr.services.LocalBinder;
import mobi.boilr.boilr.services.StorageAndControlService;
import mobi.boilr.boilr.utils.IconToast;
import mobi.boilr.boilr.utils.Log;
import mobi.boilr.libdynticker.core.Exchange;
import mobi.boilr.libdynticker.core.Pair;
import mobi.boilr.libpricealarm.Alarm;
import mobi.boilr.libpricealarm.TimeFrameSmallerOrEqualUpdateIntervalException;
import mobi.boilr.libpricealarm.UpperLimitSmallerOrEqualLowerLimitException;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.MenuItem;

public abstract class AlarmCreationFragment extends AlarmPreferencesFragment {

	@SuppressWarnings("unchecked")
	private class UpdatePairsConnection implements ServiceConnection {
		private String exchangeCode;
		private String exchangeName;
		private String pairString;

		public UpdatePairsConnection(String exchangeCode, String exchangeName, String pairString) {
			super();
			this.exchangeCode = exchangeCode;
			this.exchangeName = exchangeName;
			this.pairString = pairString;
		}

		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			mStorageAndControlService = ((LocalBinder<StorageAndControlService>) binder).getService();
			mBound = true;
			updatePairsList(exchangeCode, exchangeName, pairString);
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mBound = false;
		}
	};

	protected abstract class OnAlarmSettingsPreferenceChangeListener implements
	OnPreferenceChangeListener {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			String key = preference.getKey();
			if(key.equals(PREF_KEY_EXCHANGE)) {
				ListPreference listPref = (ListPreference) preference;
				mExchangeIndex = listPref.findIndexOfValue((String) newValue);
				String exchangeName = (String) listPref.getEntries()[mExchangeIndex];
				listPref.setSummary(exchangeName);
				mPairIndex = 0;
				updatePairsList((String) newValue, exchangeName, null);
			} else if(key.equals(PREF_KEY_PAIR)) {
				mPairIndex = Integer.parseInt((String) newValue);
				preference.setSummary(mPairs.get(mPairIndex).toString());
				updateDependentOnPairAux();
			} else if(key.equals(PREF_KEY_TYPE)) {
				Fragment creationFrag;
				Bundle args = new Bundle();
				args.putInt("exchangeIndex", mExchangeIndex);
				args.putInt("pairIndex", mPairIndex);
				args.putString("alertType", mAlarmAlertTypePref.getValue());
				args.putString("alertSound", mAlertSoundPref.getValue());
				args.putString("vibrate", mVibratePref.getValue());
				if(newValue.equals(PREF_VALUE_PRICE_CHANGE)) {
					creationFrag = new PriceChangeAlarmCreationFragment();
				} else { // newValue.equals(PREF_VALUE_PRICE_HIT))
					creationFrag = new PriceHitAlarmCreationFragment();
				}
				creationFrag.setArguments(args);
				mEnclosingActivity.getFragmentManager().beginTransaction().replace(android.R.id.content, creationFrag).commit();
			} else if(key.equals(PREF_KEY_UPDATE_INTERVAL)) {
				preference.setSummary(mEnclosingActivity.getString(R.string.seconds_abbreviation, newValue));
			} else if(key.equals(PREF_KEY_ALARM_ALERT_TYPE)) {
				ListPreference alertTypePref = (ListPreference) preference;
				String alertType = (String) newValue;
				alertTypePref.setSummary(alertTypePref.getEntries()[alertTypePref.findIndexOfValue(alertType)]);
				// Change selectable ringtones according to the alert type
				mAlertSoundPref.setRingtoneType(alertType);
				mAlertSoundPref.setDefaultValue();
			} else if(key.equals(PREF_KEY_ALARM_ALERT_SOUND)) {
				// Nothing to do.
			} else if(key.equals(PREF_KEY_ALARM_VIBRATE)) {
				ListPreference vibratePref = (ListPreference) preference;
				vibratePref.setSummary(vibratePref.getEntries()[vibratePref.findIndexOfValue((String) newValue)]);
			} else {
				Log.d("No behavior for " + key);
			}
			return true;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String alertType = null, vibrate = null;
		if(savedInstanceState == null) {
			Bundle args = getArguments();
			String alertSound = null;
			if(args != null) {
				mExchangeIndex = args.getInt("exchangeIndex");
				mPairIndex = args.getInt("pairIndex");
				alertType = args.getString("alertType");
				alertSound = args.getString("alertSound");
				vibrate = args.getString("vibrate");
			}
			else {
				mExchangeIndex = mSharedPrefs.getInt("exchangeIndex", mExchangeListPref.findIndexOfValue(mExchangeListPref.getValue()));
				mPairIndex = mSharedPrefs.getInt("pairIndex", 0);
			}

			if(alertType == null)
				alertType = DEFAULT;
			if(vibrate == null)
				vibrate = DEFAULT;

			mAlertSoundPref.setRingtoneType(alertType);
			if(alertSound != null) {
				mAlertSoundPref.setValue(alertSound);
			} else {
				mAlertSoundPref.setDefaultValue();
			}
		} else {
			mExchangeIndex = savedInstanceState.getInt("exchangeIndex");
			mPairIndex = savedInstanceState.getInt("pairIndex");
			alertType = savedInstanceState.getString("alertType");
			vibrate = savedInstanceState.getString("vibrate");

			mAlertSoundPref.setRingtoneType(alertType);
			mAlertSoundPref.setSummary(mAlertSoundPref.getEntry());
		}
		mAlarmAlertTypePref.setValue(alertType);
		mAlarmAlertTypePref.setSummary(mAlarmAlertTypePref.getEntry());
		mVibratePref.setValue(vibrate);
		mVibratePref.setSummary(mVibratePref.getEntry());

		CharSequence exchangeCode = mExchangeListPref.getEntryValues()[mExchangeIndex];
		CharSequence exchangeName = mExchangeListPref.getEntries()[mExchangeIndex];
		mExchangeListPref.setSummary(exchangeName);
		mExchangeListPref.setValueIndex(mExchangeIndex);

		mStorageAndControlServiceConnection = new UpdatePairsConnection(exchangeCode.toString(), exchangeName.toString(), null);
		mEnclosingActivity.bindService(mServiceIntent, mStorageAndControlServiceConnection, Context.BIND_AUTO_CREATE);

		setHasOptionsMenu(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.action_send_now:
				createAlarmAndReturn();
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putInt("exchangeIndex", mExchangeIndex);
		savedInstanceState.putInt("pairIndex", mPairIndex);
		savedInstanceState.putString("alertType", mAlarmAlertTypePref.getValue());
		savedInstanceState.putString("vibrate", mVibratePref.getValue());
	}

	private void createAlarmAndReturn() {
		Integer alertType = mAlarmAlertTypePref.getValue().equals(DEFAULT) ? null : Integer.parseInt(mAlarmAlertTypePref.getValue());
		String alertSound = mAlertSoundPref.getValue().equals(DEFAULT) ? null : mAlertSoundPref.getValue();
		Boolean vibrate = mVibratePref.getValue().equals(DEFAULT) ? null : Boolean.parseBoolean(mVibratePref.getValue());
		AndroidNotifier notifier = new AndroidNotifier(mEnclosingActivity, alertType, alertSound, vibrate);
		String failMsg = mEnclosingActivity.getString(R.string.failed_create_alarm);
		try {
			if(!mBound) {
				throw new IOException(mEnclosingActivity.getString(R.string.not_bound, "AlarmCreationFragment"));
			}
			int id = mStorageAndControlService.generateAlarmID();
			Exchange exchange = mStorageAndControlService.getExchange(mExchangeListPref.getValue());
			Pair pair = mPairs.get(mPairIndex);
			Alarm alarm = makeAlarm(id, exchange, pair, notifier);
			mStorageAndControlService.addAlarm(alarm);
			Intent alarmIdIntent = new Intent();
			alarmIdIntent.putExtra("ALARM_ID", id);
			mEnclosingActivity.setResult(Activity.RESULT_OK, alarmIdIntent);
			mEnclosingActivity.finish();
		} catch(UpperLimitSmallerOrEqualLowerLimitException e) {
			failMsg += " " + mEnclosingActivity.getString(R.string.upper_must_larger_lower);
			Log.e(failMsg, e);
			IconToast.warning(mEnclosingActivity, failMsg);
		} catch(TimeFrameSmallerOrEqualUpdateIntervalException e) {
			failMsg += " " + mEnclosingActivity.getString(R.string.frame_must_longer_interval);
			Log.e(failMsg, e);
			IconToast.warning(mEnclosingActivity, failMsg);
		} catch(Exception e) {
			Log.e(failMsg, e);
			IconToast.warning(mEnclosingActivity, failMsg + " " + e.getMessage());
		}
	}

	protected abstract Alarm makeAlarm(int id, Exchange exchange, Pair pair, AndroidNotifier notifier)
 throws UpperLimitSmallerOrEqualLowerLimitException,
			TimeFrameSmallerOrEqualUpdateIntervalException, IOException, InterruptedException,
			ExecutionException;

	protected void setUpdateIntervalPref() {
		String updateInterval = mSharedPrefs.getString(SettingsFragment.PREF_KEY_DEFAULT_UPDATE_INTERVAL, "");
		mUpdateIntervalPref.setText(updateInterval);
		mUpdateIntervalPref.setSummary(mEnclosingActivity.getString(R.string.seconds_abbreviation, updateInterval));
	}

	protected void checkAndSetUpdateIntervalPref() {
		String updateInterval = mUpdateIntervalPref.getText();
		if(updateInterval == null || updateInterval.equals("")) {
			setUpdateIntervalPref();
		} else {
			mUpdateIntervalPref.setSummary(mEnclosingActivity.getString(R.string.seconds_abbreviation, updateInterval));
		}
	}
}
