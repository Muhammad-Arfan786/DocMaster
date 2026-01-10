package com.docreader.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.docreader.R;
import com.docreader.models.RecentFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying recent files in RecyclerView.
 */
public class RecentFilesAdapter extends RecyclerView.Adapter<RecentFilesAdapter.ViewHolder> {

    private List<RecentFile> files;
    private final OnFileClickListener listener;
    private final SimpleDateFormat dateFormat;

    public interface OnFileClickListener {
        void onFileClick(RecentFile file);
        void onRemoveClick(RecentFile file);
    }

    public RecentFilesAdapter(List<RecentFile> files, OnFileClickListener listener) {
        this.files = files;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentFile file = files.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void updateFiles(List<RecentFile> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivFileIcon;
        private final TextView tvFileName;
        private final TextView tvFilePath;
        private final TextView tvFileDate;
        private final MaterialButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFilePath = itemView.findViewById(R.id.tvFilePath);
            tvFileDate = itemView.findViewById(R.id.tvFileDate);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }

        void bind(RecentFile file) {
            tvFileName.setText(file.getName());
            tvFilePath.setText(file.getPath());
            tvFileDate.setText(dateFormat.format(new Date(file.getLastOpened())));

            // Set icon based on file type
            switch (file.getType()) {
                case "pdf":
                    ivFileIcon.setImageResource(R.drawable.ic_pdf);
                    break;
                case "doc":
                case "docx":
                    ivFileIcon.setImageResource(R.drawable.ic_doc);
                    break;
                default:
                    ivFileIcon.setImageResource(R.drawable.ic_document);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileClick(file);
                }
            });

            btnRemove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveClick(file);
                }
            });
        }
    }
}
