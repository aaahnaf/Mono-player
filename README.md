# Mono Player

A monochrome, audio-reactive MP3 player for Android. The interface is plain HTML and a three.js scene, wrapped as a native app with Capacitor. A small native plugin reads every MP3 on the phone with title, artist, album and embedded cover art.

```
src/index.html                    the whole app (UI, player, three.js scene)
native/MusicLibraryPlugin.java    Android plugin: MediaStore scan + cover extraction
scripts/build-www.mjs             copies the app and three.js into www/
scripts/setup-android.sh          creates the android/ project and installs the plugin
.github/workflows/build-apk.yml   builds the APK on GitHub
capacitor.config.json             app id and name
```

## How it works

- `MusicLibrary.getTracks()` asks for audio permission (`READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` below), then queries `MediaStore` for MP3s and returns title, artist, album, album id, duration and file path.
- `MusicLibrary.getArt()` pulls the embedded cover from the file with `MediaMetadataRetriever`, caches it as a JPEG and returns its path. Covers load lazily as rows scroll into view.
- Playback uses a normal HTML audio element. Files are served to the WebView with `Capacitor.convertFileSrc(path)`. A Web Audio analyser feeds bass, mid and high levels into the three.js shader.
- Three.js is bundled locally (pinned to 0.160.0), so the app works fully offline.

## Try it in a browser first

```bash
npm install
npm run serve
```

Open http://localhost:8080 and pick some MP3 files. Titles come from file names here. Tags and covers only work on the phone.

## Build the APK on GitHub (no local Android setup)

1. Create a new GitHub repository and push this folder to the `main` branch.
2. Open the repo, go to **Actions**, and wait for **Build APK** to finish. You can also start it with **Run workflow**.
3. Open the finished run and download the **player-apk** artifact. Unzip it to get `app-debug.apk`.
4. Copy the APK to your phone, open it, and allow "Install unknown apps" when asked.
5. On first launch, allow access to music and audio.

## Build locally

Requirements: Node 18+, JDK 17, Android Studio (or just the Android SDK with `ANDROID_HOME` set).

```bash
npm install
npm run android          # builds www, creates android/, installs plugin, syncs
cd android && ./gradlew assembleDebug
```

The APK appears at `android/app/build/outputs/apk/debug/app-debug.apk`. To use Android Studio instead, run `npx cap open android` and press Run.

After any change to `src/index.html`, run `npm run www && npx cap sync android`.

## Customise

- **App name and id:** edit `capacitor.config.json`. If you change `appId`, also change the `package` line and folder path in `native/MusicLibraryPlugin.java` and `scripts/setup-android.sh`.
- **Include other formats (FLAC, M4A, WAV):** remove the `MIME_TYPE='audio/mpeg'` clause in `MusicLibraryPlugin.java`.
- **Icon and splash:** place `icon.png` (1024×1024) and `splash.png` in an `assets/` folder, then run `npx @capacitor/assets generate --android` before the Gradle build.
- **Look of the scene:** the shader and uniforms are near the bottom of `src/index.html`.

## Signed release APK

Debug APKs install fine. For a signed release build:

1. Create a keystore: `keytool -genkey -v -keystore release.jks -alias player -keyalg RSA -keysize 2048 -validity 10000`
2. Store it as a base64 GitHub secret (`base64 -w0 release.jks`), plus its passwords.
3. In the workflow, decode it to a file, run `./gradlew assembleRelease`, and sign with `apksigner` (or add a `signingConfigs` block in `android/app/build.gradle`).

## Notes and limits

- Music keeps playing while the app is open. Playback while the screen is off or the app is in the background depends on the Android WebView and may pause. For reliable background playback, add a foreground-service or background-mode Capacitor plugin and a media notification.
- The scan uses the file path from MediaStore. This works with the audio permission on current Android versions. If a device returns no tracks, check that the MP3s are indexed (restart the phone or run a media scan).
- Files without embedded artwork show no cover, and the cover area fades out.

## Troubleshooting

- **Permission denied loop:** tap the title to retry, or enable Music and audio under Settings > Apps > Player > Permissions.
- **Gradle fails with Java errors:** make sure JDK 17 is the active Java.
- **Blank screen:** run `npm run www` before `npx cap sync android`. `www/` must contain `index.html` and `three.module.js`.
