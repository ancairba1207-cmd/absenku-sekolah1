package com.absenku.sekolah;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Toast;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.messaging.FirebaseMessaging;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

public class MainActivity extends Activity {

    private void ambilFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        getSharedPreferences("absenku_fcm", MODE_PRIVATE)
                                .edit()
                                .putString("token", token)
                                .apply();

                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript(
                                        "(function(){"
                                        + "try {"
                                        + "if (window.simpanFCMToken) { window.simpanFCMToken(); }"
                                        + "} catch(e) {}"
                                        + "})()",
                                        null
                                );
                            }
                        });

                        android.util.Log.d("ABSENKU_FCM", "TOKEN=" + token);
                    } else {
                        android.util.Log.e("ABSENKU_FCM", "Gagal mendapatkan FCM token", task.getException());
                    }
                });
    }

    private WebView webView;
    private FrameLayout mainLayout;
    private AdView adView;
    private static final int CAMERA_REQ = 1001;
    private static final int FILE_CHOOSER_REQ = 1002;
    private ValueCallback<android.net.Uri[]> filePathCallback;
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQ &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    1003
            );
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ambilFCMToken();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    1003
            );
        }

        mainLayout = new FrameLayout(this);
        webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(-1, -1);
        webParams.bottomMargin = 60;
        mainLayout.addView(webView, webParams);
        adView = new AdView(this);
        adView.setAdSize(com.google.android.gms.ads.AdSize.LARGE_BANNER);
        adView.setAdUnitId("ca-app-pub-1668241409829273/5900950475");
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(-1, -2);
        adParams.gravity = android.view.Gravity.BOTTOM;
        mainLayout.addView(adView, adParams);
        MobileAds.initialize(this, status -> {});
        adView.loadAd(new AdRequest.Builder().build());
        setContentView(mainLayout);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                view.evaluateJavascript(
                        "(function(){"
                        + "try {"
                        + "if (window.simpanFCMToken) { window.simpanFCMToken(); }"
                        + "} catch(e) {}"
                        + "})()",
                        null
                );
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidPrint");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<android.net.Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                    startActivityForResult(intent, FILE_CHOOSER_REQ);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "Tidak dapat membuka galeri",
                            Toast.LENGTH_SHORT
                    ).show();
                    return false;
                }
            }
        });

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject server = py.getModule("server");
                server.callAttr("start_server");
                runOnUiThread(() -> webView.loadUrl("http://127.0.0.1:5000/"));
            } catch (Exception e) {
                runOnUiThread(() -> webView.loadData(
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><h2>ABSENKU SEKOLAH</h2><p>Server gagal:</p><pre>"
                    + android.text.TextUtils.htmlEncode(e.toString()) + "</pre>",
                    "text/html", "UTF-8"));
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQ) {
            if (filePathCallback == null) return;

            android.net.Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                android.net.Uri uri = data.getData();

                if (uri != null) {
                    results = new android.net.Uri[]{uri};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private class AndroidBridge {
        @JavascriptInterface
        public String getFCMToken() {
            return getSharedPreferences("absenku_fcm", MODE_PRIVATE)
                    .getString("token", "");
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("ABSENKU SEKOLAH");
                    printManager.print("ABSENKU SEKOLAH", adapter, new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cetak gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void doubleScanFeedback() {
            runOnUiThread(() -> {
                try {
                    MediaPlayer mp = MediaPlayer.create(MainActivity.this, R.raw.double_scan);
                    if (mp != null) {
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();
                    }
                } catch (Exception ignored) {}

                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createWaveform(
                                    new long[]{0, 180, 100, 180}, -1));
                        } else {
                            vibrator.vibrate(new long[]{0, 180, 100, 180}, -1);
                        }
                    }
                } catch (Exception ignored) {}

                Toast.makeText(
                        MainActivity.this,
                        "⚠️ QR SUDAH DI-SCAN",
                        Toast.LENGTH_LONG
                ).show();
            });
        }

        @JavascriptInterface
        public void successFeedback() {
            runOnUiThread(() -> {
                try {
                    MediaPlayer mp = MediaPlayer.create(MainActivity.this, R.raw.scan_success);
                    if (mp != null) {
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();
                    }
                } catch (Exception ignored) {}
                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
                        else vibrator.vibrate(180);
                    }
                } catch (Exception ignored) {}
                Toast.makeText(MainActivity.this, "✅ QR berhasil dibaca", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
