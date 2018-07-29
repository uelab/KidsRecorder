package com.userempowermentlab.kidsrecorder.Data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.userempowermentlab.kidsrecorder.Helper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A singleton class for managing local recorded data
 * Function: save/delete recording, provide time naming of the file
 *           Manage uploading, Buffer upload files, Manager preceding files
 * Created by mingrui on 7/16/2018.
 */

public class DataManager {
    private static final DataManager instance = new DataManager();

    private boolean autoUpload = false; // whether the recording should be auto uploaded or manually uploaded
    private int bufferSize = 0; // buffer file or not; if buffered, the file will be delayed to upload after the buffer size is reached
    private int maxFilesBeforeDelete = 0; // 0 - never delete; > 0 - delete the old files if more than the number of recording exists
    private String folderName = null; // the folder name of the recorded files

    //file list arrays
    private ArrayList<RecordItem> mFolderFileList; //mFolderFileList stores all recording files in the folder
    //we need the lists to be thread-safe
    private List<String> mFileUploading = Collections.synchronizedList(new ArrayList<String>()); // the uploading file list
    private List<String> mFileBuffer = Collections.synchronizedList(new ArrayList<String>()); // the uploading buffer

    //if the clip is not keeped, we store them in this buffer
    private List<RecordItem> mShouldNotKeepBuffer = new ArrayList<RecordItem>(); //Only useful in preceding mode

    //permanent storage. DB is for file information, Preferences is for uploading buffer
    private RecordItemDAO recordItemDAO;
    private Context context;
    private SharedPreferences preferences;

    private Handler mHandler = new Handler();

    /**
     * When the manager is instantiated for the first time
     * call the Initialize at first after set the folder name
     * The function initializes the database and retrieves the recording lists in the database
     * Also load the file buffer
     */
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
                for (int i = mFolderFileList.size()-1; i >= 0; --i){
                    RecordItem item = mFolderFileList.get(i);
                    final File record = new File(item.path);
                    if (!record.exists() || !item.should_keep) {
                        Log.d("[RAY]", "delete file, should keep "+item.should_keep);
                        mFolderFileList.remove(i);
                        new deleteAsyncTask(recordItemDAO).execute(item);
                    }
                }
            }
        });
        loadBuffer();
    }

    //singleton
    private DataManager(){}

    /**
     * Get the instance of the manager. It is singleton class, thus there would only be one instance of the class through the whole application
     * Use this function to retrieve the manager
     */
    public static DataManager getInstance(){ return instance; }

    //setters

    /**
     * How many files should be kept in the local storage
     * @param maxFilesBeforeDelete the amount of local recording files should be kept
     */
    public void setMaxFilesBeforeDelete(int maxFilesBeforeDelete) {
        this.maxFilesBeforeDelete = maxFilesBeforeDelete;
    }

    /**
     * Whether the recordings should be uploaded automatically
     */
    public void setAutoUpload(boolean autoUpload) {
        this.autoUpload = autoUpload;
    }

    /**
     * Set the folder name where the recording clips should be stored
     * @param folderName
     */
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

    /**
     * Set the buffer size of the uploading function. The buffer would only be effective when auto-upload is enabled
     * @param bufferSize how large (how many files would be stored before uploading) of the buffer
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    //getters

    /**
     * Get the recording item at position of the mFolderFileList
     * @param pos
     */
    public RecordItem getItemAtPos(int pos) {
        if (mFolderFileList.size() > pos && pos >= 0){
            return mFolderFileList.get(pos);
        }
        return null;
    }

    /**
     * Get how many recording items in mFolderFileList (in the local storage folder)
     */
    public int getItemCout() {
        return mFolderFileList.size();
    }

    //buffer

    /**
     * Store the buffer information in permanent storage
     */
    public void storeBuffer() {

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String buffer_json = gson.toJson(mFileBuffer);
        editor.putString("bufferList", buffer_json);
        editor.apply();
    }

    /**
     * Load the buffer information from permanent storage
     */
    private void loadBuffer() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String buffer_json = preferences.getString("bufferList", null);
        if (buffer_json != null) {
            Type type = new TypeToken<ArrayList<String>>() {
            }.getType();
            mFileBuffer = gson.fromJson(buffer_json, type);
            mFileBuffer = Collections.synchronizedList(mFileBuffer);
            Log.d("[RAY]", "loaded Buffer!");
        }

        //check if every file in bufferlist exists
        for (int i = mFileBuffer.size()-1; i >= 0; --i){
            File f = new File(mFileBuffer.get(i));
            if (!f.exists())
                mFileBuffer.remove(i);
        }
    }

    /**
     * The buffer is full. Upload the first file in the buffer
     */
    private void uploadBuffer() {
        if (!autoUpload || !Helper.CheckNetworkConnected(context)) return;
        String filename;
        synchronized (mFileBuffer) {
            final String fname = mFileBuffer.remove(0);
            filename = fname;
        }
//        Log.d("[RAY]", "now uploading ... "+filename);
        //upload fname
        uploadFile(filename);
    }

    //uploading
    /**
     * Upload the file through fileuploader (here is the amazon uploader)
     */
    public void uploadFile(String fname) {
        if (!Helper.CheckNetworkConnected(context) || mFileUploading.contains(fname)) return;
        if (bufferSize > 0){
            synchronized (mFileUploading) {
                mFileUploading.add(fname);
            }
        }
        String[] tokens = fname.split("/");
        DataUploader.AmazonAWSUploader(context, fname, "public/"+tokens[tokens.length-1]);
    }

    /**
     * Get notified when upload file is finished
     */
    public void OnUploadFinished(String filename) {
//        Log.d("[RAY]", "OnUploadFinished + " + filename);
        //when uploading finished, upload the next buffer if buffersize is enough
        if (bufferSize > 0) {
            synchronized (mFileUploading) {
                mFileUploading.remove(filename);
            }
            if (mFileBuffer.size() > bufferSize)
                uploadBuffer();
        }
        //update the item information
        RecordItem item = findItemByPath(filename);
        if (item != null) {
            item.uploaded = true;
            new updateAsyncTask(recordItemDAO).execute(item);
        }
    }

    public void OnUploadError(String filename) {
        if (bufferSize > 0) {
            //if upload error, we add the file to buffer again
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
    /**
     * Get the file name based on current name
     */
    public String getRecordingNameOfTime(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp += ".wav";
        return folderName + File.separator + timeStamp;
    }

    /**
     * Get the file name based on current name, with custom prefix
     * @param prefix
     */
    public String getRecordingNameOfTimeWithPrefix(String prefix) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp = prefix + timeStamp + ".wav";
        return folderName + File.separator + timeStamp;
    }

    //new recording added, clean old files
    /**
     * Get called when new recording finishes
     * Create the RecordItem instance for new recording
     * Clean old local files if maxFilesBeforeDelete > 0, upload files if auto_upload is on
     * If the file should not be kept, it will be stored in mShouldNotKeepBuffer and be deleted later
     * If the file should be kept, it will be stored in the mFolderFileList
     * @param filename the file name of the new recording
     * @param createdate the time of the recording started
     * @param duration the recording duration
     * @param shouldkeep whether the recording should be kept
     * @param preceding_mode if preceding mode is on and the file shouldkeep is true,
     *                       it will also keep most recent two files in mShouldNotKeepBuffer, and move them to mFolderFileList
     *                       Because they are the preceding recordings of the formal recording file
     */
    public void newRecordingAdded(String filename, String createdate, int duration, boolean shouldkeep, boolean preceding_mode) {
        RecordItem newitem = new RecordItem();
        newitem.path = filename;
        String[] tokens = filename.split("/");
        newitem.filename = tokens[tokens.length-1];
        newitem.createDate = createdate;
        newitem.duration = duration;
        newitem.uploaded = false;
        newitem.should_keep = shouldkeep;

        if (shouldkeep) {
            if (preceding_mode){
                //if in preceding mode and the shouldkeep is true, it means the recording file is triggered intentionally
                //rather than the background recording. Thus we should store its preceding two clips
                int bfsize = mShouldNotKeepBuffer.size();
                //we set bfsize - 2 because we want preceding two file clips, as only one preceding might not be long enough
                for (int i = bfsize-1; i >= Math.max(0, bfsize-2); --i){
                    RecordItem item = mShouldNotKeepBuffer.remove(0);
                    item.should_keep = true;
                    mFolderFileList.add(0, item);
                    new updateAsyncTask(recordItemDAO).execute(item);
                    if (autoUpload){
                        if (bufferSize == 0){
                            uploadFile(item.path);
                        } else {
                            synchronized (mFileBuffer) {
                                mFileBuffer.add(item.path);
                            }
                        }
                    }
                }
            }
            mFolderFileList.add(0, newitem);
            new insertAsyncTask(recordItemDAO).execute(newitem);
        } else {
            // if should_keep is false, then it is temporary background clips for preceding files
            // we store them in the shouldnotkeepbuffer, and when the buffer is full, we delete the first file
            mShouldNotKeepBuffer.add(newitem);
            new insertAsyncTask(recordItemDAO).execute(newitem);

            if (mShouldNotKeepBuffer.size() > 3) {
                RecordItem item = mShouldNotKeepBuffer.remove(0);
                new deleteAsyncTask(recordItemDAO).execute(item);
                File file = new File(item.path);
                file.delete();
            }
        }

        deleteFilesOutOfMaxFiles();
        if (autoUpload) {
            //if no buffer, upload new files
            if (bufferSize == 0 && shouldkeep) {
                //upload
                uploadFile(filename);
            } else {
                if (shouldkeep) {
                    synchronized (mFileBuffer) {
                        mFileBuffer.add(filename);
                        storeBuffer();
                    }
                }
                if (mFileBuffer.size() > bufferSize)
                    uploadBuffer();
            }
        }
    }

    //local file operations

    /**
     * Delete old local files if current local files is more than the maxFilesBeforeDelete parameter
     */
    private void deleteFilesOutOfMaxFiles() {
        if (folderName != null){
            if (maxFilesBeforeDelete <= 0) return;
            int size = mFolderFileList.size();
            if (size > maxFilesBeforeDelete){
                for (int i = size-1; i >= maxFilesBeforeDelete; --i){
                    String fname = mFolderFileList.get(i).path;
                    // if the file is in the buffer, we should wait until it's uploaded
                    if ( !(mFileBuffer.contains(fname) || mFileUploading.contains(fname)) ) {
                        deleteFile(mFolderFileList.get(i));
                    }
                }
            }
        }
    }

    /**
     * Delete a local file
     */
    public void deleteFile(RecordItem item) {
        File file = new File(item.path);
        file.delete();
//        Log.d("[RAY]", "deleteFileAtLocation: "+ item.filename + "uploaded? " + item.uploaded + " starting date: " + item.createDate);

        //remove from every list
        mFolderFileList.remove(item);
        new deleteAsyncTask(recordItemDAO).execute(item);
        synchronized (mFileBuffer) {
            mFileBuffer.remove(item.path);
        }
        synchronized (mFileUploading) {
            mFileUploading.remove(item.path);
        }
    }

    /**
     * Return the item with a certain path
     * @param fname the path of the item
     */
    private RecordItem findItemByPath(String fname) {
        for(RecordItem item : mFolderFileList) {
            if(item.path == fname) {
                return item;
            }
        }
        return null;
    }

    //DB Operations
    /**
     * Async class for inserting the item in DB
     */
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

    /**
     * Async class for deleting the item in DB
     */
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

    /**
     * Async class for updating the item in DB
     */
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
