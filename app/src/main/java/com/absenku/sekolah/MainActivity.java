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
import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

                        android.util.Log.d(
                                "ABSENKU_FCM",
                                "FCM TOKEN BERHASIL | panjang=" + token.length() + " | webView=" + (webView != null)
                        );
                    } else {
                        android.util.Log.e(
                                "ABSENKU_FCM",
                                "GAGAL mendapatkan FCM token",
                                task.getException()
                        );
                    }
                });
    }

    private WebView webView;
    private FrameLayout mainLayout;
    private static final int CAMERA_REQ = 1001;
    private static final int FILE_CHOOSER_REQ = 1002;
    private ValueCallback<android.net.Uri[]> filePathCallback;
    private String notificationRoute = "";

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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null && intent.hasExtra("notification_route")) {
            String route = intent.getStringExtra("notification_route");

            if (route != null && !route.isEmpty() && webView != null) {
                final String safeRoute = route.replace("\\", "\\\\").replace("'", "\\'");

                webView.postDelayed(() -> {
                    try {
                        webView.evaluateJavascript(
                                "window.location.href='" + safeRoute + "';",
                                null
                        );
                    } catch (Exception e) {
                        android.util.Log.e(
                                "ABSENKU_FCM",
                                "Gagal membuka route dari notifikasi",
                                e
                        );
                    }
                }, 500);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() != null && getIntent().hasExtra("notification_route")) {
            notificationRoute = getIntent().getStringExtra("notification_route");
            if (notificationRoute == null) {
                notificationRoute = "";
            }
        }


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

        mainLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottomInset = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, 0, 0, bottomInset);
            return insets;
        });

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(-1, -1);
        mainLayout.addView(webView, webParams);
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

                ambilFCMToken();

                if (!notificationRoute.isEmpty()) {
                    final String route = notificationRoute;
                    notificationRoute = "";
                    view.postDelayed(() -> {
                        try {
                            String safeRoute = route.replace("\\", "\\\\").replace("'", "\\'");
                            view.evaluateJavascript(
                                    "window.location.href='" + safeRoute + "';",
                                    null
                            );
                        } catch (Exception e) {
                            android.util.Log.e("ABSENKU_FCM", "Gagal membuka route notifikasi", e);
                        }
                    }, 1200);
                }
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
        public void showSuccessPopup(String nama, String kelas, String jam, String status) {
            runOnUiThread(() -> {
                try {
                    FrameLayout overlay = new FrameLayout(MainActivity.this);
                    overlay.setBackgroundColor(Color.argb(150, 15, 23, 42));

                    LinearLayout box = new LinearLayout(MainActivity.this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setGravity(Gravity.CENTER);
                    box.setPadding(45, 35, 45, 35);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.WHITE);
                    bg.setCornerRadius(55);
                    box.setBackground(bg);
                    box.setElevation(30);

                    TextView icon = new TextView(MainActivity.this);
                    icon.setText("✓");
                    icon.setTextColor(Color.WHITE);
                    icon.setTextSize(42);
                    icon.setGravity(Gravity.CENTER);

                    GradientDrawable circle = new GradientDrawable();
                    circle.setColor(Color.rgb(34, 197, 94));
                    circle.setShape(GradientDrawable.OVAL);
                    icon.setBackground(circle);

                    LinearLayout.LayoutParams iconParams =
                            new LinearLayout.LayoutParams(105, 105);
                    iconParams.gravity = Gravity.CENTER;
                    box.addView(icon, iconParams);

                    TextView title = new TextView(MainActivity.this);
                    title.setText("ABSEN BERHASIL");
                    title.setTextColor(Color.rgb(22, 163, 74));
                    title.setTextSize(24);
                    title.setGravity(Gravity.CENTER);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);

                    LinearLayout.LayoutParams titleParams =
                            new LinearLayout.LayoutParams(-1, -2);
                    titleParams.topMargin = 18;
                    box.addView(title, titleParams);

                    TextView nameView = new TextView(MainActivity.this);
                    nameView.setText(nama == null ? "" : nama);
                    nameView.setTextColor(Color.rgb(51, 65, 85));
                    nameView.setTextSize(18);
                    nameView.setGravity(Gravity.CENTER);
                    nameView.setTypeface(null, android.graphics.Typeface.BOLD);
                    box.addView(nameView);

                    TextView classView = new TextView(MainActivity.this);
                    classView.setText("Kelas: " + (kelas == null ? "" : kelas));
                    classView.setTextColor(Color.rgb(71, 85, 105));
                    classView.setTextSize(15);
                    classView.setGravity(Gravity.CENTER);
                    box.addView(classView);

                    TextView timeView = new TextView(MainActivity.this);
                    timeView.setText("Jam: " + (jam == null ? "" : jam));
                    timeView.setTextColor(Color.rgb(100, 116, 139));
                    timeView.setTextSize(15);
                    timeView.setGravity(Gravity.CENTER);
                    box.addView(timeView);

                    TextView statusView = new TextView(MainActivity.this);
                    statusView.setText((status == null ? "" : status).toUpperCase());
                    statusView.setTextColor(Color.rgb(37, 99, 235));
                    statusView.setTextSize(16);
                    statusView.setGravity(Gravity.CENTER);
                    statusView.setTypeface(null, android.graphics.Typeface.BOLD);

                    LinearLayout.LayoutParams statusParams =
                            new LinearLayout.LayoutParams(-1, -2);
                    statusParams.topMargin = 8;
                    box.addView(statusView, statusParams);

                    FrameLayout.LayoutParams boxParams =
                            new FrameLayout.LayoutParams(
                                    (int)(getResources().getDisplayMetrics().widthPixels * 0.82f),
                                    -2,
                                    Gravity.CENTER
                            );

                    overlay.addView(box, boxParams);
                    mainLayout.addView(overlay,
                            new FrameLayout.LayoutParams(-1, -1));

                    box.setScaleX(0.45f);
                    box.setScaleY(0.45f);
                    box.setAlpha(0f);

                    box.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(450)
                            .start();

                    box.postDelayed(() -> {
                        box.animate()
                                .scaleX(0.45f)
                                .scaleY(0.45f)
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction(() -> mainLayout.removeView(overlay))
                                .start();
                    }, 2300);

                } catch (Exception e) {
                    android.util.Log.e("ABSENKU_POPUP",
                            "Gagal menampilkan popup sukses", e);
                }
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
                } catch (Exception e) {
                    android.util.Log.e("ABSENKU_SOUND", "Gagal memutar suara sukses", e);
                }

                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createOneShot(
                                    300, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(300);
                        }
                    } else {
                        android.util.Log.e("ABSENKU_VIBRATE", "Vibrator tidak tersedia");
                    }
                } catch (Exception e) {
                    android.util.Log.e("ABSENKU_VIBRATE", "Gagal melakukan getar", e);
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
