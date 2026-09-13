package com.absenku.sekolah;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import android.net.Uri;
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
import android.webkit.URLUtil;
import android.app.DownloadManager;
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
    private static final int CAMERA_CHAT_PERMISSION_REQ = 1005;
    private static final int CAMERA_CAPTURE_REQ = 1004;
    private ValueCallback<android.net.Uri[]> filePathCallback;
    private Uri cameraOutputUri;
    private String notificationRoute = "";

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        // =====================================================
        // IZIN KAMERA SAAT STARTUP APLIKASI
        // =====================================================
        // CAMERA_REQ hanya untuk permintaan izin kamera awal.
        // Jangan langsung membuka kamera chat di sini.
        if (requestCode == CAMERA_REQ) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Setelah izin kamera diberikan, lanjutkan meminta
                // izin notifikasi Android 13+ jika diperlukan.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            1003
                    );
                }

            } else {
                android.util.Log.w(
                        "ABSENKU_PERMISSION",
                        "Izin kamera startup ditolak"
                );
            }

            return;
        }

        // =====================================================
        // IZIN KAMERA
        // =====================================================
        if (requestCode == CAMERA_CHAT_PERMISSION_REQ) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Setelah izin kamera chat diberikan, buka kamera.
                bukaKameraUntukChat();

            } else {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }

                Toast.makeText(
                        this,
                        "Izin kamera diperlukan untuk mengambil foto.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return;
        }

        // =====================================================
        // IZIN NOTIFIKASI ANDROID 13+
        // =====================================================
        if (requestCode == 1003 &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                android.util.Log.d(
                        "ABSENKU_FCM",
                        "Izin notifikasi diberikan"
                );
            }

            return;
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

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(0);

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
            int topInset;
            int bottomInset;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemInsets =
                        insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()
                                | android.view.WindowInsets.Type.navigationBars()
                        );
                topInset = systemInsets.top;
                bottomInset = systemInsets.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
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
        // Download lampiran chat ke folder Download Android.
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                String fileName = URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimeType
                );

                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "lampiran_" + System.currentTimeMillis();
                }

                DownloadManager.Request request =
                        new DownloadManager.Request(Uri.parse(url));

                request.setTitle(fileName);
                request.setDescription("Mengunduh lampiran Absenku...");
                request.setMimeType(
                        mimeType != null && !mimeType.isEmpty()
                                ? mimeType
                                : "application/octet-stream"
                );

                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);

                request.setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        fileName
                );

                DownloadManager downloadManager =
                        (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                if (downloadManager != null) {
                    downloadManager.enqueue(request);

                    Toast.makeText(
                            MainActivity.this,
                            "Lampiran sedang diunduh...",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "Download Manager tidak tersedia",
                            Toast.LENGTH_LONG
                    ).show();
                }

            } catch (Exception e) {
                android.util.Log.e(
                        "ABSENKU_DOWNLOAD",
                        "Gagal mengunduh lampiran",
                        e
                );

                Toast.makeText(
                        MainActivity.this,
                        "Gagal mengunduh lampiran",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

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
                    // Input dengan capture="environment" = buka kamera
                    if (fileChooserParams.isCaptureEnabled()) {
                        bukaKameraUntukChat();
                        return true;
                    }

                    // Foto/Galeri/File menggunakan intent asli WebView.
                    // Jangan dipaksa menjadi image/*.
                    Intent intent = fileChooserParams.createIntent();
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQ
                    );

                    return true;

                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Tidak dapat membuka pemilih file",
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

    private void bukaKameraUntukChat() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_CHAT_PERMISSION_REQ
                );
                return;
            }

            File folder = new File(
                    getCacheDir(),
                    "chat_camera"
            );

            if (!folder.exists() && !folder.mkdirs()) {
                throw new Exception(
                        "Folder kamera tidak dapat dibuat"
                );
            }

            String waktu = new SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
            ).format(new Date());

            File foto = new File(
                    folder,
                    "IMG_" + waktu + ".jpg"
            );

            cameraOutputUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    foto
            );

            Intent cameraIntent = new Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE
            );

            cameraIntent.putExtra(
                    android.provider.MediaStore.EXTRA_OUTPUT,
                    cameraOutputUri
            );

            cameraIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            // Berikan izin URI foto secara eksplisit kepada aplikasi kamera.
            android.content.pm.ResolveInfo cameraInfo =
                    getPackageManager().resolveActivity(
                            cameraIntent,
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                    );

            if (cameraInfo != null && cameraInfo.activityInfo != null) {
                grantUriPermission(
                        cameraInfo.activityInfo.packageName,
                        cameraOutputUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            }

            if (cameraIntent.resolveActivity(
                    getPackageManager()
            ) == null) {
                throw new Exception(
                        "Aplikasi kamera tidak ditemukan"
                );
            }

            startActivityForResult(
                    cameraIntent,
                    CAMERA_CAPTURE_REQ
            );

        } catch (Exception e) {
            cameraOutputUri = null;

            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }

            Toast.makeText(
                    this,
                    "Kamera tidak dapat dibuka: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            android.content.Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        // =====================================================
        // HASIL FOTO DARI KAMERA
        // =====================================================
        if (requestCode == CAMERA_CAPTURE_REQ) {

            if (filePathCallback == null) {
                cameraOutputUri = null;
                return;
            }

            android.net.Uri[] results = null;

            if (resultCode == RESULT_OK &&
                    cameraOutputUri != null) {

                results = new android.net.Uri[]{
                        cameraOutputUri
                };

            } else if (cameraOutputUri != null) {

                try {
                    getContentResolver().delete(
                            cameraOutputUri,
                            null,
                            null
                    );
                } catch (Exception ignored) {
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraOutputUri = null;

            return;
        }

        // =====================================================
        // HASIL GALERI / FILE PICKER
        // =====================================================
        if (requestCode == FILE_CHOOSER_REQ) {

            if (filePathCallback == null) {
                return;
            }

            android.net.Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                android.net.Uri selectedUri = null;

                android.content.ClipData clipData =
                        data.getClipData();

                if (clipData != null &&
                        clipData.getItemCount() > 0) {

                    selectedUri = clipData.getItemAt(0).getUri();

                } else if (data.getData() != null) {

                    selectedUri = data.getData();
                }

                if (selectedUri != null) {

                    try {
                        String namaFile = null;

                        android.database.Cursor cursor =
                                getContentResolver().query(
                                        selectedUri,
                                        new String[]{
                                                android.provider.OpenableColumns.DISPLAY_NAME
                                        },
                                        null,
                                        null,
                                        null
                                );

                        if (cursor != null) {
                            try {
                                if (cursor.moveToFirst()) {
                                    int index =
                                            cursor.getColumnIndex(
                                                    android.provider.OpenableColumns.DISPLAY_NAME
                                            );

                                    if (index >= 0) {
                                        namaFile =
                                                cursor.getString(index);
                                    }
                                }
                            } finally {
                                cursor.close();
                            }
                        }

                        if (namaFile == null ||
                                namaFile.trim().isEmpty()) {

                            namaFile =
                                    "lampiran_" +
                                    System.currentTimeMillis();
                        }

                        namaFile = namaFile.replaceAll(
                                "[\\\\/:*?\"<>|]",
                                "_"
                        );

                        String mimeType =
                                getContentResolver()
                                        .getType(selectedUri);

                        if (mimeType == null ||
                                mimeType.trim().isEmpty()) {

                            mimeType =
                                    "application/octet-stream";
                        }

                        /*
                         * Pastikan nama file memiliki ekstensi.
                         * Ini penting agar Flask/WebView dapat
                         * mengenali PDF, Word, Excel, dll.
                         */
                        if (!namaFile.contains(".")) {

                            String ext = "";

                            if (mimeType.equals("application/pdf")) {
                                ext = ".pdf";

                            } else if (
                                    mimeType.equals("application/msword")
                            ) {
                                ext = ".doc";

                            } else if (
                                    mimeType.equals(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                            ) {
                                ext = ".docx";

                            } else if (
                                    mimeType.equals("application/vnd.ms-excel")
                            ) {
                                ext = ".xls";

                            } else if (
                                    mimeType.equals(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                            ) {
                                ext = ".xlsx";

                            } else if (
                                    mimeType.equals("application/vnd.ms-powerpoint")
                            ) {
                                ext = ".ppt";

                            } else if (
                                    mimeType.equals(
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    )
                            ) {
                                ext = ".pptx";

                            } else if (
                                    mimeType.startsWith("image/")
                            ) {
                                ext = ".jpg";
                            }

                            if (!ext.isEmpty()) {
                                namaFile += ext;
                            }
                        }

                        File folder =
                                new File(
                                        getCacheDir(),
                                        "chat_files"
                                );

                        if (!folder.exists() &&
                                !folder.mkdirs()) {

                            throw new java.io.IOException(
                                    "Folder cache chat tidak dapat dibuat"
                            );
                        }

                        File cacheFile =
                                new File(
                                        folder,
                                        namaFile
                                );

                        try (
                                java.io.InputStream input =
                                        getContentResolver()
                                                .openInputStream(
                                                        selectedUri
                                                );

                                java.io.OutputStream output =
                                        new java.io.FileOutputStream(
                                                cacheFile
                                        )
                        ) {

                            if (input == null) {
                                throw new java.io.IOException(
                                        "Tidak dapat membaca file"
                                );
                            }

                            byte[] buffer =
                                    new byte[8192];

                            int length;

                            while (
                                    (length =
                                        input.read(buffer)) != -1
                            ) {
                                output.write(
                                        buffer,
                                        0,
                                        length
                                );
                            }
                        }

                        /*
                         * Kembalikan URI asli dari DocumentsProvider
                         * ke WebView. WebView membutuhkan content:// URI
                         * asli agar file masuk sebagai multipart/form-data.
                         *
                         * cacheFile tetap dibuat sebagai pengecekan bahwa
                         * file benar-benar dapat dibaca dari Android.
                         */
                        android.net.Uri fileUri =
                                androidx.core.content.FileProvider.getUriForFile(
                                        MainActivity.this,
                                        MainActivity.this.getPackageName() + ".fileprovider",
                                        cacheFile,
                                        namaFile
                                );

                        MainActivity.this.grantUriPermission(
                                MainActivity.this.getPackageName(),
                                fileUri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );

                        results =
                                new android.net.Uri[]{
                                        fileUri
                                };

                        android.util.Log.d(
                                "ABSENKU_FILE",
                                "URI SIAP UNTUK WEBVIEW: " + results[0]
                        );

                        android.util.Log.d(
                                "ABSENKU_FILE",
                                "FILE SIAP: " +
                                namaFile +
                                " | MIME=" +
                                mimeType +
                                " | SIZE=" +
                                cacheFile.length()
                        );

                    } catch (Exception e) {

                        android.util.Log.e(
                                "ABSENKU_FILE",
                                "GAGAL FILE: " +
                                e.getClass()
                                        .getSimpleName() +
                                " | " +
                                e.getMessage()
                        );

                        /*
                         * Jangan mengembalikan content:// URI
                         * yang bermasalah. Batalkan saja agar
                         * WebView tidak mengirim file kosong.
                         */
                        results = null;
                    }
                }
            }

            android.util.Log.d(
                    "ABSENKU_FILE",
                    "CALLBACK FILE: " +
                            (results == null
                                    ? "NULL"
                                    : results[0].toString())
            );

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
