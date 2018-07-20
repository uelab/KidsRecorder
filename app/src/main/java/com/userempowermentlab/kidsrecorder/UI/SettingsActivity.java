package com.userempowermentlab.kidsrecorder.UI;

import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.userempowermentlab.kidsrecorder.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new SettingsFragment()).commit();

        // Enable the Up button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
