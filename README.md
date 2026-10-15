# Nudge Android APK wrapper

This is a small native Android app that opens your existing Nudge dashboard inside an Android WebView. It does **not** run Node.js, Puppeteer, WhatsApp Web automation, or the scheduler on the phone. Those remain on your separately hosted Nudge backend, which must stay online.

## Important: no Tailscale required, but a public HTTPS backend is required

Because you don't want Tailscale, host the existing Nudge backend on a server you control that has a stable public HTTPS URL, such as `https://nudge.example.com`. Use a VPS with persistent disk and working Chromium/Puppeteer support. The server must store `data/wa-session`, `data/db.json`, and `data/uploads` on persistent storage. Keep the project server bound to `127.0.0.1` and expose it through a properly configured HTTPS reverse proxy (for example, Nginx with a valid TLS certificate). Do not directly expose port 3000 to the internet.

The existing server requires `APP_PASSWORD` and should run behind your own TLS reverse proxy with `REQUIRE_AUTH=1` and `TRUST_PROXY=1`. Set `TRUST_PROXY=1` only if the immediate proxy is yours and sets `X-Forwarded-Proto` correctly. Use a long unique password. Disable any public directory listing; do not publish the `data` directory or session files. Keep a backup of your current data before migrating. Do not copy a Windows Chrome/Puppeteer session directory to Linux unless your deployment procedure supports it; you may need to link WhatsApp once on the new server.

## Configure and install

1. Build the APK below.
2. Install `app-debug.apk` on your Android phone.
3. Open Nudge and enter your backend HTTPS URL, e.g. `https://nudge.example.com`.
4. Tap **Connect securely** and sign in using your Nudge server password.
5. The app remembers the URL. Use the **Server** button to change it later.

The app refuses plain HTTP to avoid sending the password/session over an unencrypted connection. Self-signed or invalid HTTPS certificates are rejected.

## Build the debug APK locally

Requirements: Android Studio/Android SDK, Java 21, Android platform 35, Build Tools 35.0.0, Gradle 8.9.

From this directory run:

```bash
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

You can also push the **contents of this `nudge-android` folder as the repository root** to a GitHub repository (make sure the hidden `.github/workflows/build-apk.yml` file is included). The workflow starts automatically on a push to `main`/`master`, or you can run it via **Actions → Build Nudge APK → Run workflow**. When it completes, open the workflow run, download the `nudge-debug-apk` artifact, and extract `app-debug.apk`. This hosted build route is useful if Android SDK tools aren't installed on your PC.

## Security and operation notes

- This APK is a dashboard/client only. It does not need WhatsApp permissions and does not hold your WhatsApp session.
- The WhatsApp session, schedules, send history, and uploaded media stay on the backend server.
- The server must stay powered on and connected for scheduled sends. Closing the Android app does not stop jobs.
- Protect access with HTTPS and the Nudge login. Never embed the backend password in this APK, repository, or source code.
- This uses the project's existing unofficial `whatsapp-web.js` automation. It may disconnect and may be restricted by WhatsApp; use only for messages people expect.
- This debug APK is not Play Store-signed. For personal sideloading, Android may prompt you to allow installation from your chosen file manager/browser. For public distribution, create and secure a release signing key and review Play policies.
