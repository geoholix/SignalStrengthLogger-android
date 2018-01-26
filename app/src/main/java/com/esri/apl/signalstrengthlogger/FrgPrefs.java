package com.esri.apl.signalstrengthlogger;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Toast;

import com.esri.apl.signalstrengthlogger.data.LocalDBUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FrgPrefs extends PreferenceFragmentCompat {
  // onPermissionsResult needs to know when the permissions request was made
  private final static int REQ_PERMISSIONS_ON_STARTUP = 1;
  private final static int REQ_PERMISSIONS_ON_STARTLOGGING = 2;

  private Context mCtx;

  private final static String[] PERMISSIONS_NEEDED =
      new String[] {Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION};

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    this.mCtx = context;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);


    (new Thread(new Runnable() {
      @Override
      public void run() {
        LocalDBUtils dbUtils = new LocalDBUtils(mCtx);
        SQLiteDatabase db = dbUtils.getWritableDatabase();
        db.close();
      }
    })).start();

    checkAndRequestPermissions(REQ_PERMISSIONS_ON_STARTUP);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setupSimplePreferencesScreen();
  }

  /**
   *
   * @param req_code The proper REQ_ constant defined at the top of this class
   * @return true if all permissions have been granted; false if not
   */
  private boolean checkAndRequestPermissions(int req_code) {
    List<String> permsNeeded = new ArrayList<>();
    for (int iPerm = 0; iPerm < PERMISSIONS_NEEDED.length; iPerm++) {
      if (ContextCompat.checkSelfPermission(
          mCtx, PERMISSIONS_NEEDED[iPerm]) != PackageManager.PERMISSION_GRANTED)
        permsNeeded.add(PERMISSIONS_NEEDED[iPerm]);
    }
    int iPermsNeeded = permsNeeded.size();
    if (iPermsNeeded > 0)
      requestPermissions(permsNeeded.toArray(new String[iPermsNeeded]), req_code);

    return (iPermsNeeded == 0);
  }

  /**
   * Shows the simplified settings UI if the device configuration if the
   * device configuration dictates that a simplified, single-pane UI should be
   * shown.
   */
  private void setupSimplePreferencesScreen() {
/*        if (!isSimplePreferences(this)) {
            return;
        }*/

    // In the simplified UI, fragments are not used at all and we instead
    // use the older PreferenceActivity APIs.

    // Compute a first-time default for device id
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
    String deviceId = prefs.getString(getString(R.string.pref_key_device_id), "");
    if (TextUtils.isEmpty(deviceId)) {
      deviceId = UUID.randomUUID().toString();
      prefs.edit().putString(getString(R.string.pref_key_device_id), deviceId).commit();
    }
/*    Preference prefId = findPreference(getString(R.string.pref_key_device_id));
    if (TextUtils.isEmpty(
        prefId.getSharedPreferences().getString(prefId.getKey(), "")
    ))
    {
//      String sDeviceId = null; // = InstanceID.getInstance(this).getId();
//          Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
      String sDeviceId = UUID.randomUUID().toString();
      prefId.getEditor().putString(prefId.getKey(), sDeviceId).commit();
    }*/

    // Add 'general' preferences.
    addPreferencesFromResource(R.xml.prefs_ui);

/*        // Add 'notifications' preferences, and a corresponding header.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_notifications);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_notification);

        // Add 'data and sync' preferences, and a corresponding header.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_data_sync);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_data_sync);*/

    // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
    // their values. When their values change, their summaries are updated
    // to reflect the new value, per the Android Design guidelines.
/*        bindPreferenceSummaryToValue(findPreference(
            getString(R.string.pref_key_tracking_enabled)));*/
    // Ordinarily wouldn't bind on/off but need to start/stop service when changed
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_logging_enabled)));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_feat_svc_url)));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_user_id)));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_user_pw)));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_tracking_displacement)));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_tracking_interval)
    ));
    bindPreferenceSummaryToValue(findPreference(
        getString(R.string.pref_key_device_id)));

/*        bindPreferenceSummaryToValue(findPreference("notifications_new_message_ringtone"));
        bindPreferenceSummaryToValue(findPreference("sync_frequency"));*/
  }

  /**
   * {@inheritDoc}
   */
/*    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }*/

  /**
   * Helper method to determine if the device has an extra-large screen. For
   * example, 10" tablets are extra-large.
   */
 /*   private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }*/

  /**
   * Determines whether the simplified settings UI should be shown. This is
   * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
   * doesn't have newer APIs like {@link PreferenceFragment}, or the device
   * doesn't have an extra-large screen. In these cases, a single-pane
   * "simplified" settings UI should be shown.
   */
/*    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }*/

  /**
   * {@inheritDoc}
   */
/*    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }*/

  /**
   * A preference value change listener that updates the preference's summary
   * to reflect its new value.
   */
  private Preference.OnPreferenceChangeListener mBindPreferenceSummaryToValueListener =
      new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
          String stringValue = TextUtils.isEmpty(String.valueOf(value))
              ? "<Unspecified>" : String.valueOf(value);

          if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                index >= 0
                    ? listPreference.getEntries()[index]
                    : null);
          } else if (preference.getKey().equals(getString(R.string.pref_key_user_pw)) && value != null) {
            // Replace password with asterisks
            int pwLen = value.toString().length();
            String sPwMask = new String(new char[pwLen]).replace("\0", "*");
            preference.setSummary(sPwMask);
          } else if (preference.getKey().equals(getString(
              R.string.pref_key_logging_enabled))
              && value != null && value instanceof Boolean) {
            // Start/stop the tracking service
            boolean isTrackingEnabled = (boolean) value;
            if (!isTrackingEnabled) stopLogging();
            else { // Trying to start logging
              // Check that permissions have been granted
              EditTextPreference prefUserId = (EditTextPreference)getPreferenceScreen()
                  .findPreference(getString(R.string.pref_key_user_id));
              if (prefUserId.getText().isEmpty()) {
                Toast toast = Toast.makeText(mCtx,
                    "Please set a User ID to allow logging", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();
                return false;
              }
              if (checkAndRequestPermissions(REQ_PERMISSIONS_ON_STARTLOGGING))
                startLogging();
              else
                return false;
            }
          } else if (preference.getKey().equals(getString(R.string.pref_key_tracking_interval))) {
            // Validate tracking interval
            String sMinInterval = getString(R.string.pref_min_tracking_interval);
            String sMaxInterval = getString(R.string.pref_max_tracking_interval);
            int iMinInterval = Integer.parseInt(sMinInterval);
            int iMaxInterval = Integer.parseInt(sMaxInterval);

            int iInterval;
            boolean bIsValidInterval = true;

            if (TextUtils.isEmpty(stringValue) || !TextUtils.isDigitsOnly(stringValue)) {
              bIsValidInterval = false;
            } else {
              iInterval = Integer.parseInt(stringValue);
              if (iInterval < iMinInterval || iInterval > iMaxInterval) {
                bIsValidInterval = false;
              }
            }
            if (!bIsValidInterval) {
              Toast toast = Toast.makeText(
                  mCtx,
                  getString(R.string.validation_interval_displacement, iMinInterval, iMaxInterval),
                  Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
              toast.show();
              return false;
            } else preference.setSummary(stringValue);
          } else if (preference.getKey().equals(getString(R.string.pref_key_tracking_displacement))) {
            // Validate tracking displacement
            String sMinDisp = getString(R.string.pref_min_tracking_displacement);
            String sMaxDisp = getString(R.string.pref_max_tracking_displacement);
            int iMinDisp = Integer.parseInt(sMinDisp);
            int iMaxDisp = Integer.parseInt(sMaxDisp);

            int iDisp;
            boolean bIsValidDisp = true;

            if (TextUtils.isEmpty(stringValue) || !TextUtils.isDigitsOnly(stringValue)) {
              bIsValidDisp = false;
            } else {
              iDisp = Integer.parseInt(stringValue);
              if (iDisp < iMinDisp || iDisp > iMaxDisp) {
                bIsValidDisp = false;
              }
            }
            if (!bIsValidDisp) {
              Toast toast = Toast.makeText(
                  mCtx,
                  getString(R.string.validation_interval_displacement, iMinDisp, iMaxDisp),
                  Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
              toast.show();
              return false;
            } else preference.setSummary(stringValue);
          } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
          }
          return true;
        }
      };

  /**
   * Binds a preference's summary to its value. More specifically, when the
   * preference's value is changed, its summary (line of text below the
   * preference title) is updated to reflect the value. The summary is also
   * immediately updated upon calling this method. The exact display format is
   * dependent on the type of preference.
   *
   * @see #mBindPreferenceSummaryToValueListener
   */
  private void bindPreferenceSummaryToValue(Preference preference) {
    // Set the listener to watch for value changes.
    preference.setOnPreferenceChangeListener(mBindPreferenceSummaryToValueListener);

    // Trigger the listener immediately with the preference's
    // current value.
    mBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
        PreferenceManager
            .getDefaultSharedPreferences(preference.getContext())
            .getAll()
            .get(preference.getKey()));
  }



  private void startLogging() {
    SwitchPreferenceCompat prefLogging = (SwitchPreferenceCompat) getPreferenceScreen()
        .findPreference(getString(R.string.pref_key_logging_enabled));
    prefLogging.setChecked(true);

    Intent intent = new Intent();
    intent.setClass(mCtx, SvcLocationLogger.class);
    getActivity().startService(intent);
  }

  private void stopLogging() {
    Intent intent = new Intent(mCtx, SvcLocationLogger.class);
//    intent.setClass(this, SvcLocationLogger.class);
    getActivity().stopService(intent);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    boolean allPermsGranted = true;
    for (int perm : grantResults)
      if (perm != PackageManager.PERMISSION_GRANTED) {
        allPermsGranted = false;
        break;
      }

    if (!allPermsGranted) {
      // Present rationale
      AlertDialog dlg = new AlertDialog.Builder(mCtx)
          .setMessage("Location and phone signal strength permissions must be granted to allow logging to begin.")
          .setTitle("Permission Needed")
          .setPositiveButton(android.R.string.ok, null)
          .create();
      dlg.show();
    } else if (requestCode == REQ_PERMISSIONS_ON_STARTLOGGING) {
      startLogging();
    }
  }
}
