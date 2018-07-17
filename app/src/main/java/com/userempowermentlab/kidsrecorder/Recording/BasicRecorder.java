package com.userempowermentlab.kidsrecorder.Recording;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import omrecorder.AudioChunk;
import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;

/**
 * Created by mingrui on 7/16/2018.
 */

public class BasicRecorder implements PullTransport.OnAudioChunkPulledListener{
    //configuration variables
    private String filePath = null;
    private Recorder recorder = null;
    private boolean isRecording;

    public boolean isRecording() {
        return isRecording;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() { return filePath; }

    public void Start(){
        if (filePath == null) return;
        isRecording = true;
        if(recorder == null) {
            recorder = OmRecorder.wav(
                    new PullTransport.Default(mic(), BasicRecorder.this),
                    new File(filePath));
        }
        recorder.startRecording();
    }

    public void Stop(){
        isRecording = false;
        if (recorder != null){
            try {
                recorder.stopRecording();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                recorder = null;
            }
        }
    }

    public void Pause(){
        isRecording = false;
        if (recorder != null){
            recorder.pauseRecording();
        }
    }

    public void Resume(){
        isRecording = true;
        if (recorder != null){
            recorder.resumeRecording();
        }
    }

    @Override
    public void onAudioChunkPulled(AudioChunk audioChunk) {
        //passs
    }

    private PullableSource mic() {
        return new PullableSource.Default(
                new AudioRecordConfig.Default(
                        MediaRecorder.AudioSource.MIC, AudioFormat.ENCODING_PCM_16BIT,
                        AudioFormat.CHANNEL_IN_MONO, 44100
                )
        );
    }
}
