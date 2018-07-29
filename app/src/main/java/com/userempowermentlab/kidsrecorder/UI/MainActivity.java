package com.userempowermentlab.kidsrecorder.UI;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.Toast;

import com.amazonaws.mobile.auth.core.IdentityManager;
import com.amazonaws.mobile.auth.core.SignInStateChangeListener;
import com.amazonaws.mobile.auth.ui.SignInUI;
import com.userempowermentlab.kidsrecorder.Data.DataManager;
import com.userempowermentlab.kidsrecorder.R;
import com.userempowermentlab.kidsrecorder.Recording.RecordingManager;
import com.userempowermentlab.kidsrecorder.Recording.RecordingStatus;

import com.amazonaws.mobile.client.AWSMobileClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main activity for recorder
 * Also demonstrate how to use the recorder and save/upload file according to different settings
 */
public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    private RecordingManager recordingManager = null;
    private DataManager dataManager;
    private BroadcastReceiver receiver; //receive the recording status change notifications
    private boolean serviceBound = false;
    private boolean registeredReceiver = false;
    private boolean isUIRecording = false; //whether the reocording button is pressed

    //recording settings
    private int record_length; // the recording time in seconds
    private boolean record_background = false;
    private boolean record_keepawake = false;
    private boolean record_autorestart = false;
    private boolean storage_autoupload = false;
    private String storage_fileprefix = "";
    private int storage_buffersize = 0;
    private int storage_limit = 0;
    private boolean preceding_mode = false;
    private int preceding_time = 0;
    Set<String> notifyEvents = null;
    Set<String> notifyStyles = null;

    SharedPreferences sharedPreferences; //The preferences stores the setting parameters from the setting UI
    Context context;
    IntentFilter filter;

    //UI
    private Chronometer mChronometer = null;
    private FloatingActionButton mRecordButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AWSMobileClient.getInstance().initialize(this).execute();

        setupUI();
        context = this;
        //initialize necessary classes
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        //The procedure to instantiate the datamanager
        //first call getInstance, then setfoldername, then call Initialize
        dataManager = DataManager.getInstance();
        try {
            dataManager.setFolderName("KidsRecorder");
            dataManager.Initialize(context);

        } catch (IOException e) {
            e.printStackTrace();
        }

        //check for necessary permissions
        if (checkAndRequestPermissions()) {
            StartService();
        }

        //get recorder status notifications and process thems
        filter = new IntentFilter();
        filter.addAction(RecordingManager.RECORDER_BROADCAST_ACTION);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //do something based on the recorder's status
                RecordingStatus status = (RecordingStatus) intent.getSerializableExtra("action");
                switch (status){
                    case RECORDING_STARTED:
                        //the recording start, not because the user pressed the button
                        startRecrodingUI();
                        if (notifyEvents != null && notifyEvents.contains(getResources().getString(R.string.start))) {
                            //indicate the status in UI
                            makeIndicator(getResources().getString(R.string.start));
                        }
                        break;
                    case RECORDING_STOPPED:
                        //the recording stopped, not because the user pressed the button
                        stopRecordingUI();
                        if (notifyEvents != null){
                            if (notifyEvents.contains(getResources().getString(R.string.stop))) {
                                makeIndicator(getResources().getString(R.string.stop));
                            } else if (notifyStyles != null && notifyStyles.contains(R.string.statusBar)) {
                                //if stop is not selected as event, but start is and there's status bar notification
                                //we need to cancel it
                                recordingManager.cancelNotification();
                            }
                        }
                        break;
                    case RECORDING_PAUSED:
                        break;
                    case RECORDING_RESUMED:
                        break;
                    case RECORDING_TIME_UP:
                        if (record_autorestart){
                            startRecording();
                            startRecrodingUI();
                        }
                        break;
                }
            }
        };
    }

    //Menu UI setup
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actionbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()){
            case R.id.action_file:
                intent = new Intent(this, FileExplorerActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_signout:
                IdentityManager.getDefaultIdentityManager().signOut();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //retrieve the settings from the settingUI and set them to record manager and data manager
    private void updateRecordSettings(){
        boolean record_timing = sharedPreferences.getBoolean("record_timing", false);
        String tempstring = sharedPreferences.getString("record_length", "0");
        record_length = Integer.parseInt(tempstring);
        if (record_timing == false) record_length = 0;
        record_autorestart = sharedPreferences.getBoolean("record_autorestart", false);

        record_background = sharedPreferences.getBoolean("record_background", false);
        record_keepawake = sharedPreferences.getBoolean("record_keepawake", false);

        storage_autoupload = sharedPreferences.getBoolean("storage_autoupload", false);
        storage_fileprefix = sharedPreferences.getString("storage_fileprefix", "");
        tempstring = sharedPreferences.getString("storage_buffersize", "0");
        storage_buffersize = Integer.parseInt(tempstring);
        tempstring = sharedPreferences.getString("storage_limit", "0");
        storage_limit = Integer.parseInt(tempstring);

        notifyEvents = sharedPreferences.getStringSet("indicator_event", null);
        notifyStyles = sharedPreferences.getStringSet("indicator_style", null);

        tempstring = sharedPreferences.getString("preceding_time", "0");
        preceding_time = Integer.parseInt(tempstring);
        if (preceding_time > 0){
            preceding_mode = true;
        }

        dataManager.setBufferSize(storage_buffersize);
        dataManager.setMaxFilesBeforeDelete(storage_limit);
        dataManager.setAutoUpload(storage_autoupload);

        if (recordingManager == null) return;
        recordingManager.setAlwaysRunning(record_keepawake & record_background);
        recordingManager.setShould_preced(preceding_mode);
        recordingManager.setPrecedingTime(preceding_time);
    }

    private void setupUI() {
        mChronometer = findViewById(R.id.chronometer);
        mRecordButton = findViewById(R.id.recordBtn);

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordingManager == null) return;

                if (isUIRecording){
                    stopRecording();
                    isUIRecording = false;
                    if (preceding_mode && preceding_time > 0){
                        recordingManager.setShould_keep(false);
                    }
                } else {
                    if (preceding_mode && preceding_time > 0){
                        //if the preceding mode is on, then we should first stop the background recording
                        stopSilentRecording();
                        recordingManager.setShould_keep(true);
                    }
                    //then start the formal recording
                    startRecording();
                    isUIRecording = true;
                }
            }
        });
    }

    void stopRecording() {
        if (recordingManager == null) return;
        if (recordingManager.isRecording())
            recordingManager.StopRecording();
    }

    //stop recording silently without getting broadcast (as the UI would be changed if there's broadcast)
    void stopSilentRecording(){
        if (recordingManager == null) return;
        if (recordingManager.isRecording())
            recordingManager.StopRecordingSilently();
    }

    /**
     * Stop recording UI (button appearance and the chronometer)
     */
    void stopRecordingUI() {
        mChronometer.stop();
        mRecordButton.setImageResource(R.drawable.ic_media_play);
    }

    void startRecording(){
        if (recordingManager == null) return;
        updateRecordSettings();
        recordingManager.setAlwaysRunning(record_keepawake & record_background);
        int time_limit = record_length;
        recordingManager.StartRecording(dataManager.getRecordingNameOfTimeWithPrefix(storage_fileprefix), time_limit);
    }

    /**
     * Start recording UI (button appearance and the chronometer)
     */
    void startRecrodingUI() {
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
        mRecordButton.setImageResource(R.drawable.ic_media_stop);
    }

    /**
     * Show different indicator (vibrate/notification/sould/toast) according to setting
     */
    void makeIndicator(String event) {
        if (notifyStyles == null) return;
        if (notifyStyles.contains(getResources().getString(R.string.vibration))) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(700,VibrationEffect.DEFAULT_AMPLITUDE));
            }else{
                //deprecated in API 26
                v.vibrate(700);
            }
        }
        if (notifyStyles.contains(getResources().getString(R.string.sound))){
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        }
        if (notifyStyles.contains(getResources().getString(R.string.statusBar))) {
            if (recordingManager != null) {
                if (event == getResources().getString(R.string.start)) {
                    recordingManager.createNotification();
                } else if (event == getResources().getString(R.string.stop)) {
                    recordingManager.cancelNotification();
                }
            }
        }
        if (notifyStyles.contains(getResources().getString(R.string.toast))) {
            if (event == getResources().getString(R.string.start)) {
                Toast.makeText(this, getResources().getString(R.string.isrecording),
                        Toast.LENGTH_LONG).show();
            } else if (event == getResources().getString(R.string.stop)) {
                Toast.makeText(this, getResources().getString(R.string.notrecording),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registeredReceiver = true;
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (registeredReceiver) {
            unregisterReceiver(receiver);
        }
        registeredReceiver = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateRecordSettings();
        //if the preceding mode is on, and the background mode is off, then should start preceding recording silently
        if (!record_background && recordingManager != null && preceding_time > 0 && preceding_mode ) {
            recordingManager.StartRecordingSilently(dataManager.getRecordingNameOfTime(), preceding_time);
        }
    }

    /**
     * When the activity disappear (when the application enters background)
     */
    @Override
    protected void onStop() {
        super.onStop();
        //if the background recording is off, then stop current recording
        if (!record_background && recordingManager != null && recordingManager.isRecording()){
            isUIRecording = false;
            Log.d("[Ray]", "onStop: !!!!!");
            recordingManager.cancelNotification();
            if (recordingManager != null && recordingManager.isRecording())
                recordingManager.StopRecordingWithoutStartingBackground();
            stopRecordingUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serviceConnection != null){
            unbindService(serviceConnection);
        }
    }

    private void StartService() {
        Intent recorderIntent = new Intent(this, RecordingManager.class);
        startService(recorderIntent);
        bindService(recorderIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissionRecording = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
            int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            List<String> listPermissionsNeeded = new ArrayList<>();

            if (permissionRecording != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
            }

            if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        String TAG = "LOG_PERMISSION";
        Log.d(TAG, "Permission callback called-------");
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {

                Map<String, Integer> perms = new HashMap<>();
                // Initialize the map with both permissions
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // Check for both permissions

                    if (perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            ) {
                        Log.d(TAG, "Phone state and storage permissions granted");
                        // process the normal flow
                        //else any one or both the permissions are not granted
                        StartService();
                    } else {
                        Log.d(TAG, "Some permissions are not granted ask again ");
                        //permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
//                      //shouldShowRequestPermissionRationale will return true
                        //show the dialog or snackbar saying its necessary and try again otherwise proceed with setup.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                            showDialogOK("Phone state and storage permissions required for this app",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which) {
                                                case DialogInterface.BUTTON_POSITIVE:
                                                    checkAndRequestPermissions();
                                                    break;
                                                case DialogInterface.BUTTON_NEGATIVE:
                                                    // proceed with logic by disabling the related features or quit the app.
                                                    break;
                                            }
                                        }
                                    });
                        }
                        //permission is denied (and never ask again is  checked)
                        //shouldShowRequestPermissionRationale will return false
                        else {
                            Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG)
                                    .show();
                            //proceed with logic by disabling the related features or quit the app.
                        }
                    }
                }
            }
        }

    }

    private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", okListener)
                .create()
                .show();
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            RecordingManager.LocalBinder binder = (RecordingManager.LocalBinder) service;
            recordingManager = binder.getServiceInstance();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

}
