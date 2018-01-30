package com.esri.apl.signalstrengthlogger.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.esri.apl.signalstrengthlogger.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

  /** Meant to be called from a worker thread **/
  public static void deletePreviouslyPostedRecords(SQLiteDatabase db, Context ctx) {
    String where = ctx.getString(R.string.whereclause_posted_records,
        ctx.getString(R.string.columnname_was_added_to_fc));
    String table = ctx.getString(R.string.tablename_readings);
    int iRes = db.delete(table, where, null);
  }

  /** Meant to be called from a worker thread **/
  public static Cursor getUnpostedRecords(SQLiteDatabase db, Context ctx) {
    String table = ctx.getString(R.string.viewname_unposted_records);
/*    String where = getString(R.string.whereclause_unposted_records,
        getString(R.string.columnname_was_added_to_fc));*/
    return
        db.query(table, null, null, null, null, null, null);
  }

  /** Meant to be called from a worker thread
   * @param cur SQLite DB cursor to list of unposted records
   * @param ctx Service context
   * @param sharedPrefs Shared preferences
   * @return list of rowids of records that were successfully posted
   * **/
  public static List<Long> postUnpostedRecords(
      Cursor cur, Context ctx, SharedPreferences sharedPrefs)
      throws IOException, JSONException {
    // Get JSON for adds
    List<String> jsonAdds = new ArrayList<>();
    while (cur.moveToNext()) {
      String jsonAdd = DBUtils.jsonForOneFeature(ctx,
          cur.getFloat(cur.getColumnIndex(ctx.getString(R.string.columnname_longitude))),
          cur.getFloat(cur.getColumnIndex(ctx.getString(R.string.columnname_latitude))),
          cur.getFloat(cur.getColumnIndex(ctx.getString(R.string.columnname_altitude))),
          cur.getFloat(cur.getColumnIndex(ctx.getString(R.string.columnname_signalstrength))),
          cur.getLong(cur.getColumnIndex(ctx.getString(R.string.columnname_date))),
          cur.getString(cur.getColumnIndex(ctx.getString(R.string.columnname_osname))),
          cur.getString(cur.getColumnIndex(ctx.getString(R.string.columnname_osversion))),
          cur.getString(cur.getColumnIndex(ctx.getString(R.string.columnname_phonemodel))),
          cur.getString(cur.getColumnIndex(ctx.getString(R.string.columnname_deviceid)))
      );
      jsonAdds.add(jsonAdd);
    }
    String sJsonAdds = "[" + TextUtils.join(",", jsonAdds.toArray(new String[]{})) + "]";
//    String sParams = "?f=json&features=" + sJsonAdds;

    // Post via REST
    String svcUrl = sharedPrefs.getString(ctx.getString(R.string.pref_key_feat_svc_url), null);
    List<Long> successes = new ArrayList<>();
    OkHttpClient http = new OkHttpClient();
    MediaType mtJSON = MediaType.parse("application/json; charset=utf-8");

    RequestBody reqBody = new FormBody.Builder()
        .add("f", "json")
        .add("rollbackOnFailure", "false")
        .add("features", sJsonAdds)
        .build();
    Request req = new Request.Builder()
        .url(svcUrl)
        .post(reqBody)
        .build();
    Response resp = http.newCall(req).execute();

    // Examine results and return list of rowids of successes
    String jsonRes = resp.body().string();
    JSONObject res = new JSONObject(jsonRes);

    // If error, exit
    if (res.has("error")) {
      throw new IOException("Error adding features: " + res.getJSONObject("error")
        .getJSONArray("details").join("; "));
    }

    // Otherwise, parse the results
    JSONArray addResults = res.getJSONArray("addResults");

    final int rowIdCol = cur.getColumnIndex("rowid");

    cur.moveToFirst();
    for (int iRes = 0; iRes < addResults.length(); iRes++) {
      JSONObject addResult = addResults.getJSONObject(iRes);
      if (addResult.getString("success").equals("true")) {
        long iSuccess = cur.getLong(rowIdCol);
        successes.add(iSuccess);
      }
      cur.moveToNext();
    }

    return successes;
  }

  /** Meant to be called from a worker thread.<p/>
   * Assumption: successes is a valid list (even if empty) **/
  public static void updateNewlySentRecordsAsPosted(SQLiteDatabase db, Context ctx, List<Long> successes) {
    String table = ctx.getString(R.string.tablename_readings);
    String postStatusColumn = ctx.getString(R.string.columnname_was_added_to_fc);
    String where = postStatusColumn + " = 0 "
        + "AND rowid IN ("
        + TextUtils.join(",", successes.toArray(new Long[]{}))
        + ")";
    ContentValues postStatus = new ContentValues();
    postStatus.put(postStatusColumn, 1);
    db.update(table, postStatus, where, null);
  }
}
