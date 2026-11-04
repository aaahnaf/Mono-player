package com.example.player;

import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.provider.MediaStore;
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

@CapacitorPlugin(name = "MusicLibrary", permissions = {
    @Permission(alias = "audio13", strings = {"android.permission.READ_MEDIA_AUDIO"}),
    @Permission(alias = "audioLegacy", strings = {"android.permission.READ_EXTERNAL_STORAGE"})
})
public class MusicLibraryPlugin extends Plugin {

  private String alias() { return Build.VERSION.SDK_INT >= 33 ? "audio13" : "audioLegacy"; }

  @PluginMethod
  public void getTracks(PluginCall call) {
    if (getPermissionState(alias()) != PermissionState.GRANTED) {
      requestPermissionForAlias(alias(), call, "afterPermission");
      return;
    }
    load(call);
  }

  @PermissionCallback
  private void afterPermission(PluginCall call) {
    if (getPermissionState(alias()) == PermissionState.GRANTED) load(call);
    else call.reject("Permission denied");
  }

  private void load(PluginCall call) {
    JSArray out = new JSArray();
    String[] cols = {
      MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
    };
    // MP3 only. Remove the MIME clause to include every music format.
    String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.MIME_TYPE + "='audio/mpeg'";
    try (Cursor q = getContext().getContentResolver().query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, sel, null,
        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
      while (q != null && q.moveToNext()) {
        JSObject o = new JSObject();
        o.put("id", q.getLong(0));
        o.put("title", q.getString(1));
        o.put("artist", q.getString(2));
        o.put("album", q.getString(3));
        o.put("albumId", q.getLong(4));
        o.put("duration", q.getLong(5));
        o.put("path", q.getString(6));
        out.put(o);
      }
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
    if (!f.exists() && path != null) {
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
}
