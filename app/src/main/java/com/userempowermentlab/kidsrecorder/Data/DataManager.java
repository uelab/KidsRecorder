package com.userempowermentlab.kidsrecorder.Data;

import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by mingrui on 7/16/2018.
 */

public class DataManager {
    private int bufferSize = 0; // buffer file or not; if buffered, the file will be delayed to upload after the buffer size is reached
    private int maxFilesBeforeDelete = 0; // 0 - never delete; > 0 - delete the old files if more than the number of recording exists
    private String folderName; // the folder name of the recorded files

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

    public String getRecordingNameOfTime(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp += ".wav";
        return timeStamp;
    }

    public String getRecordingNameOfTimeWithPrefix(String prefix) {
        String timeStamp = prefix+getRecordingNameOfTime();
        return timeStamp;
    }

    public void deleteFilesOutOfMaxFiles() {
        if (folderName != null){
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

            if (listOfFiles.length > maxFilesBeforeDelete){
                for (int i = maxFilesBeforeDelete; i < listOfFiles.length; ++i){
                    deleteFileAtLocation(listOfFiles[i].getAbsolutePath());
                }
            }
        }
    }

    public void deleteFileAtLocation(String location) {
        File file = new File(location);
        file.delete();
    }
}
