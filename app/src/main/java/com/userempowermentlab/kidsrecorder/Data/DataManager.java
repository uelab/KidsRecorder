package com.userempowermentlab.kidsrecorder.Data;

import android.content.Context;
import android.content.SharedPreferences;
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

    private ArrayList<String> mFolderFileList = new ArrayList<String>();
    //we need the lists to be thread-safe
    private List<String> mFileUploading = Collections.synchronizedList(new ArrayList<String>());
    private List<String> mFileBuffer = Collections.synchronizedList(new ArrayList<String>());
    private Context context;
    private SharedPreferences preferences;

    private Handler mHandler = new Handler();

    //call the Initialize at first after set the folder name
    public void Initialize(Context context) {
        this.context = context;
        scanFolder();
        loadBuffer();
    }

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
        String json = preferences.getString("audioArrayList", null);
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        mFileBuffer = gson.fromJson(json, type);

        if (json == null){
            Log.d("[RAY]", "loadBuffer: new buffer allocated");
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
    public void newRecordingAdded(String filename) {
        Log.d("[RAY]", "Connected ? " + Helper.CheckNetworkConnected(context));
        mFolderFileList.add(0, filename);
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
    // get list of all recording files in the folder
    private void scanFolder() {
        mFolderFileList.clear();
        File folder = new File(folderName);
        File[] listOfFiles = folder.listFiles(
                new FilenameFilter() {
                    public boolean accept(File dir, String filename)
                    { return filename.endsWith(".wav"); } });

        Arrays.sort(listOfFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                //sort file modified dates by decreasing order
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });

        for (File f : listOfFiles){
            try {
                mFolderFileList.add(f.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    private void deleteFilesOutOfMaxFiles() {
        if (folderName != null){
            if (maxFilesBeforeDelete <= 0) return;
            int size = mFolderFileList.size();
            if (size > maxFilesBeforeDelete){
                for (int i = size-1; i >= maxFilesBeforeDelete; --i){
                    String fname = mFolderFileList.get(i);
                    // if the file is in the buffer, we should wait until it's uploaded
                    if ( !(mFileBuffer.contains(fname) || mFileUploading.contains(fname)) ) {
                        deleteFileAtLocation(fname);
                    }
                }
            }
        }
    }

    public void deleteFileAtLocation(String location) {
        File file = new File(location);
        file.delete();
//        Log.d("[RAY]", "deleteFileAtLocation: "+location);

        //remove from every list
        mFolderFileList.remove(location);
        synchronized (mFileBuffer) {
            mFileBuffer.remove(location);
        }
        synchronized (mFileUploading) {
            mFileUploading.remove(location);
        }
    }


}
