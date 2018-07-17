package com.userempowermentlab.kidsrecorder.Recording;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Created by mingrui on 7/16/2018.
 */

public class RecordingManager extends Service {
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

    private RecordingManagerListener mListener;
    private Handler mHandler = new Handler();
    Runnable mTimerStopRecorder;
    private BasicRecorder recorder = null;
    PowerManager.WakeLock wakeLock;
    private final IBinder mBinder = new LocalBinder();

    public void setRecordingTime(int recordingTime) {
        this.recordingTime = recordingTime;
    }

    public void setAlwaysRunning(boolean alwaysRunning) {
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
                //notifying the holder that record is stopped due to timing
                mListener.onRecordingStateChanged(RecordingStatus.RECORDING_TIME_UP);
                mHandler.removeCallbacks(mTimerStopRecorder);
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
        if (alwaysRunning){
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyWakelockTag");
            wakeLock.acquire();
        }
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
    }

    public void StartRecording(String filename, int timeLimit){
        recorder.setFilePath(filename);
        recordingTime = timeLimit;
        recorder.Start();
        mListener.onRecordingStateChanged(RecordingStatus.RECORDING_STARTED);
    }

    public void StopRecording() {
        if (recorder.isRecording()){
            recorder.Stop();
        }
        mListener.onRecordingStateChanged(RecordingStatus.RECORDING_STOPPED);
    }

    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
        }
        mListener.onRecordingStateChanged(RecordingStatus.RECORDING_PAUSED);
    }

    public void ResumeRecording() {
        if (recorder.isRecording()){
            recorder.Resume();
        }
        mListener.onRecordingStateChanged(RecordingStatus.RECORDING_RESUMED);
    }

}
