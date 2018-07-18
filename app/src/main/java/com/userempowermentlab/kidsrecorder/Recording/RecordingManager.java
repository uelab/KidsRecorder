package com.userempowermentlab.kidsrecorder.Recording;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.userempowermentlab.kidsrecorder.Data.DataManager;

import static android.content.ContentValues.TAG;

/**
 * Created by mingrui on 7/16/2018.
 */

public class RecordingManager extends Service {
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

    private DataManager manager;
    private RecordingManagerListener mListener;
    private Handler mHandler = new Handler();
    Runnable mTimerStopRecorder;
    private BasicRecorder recorder = null;
    PowerManager.WakeLock wakeLock;
    private final IBinder mBinder = new LocalBinder();

    public int getRecordingTime() {
        return recordingTime;
    }

    public void setAlwaysRunning(boolean alwaysRunning) {
        if (alwaysRunning){
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyWakelockTag");
            wakeLock.acquire();
        } else if (wakeLock != null) {
            wakeLock.release();
        }
        this.alwaysRunning = alwaysRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recorder = new BasicRecorder();
        mTimerStopRecorder = new Runnable() {
            @Override
            public void run() {
                StopRecording();
                if (mListener != null) {
                    //notifying the holder that record is stopped due to timing
                    mListener.onRecordingStateChanged(RecordingStatus.RECORDING_TIME_UP, recorder.getFilePath());
                }
            }
        };
    }

    //returns the instance of the service
    public class LocalBinder extends Binder {
        public RecordingManager getServiceInstance(){
            return RecordingManager.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //get singleton DataManager
        manager = DataManager.getInstance();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (alwaysRunning && wakeLock != null) {
            wakeLock.release();
        }
    }

    public void setmListener(RecordingManagerListener listener) {
        mListener = listener;
    }

    public void StartRecording(String filename) {
        recorder.setFilePath(filename);
        recorder.Start();
        if (recordingTime > 0) {
            mHandler.postDelayed(mTimerStopRecorder, recordingTime);
        }
        if (mListener != null) {
            mListener.onRecordingStateChanged(RecordingStatus.RECORDING_STARTED, recorder.getFilePath());
        }
        Log.d("[RAY]", "Recording start");
    }

    public void StartRecording(String filename, int timeLimit){
        recordingTime = timeLimit;
        StartRecording(filename);
    }

    public void StopRecording() {
        if (recorder.isRecording()){
            recorder.Stop();
        }
        if (manager != null) {
            manager.newRecordingAdded(recorder.getFilePath());
        }
        if (mListener != null) {
            mListener.onRecordingStateChanged(RecordingStatus.RECORDING_STOPPED, recorder.getFilePath());
        }
    }

    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
        }
        if (mListener != null) {
            mListener.onRecordingStateChanged(RecordingStatus.RECORDING_PAUSED, recorder.getFilePath());
        }
    }

    public void ResumeRecording() {
        if (recorder.isRecording()){
            recorder.Resume();
        }
        if (mListener != null) {
            mListener.onRecordingStateChanged(RecordingStatus.RECORDING_RESUMED, recorder.getFilePath());
        }
    }

}
