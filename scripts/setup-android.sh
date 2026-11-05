#!/usr/bin/env bash
# Creates the Android project (once) and wires in the native music plugin.
set -e
[ -d www ] || npm run www
[ -d android ] || npx --yes @capacitor/cli@6 add android
D=android/app/src/main/java/com/example/player
mkdir -p "$D"
cp native/MusicLibraryPlugin.java "$D/"
cat > "$D/MainActivity.java" <<'JAVA'
package com.example.player;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MusicLibraryPlugin.class);
    super.onCreate(savedInstanceState);
    // Wrap the bridge WebViewClient so /_music/<id> requests get real HTTP
    // Range support (206 Partial Content). Everything else goes to the
    // original Capacitor client untouched.
    final Bridge b = getBridge();
    if (b != null && b.getWebView() != null && Build.VERSION.SDK_INT >= 21) {
      final WebViewClient original = b.getWebViewClient();
      b.getWebView().setWebViewClient(new WebViewClient() {
        @TargetApi(21)
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
          String path = request.getUrl().getPath();
          if (path != null && path.startsWith("/_music/")) {
            MusicLibraryPlugin p = null;
            try {
              p = (MusicLibraryPlugin) b.getPlugin("MusicLibrary").getInstance();
            } catch (Exception ignored) {}
            if (p != null) {
              Map<String, String> headers = new HashMap<>();
              try {
                Map<String, String> rh = request.getRequestHeaders();
                if (rh != null) for (Map.Entry<String, String> e : rh.entrySet()) {
                  headers.put(e.getKey() == null ? "" : e.getKey().toLowerCase(), e.getValue());
                }
              } catch (Exception ignored) {}
              WebResourceResponse r = p.serveAudio(path, headers);
              if (r != null) return r;
            }
          }
          return original.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
          return original.shouldOverrideUrlLoading(view, url);
        }

        @TargetApi(24)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
          return original.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
          original.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
          original.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
          original.onReceivedError(view, errorCode, description, failingUrl);
        }

        @TargetApi(23)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
          original.onReceivedError(view, request, error);
        }

        @TargetApi(26)
        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
          return original.onRenderProcessGone(view, detail);
        }
      });
    }
  }
}
JAVA
M=android/app/src/main/AndroidManifest.xml
grep -q READ_MEDIA_AUDIO "$M" || sed -i 's#<application#<uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>\n    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>\n    <application#' "$M"
echo "Android project ready"
