package com.userempowermentlab.kidsrecorder.Data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {RecordItem.class}, version = 1)
public abstract class RecordItemRoomDatabase extends RoomDatabase {
    public abstract RecordItemDAO recordItemDAO();
    private static RecordItemRoomDatabase INSTANCE;

    public static RecordItemRoomDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (RecordItemRoomDatabase.class) {
                if (INSTANCE == null) {
                    // Create database here
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            RecordItemRoomDatabase.class, "recorditem_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
