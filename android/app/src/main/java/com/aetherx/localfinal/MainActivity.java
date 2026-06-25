package com.aetherx.localfinal;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.aetherx.localfinal.wallpaper.AetherXLiveWallpaperPlugin;
import com.aetherx.localfinal.wallpaper.AetherXLiveWallpaperService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AetherXLiveWP";
    private static final int REQ_PICK_VIDEO = 1001;

    private TextView txtStatus;
    private String lastPickedPath = null;
    private String lastPickedUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        Button btnPick = findViewById(R.id.btnPickVideo);
        Button btnSave = findViewById(R.id.btnSaveVideo);
        Button btnApply = findViewById(R.id.btnApplyWallpaper);
        Button btnStatus = findViewById(R.id.btnStatus);

        btnPick.setOnClickListener(v -> pickVideoFromDevice());
        btnSave.setOnClickListener(v -> saveLastVideoAsWallpaper());
        btnApply.setOnClickListener(v -> openLivePicker());
        btnStatus.setOnClickListener(v -> showStatus());

        showStatus();
    }

    private void pickVideoFromDevice() {
        Log.i(TAG, "pickVideoFromDevice opening ACTION_OPEN_DOCUMENT");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_VIDEO);
        } catch (Exception e) {
            Toast.makeText(this, "No se puede abrir el selector: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_VIDEO) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Selección cancelada", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        Log.i(TAG, "selectedVideoUri=" + uri);
        try {
            final int flags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception e) {
            Log.w(TAG, "takePersistableUriPermission failed: " + e.getMessage());
        }
        try {
            File outFile = ensureWallpaperFile("picked-" + System.currentTimeMillis() + ".mp4");
            ContentResolver resolver = getContentResolver();
            long total = 0;
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
            }
            lastPickedPath = outFile.getAbsolutePath();
            lastPickedUri = uri.toString();
            Log.i(TAG, "copied bytes=" + total + " to=" + lastPickedPath);
            Toast.makeText(this, "Vídeo copiado (" + total + " bytes). Ahora pulsa GUARDAR.", Toast.LENGTH_LONG).show();
            txtStatus.setText("Vídeo listo:\n" + lastPickedPath + "\nbytes=" + total + "\n\nPulsa GUARDAR para fijarlo como wallpaper.");
        } catch (Exception e) {
            Log.e(TAG, "pick-video copy failed", e);
            Toast.makeText(this, "Error copiando vídeo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveLastVideoAsWallpaper() {
        if (lastPickedPath == null) {
            // Si no hay vídeo nuevo, conserva el ya guardado
            SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String existing = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
            if (existing == null) {
                Toast.makeText(this, "Primero selecciona un vídeo", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Ya hay un vídeo guardado", Toast.LENGTH_SHORT).show();
            showStatus();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, lastPickedPath)
            .putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, lastPickedUri)
            .commit();
        Log.i(TAG, "persistVideoPath savedWallpaperVideo=" + lastPickedPath);
        Toast.makeText(this, "Guardado. Pulsa APLICAR.", Toast.LENGTH_LONG).show();
        showStatus();
    }

    private void openLivePicker() {
        try {
            ComponentName comp = new ComponentName(getPackageName(), AetherXLiveWallpaperService.class.getName());
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "open-picker-failed", e);
            Toast.makeText(this, "No se pudo abrir el selector: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showStatus() {
        SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
        String uri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
        StringBuilder sb = new StringBuilder();
        sb.append("savedPath=").append(path).append('\n');
        sb.append("savedUri=").append(uri).append('\n');
        if (path != null) {
            File f = new File(path);
            sb.append("exists=").append(f.exists()).append('\n');
            sb.append("size=").append(f.exists() ? f.length() : 0).append('\n');
            sb.append("canRead=").append(f.canRead()).append('\n');
            if (f.exists()) {
                try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
                    sb.append("fdOk=").append(pfd != null).append('\n');
                } catch (Exception e) {
                    sb.append("fdError=").append(e.getMessage()).append('\n');
                }
            }
        }
        if (lastPickedPath != null && !lastPickedPath.equals(path)) {
            sb.append("\nPendiente de guardar:\n").append(lastPickedPath);
        }
        Log.i(TAG, sb.toString());
        txtStatus.setText(sb.toString());
    }

    private File ensureWallpaperFile(String fileName) {
        File dir = new File(getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }
}
