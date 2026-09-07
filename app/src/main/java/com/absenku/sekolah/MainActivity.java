package com.absenku.sekolah;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int CAMERA_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidPrint");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
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

    private class AndroidBridge {
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
