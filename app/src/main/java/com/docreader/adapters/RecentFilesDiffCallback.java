package com.docreader.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.docreader.models.RecentFile;

import java.util.List;
import java.util.Objects;

/**
 * DiffUtil.Callback implementation for efficient RecyclerView updates.
 * Replaces notifyDataSetChanged() with more efficient differential updates.
 */
public class RecentFilesDiffCallback extends DiffUtil.Callback {

    private final List<RecentFile> oldList;
    private final List<RecentFile> newList;

    public RecentFilesDiffCallback(@NonNull List<RecentFile> oldList, @NonNull List<RecentFile> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        RecentFile oldFile = oldList.get(oldItemPosition);
        RecentFile newFile = newList.get(newItemPosition);
        // Items are the same if they have the same file path
        return Objects.equals(oldFile.getPath(), newFile.getPath());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        RecentFile oldFile = oldList.get(oldItemPosition);
        RecentFile newFile = newList.get(newItemPosition);
        // Check if all fields are the same
        return Objects.equals(oldFile.getPath(), newFile.getPath()) &&
               Objects.equals(oldFile.getName(), newFile.getName()) &&
               Objects.equals(oldFile.getType(), newFile.getType()) &&
               oldFile.getLastOpened() == newFile.getLastOpened();
    }

    /**
     * Static helper for item callback (for ListAdapter).
     */
    public static final DiffUtil.ItemCallback<RecentFile> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<RecentFile>() {
        @Override
        public boolean areItemsTheSame(@NonNull RecentFile oldItem, @NonNull RecentFile newItem) {
            return Objects.equals(oldItem.getPath(), newItem.getPath());
        }

        @Override
        public boolean areContentsTheSame(@NonNull RecentFile oldItem, @NonNull RecentFile newItem) {
            return Objects.equals(oldItem.getPath(), newItem.getPath()) &&
                   Objects.equals(oldItem.getName(), newItem.getName()) &&
                   Objects.equals(oldItem.getType(), newItem.getType()) &&
                   oldItem.getLastOpened() == newItem.getLastOpened();
        }
    };
}
