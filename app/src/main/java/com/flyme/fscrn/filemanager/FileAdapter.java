package com.flyme.fscrn.filemanager;

import android.view.LayoutInflater;
import com.flyme.fscrn.R;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<File> files = new ArrayList<>();
    private Set<File> selectedFiles = new HashSet<>();
    private final OnFileClickListener listener;
    private final OnFileLongClickListener longClickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public interface OnFileLongClickListener {
        void onFileLongClick(File file);
    }

    public FileAdapter(OnFileClickListener listener, OnFileLongClickListener longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setFiles(List<File> newFiles) {
        this.files = newFiles;
        this.selectedFiles.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedFiles.clear();
        notifyDataSetChanged();
    }

    public Set<File> getSelectedFiles() {
        return selectedFiles;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        CheckBox checkBox;
        TextView nameView;
        TextView sizeView;
        TextView dateView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.item_icon);
            checkBox = itemView.findViewById(R.id.item_checkbox);
            nameView = itemView.findViewById(R.id.item_name);
            sizeView = itemView.findViewById(R.id.item_size);
            dateView = itemView.findViewById(R.id.item_date);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    File file = files.get(position);
                    // If in selection mode, click toggles selection instead of opening
                    if (!selectedFiles.isEmpty() && !file.getName().equals("..")) {
                        toggleSelection(file);
                    } else {
                        listener.onFileClick(file);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    File clickedFile = files.get(position);
                    if (!clickedFile.getName().equals("..")) {
                        longClickListener.onFileLongClick(clickedFile);
                        return true;
                    }
                }
                return false;
            });
        }

        public void bind(File file) {
            if (file.getName().equals("..")) {
                nameView.setText("..");
                iconView.setVisibility(View.VISIBLE);
                checkBox.setVisibility(View.GONE);
                iconView.setImageResource(R.drawable.ic_folder);
                sizeView.setText("Наверх");
                dateView.setText("");
                itemView.setBackgroundResource(0);
                return;
            }

            nameView.setText(file.getName());
            dateView.setText(dateFormat.format(new Date(file.lastModified())));

            if (file.isDirectory()) {
                iconView.setImageResource(R.drawable.ic_folder);
                sizeView.setText("Папка");
            } else {
                setFileIcon(file, iconView);
                sizeView.setText(formatSize(file.length()));
            }

            boolean isSelected = selectedFiles.contains(file);
            if (!selectedFiles.isEmpty()) {
                iconView.setVisibility(View.GONE);
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(isSelected);
            } else {
                iconView.setVisibility(View.VISIBLE);
                checkBox.setVisibility(View.GONE);
            }

            if (isSelected) {
                itemView.setBackgroundColor(0x3300FF00); // Light green for selection
            } else {
                itemView.setBackgroundResource(0);
            }
        }

        private void setFileIcon(File file, ImageView iconView) {
            String name = file.getName();
            String ext = "";
            int i = name.lastIndexOf('.');
            if (i > 0 && i < name.length() - 1) {
                ext = name.substring(i + 1).toLowerCase(Locale.US);
            }

            if (ext.equals("apk")) {
                iconView.setImageResource(R.drawable.ic_file_apk);
                return;
            } else if (ext.equals("zip") || ext.equals("rar") || ext.equals("7z") || ext.equals("tar") || ext.equals("gz")) {
                iconView.setImageResource(R.drawable.ic_file_archive);
                return;
            }

            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) {
                    iconView.setImageResource(R.drawable.ic_file_image);
                } else if (mimeType.startsWith("video/")) {
                    iconView.setImageResource(R.drawable.ic_file_video);
                } else if (mimeType.startsWith("audio/")) {
                    iconView.setImageResource(R.drawable.ic_file_audio);
                } else {
                    iconView.setImageResource(R.drawable.ic_file);
                }
            } else {
                iconView.setImageResource(R.drawable.ic_file);
            }
        }
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
