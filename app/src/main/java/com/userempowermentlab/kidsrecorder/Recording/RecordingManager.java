package com.userempowermentlab.kidsrecorder.Recording;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Created by mingrui on 7/16/2018.
 */

public class RecordingManager extends Service {
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private int bufferSize = 0; // buffer file or not
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

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

    public void StartRecording(String filename) {
        recorder.setFilePath(filename);
        recorder.Start();
    }

    public void StartRecording(String filename, int timeLimit){
        recorder.setFilePath(filename);
        recordingTime = timeLimit;
        recorder.Start();
    }

    public void StopRecording() {
        if (recorder.isRecording()){
            recorder.Stop();
        }
    }

    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
        }
    }

    public void ResumeRecording() {
        if (recorder.isRecording()){
            recorder.Resume();
        }
    }
}
