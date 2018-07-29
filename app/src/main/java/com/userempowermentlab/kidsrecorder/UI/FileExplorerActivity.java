package com.userempowermentlab.kidsrecorder.UI;

import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.userempowermentlab.kidsrecorder.FileViewAdapter;
import com.userempowermentlab.kidsrecorder.Listener.FileVIewMultiselectedListener;
import com.userempowermentlab.kidsrecorder.R;

/**
 * FileExplorer UI Activity, show the local recording clips
 * Code reference from SoundRecorder: https://github.com/dkim0419/SoundRecorder
 */
public class FileExplorerActivity extends AppCompatActivity implements FileVIewMultiselectedListener{
    private RecyclerView mRecyclerView;
    private FileViewAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    //UI
    MenuItem itemDelete;
    MenuItem itemShare;
    MenuItem itemUpload;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_explorer);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        mRecyclerView = (RecyclerView)findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);

        mRecyclerView.setLayoutManager(llm);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mAdapter = new FileViewAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setFileViewMultiselectedListener(this);
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isMultiSelectionEnabled()){
            mAdapter.deSelectAll();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fileactionbar, menu);
        itemUpload = menu.findItem(R.id.action_upload);
        itemDelete = menu.findItem(R.id.action_delete);
        itemShare = menu.findItem(R.id.action_share);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onMultiselectEnabled(boolean enabled) {
        if (enabled){
            itemUpload.setVisible(true);
            itemShare.setVisible(true);
            itemDelete.setVisible(true);
        } else {
            itemUpload.setVisible(false);
            itemShare.setVisible(false);
            itemDelete.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_upload:
                mAdapter.UploadSelectedFiles();
                return true;
            case R.id.action_delete:
                mAdapter.ShowDeleteFileDialog();
                return true;
            case R.id.action_share:
                mAdapter.ShowShareFileDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
