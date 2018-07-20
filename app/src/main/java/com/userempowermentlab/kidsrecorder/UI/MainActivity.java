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
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
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

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    private RecordingManager recordingManager = null;
    private DataManager dataManager;
    private BroadcastReceiver receiver;
    private boolean serviceBound = false;
    private boolean registeredReceiver = false;

    //recording settings
    private int record_length = 5;
    private int buffer_size = 5;
    private boolean record_autorestart = false;
    private boolean record_background = false;
    private boolean record_keepawake = false;
    private boolean storage_autoupload = false;
    private String storage_fileprefix = "";
    private int storage_buffersize = 0;
    private int storage_limit = 0;

    SharedPreferences sharedPreferences;
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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        dataManager = DataManager.getInstance();
        dataManager.setMaxFilesBeforeDelete(15);
        try {
            dataManager.setFolderName("KidsRecorder");
//            dataManager.setBufferSize(3);
            dataManager.Initialize(context);

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (checkAndRequestPermissions()) {
            StartService();
        }

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
                        break;
                    case RECORDING_STOPPED:
                        //the recording stopped, not because the user pressed the button
                        stopRecordingUI();
                        break;
                    case RECORDING_PAUSED:
                        break;
                    case RECORDING_RESUMED:
                        break;
                    case RECORDING_TIME_UP:
                        if (record_autorestart){
                            Log.d("[RAY]", "timeup!!!!! restart!!!!");
                            startRecording();
                        }
                        break;
                }
            }
        };
    }

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
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateRecordSettings(){
        boolean record_timing = sharedPreferences.getBoolean("record_timing", false);
        String tempstring = sharedPreferences.getString("record_length", "0");
        record_length = Integer.parseInt(tempstring);
        if (record_timing == false) record_length = 0;
        record_autorestart = sharedPreferences.getBoolean("record_autorestart", false);

        record_background = sharedPreferences.getBoolean("record_background", false);
        record_keepawake = sharedPreferences.getBoolean("record_keepawake", false);

        storage_autoupload = sharedPreferences.getBoolean("record_autorestart", false);
        storage_fileprefix = sharedPreferences.getString("storage_fileprefix", "");
        tempstring = sharedPreferences.getString("storage_buffersize", "0");
        storage_buffersize = Integer.parseInt(tempstring);
        tempstring = sharedPreferences.getString("storage_limit", "0");
        storage_limit = Integer.parseInt(tempstring);

        dataManager.setBufferSize(storage_buffersize);
        dataManager.setMaxFilesBeforeDelete(storage_limit);
        dataManager.setAutoUpload(storage_autoupload);
    }

    private void setupUI() {
        mChronometer = findViewById(R.id.chronometer);
        mRecordButton = findViewById(R.id.recordBtn);

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordingManager == null) return;
                if (recordingManager.isRecording()){
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });
    }

    void stopRecording() {
        if (recordingManager == null) return;
        if (recordingManager.isRecording())
            recordingManager.StopRecording();
    }

    void stopRecordingUI() {
        mChronometer.stop();
        mRecordButton.setImageResource(R.drawable.ic_media_play);
    }

    void startRecording(){
        if (recordingManager == null) return;
        updateRecordSettings();
        recordingManager.setAlwaysRunning(record_keepawake & record_background);
        recordingManager.StartRecording(dataManager.getRecordingNameOfTimeWithPrefix(storage_fileprefix), record_length);
    }

    void startRecrodingUI() {
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
        mRecordButton.setImageResource(R.drawable.ic_media_stop);
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
    protected void onStop() {
        super.onStop();
        if (!record_background && recordingManager != null && recordingManager.isRecording()){
            stopRecording();
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
