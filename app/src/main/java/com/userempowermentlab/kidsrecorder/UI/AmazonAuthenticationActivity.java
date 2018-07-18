package com.userempowermentlab.kidsrecorder.UI;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.amazonaws.mobile.auth.ui.SignInUI;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;

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