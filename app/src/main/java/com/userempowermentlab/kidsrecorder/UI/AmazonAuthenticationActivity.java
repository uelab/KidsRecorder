package com.userempowermentlab.kidsrecorder.UI;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.amazonaws.mobile.auth.ui.SignInUI;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;

/**
 * The authentication activity class
 * It creates the log-in activity. (IF WANT USER TO LOG IN FIRST TO USE THE APP, PLEASE MAKE IT THE STARTING ACTIVITY OF THE APPLICATION)
 * Code provided by Amazon https://docs.aws.amazon.com/aws-mobile/latest/developerguide/add-aws-mobile-user-sign-in.html
 * Created by mingrui on 7/16/2018.
 */
public class AmazonAuthenticationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add a call to initialize AWSMobileClient
        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("AuthenticationActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
                SignInUI signin = (SignInUI) AWSMobileClient.getInstance().getClient(AmazonAuthenticationActivity.this, SignInUI.class);
                signin.login(AmazonAuthenticationActivity.this, MainActivity.class).execute();
            }
        }).execute();
    }
}