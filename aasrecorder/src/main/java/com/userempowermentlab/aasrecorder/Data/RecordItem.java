package com.userempowermentlab.aasrecorder.Data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

/**
 * The database item for a recording
 */

@Entity(tableName = "recorditem_table")
public class RecordItem {
    @NonNull
    public String filename;

    @PrimaryKey
    @NonNull
    public String path;

    public int duration; //sec
    public String createDate;

    public boolean should_keep; //whether the clip should be kept and upload

    public boolean uploaded;
}
