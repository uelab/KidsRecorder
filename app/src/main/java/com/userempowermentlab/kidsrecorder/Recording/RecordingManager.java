package com.userempowermentlab.kidsrecorder.Recording;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.userempowermentlab.kidsrecorder.Data.DataManager;
import com.userempowermentlab.kidsrecorder.R;
import com.userempowermentlab.kidsrecorder.UI.MainActivity;

import static android.content.ContentValues.TAG;

/**
 * Created by mingrui on 7/16/2018.
 */

public class RecordingManager extends Service {
    public static final String RECORDER_BROADCAST_ACTION = "com.userempowermentlab.kidsrecorder.Recording.ACTION";
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

    private boolean should_preced = false; // whether to record preceding or not
    private int precedingTime = 0; //enable preceding time record , in ms
    private boolean should_keep = true; // if should_keep && auto_upload, the file would be upload, otherwise it won't

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

    public void setShould_preced(boolean should_preced) {
        this.should_preced = should_preced;
    }

    public void setPrecedingTime(int precedingTime) {
        should_preced = true;
        this.precedingTime = precedingTime * 1000;
    }

    public void setShould_keep(boolean should_keep) {
        this.should_keep = should_keep;
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
                //if preceding mode open and the recording is not triggered by outside
                // then should auto restart the background recording
                if (precedingTime > 0 && !should_keep){
                    StopRecordingSilently();
                    SystemClock.sleep(100);
                    StartRecordingSilently(manager.getRecordingNameOfTimeWithPrefix("preceding"));
                } else {
                    sendBroadCast(RecordingStatus.RECORDING_TIME_UP);
                    StopRecording();
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

    public void createNotification() {
        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel(mNotifyManager);
        NotificationCompat.Builder buildier = new NotificationCompat.Builder(this.getApplicationContext());
        Intent intent = new Intent(this, MainActivity.class);

        buildier.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .setContentTitle(getResources().getString(R.string.isrecording))
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setWhen(System.currentTimeMillis());
        mNotifyManager.notify(0, buildier.build());
    }

    public void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    @TargetApi(26)
    private void createChannel(NotificationManager notificationManager) {
        String name = getResources().getString(R.string.isrecording);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel mChannel = new NotificationChannel(name, name, importance);
        mChannel.enableLights(true);
        notificationManager.createNotificationChannel(mChannel);
    }

    public void StartRecording(String filename) {
        Log.d("[RAY]", "Recording start + " + filename);
        if (recorder.isRecording()){
            StopRecordingSilently();
        }
        should_keep = true;
        recorder.setFilePath(filename);
        recorder.Start();
        if (recordingTime > 0) {
            mHandler.postDelayed(mTimerStopRecorder, recordingTime);
        }
        sendBroadCast(RecordingStatus.RECORDING_STARTED);
    }

    //timelimit in seconds
    public void StartRecording(String filename, int timeLimit){
        recordingTime = timeLimit*1000;
        StartRecording(filename);
    }

    //silent version for preceding recording
    public void StartRecordingSilently(String filename, int timeLimit){
        precedingTime = timeLimit*1000;
        StartRecordingSilently(filename);
    }

    public void StartRecordingSilently(String filename){
        if (recorder.isRecording()){
            StopRecordingSilently();
        }
        if (precedingTime <= 0) return;
        Log.d("[RAY]", "Recording silent start");
        should_keep = false;
        recorder.setFilePath(filename);
        recorder.Start();
        mHandler.postDelayed(mTimerStopRecorder, precedingTime);
    }

    public void StopRecordingSilently(){
        Log.d("[RAY]", "Recording Stopped, should_KEEP "+ should_keep);
        mHandler.removeCallbacks(mTimerStopRecorder);
        if (recorder.isRecording()){
            recorder.Stop();
        }
        if (manager != null) {
            manager.newRecordingAdded(recorder.getFilePath(), recorder.getStartDate(), recorder.getDuration(), should_keep, should_preced);
        }
    }

    //add notification
    public void StopRecording() {
        Log.d("[RAY]", "preceding time : "+ precedingTime);

        StopRecordingSilently();
       sendBroadCast(RecordingStatus.RECORDING_STOPPED);
       //for preceding auto start
       if (precedingTime > 0) {
           SystemClock.sleep(100);
           Log.d("[RAY]", "StopRecording: autostart");
           StartRecordingSilently(manager.getRecordingNameOfTimeWithPrefix("preceding"));
       }
    }

    public void StopRecordingWithoutStartingBackground() {
        StopRecordingSilently();
        sendBroadCast(RecordingStatus.RECORDING_STOPPED);
    }

    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
            mHandler.removeCallbacks(mTimerStopRecorder);
        }
        sendBroadCast(RecordingStatus.RECORDING_PAUSED);
    }

    public void ResumeRecording() {
        if (recorder.isRecording()){
            recorder.Resume();
            //we need to restart the timer as it is paused
            if (recordingTime > 0){
                mHandler.postDelayed(mTimerStopRecorder, recordingTime-1000*recorder.getDuration());
            }
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
