package com.userempowermentlab.kidsrecorder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.userempowermentlab.kidsrecorder.Data.DataManager;
import com.userempowermentlab.kidsrecorder.Data.RecordItem;
import com.userempowermentlab.kidsrecorder.UI.PlaybackFragment;

import java.util.concurrent.TimeUnit;

public class FileViewAdapter extends RecyclerView.Adapter<FileViewAdapter.RecordingsViewHolder>  {
    Context mContext;
    RecordItem item;
    DataManager dataManager;

    public FileViewAdapter(Context context) {
        super();
        mContext = context;
        dataManager = DataManager.getInstance();
    }

    public static class RecordingsViewHolder extends RecyclerView.ViewHolder {
        protected TextView vName;
        protected TextView vLength;
        protected TextView vDateAdded;
        protected TextView vUploaded;
        protected View cardView;

        public RecordingsViewHolder(View v) {
            super(v);
            vName = v.findViewById(R.id.file_name_text);
            vLength = v.findViewById(R.id.file_length_text);
            vDateAdded = v.findViewById(R.id.file_date_added_text);
            vUploaded = v.findViewById(R.id.upload_label);
            cardView = v.findViewById(R.id.recordingcardview);
        }
    }

    @Override
    public RecordingsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View itemView = LayoutInflater.
                from(parent.getContext()).
                inflate(R.layout.recordingcardview, parent, false);

        mContext = parent.getContext();

        return new RecordingsViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final RecordingsViewHolder holder, int position) {

        item = dataManager.getItemAtPos(position);
        int itemDuration = item.duration;

        long minutes = TimeUnit.SECONDS.toMinutes(itemDuration);
        long seconds = TimeUnit.SECONDS.toSeconds(itemDuration)
                - TimeUnit.MINUTES.toSeconds(minutes);

        holder.vName.setText(item.filename);
        holder.vLength.setText(String.format("%02d:%02d", minutes, seconds));
        holder.vDateAdded.setText(item.createDate);
        if (!item.uploaded){
            holder.vUploaded.setVisibility(View.INVISIBLE);
        } else {
            holder.vUploaded.setVisibility(View.VISIBLE);
        }

        // define an on click listener to open PlaybackFragment
        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    PlaybackFragment playbackFragment =
                            new PlaybackFragment().newInstance(dataManager.getItemAtPos(holder.getLayoutPosition()));

                    FragmentTransaction transaction = ((FragmentActivity) mContext)
                            .getSupportFragmentManager()
                            .beginTransaction();

                    playbackFragment.show(transaction, "dialog_playback");

                } catch (Exception e) {
                    Log.e("[RAY]", "exception", e);
                }
            }
        });

        holder.cardView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return false;
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return dataManager.getItemCout();
    }

    public void shareFileDialog(int position) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
//        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(getItem(position).getFilePath())));
        shareIntent.setType("audio/wav");
//        mContext.startActivity(Intent.createChooser(shareIntent, mContext.getText(R.string.send_to)));
    }

}
