package in.sursandconnect.app;
import java.io.FileNotFoundException;
import android.webkit.MimeTypeMap;
import android.provider.OpenableColumns;
import android.os.ParcelFileDescriptor;
import android.database.MatrixCursor;
import android.database.Cursor;
import android.content.ContentValues;
import android.content.ContentProvider;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://sursandconnect.github.io/sursand-connect-app/";
    private static final String INTERNAL_HOST = "sursandconnect.github.io";
    private static final int REQ_LOCATION = 2001;
    private static final int REQ_CAMERA = 2002;
    private static final int REQ_NOTIFICATION = 2003;
    private static final int REQ_FILE = 2004;

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applySystemBarInsets();

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        createNotificationChannel();
        requestNotificationPermission();
        configureWebView();
        scheduleNativeUpdateChecks();

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(APP_URL);
    }

    private void applySystemBarInsets() {
        final View root = findViewById(R.id.rootView);
        if (root == null) return;

        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int top = 0;
            int bottom = 0;
            int left = 0;
            int right = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.statusBars() |
                    WindowInsets.Type.navigationBars()
                );
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = windowInsets.getSystemWindowInsetTop();
                bottom = windowInsets.getSystemWindowInsetBottom();
                left = windowInsets.getSystemWindowInsetLeft();
                right = windowInsets.getSystemWindowInsetRight();
            }

            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });

        root.requestApplyInsets();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " SursandConnectAndroid/1.0");

        webView.addJavascriptInterface(new NativeBridge(), "SursandNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                injectNativeHelpers();
                // Encourage the existing web service worker to refresh without exposing hosting details.
                view.evaluateJavascript("if(navigator.serviceWorker){navigator.serviceWorker.getRegistrations().then(r=>r.forEach(x=>x.update())).catch(()=>{});}", null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(new String[]{res});
                            else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                            return;
                        }
                    }
                    request.deny();
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                launchFileChooser(params);
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> startDownload(url, userAgent, mimetype));
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        if ((scheme.equals("http") || scheme.equals("https")) && host.equals(INTERNAL_HOST)) return false;

        // Never expose GitHub / repository destinations to normal users.
        if (host.equals("github.com") || host.endsWith(".github.com")) {
            Toast.makeText(this, "This link is not available in the app.", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (scheme.equals("tel") || scheme.equals("sms") || scheme.equals("mailto") || scheme.equals("geo") || scheme.equals("market") || scheme.equals("intent")) {
            openExternal(uri);
            return true;
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            openExternal(uri);
            return true;
        }
        return false;
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No compatible app is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        Intent content = params.createIntent();
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType(params.getAcceptTypes() != null && params.getAcceptTypes().length > 0 && !params.getAcceptTypes()[0].isEmpty() ? params.getAcceptTypes()[0] : "image/*");

        Intent camera = null;
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            try {
                camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                String name = "SursandConnect_" + System.currentTimeMillis() + ".jpg";
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sursand Connect");
                cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (cameraUri != null) camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            } catch (Exception ignored) { camera = null; }
        }

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INTENT, content);
        if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
        try { startActivityForResult(chooser, REQ_FILE); }
        catch (Exception e) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
            Toast.makeText(this, "Unable to open image picker.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
                else if (cameraUri != null) result = new Uri[]{cameraUri};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            cameraUri = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    private void startDownload(String url, String userAgent, String mime) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mime);
            req.addRequestHeader("User-Agent", userAgent);
            req.setTitle("Sursand Connect download");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to start download.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:#f8fafc;font-family:Arial;color:#263238;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px'><div style='max-width:420px;text-align:center;background:#fff;border-radius:24px;padding:28px;box-shadow:0 18px 45px rgba(15,23,42,.12)'><div style='width:76px;height:76px;border-radius:24px;background:#f28c28;color:#fff;margin:auto;display:flex;align-items:center;justify-content:center;font-size:27px;font-weight:900'>SC</div><h2>Sursand Connect</h2><p style='color:#667085;line-height:1.6'>You appear to be offline. Previously cached pages remain available when possible.</p><button onclick=\"location.href='" + APP_URL + "'\" style='border:0;border-radius:12px;background:#238b45;color:white;padding:12px 22px;font-weight:800'>Try Again</button></div></body></html>";
        webView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private void injectNativeHelpers() {
        String js =
            "(function(){" +
            "try{" +

            // Native share fallback.
            "if(!navigator.share&&window.SursandNative){" +
              "navigator.share=function(d){" +
                "SursandNative.share((d&&d.title)||'Sursand Connect',(d&&d.text)||'',(d&&d.url)||location.href);" +
                "return Promise.resolve();" +
              "};" +
            "}" +

            // Remove privacy/development explanatory text from the account screen.
            "function cleanAccountPrivacy(){" +
              "try{" +
                "if(!/account\\.html(?:$|[?#])/i.test(location.href))return;" +
                "var phrases=[" +
                  "'account privacy'," +
                  "'privacy description'," +
                  "'your personal information'," +
                  "'personal information is'," +
                  "'we respect your privacy'," +
                  "'your privacy'," +
                  "'data privacy'," +
                  "'privacy and security'" +
                "];" +
                "document.querySelectorAll('p,small,.note,.info,.privacy,.privacy-note,.account-note').forEach(function(el){" +
                  "var t=(el.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                  "if(!t||t.length>650)return;" +
                  "if(phrases.some(function(p){return t.indexOf(p)!==-1;}))el.remove();" +
                "});" +
              "}catch(e){}" +
            "}" +
            // APK lower navigation only: Shop -> Emergency, Services -> City Connect. +
            "function fixBottomNavigation(){" +
              "try{" +
                "var links=document.querySelectorAll('nav a,.bottom a,.bottom-nav a,.sc-bottom a,.sc-bottom-nav a');" +
                "Array.prototype.forEach.call(links,function(a){" +
                  "var text=(a.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                  "var href=(a.getAttribute('href')||'');" +
                  "var low=href.toLowerCase();" +
                  "if(text.indexOf('shop')!==-1||low.indexOf('businesses.html')!==-1){" +
                    "a.setAttribute('href',low.indexOf('p/')!==-1?'p/emergency.html':'emergency.html');" +
                    "a.innerHTML='🚨<span>Emergency</span>';" +
                  "}" +
                  "if(text.indexOf('services')!==-1||low.indexOf('services.html')!==-1){" +
                    "a.setAttribute('href',low.indexOf('p/')!==-1?'p/city-connect.html':'city-connect.html');" +
                    "a.innerHTML='<span style=\\\"width:22px;height:22px;border-radius:50%;background:#25D366;color:#fff;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:900;line-height:1;margin-bottom:1px\\\">☎</span><span>City Connect</span>';" +
                  "}" +
                "});" +
              "}catch(e){}" +
            "}" +
            "cleanAccountPrivacy();" +
            "fixBottomNavigation();" +
            "if(document.body){" +
              "new MutationObserver(function(){cleanAccountPrivacy();fixBottomNavigation();}).observe(document.body,{childList:true,subtree:true});" +
            "}" +

            "}catch(e){}" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    public class NativeBridge {
        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                try {
                    File sourceApk = new File(getApplicationInfo().sourceDir);
                    File shareDir = new File(getCacheDir(), "shared_apk");
                    if (!shareDir.exists() && !shareDir.mkdirs()) {
                        throw new Exception("Unable to create share folder");
                    }

                    File shareApk = new File(shareDir, "Sursand-Connect.apk");

                    try (FileInputStream input = new FileInputStream(sourceApk);
                         FileOutputStream output = new FileOutputStream(shareApk, false)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = input.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }
                        output.flush();
                    }

                    Uri apkUri = Uri.parse(
                        "content://" + getPackageName() + ".apkshare/Sursand-Connect.apk"
                    );

                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("application/vnd.android.package-archive");
                    sendIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT,
                        title == null || title.trim().isEmpty() ? "Sursand Connect" : title);
                    sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    Intent chooser = Intent.createChooser(sendIntent, "Share Sursand Connect APK");
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);

                } catch (Exception e) {
                    Toast.makeText(
                        MainActivity.this,
                        "Unable to share APK file.",
                        Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public String getAppVersion() { return "1.0.0"; }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("sursand_updates", "Sursand Connect Updates", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Events and announcements from Sursand Connect");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    private void scheduleNativeUpdateChecks() {
        Intent intent = new Intent(this, UpdateCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 4102, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
        long every = 60L * 60L * 1000L; // hourly, battery-friendly
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, every, pi);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
}
