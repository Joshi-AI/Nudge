package com.nudge.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Native shell for the remotely hosted Nudge dashboard.
 * WhatsApp Web automation and the scheduler remain on the server; this app is only a client.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "nudge_android";
    private static final String KEY_SERVER_URL = "server_url";
    private static final int MENU_CHANGE_SERVER = 1001;
    private static final int FILE_CHOOSER_REQUEST = 2120;

    private LinearLayout root;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> filePathCallback;
    private String serverUrl = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 22, 29));
        getWindow().setNavigationBarColor(Color.rgb(15, 22, 29));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        buildRoot();
        if (validServerUrl(serverUrl)) showWebView(); else showServerSetup("");
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 22, 29));
        setContentView(root);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void showServerSetup(String error) {
        root.removeAllViews();
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        TextView title = text("Nudge", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text("Connect to your Nudge server", 18, Color.rgb(207, 218, 228));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(16);
        root.addView(subtitle, subParams);

        TextView explanation = text(
            "Enter the HTTPS address where your Nudge backend is hosted. The app is a remote control panel; the server must stay online to send scheduled messages.",
            14, Color.rgb(154, 171, 188));
        LinearLayout.LayoutParams expParams = new LinearLayout.LayoutParams(-1, -2);
        expParams.topMargin = dp(8);
        root.addView(explanation, expParams);

        EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.rgb(145, 159, 173));
        urlInput.setHint("https://nudge.example.com");
        urlInput.setText(serverUrl);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSelectAllOnFocus(true);
        urlInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        urlInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(58, 183, 149)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(24);
        root.addView(urlInput, inputParams);

        TextView err = text(error, 13, Color.rgb(255, 138, 128));
        LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(-1, -2);
        errParams.topMargin = dp(8);
        root.addView(err, errParams);

        Button connect = new Button(this);
        connect.setText("Connect securely");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(18);
        root.addView(connect, buttonParams);
        connect.setOnClickListener(v -> {
            String candidate = normalizeServerUrl(urlInput.getText().toString());
            if (!validServerUrl(candidate)) {
                err.setText("Use a valid HTTPS URL, for example https://nudge.example.com. HTTP is disabled for security.");
                return;
            }
            serverUrl = candidate;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SERVER_URL, serverUrl).apply();
            showWebView();
        });

        TextView note = text("Security: use a private server with a strong Nudge password. Do not share your WhatsApp session folder.",
                12, Color.rgb(145, 159, 173));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(24);
        root.addView(note, noteParams);
    }

    private String normalizeServerUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private boolean validServerUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showWebView() {
        root.removeAllViews();
        root.setPadding(0, 0, 0, 0);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(4), dp(8), dp(4));
        toolbar.setBackgroundColor(Color.rgb(15, 22, 29));
        TextView title = text("Nudge", 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button change = new Button(this);
        change.setText("Server");
        change.setTextSize(12);
        toolbar.addView(change, new LinearLayout.LayoutParams(-2, dp(44)));
        change.setOnClickListener(v -> confirmServerChange());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(15, 22, 29));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Android file picker provides attachment URIs.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available on this device.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (isSameOrigin(target, Uri.parse(serverUrl))) return false;
                if ("https".equalsIgnoreCase(target.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
                    catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return true;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // Never bypass invalid TLS certificates.
                Toast.makeText(MainActivity.this, "Secure connection failed. Check the server's HTTPS certificate.", Toast.LENGTH_LONG).show();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) Toast.makeText(MainActivity.this, "Cannot reach Nudge server. Check internet and server status.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        webView.loadUrl(serverUrl);
    }

    private boolean isSameOrigin(Uri a, Uri b) {
        if (a == null || b == null) return false;
        return "https".equalsIgnoreCase(a.getScheme())
                && "https".equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private int effectivePort(Uri uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private void confirmServerChange() {
        new AlertDialog.Builder(this)
                .setTitle("Change Nudge server?")
                .setMessage("This changes the server this app connects to. Your WhatsApp session and schedules stay on the existing server.")
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Change", (d, w) -> {
                    if (webView != null) {
                        webView.stopLoading();
                        webView.destroy();
                        webView = null;
                    }
                    serverUrl = "";
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_SERVER_URL).apply();
                    showServerSetup("");
                }).show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_CHANGE_SERVER, 0, "Change server");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_CHANGE_SERVER) { confirmServerChange(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
