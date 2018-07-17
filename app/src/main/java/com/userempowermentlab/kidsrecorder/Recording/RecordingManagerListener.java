package com.userempowermentlab.kidsrecorder.Recording;

/**
 * Created by mingrui on 7/16/2018.
 */

public interface RecordingManagerListener {
    // you can define any parameter as per your requirement
    public void onRecordingStateChanged(RecordingStatus status, String filename);
}
