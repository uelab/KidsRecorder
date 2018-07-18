package com.userempowermentlab.kidsrecorder.Data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity(tableName = "recorditem_table")
public class RecordItem {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String filename;

    public int duration; //sec
    public String createDate;

    public boolean uploaded;
}
