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
    public static final String RECORDER_BROADCAST_ACTION = "com.userempowermentlab.kidsrecorder.Recording.ACTION";
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

    private DataManager manager;
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

    public int getRecordedTime() {
        if (recorder != null && recorder.isRecording()) {
            return recorder.getRecordedTime();
        }
        else return 0;
    }

    public boolean isRecording(){
        if (recorder != null) {
            return recorder.isRecording();
        } else return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recorder = new BasicRecorder();
        mTimerStopRecorder = new Runnable() {
            @Override
            public void run() {
                StopRecording();
                sendBroadCast(RecordingStatus.RECORDING_TIME_UP);
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
        super.onStartCommand(intent, flags, startId);
        //get singleton DataManager
        manager = DataManager.getInstance();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (alwaysRunning && wakeLock != null) {
            wakeLock.release();
        }
    }

    public void StartRecording(String filename) {
        recorder.setFilePath(filename);
        recorder.Start();
        if (recordingTime > 0) {
            mHandler.postDelayed(mTimerStopRecorder, recordingTime);
        }
        sendBroadCast(RecordingStatus.RECORDING_STARTED);
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
            manager.newRecordingAdded(recorder.getFilePath(), recorder.getStartDate(), recorder.getDuration());
        }
        sendBroadCast(RecordingStatus.RECORDING_STOPPED);
    }

    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
        }
        sendBroadCast(RecordingStatus.RECORDING_PAUSED);
    }

    public void ResumeRecording() {
        if (recorder.isRecording()){
            recorder.Resume();
        }
        sendBroadCast(RecordingStatus.RECORDING_RESUMED);
    }

    private void sendBroadCast(RecordingStatus status) {
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(RECORDER_BROADCAST_ACTION);
        broadCastIntent.putExtra("action", status);
        broadCastIntent.putExtra("filename", recorder.getFilePath());
        sendBroadcast(broadCastIntent);
    }
}
