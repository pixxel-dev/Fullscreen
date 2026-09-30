package com.flyme.fscrn;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.os.StatFs;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileManagerFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private FileAdapter leftAdapter;
    private FileAdapter rightAdapter;

    private TextView leftPathText, leftStorageInfo;
    private TextView rightPathText, rightStorageInfo;

    private File currentLeftDir = Environment.getExternalStorageDirectory();
    private File currentRightDir = Environment.getExternalStorageDirectory();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_manager, container, false);
    }

    private boolean permissionRequested = false;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUI(view);

        if (!hasStoragePermission()) {
            permissionRequested = true;
            checkStoragePermissions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            loadDirectory(currentLeftDir, true);
            loadDirectory(currentRightDir, false);
        } else if (!permissionRequested) {
            // Only request automatically if we haven't just returned from a denied request
            permissionRequested = true;
            checkStoragePermissions();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int readPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void setupUI(View view) {
        leftPathText = view.findViewById(R.id.left_path_text);
        leftStorageInfo = view.findViewById(R.id.left_storage_info);
        RecyclerView leftRecycler = view.findViewById(R.id.left_recycler_view);

        rightPathText = view.findViewById(R.id.right_path_text);
        rightStorageInfo = view.findViewById(R.id.right_storage_info);
        RecyclerView rightRecycler = view.findViewById(R.id.right_recycler_view);

        leftAdapter = new FileAdapter(file -> handleFileClick(file, true));
        leftRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        leftRecycler.setAdapter(leftAdapter);

        rightAdapter = new FileAdapter(file -> handleFileClick(file, false));
        rightRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        rightRecycler.setAdapter(rightAdapter);
    }

    private void handleFileClick(File file, boolean isLeftPanel) {
        if (file.getName().equals("..")) {
            File parent = (isLeftPanel ? currentLeftDir : currentRightDir).getParentFile();
            if (parent != null) {
                loadDirectory(parent, isLeftPanel);
            }
        } else if (file.isDirectory()) {
            loadDirectory(file, isLeftPanel);
        }
    }

    private void loadDirectory(File dir, boolean isLeftPanel) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;

        if (isLeftPanel) {
            currentLeftDir = dir;
            leftPathText.setText(dir.getAbsolutePath());
            updateStorageInfo(dir, leftStorageInfo);
        } else {
            currentRightDir = dir;
            rightPathText.setText(dir.getAbsolutePath());
            updateStorageInfo(dir, rightStorageInfo);
        }

        File[] filesArray = dir.listFiles();
        List<File> filesList = new ArrayList<>();

        if (dir.getParentFile() != null) {
            filesList.add(new File(dir, ".."));
        }

        if (filesArray != null) {
            filesList.addAll(Arrays.asList(filesArray));
        }

        if (isLeftPanel) {
            leftAdapter.setFiles(filesList);
        } else {
            rightAdapter.setFiles(filesList);
        }
    }

    private void updateStorageInfo(File dir, TextView infoView) {
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            long totalBytes = stat.getTotalBytes();
            long freeBytes = stat.getAvailableBytes();
            long usedBytes = totalBytes - freeBytes;

            infoView.setText(String.format("Использовано: %s / %s",
                FileAdapter.formatSize(usedBytes),
                FileAdapter.formatSize(totalBytes)));
        } catch (Exception e) {
            infoView.setText("Информация о диске недоступна");
        }
    }

    private void checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", requireContext().getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDirectory(currentLeftDir, true);
                loadDirectory(currentRightDir, false);
            }
        }
    }
}
