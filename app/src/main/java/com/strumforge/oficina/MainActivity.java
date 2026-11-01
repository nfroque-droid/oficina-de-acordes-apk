package com.strumforge.oficina;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * A Oficina de Acordes num WebView. A página vem dos assets (app/src/main/assets/www) através de
 * https://appassets.androidplatform.net, para que o armazenamento local (favoritos, definições,
 * registo de prática) fique sempre na mesma origem e sobreviva às atualizações da APK.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/assets/www/";
    private static final String START = BASE + "index.html";
    private static final int REQ_FILE = 41;
    private static final String FOLDER = "Oficina de Acordes";

    private WebView web;
    private WebViewAssetLoader assets;
    private ValueCallback<Uri[]> fileCallback;
    private WebView printWeb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        assets = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        // Ecrã inteiro de ponta a ponta; o espaço das barras do sistema e do teclado é dado como margem.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0E1220"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0E1220"));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), root);
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "OficinaAndroid");
        web.setWebViewClient(new AppClient(false));
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Escolher ficheiro"), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(START);
        }
    }

    /** Serve os assets e manda para o browser do telemóvel qualquer ligação para fora da app. */
    private class AppClient extends WebViewClient {
        private final boolean forPrint;
        private boolean printed;

        AppClient(boolean forPrint) { this.forPrint = forPrint; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assets.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost())) return false;
            openExternal(u);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!forPrint || printed) return;
            printed = true;
            String job = view.getTitle() != null && !view.getTitle().isEmpty() ? view.getTitle() : FOLDER;
            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            if (pm != null) pm.print(job, view.createPrintDocumentAdapter(job), new PrintAttributes.Builder().build());
        }
    }

    /** Chamado por android-bridge.js (window.OficinaAndroid). */
    private class Bridge {
        @JavascriptInterface
        public String saveFile(String name, String mime, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "fail";
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) return "fail";
                        out.write(bytes);
                    }
                    toast("Guardado em Transferências/" + FOLDER + ": " + safe);
                } else {
                    File dir = new File(getCacheDir(), "partilha");
                    if (!dir.exists() && !dir.mkdirs()) return "fail";
                    File f = new File(dir, safe);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                    uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                }
                share(uri, mime, safe);
                return "ok";
            } catch (Exception e) {
                return "fail";
            }
        }

        @JavascriptInterface
        public boolean printHtml(String html, String title) {
            runOnUiThread(() -> {
                WebView pw = new WebView(MainActivity.this);
                pw.getSettings().setJavaScriptEnabled(false);
                pw.setWebViewClient(new AppClient(true));
                printWeb = pw; // manter a referência até ao fim da impressão
                pw.loadDataWithBaseURL(BASE, html, "text/html", "UTF-8", null);
            });
            return true;
        }
    }

    /** Depois de guardar, oferece abrir ou partilhar (por exemplo, para o OneDrive ou o Drive). */
    private void share(Uri uri, String mime, String name) {
        runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TITLE, name);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, "Partilhar ou guardar " + name);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{view});
            try { startActivity(chooser); } catch (ActivityNotFoundException ignored) { }
        });
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            toast("Não há nenhuma app para abrir esta ligação.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
