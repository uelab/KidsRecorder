# KidsRecorder
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)
[![Build Status](https://travis-ci.com/uelab/KidsRecorder.svg?branch=master)](https://travis-ci.com/uelab/KidsRecorder)
[![](https://jitpack.io/v/uelab/KidsRecorder.svg)](https://jitpack.io/#uelab/KidsRecorder)

A recorder for research with kids. Associated with the paper: [Anchored Audio Sampling: A Seamless Method for Exploring Children's Thoughts During Deployment Studies](https://makeabilitylab.cs.washington.edu/media/publications/Hiniker_AnchoredAudioSamplingASeamlessMethodForExploringChildrenSThoughtsDuringDeploymentStudies_2019.pdf)

![]()
<img src="https://github.com/DrustZ/KidsRecorder/blob/master/Screenshot_20180729-103148.png" width="210">
<img src="https://github.com/DrustZ/KidsRecorder/blob/master/Screenshot_20180729-103158.png" width="210">
<img src="https://github.com/DrustZ/KidsRecorder/blob/master/Screenshot_20180729-103205.png" width="210">
<img src="https://github.com/DrustZ/KidsRecorder/blob/master/Screenshot_20180729-103209.png" width="210">

## How to use

If you just want to use the core recorder and file functions, you can  in your project:
```java
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
And then configure the dependency:

```java
	dependencies {
	        implementation 'com.github.uelab:KidsRecorder:0.11'
	}
```

However, if you want to also use the UI for recorder, the file explorer UI: please download the code and add them in your project. Examples are provided in this project.

### AndroidManifest
1 - Add permissions into you `AndroidManifest.xml` and [request for them in Android 6.0+](https://developer.android.com/training/permissions/requesting.html)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />  
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />  
<uses-permission android:name="android.permission.WAKE_LOCK" /> //for recording during lock screen 
<uses-permission android:name="android.permission.INTERNET" />  
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />  
<uses-permission android:name="android.permission.VIBRATE"/> //for vibrating the phone as indicator
```

1.5 - If you want to use Amazon AWS for uploading recording files, please add the `AmazonAuthenticationActivity` in the `AndroidManifest.xml`. Add `FileExplorerActivity` and `FileExplorerActivity` as well if you want the activities. 
_Please specify the later two activities as the child activity of the main recording activity_.

* For `AmazonAuthenticationActivity`
```xml
<activity android:name=".UI.AmazonAuthenticationActivity">  
<intent-filter> <action android:name="android.intent.action.MAIN" />  
<category android:name="android.intent.category.LAUNCHER" />  
</intent-filter></activity>
```
* For `FileExplorerActivity` and `FileExplorerActivity` (the `MainActivity` is the main recording activity)
```xml 
<activity android:name=".UI.FileExplorerActivity">  
 <meta-data  android:name="android.support.PARENT_ACTIVITY"  
  android:value=".UI.MainActivity" />  
</activity>  
  
<activity android:name=".UI.SettingsActivity"  
  android:theme="@style/AppTheme.SettingsTheme">  
 <meta-data  android:name="android.support.PARENT_ACTIVITY"  
  android:value=".UI.MainActivity" />  
</activity>
```

2 - Because the `RecordingManager` is a `Service`, we need to add corresponding `Service` in the `AndroidManifest.xml`. 

```xml
<service android:name=".Recording.RecordingManager" />
```
* Also add `TransferService` if you want to use [AWS uploading function](https://docs.aws.amazon.com/aws-mobile/latest/developerguide/add-aws-mobile-user-data-storage.html)
```xml
<service android:name="com.amazonaws.mobileconnectors.s3.transferutility.TransferService"  
  android:enabled="true" />
```

3 - If you want to enable share function of the recording files, please add following settings in the `AndroidManifest.xml` to enable file sharing uri.

```xml
<provider  
  android:name="android.support.v4.content.FileProvider"  
  android:authorities="${applicationId}.fileprovider"  
  android:exported="false"  
  android:grantUriPermissions="true">  
 <meta-data  android:name="android.support.FILE_PROVIDER_PATHS"  
  android:resource="@xml/provider_paths" />  
</provider>
```

### RecordingManager & DataManager
`RecordingManager` is the main recording class. It supports
- [x] Record/Stop/Pause/Resume in Background/Foreground mode
- [x] Broadcasting when record status changes
- [x] Communicates with `DataManager` when recording finishes. `DataManager` will manage remaining data operations.
- [x] Supports preceding recording mode (Record the preceding x seconds before the formal recording, will describe later)

 `DataManager` is the main data operator to manage recording storage/deletion/uploading. It supports
 - [x] Store newly recorded clips into [Room Database](https://developer.android.com/training/data-storage/room/)
 - [x] Delete old recordings to only keep recent n recordings
 - [x] Upload automatically to AWS, and reuploading if the uploading the fails
 - [x] Uploading buffer. Delay the uploading by keeping the newly records in a buffer. Only upload them when the buffer is full
 - [x] Handle preceding files and delete them regularly (will discuss later)

_`RecordingManager` and `DataManager` is used together to handle the whole **recording-file storage-file uploading** process_

1 - Initialize the `RecordingManager` and `DataManager` in your recording activity. Notice that `DataManager` is a singleton class (which means there would only be one instance in the whole application), thus we need to call `DataManager.getInstance()` to get the instance.

* First, initialize the `DataManager` 
```java
//The procedure to instantiate the datamanager  
//first call getInstance, then setfoldername, then call Initialize  
dataManager = DataManager.getInstance();  
try {
	//set the name of local folder where the recordings will be stored
    dataManager.setFolderName("KidsRecorder");  
	dataManager.Initialize(context);  
  
} catch (IOException e) {  
    e.printStackTrace();  
}  
```

* Then after checking all permissions, start the `RecordingManager` Service. Because we will interact with the service later, thus we need to bind it to get its instance. The following code describes how to start the service and bind it.
```java
void startRecordingService() {
	Intent recorderIntent = new Intent(this, RecordingManager.class);  
	startService(recorderIntent);  
	bindService(recorderIntent, serviceConnection, Context.BIND_AUTO_CREATE);
}

//Binding this Client to the AudioPlayer Service  
private ServiceConnection serviceConnection = new ServiceConnection() {  
    @Override  
  public void onServiceConnected(ComponentName name, IBinder service) {  
        // We've bound to LocalService, cast the IBinder and get Service instance  
  RecordingManager.LocalBinder binder = (RecordingManager.LocalBinder) service;  
  //Here we retrieve the RecordingManager service
  recordingManager = binder.getServiceInstance();  
  serviceBound = true;  
  }  
  
    @Override  
  public void onServiceDisconnected(ComponentName name) {  
        serviceBound = false;  
  }  
};
```

2 - Let's start recording!

* To start a timed recording (in seconds)
```java
recordingManager.StartRecording(dataManager.getRecordingNameOfTime(), time_limit);  
```

* To start a recording (without timing)
```java
recordingManager.StartRecording(dataManager.getRecordingNameOfTime(), 0); 
```

* To stop a recording
```java
if (recordingManager.isRecording())  
    recordingManager.StopRecording();
```

* To pause/resume a recording
```java
recordingManager.PauseRecording();
recordingManager.ResumeRecording();
```

3 - Configure recording/storage settings

```java
//set buffer size
dataManager.setBufferSize(int);  
//set max local file storage
dataManager.setMaxFilesBeforeDelete(int);  
//set whether upload autimatically
dataManager.setAutoUpload(boolean);  

//set whether recording even screen is locked  
recordingManager.setAlwaysRunning(boolean);  
//enable preceding mode 
recordingManager.setShouldPrecede(true);
//set preceding time before the formal recording, in seconds
recordingManager.setPrecedingTime(int);
```

### Foreground/Background recording
Because `RecordingManager` is a service, it also runs in the background (when the app is not the current screen app). Thus if you want to only record in foreground, you can stop the recording in `onStop()`:

```java
@Override  
protected void onStop() {  
    super.onStop();  
	//if the background recording is off, then stop current recording  
	if (!record_background && recordingManager != null && recordingManager.isRecording()){  
		recordingManager.StopRecording();  
  }  
}
``` 

### Preceding mode
Preceding mode means when recording, a preceding time will also be recorded. For example, if we set preceding time to 10 seconds, when the user click "record" button (a formal recording),  10 seconds before the buttonclicking would also be stored.

The mechanism of preceding mode is that `RecordingManager` would keep recording small clips that equal to length ``precedingTime``. When the formal recording is triggered, it would stop the small recording, and keep the former two clips, then start the formal recording. When the formal recording ends, it would start small clips recording again. If there is no formal recording, it will discard the small clips constantly. 

* To enable preceding mode, we need to set 
```java
recordingManager.setPrecedingTime(int); //specify how long before the formal recording we need to keep
```
* Then start the always-on recording using `silent mode`
```java
recordingManager.StartRecordingSilently(String filename)
```

* If we want to start a formal recording, call `StartRecording` (not the silent mode) (the normal recording procedure)
```java
recordingManager.StartRecording(dataManager.getRecordingNameOfTime(), 0); 
``` 

* and Stop
```java
recordingManager.StopRecording();
``` 

* If we want to stop the preceding always-on recording, we need to call 
```java
recordingManager.StopRecordingWithoutStartingBackground();
```
In this way, we stopped all recordings (including the formal recording)

#### Merge Preceding Files with the Recording File
* you can set the recordingManager's `should_mergeprecede` to true to enable the merge function. The defualt is true. It will merge the preceding files with the anchor file together, thus resulting in a larger recording file. If it's false, then separate files will be stored, including the preceding files with name " -preceding.wav" and the anchor file.

_Notice that because the formal recording might interrupt the preceding small clip recording, thus to make sure the preceding recording is long enough, we store the two preceding recordings rather than one. Preceding recordings is named with "Preceding" prefix_

### AWS Authentication & Uploading
To enable the AWS integration, please refre to [here] (https://docs.aws.amazon.com/aws-mobile/latest/developerguide/aws-mobile-android-and-iOS.html)

And put your mobile hub configuration file into _/app/src/main/res/raw/_

### UI 
### FileExplorer
To use the ``FileExplorerActivity`` in the library, you can just include them in your project.


For more details, please read the comments in the corresponding files.

## Dependencies
- [OmRecorder](https://github.com/kailash09dabhi/OmRecorder)
- [Room Database](https://developer.android.com/topic/libraries/architecture/room)
- [AWS Android sdk](https://docs.aws.amazon.com/aws-mobile/latest/developerguide/getting-started.html)
- [Android Preference Support Library v7 and v14](https://developer.android.com/topic/libraries/support-library/packages#v7-preference)
- [Android CardView and RecyclerView Library](https://developer.android.com/topic/libraries/support-library/packages#v7-cardview)
- [Gson](https://github.com/google/gson)


## Citation
If you use the code in your paper, then please cite it as:

```
@inproceedings{Hiniker:2019:AAS:3290605.3300238,
 author = {Hiniker, Alexis and Froehlich, Jon E. and Zhang, Mingrui and Beneteau, Erin},
 title = {Anchored Audio Sampling: A Seamless Method for Exploring Children's Thoughts During Deployment Studies},
 booktitle = {Proceedings of the 2019 CHI Conference on Human Factors in Computing Systems},
 series = {CHI '19},
 year = {2019},
 isbn = {978-1-4503-5970-2},
 location = {Glasgow, Scotland Uk},
 pages = {8:1--8:13},
 articleno = {8},
 numpages = {13},
 url = {http://doi.acm.org/10.1145/3290605.3300238},
 doi = {10.1145/3290605.3300238},
 acmid = {3300238},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {cci, context-aware esm, data collection tools, early childhood, field deployments, study methods},
} 
```

## License
```
MIT License

Copyright (c) 2018 Mingrui Zhang

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
