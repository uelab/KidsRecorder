package com.userempowermentlab.aasrecorder.Data;

import android.content.Context;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;

/**
 * The upload class
 * Code provided by Amazon https://docs.aws.amazon.com/aws-mobile/latest/developerguide/add-aws-mobile-user-data-storage.html
 * Created by mingrui on 7/16/2018.
 */

public class DataUploader {

    public static void AmazonAWSUploader(final Context context, final String filename, final String toname) {
        TransferUtility transferUtility =
                TransferUtility.builder()
                        .context(context)
                        .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                        .s3Client(new AmazonS3Client(AWSMobileClient.getInstance().getCredentialsProvider()))
                        .build();

        TransferObserver uploadObserver =
                transferUtility.upload(
                        toname,
                        new File(filename));

        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state) {
                    // Handle a completed upload.
                    DataManager manager = DataManager.getInstance();
                    manager.OnUploadFinished(filename);
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                int percentDone = (int)percentDonef;
            }

            @Override
            public void onError(int id, Exception ex) {
                ex.printStackTrace();
                // Handle errors
                DataManager manager = DataManager.getInstance();
                manager.OnUploadError(filename);
            }

        });
    }
}
