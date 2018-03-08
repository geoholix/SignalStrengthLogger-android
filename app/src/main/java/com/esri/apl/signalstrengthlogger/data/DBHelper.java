package com.esri.apl.signalstrengthlogger.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.esri.apl.signalstrengthlogger.R;

public class DBHelper extends SQLiteOpenHelper {
  private static final int DATABASE_VERSION = 3;
  private Context mContext;

  public DBHelper(Context context) {
    super(context, context.getString(R.string.databasename), null, DATABASE_VERSION);

    this.mContext = context;
  }

  /** Create the initial database if needed */
  @Override
  public void onCreate(SQLiteDatabase db) {
    String sCreateTableSql = mContext.getString(R.string.table_readings_create_sql,
        mContext.getString(R.string.tablename_readings),
        mContext.getString(R.string.columnname_longitude),
        mContext.getString(R.string.columnname_latitude),
        mContext.getString(R.string.columnname_altitude),
        mContext.getString(R.string.columnname_signalstrength),
        mContext.getString(R.string.columnname_date),
        mContext.getString(R.string.columnname_osname),
        mContext.getString(R.string.columnname_osversion),
        mContext.getString(R.string.columnname_phonemodel),
        mContext.getString(R.string.columnname_deviceid),
        mContext.getString(R.string.columnname_carrierid),
        mContext.getString(R.string.columnname_was_added_to_fc)
    );
    db.execSQL(sCreateTableSql);

    String sCreateIndexSql = mContext.getString(R.string.table_readings_create_indexes_sql,
        mContext.getString(R.string.tablename_readings),
        mContext.getString(R.string.columnname_signalstrength),
        mContext.getString(R.string.columnname_date),
        mContext.getString(R.string.columnname_was_added_to_fc));
    db.execSQL(sCreateIndexSql);

    String sCreateViewSql = mContext.getString(R.string.view_unposted_records_sql,
        mContext.getString(R.string.viewname_unposted_records),
        mContext.getString(R.string.tablename_readings),
        mContext.getString(R.string.columnname_was_added_to_fc));
    db.execSQL(sCreateViewSql);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int i, int i1) {}
}
