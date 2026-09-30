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
import android.widget.Toast;
import android.widget.ImageButton;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileManagerFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private FileAdapter mainAdapter;

    private TextView mainPathText, mainStorageInfo;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnMenu;

    private File currentDir = Environment.getExternalStorageDirectory();

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
            loadDirectory(currentDir);
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
        mainPathText = view.findViewById(R.id.main_path_text);
        mainStorageInfo = view.findViewById(R.id.main_storage_info);
        RecyclerView mainRecycler = view.findViewById(R.id.main_recycler_view);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        navigationView = view.findViewById(R.id.nav_view);
        btnMenu = view.findViewById(R.id.btn_menu);

        mainAdapter = new FileAdapter(
            file -> handleFileClick(file),
            file -> showFileOperationsDialog(file)
        );
        mainRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        mainRecycler.setAdapter(mainAdapter);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_root) {
                loadDirectory(new File("/"));
            } else if (id == R.id.nav_internal) {
                loadDirectory(Environment.getExternalStorageDirectory());
            } else if (id == R.id.nav_add_ftp || id == R.id.nav_add_sftp || id == R.id.nav_add_smb || id == R.id.nav_add_webdav) {
                Toast.makeText(getContext(), "В разработке: добавление сетевого диска", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private File fileInClipboard = null;
    private boolean isCutOperation = false;

    private void showFileOperationsDialog(File file) {
        boolean isApk = file.getName().toLowerCase().endsWith(".apk");

        List<String> optionsList = new ArrayList<>(Arrays.asList(
            "Копировать", "Вырезать", "Удалить", "Переименовать", "Свойства"
        ));
        if (isApk) {
            optionsList.add("Установить");
            optionsList.add("Установить (Root/Shizuku)");
            optionsList.add("Установить (Local ADB/Root)");
        }

        String[] options = optionsList.toArray(new String[0]);

        new AlertDialog.Builder(requireContext())
            .setTitle(file.getName())
            .setItems(options, (dialog, which) -> {
                String selectedOption = options[which];
                switch (selectedOption) {
                    case "Копировать":
                        fileInClipboard = file;
                        isCutOperation = false;
                        Toast.makeText(getContext(), "Файл скопирован. Перейдите в нужную папку и вставьте.", Toast.LENGTH_SHORT).show();
                        getActivity().invalidateOptionsMenu(); // Signal to show paste button
                        break;
                    case "Вырезать":
                        fileInClipboard = file;
                        isCutOperation = true;
                        Toast.makeText(getContext(), "Файл вырезан. Перейдите в нужную папку и вставьте.", Toast.LENGTH_SHORT).show();
                        getActivity().invalidateOptionsMenu(); // Signal to show paste button
                        break;
                    case "Удалить":
                        deleteFileRecursive(file);
                        refreshPanel();
                        break;
                    case "Переименовать":
                        showRenameDialog(file);
                        break;
                    case "Свойства":
                        showPropertiesDialog(file);
                        break;
                    case "Установить":
                        openFile(file); // reuse existing logic
                        break;
                    case "Установить (Root/Shizuku)":
                        installApkWithShizuku(file);
                        break;
                    case "Установить (Local ADB/Root)":
                        installApkWithLocalAdb(file);
                        break;
                }
            })
            .show();
    }

    public boolean hasFileInClipboard() {
        return fileInClipboard != null;
    }

    // Add paste capability
    public void pasteFile() {
        if (fileInClipboard == null || !fileInClipboard.exists()) {
            Toast.makeText(getContext(), "Буфер пуст или файл удален", Toast.LENGTH_SHORT).show();
            fileInClipboard = null;
            getActivity().invalidateOptionsMenu();
            return;
        }

        File targetFile = new File(currentDir, fileInClipboard.getName());

        new Thread(() -> {
            boolean success = false;
            try {
                if (fileInClipboard.isDirectory()) {
                    success = copyDirectory(fileInClipboard, targetFile);
                } else {
                    success = copySingleFile(fileInClipboard, targetFile);
                }

                if (success && isCutOperation) {
                    deleteFileRecursive(fileInClipboard);
                    fileInClipboard = null; // Clear clipboard after move
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), finalSuccess ? "Успешно вставлено" : "Ошибка вставки", Toast.LENGTH_SHORT).show();
                    if (finalSuccess && isCutOperation) {
                        getActivity().invalidateOptionsMenu();
                    }
                    refreshPanel();
                });
            }
        }).start();
    }

    private void installApkWithLocalAdb(File file) {
        Toast.makeText(getContext(), "Начинаю установку через ADB/Root...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // First try with su (Root), if that fails, try with normal sh (Local ADB/Shell)
                Process process = Runtime.getRuntime().exec("su");
                java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
                os.writeBytes("pm install -r \"" + file.getAbsolutePath() + "\"\n");
                os.writeBytes("exit\n");
                os.flush();

                int exitValue = process.waitFor();

                if (exitValue != 0) {
                    // Try without root if SU failed
                    Process noRootProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm install -r \"" + file.getAbsolutePath() + "\""});
                    exitValue = noRootProcess.waitFor();

                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(noRootProcess.getInputStream()));
                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    final String result = output.toString().trim();
                    final boolean success = exitValue == 0 && result.toLowerCase().contains("success");

                    getActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(getContext(), "Приложение успешно установлено (Shell)!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Ошибка установки (Shell): " + result, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Приложение успешно установлено (Root)!", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка выполнения команды: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installApkWithShizuku(File file) {
        if (!rikka.shizuku.Shizuku.pingBinder()) {
            Toast.makeText(getContext(), "Shizuku не запущен или недоступен", Toast.LENGTH_LONG).show();
            return;
        }

        if (rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            rikka.shizuku.Shizuku.requestPermission(1001);
            Toast.makeText(getContext(), "Запрошено разрешение Shizuku, попробуйте еще раз", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "Начинаю установку через Shizuku...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Process process = rikka.shizuku.Shizuku.newProcess(new String[]{"pm", "install", "-r", file.getAbsolutePath()}, null, null);

                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();

                final String result = output.toString().trim();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (result.contains("Success")) {
                            Toast.makeText(getContext(), "Успешно установлено", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Ошибка: " + result, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Сбой: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void showRenameDialog(File file) {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(file.getName());
        new AlertDialog.Builder(requireContext())
            .setTitle("Переименовать")
            .setView(input)
            .setPositiveButton("ОК", (dialog, which) -> {
                String newName = input.getText().toString();
                if (!newName.isEmpty()) {
                    File newFile = new File(file.getParent(), newName);
                    if (file.renameTo(newFile)) {
                        refreshPanel();
                    } else {
                        Toast.makeText(getContext(), "Ошибка переименования", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showPropertiesDialog(File file) {
        long size = getFolderSize(file);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
        String date = sdf.format(new java.util.Date(file.lastModified()));

        String info = "Путь: " + file.getAbsolutePath() + "\n" +
                      "Размер: " + FileAdapter.formatSize(size) + "\n" +
                      "Изменен: " + date + "\n" +
                      (file.isDirectory() ? "Тип: Папка" : "Тип: Файл");

        new AlertDialog.Builder(requireContext())
            .setTitle("Свойства")
            .setMessage(info)
            .setPositiveButton("ОК", null)
            .show();
    }

    private long getFolderSize(File file) {
        if (!file.exists()) return 0;
        if (!file.isDirectory()) return file.length();
        long size = 0;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                size += getFolderSize(f);
            }
        }
        return size;
    }

    private boolean copySingleFile(File source, File dest) {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024];
            int length;
            while ((length = in.read(buf)) > 0) {
                out.write(buf, 0, length);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean copyDirectory(File sourceLocation, File targetLocation) {
        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                return false;
            }
            String[] children = sourceLocation.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = copyDirectory(new File(sourceLocation, child), new File(targetLocation, child));
                    if (!success) return false;
                }
            }
        } else {
            return copySingleFile(sourceLocation, targetLocation);
        }
        return true;
    }

    private void deleteFileRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void refreshPanel() {
        loadDirectory(currentDir);
    }

    private void handleFileClick(File file) {
        if (file.getName().equals("..")) {
            File parent = currentDir.getParentFile();
            if (parent != null) {
                loadDirectory(parent);
            }
        } else if (file.isDirectory()) {
            loadDirectory(file);
        } else {
            openFile(file);
        }
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);

            String mimeType = getMimeType(file.getAbsolutePath());
            if (mimeType == null) {
                mimeType = "*/*";
            }

            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Если это APK, возможно пользователь хочет его установить
            if (mimeType.equals("application/vnd.android.package-archive")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Нет приложения для открытия этого типа файлов", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Ошибка при открытии файла", Toast.LENGTH_SHORT).show();
            Log.e("FileManager", "Error opening file", e);
        }
    }

    private String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        if (type == null && url.toLowerCase().endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return type;
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) {
            if (dir != null && dir.getAbsolutePath().equals("/")) {
                // allow fallback for root even if unreadable to show empty dir if unrooted
            } else {
                return;
            }
        }

        currentDir = dir;
        mainPathText.setText(dir.getAbsolutePath());
        updateStorageInfo(dir, mainStorageInfo);

        File[] filesArray = dir.listFiles();
        List<File> filesList = new ArrayList<>();

        if (dir.getParentFile() != null) {
            filesList.add(new File(dir, ".."));
        }

        if (filesArray != null) {
            filesList.addAll(Arrays.asList(filesArray));
        }

        mainAdapter.setFiles(filesList);
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
                loadDirectory(currentDir);
            }
        }
    }
}
