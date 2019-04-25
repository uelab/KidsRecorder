package com.userempowermentlab.aasrecorder.Recording;

import android.annotation.TargetApi;
import android.app.Activity;
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

import com.userempowermentlab.aasrecorder.Data.DataManager;
import com.userempowermentlab.aasrecorder.R;

import static android.content.ContentValues.TAG;

/**
 * This class is the high lever recorder manager
 * The manager is a service, which could also run in background
 *
 * Functions: start/stop/resume/pause recording, preceding mode for recording
 * Connected to the datamanager, after the recording finished, it will pass the file information to the datamanager
 *
 * To use the manager, please first start the service, then bind the service
 * Created by mingrui on 7/16/2018.
 */

public class RecordingManager extends Service {
    public static final String RECORDER_BROADCAST_ACTION = "com.userempowermentlab.kidsrecorder.Recording.ACTION";
    private int recordingTime = 0; // 0 for manually stop recording; > 0 for limited recording time in ms
    private boolean alwaysRunning = false; //always run in background (useful if want to record when the app is in background)

    private boolean should_precede = false; // whether to record preceding or not
    private int precedingTime = 0; //enable preceding time record , in ms
    private boolean should_keep = true; // if should_keep && auto_upload, the file would be upload, otherwise it won't

    private DataManager manager;
    private Handler mHandler = new Handler(); //for timer
    Runnable mTimerStopRecorder; // for timer
    private BasicRecorder recorder = null;
    PowerManager.WakeLock wakeLock;// for always recording mode, the phone cpu won't sleep even in black screen. Drain power quickly
    private final IBinder mBinder = new LocalBinder();

    /**
     * set always awake mode (even user lock screen)
     * @param alwaysRunning whether the recording service should keep phone awake
     */
    public void setAlwaysRunning(boolean alwaysRunning) {
        if (alwaysRunning){
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "uekidsrecorder:MyWakelockTag");
            wakeLock.acquire();
        } else if (wakeLock != null) {
            wakeLock.release();
        }
        this.alwaysRunning = alwaysRunning;
    }

    /**
     * whether the recorder should record preceding clips before the formal recodring start
     */
    public void setShouldPrecede(boolean should_precede) {
        this.should_precede = should_precede;
    }

    /**
     * Set time for preceding clips before the formal recording start.
     * @param precedingTime how long will be recorded before the formal recording is triggered (in second)
     */
    public void setPrecedingTime(int precedingTime) {
        should_precede = true;
        this.precedingTime = precedingTime * 1000;
    }

    /**
     * Whether the current recording file should be kept
     * If kept, the file would be shown in the file explorer and be uploaded (if the auto upload is on)
     * Otherwise, it would be deleted automatically.
     * Should_keep is useful when preceding mode is on. To get preceding recording, the recorder would always record *background* clips
     * Thus many background would not be kept, only the ones before the triggered recording could be kept.
     */
    public void setShouldKeep(boolean should_keep) {
        this.should_keep = should_keep;
    }

    /**
     * Returns the recording time (after stop or paused)
     */
    public int getElapsedRecordingTime() {
        if (recorder != null && recorder.isRecording()) {
            return recorder.getElapsedRecordingTime();
        }
        else return 0;
    }

    /**
     * Returns whether recording
     */
    public boolean isRecording(){

        if (recorder != null) {
            return recorder.isRecording();
        } else return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recorder = new BasicRecorder();

        //the task for timed recording
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

    /**
     * Create notification of the recording status on status bar
     */
    public void createNotification(Activity activity) {
        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder buildier = new NotificationCompat.Builder(this.getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel(mNotifyManager, buildier);
        try {
            Intent intent = new Intent(this, Class.forName(activity.getClass().getName()));

            buildier.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                    .setContentTitle(getResources().getString(R.string.isrecording))
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis());
            mNotifyManager.notify(0, buildier.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancel notification bar
     */
    public void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    @TargetApi(26)
    private void createChannel(NotificationManager notificationManager, NotificationCompat.Builder builder) {
        String channelID = "uelab_recorder:notificationid";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel mChannel = new NotificationChannel(channelID, getResources().getString(R.string.isrecording), importance);
        mChannel.enableLights(true);
        notificationManager.createNotificationChannel(mChannel);
        builder.setChannelId("uelab_recorder:notificationid");
    }

    /**
     * Start recording. If this function is called, the "start notification" would broadcast to other classes
     * should be used by other classes if it's formal (not preceding mode) recording
     * @param filename the filename(full path) to be saved
     */
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

    /**
     * Start recording. If this function is called, the "start notification" would broadcast to other classes
     * should be used by other classes if it's formal (not preceding mode) recording
     * @param filename the filename(full path) to be saved
     * @param timeLimit the recording time (in second)
     */
    public void StartRecording(String filename, int timeLimit){
        recordingTime = timeLimit*1000;
        StartRecording(filename);
    }

    /**
     * Start recording. If this function is called, there would be no broadcast
     * should be used by the manager itself for always-on background recordings in preceding mode
     * Or by other classes to start background preceding mode recording
     * @param filename the filename(full path) to be saved
     * @param timeLimit the preceding time (in second)
     */
    public void StartRecordingSilently(String filename, int timeLimit){
        precedingTime = timeLimit*1000;
        StartRecordingSilently(filename);
    }

    /**
     * Start recording. If this function is called, there would be no broadcast
     * should be used by the manager itself for always-on background recordings in preceding mode
     * Or by other classes to start background preceding mode recording
     * @param filename the filename(full path) to be saved
     */
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

    /**
     * Stop recording. If this function is called, there would be no broadcast
     * should be used by the manager itself for always-on background recordings in preceding mode
     * Or by other classes to stop background preceding mode recording
     */
    public void StopRecordingSilently(){
        Log.d("[RAY]", "Recording Stopped, should_KEEP "+ should_keep);
        mHandler.removeCallbacks(mTimerStopRecorder);
        if (recorder.isRecording()){
            recorder.Stop();
        }
        if (manager != null) {
            manager.newRecordingAdded(recorder.getFilePath(), recorder.getStartDateTime(), recorder.getDuration(), should_keep, should_precede);
        }
    }

    /**
     * Stop recording. If this function is called, the "stop notification" would broadcast to other classes
     * should be used by other classes if it's formal (not preceding mode) recording
     */
    public void StopRecording() {
        Log.d("[RAY]", "preceding time : "+ precedingTime);

        StopRecordingSilently();
        sendBroadCast(RecordingStatus.RECORDING_STOPPED);

        //for preceding mode on, then auto start the background recording
        if (precedingTime > 0) {
            SystemClock.sleep(100);
            Log.d("[RAY]", "StopRecording: autostart");
            StartRecordingSilently(manager.getRecordingNameOfTimeWithPrefix("preceding"));
        }
    }

    /**
     * Stop recording. If this function is called, the "stop notification" would broadcast to other classes
     * should be used by other classes if it's formal (not preceding mode) recording when preceding mode is on
     * This method would also stop preceding background recording
     */
    public void StopRecordingWithoutStartingBackground() {
        StopRecordingSilently();
        sendBroadCast(RecordingStatus.RECORDING_STOPPED);
    }

    /**
     * Pause recording. If this function is called, the "pause notification" would broadcast to other classes
     */
    public void PauseRecording() {
        if (recorder.isRecording()){
            recorder.Pause();
            mHandler.removeCallbacks(mTimerStopRecorder);
        }
        sendBroadCast(RecordingStatus.RECORDING_PAUSED);
    }

    /**
     * Resume recording. If this function is called, the "resume notification" would broadcast to other classes
     */
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


    /**
     * Send broadcast to other classes when the recorder status changed
     */
    private void sendBroadCast(RecordingStatus status) {
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(RECORDER_BROADCAST_ACTION);
        broadCastIntent.putExtra("action", status);
        broadCastIntent.putExtra("filename", recorder.getFilePath());
        sendBroadcast(broadCastIntent);
    }
}
