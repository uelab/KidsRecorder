package com.userempowermentlab.aasrecorder.Data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * The database Room Data Access Objects
 */
@Dao
public interface RecordItemDAO {

    @Insert
    void insert(RecordItem recordItem);

    @Query("DELETE FROM recorditem_table")
    void deleteAll();

    @Query("SELECT * from recorditem_table ORDER BY createDate DESC")
    List<RecordItem> getAllRecordings();

    @Update
    public void update(RecordItem... recordItems);

    @Delete
    public void delete(RecordItem... recordItems);

}
