package com.userempowermentlab.kidsrecorder.Data;

import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.AlphabeticIndex;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.userempowermentlab.kidsrecorder.Helper;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by mingrui on 7/16/2018.
 */

//a singleton class for managing local resources
public class DataManager {
    private final String STORAGE = "com.userempowermentlab.kidsrecorder.Data";
    private static final DataManager instance = new DataManager();

    private int bufferSize = 0; // buffer file or not; if buffered, the file will be delayed to upload after the buffer size is reached
    private int maxFilesBeforeDelete = 0; // 0 - never delete; > 0 - delete the old files if more than the number of recording exists
    private String folderName = null; // the folder name of the recorded files

    //file list arrays
    private ArrayList<RecordItem> mFolderFileList;
    //we need the lists to be thread-safe
    private List<String> mFileUploading = Collections.synchronizedList(new ArrayList<String>());
    private List<String> mFileBuffer = Collections.synchronizedList(new ArrayList<String>());

    //permanent storage. DB is for file information, Preferences is for uploading buffer
    private RecordItemDAO recordItemDAO;
    private List<RecordItem> recordLists;
    private Context context;
    private SharedPreferences preferences;

    private Handler mHandler = new Handler();

    //call the Initialize at first after set the folder name
    public void Initialize(Context context) {
        this.context = context;

        //db stuff
        RecordItemRoomDatabase db = RecordItemRoomDatabase.getDatabase(context);
        recordItemDAO = db.recordItemDAO();

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                mFolderFileList = new ArrayList<RecordItem>(recordItemDAO.getAllRecordings());

                //update list
                for (RecordItem item : mFolderFileList){
                    final File record = new File(item.filename);
                    if (!record.exists()) {
                        mFolderFileList.remove(item);
                        new deleteAsyncTask(recordItemDAO).execute(item);
                    }
                }
            }
        });
        loadBuffer();
    }

    //singleton
    private DataManager(){}
    public static DataManager getInstance(){ return instance; }

    //setters
    public void setMaxFilesBeforeDelete(int maxFilesBeforeDelete) {
        this.maxFilesBeforeDelete = maxFilesBeforeDelete;
    }

    public void setFolderName(String folderName) throws IOException{
        this.folderName = Environment.getExternalStorageDirectory() +
                File.separator + folderName;
        File folder = new File(this.folderName);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (!success) {
            this.folderName = null;
            throw new IOException("Create Folder Failed - Permission Denied");
        }
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    //buffer
    public void storeBuffer() {

        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(mFileBuffer);
        editor.putString("bufferList", json);
        editor.apply();
    }

    private void loadBuffer() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = preferences.getString("bufferList", null);
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        mFileBuffer = gson.fromJson(json, type);

        if (json == null){
            mFileBuffer = new ArrayList<String>();
        }
        //check if every file in bufferlist exists
        for (int i = mFileBuffer.size()-1; i >= 0; --i){
            File f = new File(mFileBuffer.get(i));
            if (!f.exists())
                mFileBuffer.remove(i);
        }
    }

    private void uploadBuffer() {
        if (!Helper.CheckNetworkConnected(context)) return;
        String filename;
        synchronized (mFileBuffer) {
            final String fname = mFileBuffer.remove(0);
            synchronized (mFileUploading) {
                mFileUploading.add(fname);
            }
            filename = fname;
        }
        //upload fname
        uploadFile(filename);
    }

    //uploading
    private void uploadFile(String fname) {
        if (!Helper.CheckNetworkConnected(context)) return;
        String[] tokens = fname.split("/");
        DataUploader.AmazonAWSUploader(context, fname, "public/"+tokens[tokens.length-1]);
    }

    //when uploading finished, upload the next buffer if buffersize is enough
    public void OnUploadFinished(String filename) {
        if (bufferSize > 0) {
            synchronized (mFileUploading) {
                mFileUploading.remove(filename);
            }
            if (mFileBuffer.size() > bufferSize)
                uploadBuffer();
        }
        RecordItem item = findItemByFilename(filename);
        if (item != null) {
            item.uploaded = true;
            new updateAsyncTask(recordItemDAO).execute(item);
        }
    }

    public void OnUploadError(String filename) {
        if (bufferSize > 0) {
            synchronized (mFileUploading) {
                mFileUploading.remove(filename);
            }
            synchronized (mFileBuffer) {
                mFileBuffer.add(0, filename);
            }
            if (!Helper.CheckNetworkConnected(context)) return;
            if (mFileBuffer.size() > bufferSize)
                uploadBuffer();
        } else {
            if (!Helper.CheckNetworkConnected(context)) return;
            //upload again
            uploadFile(filename);
        }
    }

    // fileNames
    public String getRecordingNameOfTime(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp += ".wav";
        return folderName + File.separator + timeStamp;
    }

    public String getRecordingNameOfTimeWithPrefix(String prefix) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp = prefix + timeStamp + ".wav";
        return folderName + File.separator + timeStamp;
    }

    //new recording added, clean old files
    public void newRecordingAdded(String filename, String createdate, int duration) {
        RecordItem newitem = new RecordItem();
        newitem.filename = filename;
        newitem.createDate = createdate;
        newitem.duration = duration;
        newitem.uploaded = false;

        mFolderFileList.add(0, newitem);
        new insertAsyncTask(recordItemDAO).execute(newitem);

        deleteFilesOutOfMaxFiles();
        //if no buffer, upload new files
        if (bufferSize == 0) {
            //upload
            uploadFile(filename);
        } else {
            mFileBuffer.add(filename);
            storeBuffer();
            if (mFileBuffer.size() > bufferSize)
                uploadBuffer();
        }
    }

    //local file operations
    private void deleteFilesOutOfMaxFiles() {
        if (folderName != null){
            if (maxFilesBeforeDelete <= 0) return;
            int size = mFolderFileList.size();
            if (size > maxFilesBeforeDelete){
                for (int i = size-1; i >= maxFilesBeforeDelete; --i){
                    String fname = mFolderFileList.get(i).filename;
                    // if the file is in the buffer, we should wait until it's uploaded
                    if ( !(mFileBuffer.contains(fname) || mFileUploading.contains(fname)) ) {
                        deleteFile(mFolderFileList.get(i));
                    }
                }
            }
        }
    }

    public void deleteFile(RecordItem item) {
        File file = new File(item.filename);
        file.delete();
//        Log.d("[RAY]", "deleteFileAtLocation: "+ item.filename + "uploaded? " + item.uploaded + " starting date: " + item.createDate);

        //remove from every list
        mFolderFileList.remove(item);
        new deleteAsyncTask(recordItemDAO).execute(item);
        synchronized (mFileBuffer) {
            mFileBuffer.remove(item.filename);
        }
        synchronized (mFileUploading) {
            mFileUploading.remove(item.filename);
        }
    }

    private RecordItem findItemByFilename(String fname) {
        for(RecordItem item : mFolderFileList) {
            if(item.filename == fname) {
                return item;
            }
        }
        return null;
    }

    //DB Operations
    private static class insertAsyncTask extends AsyncTask<RecordItem, Void, Void> {

        private RecordItemDAO mAsyncTaskDao;

        insertAsyncTask(RecordItemDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final RecordItem... params) {
            mAsyncTaskDao.insert(params[0]);
            return null;
        }
    }

    private static class deleteAsyncTask extends AsyncTask<RecordItem, Void, Void> {

        private RecordItemDAO mAsyncTaskDao;

        deleteAsyncTask(RecordItemDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final RecordItem... params) {
            mAsyncTaskDao.delete(params[0]);
            return null;
        }
    }

    private static class updateAsyncTask extends AsyncTask<RecordItem, Void, Void> {

        private RecordItemDAO mAsyncTaskDao;

        updateAsyncTask(RecordItemDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final RecordItem... params) {
            mAsyncTaskDao.update(params[0]);
            return null;
        }
    }
}
