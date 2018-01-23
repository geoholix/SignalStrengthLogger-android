package com.esri.apl.signalstrengthlogger.data;

import android.arch.persistence.room.Entity;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;

import com.esri.apl.signalstrengthlogger.R;

import java.security.InvalidParameterException;
import java.util.Date;

@Entity
public class SignalReading {

    private Context context;

    private Location location = null;
    private int signalStrength = 0;
    // TODO GMT?
    private Date date = new Date();
    private String osName = "Android";
    private String osVersion = Integer.toString(Build.VERSION.SDK_INT);
    private String phoneModel = Build.MANUFACTURER + " " + Build.MODEL;
    private String deviceId;


    public SignalReading(Context context) {
        this.context = context;
    }

    public SignalReading setLocation(Location location) {
        this.location = location;
        return this;
    }
    public SignalReading setDeviceId(String deviceId) {
      this.deviceId = deviceId;
      return this;
    }

/*    private double batteryPercent() {
        Intent batteryIntent =
                context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if(level == -1 || scale == -1) {
            return Double.NaN;
        }

        return ((double)level / (float)scale) * 100.0f;
    }*/


 /*   private String formatParamForJSON(int param) {
        if (param == Integer.MIN_VALUE) return "null";
        else return String.format("%d", param);
    }*/
    private String formatParamForJSON(String param) {
        if (TextUtils.isEmpty(param)) return "null";
        else return param;
    }
    private String formatParamForJSON(double param) {
        if (param == Double.NaN) return "null";
        else return String.format("%f", param);
    }
    public String getFeatureJSON() {
        if (this.location == null)
            throw new InvalidParameterException(context.getString(R.string.exc_bad_QSFeat_params));

/*        String sJSON = context.getString(R.string.http_add_feature_json,
                formatParamForJSON(this.location.getLongitude()),
                formatParamForJSON(this.location.getLatitude()),
                TextUtils.isEmpty(this.userId)
                        ? "null" : "\"" + formatParamForJSON(this.userId) + "\"",
                formatParamForJSON(location.getTime()),
                formatParamForJSON(this.sex),
                formatParamForJSON(this.mood),
                formatParamForJSON(this.age),
                location.hasAccuracy() ? formatParamForJSON(location.getAccuracy()) : "null",
                Double.isNaN(battery) ? "null" : battery,
                formatParamForJSON(os),
                formatParamForJSON(phoneHardware()),
                location.hasBearing() ? formatParamForJSON(location.getBearing()) : "null",
                location.hasSpeed() ? formatParamForJSON(location.getSpeed()) : "null",
                location.hasAltitude() ? formatParamForJSON(location.getAltitude()) : "null"
        );*/

        String sJSON = null;
        return sJSON;
    }
}
