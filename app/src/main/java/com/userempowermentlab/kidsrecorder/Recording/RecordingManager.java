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
                sendBroadCast(RecordingStatus.RECORDING_TIME_UP);
                StopRecording();
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
        recorder.setFilePath(filename);
        recorder.Start();
        if (recordingTime > 0) {
            mHandler.postDelayed(mTimerStopRecorder, recordingTime);
        }
        sendBroadCast(RecordingStatus.RECORDING_STARTED);
        Log.d("[RAY]", "Recording start");
    }

    //timelimit in seconds
    public void StartRecording(String filename, int timeLimit){
        recordingTime = timeLimit*1000;
        StartRecording(filename);
    }

    public void StopRecording() {
        Log.d("[RAY]", "Recording Stopped");
        mHandler.removeCallbacks(mTimerStopRecorder);
        if (recorder.isRecording()){
            recorder.Stop();
        }
        if (manager != null) {
            manager.newRecordingAdded(recorder.getFilePath(), recorder.getStartDate(), recorder.getDuration());
        }
        sendBroadCast(RecordingStatus.RECORDING_STOPPED);
    }

    private void _startRecording(){

    }

    private void _stopRecording(){

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
