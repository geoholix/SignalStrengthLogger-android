package com.esri.apl.signalstrengthlogger.util;

import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.esri.apl.signalstrengthlogger.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Backup all user-entered settings except
 * <ul>
 *   <li>Logging enabled/disabled</li>
 *   <li>User name</li>
 *   <li>Password</li>
 * </ul>
 */
public class PrefsBackup extends android.app.backup.BackupAgent {
  private final static String TAG = "PrefsBackup";
  private final static String ENTITY_HEADER_KEY = "com.esri.apl.signalstrengthlogger.PrefsBackup_entityheaderkey";

  @Override
  public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
    Log.d(TAG, "Backup onBackup");

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    try (FileInputStream oldStateInStream = new FileInputStream(oldState.getFileDescriptor());
         DataInputStream oldStateIn = new DataInputStream(oldStateInStream);
         FileOutputStream newStateOutStream = new FileOutputStream(newState.getFileDescriptor());
         DataOutputStream newStateOut = new DataOutputStream(newStateOutStream)) {
      boolean bDoBackup = true;
      try {
        long prefsLastModified = prefs.getLong(
              getString(R.string.pref_key_last_modified_datetime), Long.MIN_VALUE);
        long stateLastModified = oldStateIn.readLong();
        bDoBackup = (stateLastModified != prefsLastModified);
      } finally {
        if (bDoBackup) doBackup(data);

        // Finally update state and prefs to match
        long updateTime = System.currentTimeMillis();
        prefs.edit().putLong(getString(R.string.pref_key_last_modified_datetime), updateTime).apply();

        newStateOut.writeLong(updateTime);
        newStateOut.flush(); newStateOut.close();
      }
    }
  }
  private void doBackup(BackupDataOutput data) throws IOException {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(outputStream)) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

      // Save pref values to backup
      String sLogDisplacement = prefs.getString(getString(R.string.pref_key_tracking_displacement), "10");
      String sLogInterval = prefs.getString(getString(R.string.pref_key_tracking_interval), "10");
      String sSyncInterval = prefs.getString(getString(R.string.pref_key_sync_interval), "10");
      String sFeatSvcUrl = prefs.getString(getString(R.string.pref_key_feat_svc_url), "");
      String sTokenSvcUrl = prefs.getString(getString(R.string.pref_key_token_url), "");
      String sDeviceId = prefs.getString(getString(R.string.pref_key_device_id), "<unavailable>");

      int iLogDisplacement = Integer.parseInt(sLogDisplacement);
      int iLogInterval = Integer.parseInt(sLogInterval);
      int iSyncInterval = Integer.parseInt(sSyncInterval);

      out.writeInt(iLogDisplacement);
      out.writeInt(iLogInterval);
      out.writeInt(iSyncInterval);
      out.writeUTF(sFeatSvcUrl);
      out.writeUTF(sTokenSvcUrl);
      out.writeUTF(sDeviceId);
      out.flush();

      byte[] buffer = outputStream.toByteArray();
      int len = buffer.length;
      data.writeEntityHeader(ENTITY_HEADER_KEY, len);
      data.writeEntityData(buffer, len);
    }
  }

  @Override
  public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
    Log.d(TAG, "Backup onRestore");

    SharedPreferences.Editor prefsEditor =
        PreferenceManager.getDefaultSharedPreferences(this).edit();
    long updateTime = System.currentTimeMillis();

    while (data.readNextHeader()) {
      String key = data.getKey();
      int datasize = data.getDataSize();
      if (ENTITY_HEADER_KEY.equals(key)) {
        byte[] buffer = new byte[datasize];
        data.readEntityData(buffer, 0, datasize);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);
        DataInputStream in = new DataInputStream(inputStream);

        // Read values
        int iLogDisplacement = in.readInt();
        int iLogInterval = in.readInt();
        int iSyncInterval = in.readInt();
        String sFeatSvcUrl = in.readUTF();
        String sTokenSvcUrl = in.readUTF();
        String sDeviceId = in.readUTF();

        // Write values to prefs
        prefsEditor.putString(getString(R.string.pref_key_tracking_displacement),
            Integer.toString(iLogDisplacement));
        prefsEditor.putString(getString(R.string.pref_key_tracking_interval),
            Integer.toString(iLogInterval));
        prefsEditor.putString(getString(R.string.pref_key_sync_interval),
            Integer.toString(iSyncInterval));
        prefsEditor.putString(getString(R.string.pref_key_feat_svc_url), sFeatSvcUrl);
        prefsEditor.putString(getString(R.string.pref_key_token_url), sTokenSvcUrl);
        prefsEditor.putString(getString(R.string.pref_key_device_id), sDeviceId);

        prefsEditor.apply();
      } else {
        // Unknown entity key
        data.skipEntityData();
      }
    }

    // Update state and prefs to match
    prefsEditor.putLong(getString(R.string.pref_key_last_modified_datetime), updateTime).apply();
    try (FileOutputStream outputStream = new FileOutputStream(newState.getFileDescriptor());
         DataOutputStream out = new DataOutputStream(outputStream)) {
      out.writeLong(updateTime);
    }
  }
}
