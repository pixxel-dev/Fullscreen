#!/bin/bash
sed -i 's/import com.flyme.fscrn.service.OverlayService;/import com.flyme.fscrn.overlay.OverlayService;/g' app/src/main/java/com/flyme/fscrn/system/BootReceiver.java
sed -i '/package com.flyme.fscrn.filemanager;/a import com.flyme.fscrn.installer.ApkInstaller;' app/src/main/java/com/flyme/fscrn/filemanager/FileManagerFragment.java
sed -i '/package com.flyme.fscrn.installer;/a import com.flyme.fscrn.adb.NativeAdbHelper;\nimport com.flyme.fscrn.adb.LocalAdbHelper;' app/src/main/java/com/flyme/fscrn/installer/ApkInstaller.java
sed -i '/package com.flyme.fscrn.ui;/a import com.flyme.fscrn.adb.NativeAdbHelper;' app/src/main/java/com/flyme/fscrn/ui/MainActivity.java
