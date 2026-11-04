#!/usr/bin/env bash
# Creates the Android project (once) and wires in the native music plugin.
set -e
[ -d android ] || npx cap add android
D=android/app/src/main/java/com/example/player
mkdir -p "$D"
cp native/MusicLibraryPlugin.java "$D/"
cat > "$D/MainActivity.java" <<'JAVA'
package com.example.player;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MusicLibraryPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
JAVA
M=android/app/src/main/AndroidManifest.xml
grep -q READ_MEDIA_AUDIO "$M" || sed -i 's#<application#<uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>\n    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>\n    <application#' "$M"
echo "Android project ready"
