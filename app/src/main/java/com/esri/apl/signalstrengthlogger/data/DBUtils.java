package com.esri.apl.signalstrengthlogger.data;

import android.content.Context;
import android.text.TextUtils;

import com.esri.apl.signalstrengthlogger.R;

public class DBUtils {
  private static String formatParamForJSON(int param) {
    if (param == Integer.MIN_VALUE) return "null";
    else return String.format("%d", param);
  }
  private static String formatParamForJSON(long param) {
    if (param == Long.MIN_VALUE) return "null";
    else return String.format("%d", param);
  }
  private static String formatParamForJSON(String param) {
    if (TextUtils.isEmpty(param)) return "null";
    else return param;
  }
  private static String formatParamForJSON(double param) {
    if (param == Double.NaN) return "null";
    else return String.format("%f", param);
  }
  private static String formatParamForJSON(float param) {
    if (param == Float.NaN) return "null";
    else return String.format("%f", param);
  }
  public static String jsonForOneFeature(Context ctx,
                                          float x, float y, float z, float signal, long dateTime,
                                          String osName, String osVersion, String phoneModel,
                                          String deviceId) {
    String json = ctx.getString(R.string.add_one_feature_json,
        formatParamForJSON(x),
        formatParamForJSON(y),
        formatParamForJSON(z),
        formatParamForJSON(signal),
        formatParamForJSON(dateTime),
        formatParamForJSON(osName),
        formatParamForJSON(osVersion),
        formatParamForJSON(phoneModel),
        formatParamForJSON(deviceId));
    return json;
  }
}
