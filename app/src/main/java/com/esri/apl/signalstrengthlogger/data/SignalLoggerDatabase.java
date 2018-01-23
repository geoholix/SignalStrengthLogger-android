package com.esri.apl.signalstrengthlogger.data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {SignalReading.class}, version = 1)
public abstract class SignalLoggerDatabase extends RoomDatabase {
  private static SignalLoggerDatabase INSTANCE;
  public abstract SignalReadingDAO signalReadingDAO();
}
