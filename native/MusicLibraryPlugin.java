package com.example.player;

import android.content.ContentUris;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.WebResourceResponse;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "MusicLibrary", permissions = {
    @Permission(alias = "audio13", strings = {"android.permission.READ_MEDIA_AUDIO"}),
    @Permission(alias = "audioLegacy", strings = {"android.permission.READ_EXTERNAL_STORAGE"})
})
public class MusicLibraryPlugin extends Plugin {

  private String alias() { return Build.VERSION.SDK_INT >= 33 ? "audio13" : "audioLegacy"; }

  // Capacitor stores DENIED in its prefs only when the system also says the
  // rationale dialog will not show, i.e. "never ask again". Re-check the live
  // state here so granting permission in Settings makes retries work again.
  private boolean blocked() {
    String perm = Build.VERSION.SDK_INT >= 33
        ? "android.permission.READ_MEDIA_AUDIO"
        : "android.permission.READ_EXTERNAL_STORAGE";
    if (getPermissionState(alias()) != PermissionState.DENIED) return false;
    return !androidx.core.app.ActivityCompat
        .shouldShowRequestPermissionRationale(getActivity(), perm);
  }

  // Serves /_music/<mediaStoreId> with full HTTP Range support. Called from
  // MainActivity's wrapped WebViewClient; returns null when the URL is not ours.
  public WebResourceResponse serveAudio(String path, Map<String, String> headers) {
    if (path == null || !path.startsWith("/_music/")) return null;
    long id;
    try {
      String digits = path.substring(8).replaceAll("[^0-9].*$", "");
      if (digits.isEmpty()) throw new NumberFormatException("no id");
      id = Long.parseLong(digits);
    } catch (Exception e) {
      return response(404, "Not Found", -1, -1, 0, null, 0);
    }
    Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    try {
      AssetFileDescriptor afd = getContext().getContentResolver().openAssetFileDescriptor(uri, "r");
      if (afd == null) return response(404, "Not Found", -1, -1, 0, null, null);
      long total = afd.getDeclaredLength();
      if (total < 0) total = afd.getParcelFileDescriptor().getStatSize();
      final InputStream in = afd.createInputStream();
      long start = 0, end = total > 0 ? total - 1 : -1;
      boolean ranged = false;
      String range = headers == null ? null : headers.get("Range");
      if (range != null && total > 0) {
        range = range.trim();
        if (range.regionMatches(true, 0, "bytes=", 0, 6)) range = range.substring(6);
        int dash = range.indexOf('-');
        if (dash >= 0) {
          try {
            String a = range.substring(0, dash).trim();
            String b = range.substring(dash + 1).trim();
            if (a.isEmpty()) {
              long suffix = Long.parseLong(b);
              start = Math.max(0, total - suffix);
            } else {
              start = Long.parseLong(a);
              if (!b.isEmpty()) end = Math.min(Long.parseLong(b), total - 1);
            }
            ranged = true;
          } catch (NumberFormatException e) {
            ranged = false; start = 0; end = total - 1;
          }
        }
      }
      if (start < 0 || (end >= 0 && start > end) || (total > 0 && start >= total)) {
        try { in.close(); } catch (Exception ignored) {}
        return response(416, "Range Not Satisfiable", -1, -1, total, null, null);
      }
      if (end >= 0 && end >= total) end = total - 1;
      long skip = start;
      int status = ranged ? 206 : 200;
      String reason = ranged ? "Partial Content" : "OK";
      return response(status, reason, ranged ? start : -1, ranged ? end : -1, total, in, skip);
    } catch (Exception e) {
      return response(404, "Not Found", -1, -1, 0, null, null);
    }
  }

  private WebResourceResponse response(int status, String reason, long start, long end,
                                       long total, InputStream body, long skip) {
    Map<String, String> h = new HashMap<>();
    h.put("Accept-Ranges", "bytes");
    h.put("Access-Control-Allow-Origin", "*");
    if (body != null && skip > 0) body = new SkipInputStream(body, skip);
    if (start >= 0 && end >= 0) {
      h.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
      h.put("Content-Length", String.valueOf(end - start + 1));
    } else if (total > 0) {
      h.put("Content-Length", String.valueOf(total));
    }
    if (body == null) body = new java.io.ByteArrayInputStream(new byte[0]);
    return new WebResourceResponse("audio/mpeg", null, status, reason, h, body);
  }

  // Skips exactly n bytes on first read so we never block the WebView thread with skip().
  private static class SkipInputStream extends InputStream {
    private final InputStream src;
    private long toSkip;
    private final byte[] one = new byte[1];
    SkipInputStream(InputStream src, long toSkip) { this.src = src; this.toSkip = toSkip; }
    @Override public int read() throws java.io.IOException {
      if (toSkip > 0) { while (toSkip > 0) { long s = src.skip(toSkip); if (s <= 0) { src.read(one); toSkip--; } else toSkip -= s; } }
      return src.read();
    }
    @Override public int read(byte[] buf, int off, int len) throws java.io.IOException {
      if (toSkip > 0) { while (toSkip > 0) { long s = src.skip(toSkip); if (s <= 0) { if (src.read(one) < 0) break; toSkip--; } else toSkip -= s; } }
      return src.read(buf, off, len);
    }
    @Override public void close() throws java.io.IOException { src.close(); }
  }

  @PluginMethod
  public void getTracks(PluginCall call) {
    PermissionState st = getPermissionState(alias());
    if (st == PermissionState.GRANTED) { load(call); return; }
    if (st == null) { call.reject("permanently_denied", "PERMISSION_BLOCKED"); return; }
    if (st == PermissionState.DENIED && blocked()) {
      call.reject("permanently_denied", "PERMISSION_BLOCKED");
      return;
    }
    requestPermissionForAlias(alias(), call, "afterPermission");
  }

  @PermissionCallback
  private void afterPermission(PluginCall call) {
    if (getPermissionState(alias()) == PermissionState.GRANTED) load(call);
    else if (blocked()) call.reject("permanently_denied", "PERMISSION_BLOCKED");
    else call.reject("permission_denied", "PERMISSION_DENIED");
  }

  @PluginMethod
  public void openSettings(PluginCall call) {
    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", getContext().getPackageName(), null));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(i);
    call.resolve();
  }

  private void load(PluginCall call) {
    JSArray out = new JSArray();
    String[] cols = {
      MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.MIME_TYPE
    };
    // MP3 only. Remove the MIME clause to include every music format.
    String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.MIME_TYPE + "='audio/mpeg'";
    try (Cursor q = getContext().getContentResolver().query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, sel, null,
        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
      if (q != null) {
        while (q.moveToNext()) {
          JSObject o = new JSObject();
          o.put("id", q.getLong(0));
          o.put("title", q.isNull(1) || q.getString(1) == null ? "<unknown>" : q.getString(1));
          o.put("artist", q.isNull(2) ? "<unknown>" : q.getString(2));
          o.put("album", q.isNull(3) ? "<unknown>" : q.getString(3));
          o.put("albumId", q.isNull(4) ? 0L : q.getLong(4));
          o.put("duration", q.isNull(5) ? 0L : q.getLong(5));
          o.put("path", q.isNull(6) ? "" : q.getString(6));
          out.put(o);
        }
      }
    } catch (Exception e) {
      call.reject("MediaStore query failed: " + e.getMessage());
      return;
    }
    JSObject res = new JSObject();
    res.put("tracks", out);
    call.resolve(res);
  }

  @PluginMethod
  public void getArt(PluginCall call) {
    String path = call.getString("path");
    String albumId = call.getString("albumId", "0");
    File f = new File(getContext().getCacheDir(), "art_" + albumId + ".jpg");
    JSObject res = new JSObject();
    if (!f.exists() && path != null && !path.isEmpty()) {
      MediaMetadataRetriever m = new MediaMetadataRetriever();
      try {
        m.setDataSource(path);
        byte[] b = m.getEmbeddedPicture();
        if (b != null) try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(b); }
      } catch (Exception ignored) {
      } finally {
        try { m.release(); } catch (Exception ignored) {}
      }
    }
    res.put("path", f.exists() ? f.getAbsolutePath() : "");
    call.resolve(res);
  }

  @Override
  protected void handleOnDestroy() {
    // Clear the artwork cache so it does not grow without bound.
    File[] files = getContext().getCacheDir().listFiles();
    if (files != null) for (File f : files) if (f.getName().startsWith("art_")) f.delete();
  }
}
