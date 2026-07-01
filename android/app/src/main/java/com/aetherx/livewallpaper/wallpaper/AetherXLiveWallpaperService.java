package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Plan E: real live wallpaper.
 * - EGL context created on the WallpaperService Surface.
 * - Video decoded by MediaPlayer into a SurfaceTexture (external OES texture).
 * - Each frame is blitted to the wallpaper Surface with a fullscreen quad.
 *
 * Samsung SDK 36 rejects direct MediaCodec output onto the wallpaper Surface;
 * the SurfaceTexture is an ordinary decoder-friendly surface, and EGL swap
 * onto the wallpaper Surface is accepted by the compositor. 30-60 FPS real.
 */
public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";

    private void recordStep(String step) {
        Log.i(TAG, "STEP " + step);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_WALLPAPER_STEP,
                System.currentTimeMillis() + " " + step).apply();
        } catch (Throwable ignored) {}
    }

    private void recordKey(String key, String value) {
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(key, System.currentTimeMillis() + " " + value).apply();
        } catch (Throwable ignored) {}
    }

    private void persistNativeException(String message, Throwable t) {
        Log.e(TAG, message, t);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String stack = t == null ? "" : "\n" + Log.getStackTraceString(t);
            p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_NATIVE_EXCEPTION,
                System.currentTimeMillis() + " " + message + stack).apply();
        } catch (Throwable ignored) {}
    }

    private void clearNativeFailureState() {
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit()
                .putString(AetherXLiveWallpaperPlugin.KEY_LAST_NATIVE_EXCEPTION, "(none)")
                .putString(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_ERROR, "(none)")
                .apply();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recordStep("SERVICE_ONCREATE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreate");
    }

    @Override
    public Engine onCreateEngine() {
        recordStep("ON_CREATE_ENGINE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreateEngine");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "onCreateEngine");
        return new GLEngine();
    }

    // =====================================================================
    // Engine
    // =====================================================================

    private class GLEngine extends Engine {
        private GLRenderer renderer;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            recordStep("ON_SURFACE_CREATED valid=" + valid);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceCreated valid=" + valid);
            if (!valid) return;
            clearNativeFailureState();
            renderer = new GLRenderer(s);
            renderer.start();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
            if (renderer != null) renderer.onSize(width, height);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            if (renderer != null) renderer.setVisible(v);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            recordStep("SURFACE_DESTROYED");
            if (renderer != null) { renderer.shutdown(); renderer = null; }
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            recordStep("ENGINE_DESTROYED");
            if (renderer != null) { renderer.shutdown(); renderer = null; }
            super.onDestroy();
        }
    }

    // =====================================================================
    // GLRenderer: owns EGL, SurfaceTexture, MediaPlayer, prefs poll loop
    // =====================================================================

    private class GLRenderer extends Thread implements SurfaceTexture.OnFrameAvailableListener {
        private final Surface outputSurface;
        private volatile boolean stopRequested = false;
        private volatile boolean visible = true;
        private volatile int surfaceW = 0, surfaceH = 0;
        private final Object frameLock = new Object();
        private boolean frameAvailable = false;

        // EGL
        private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

        // GL
        private int program = 0;
        private int aPosLoc, aTexLoc, uMvpLoc, uTexMatrixLoc, uSamplerLoc;
        private int oesTexId = 0;
        private FloatBuffer quadVerts;
        private FloatBuffer quadTex;
        private final float[] texMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];
        private int videoW = 0, videoH = 0;

        // Media
        private SurfaceTexture videoTexture;
        private Surface videoSurface;
        private MediaPlayer mediaPlayer;
        private AssetFileDescriptor currentAfd;
        private String activePath, activeUri;

        GLRenderer(Surface s) { super("AetherXGLRenderer"); this.outputSurface = s; }

        void setVisible(boolean v) {
            visible = v;
            if (mediaPlayer != null) {
                try { if (v) mediaPlayer.start(); else mediaPlayer.pause(); } catch (Throwable ignored) {}
            }
        }

        void onSize(int w, int h) { surfaceW = w; surfaceH = h; }

        void shutdown() {
            stopRequested = true;
            synchronized (frameLock) { frameLock.notifyAll(); }
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            synchronized (frameLock) { frameAvailable = true; frameLock.notifyAll(); }
        }

        @Override
        public void run() {
            try {
                if (!initEGL()) { recordStep("GL_INIT_EGL_FAIL"); return; }
                initGL();
                createVideoTexture();
                openSource(null, null);

                long lastPrefsCheck = 0L;
                while (!stopRequested) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastPrefsCheck > 500L) {
                        lastPrefsCheck = now;
                        checkSourceChange();
                    }

                    boolean drawn = false;
                    synchronized (frameLock) {
                        if (!frameAvailable) {
                            try { frameLock.wait(200L); } catch (InterruptedException ignored) {}
                        }
                        if (frameAvailable) { frameAvailable = false; drawn = true; }
                    }
                    if (stopRequested) break;
                    if (!drawn) continue;

                    try {
                        videoTexture.updateTexImage();
                        videoTexture.getTransformMatrix(texMatrix);
                        drawFrame();
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
                    } catch (Throwable t) {
                        persistNativeException("GL_DRAW_FAIL", t);
                    }
                }
            } catch (Throwable t) {
                persistNativeException("GL_THREAD_EXCEPTION", t);
            } finally {
                releaseMedia();
                releaseGL();
                releaseEGL();
                recordStep("GL_THREAD_EXIT");
            }
        }

        // ------------- EGL -------------

        private boolean initEGL() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false;
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) return false;

            int[] attribs = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
                || numConfigs[0] <= 0) return false;
            int[] ctxAttribs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false;
            int[] surfAttribs = { EGL14.EGL_NONE };
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], outputSurface, surfAttribs, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false;
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false;
            recordStep("GL_EGL_READY");
            return true;
        }

        private void releaseEGL() {
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
                    EGL14.eglTerminate(eglDisplay);
                }
            } catch (Throwable ignored) {}
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;
        }

        // ------------- GL -------------

        private static final String VERT_SHADER =
            "attribute vec4 aPos;\n" +
            "attribute vec2 aTex;\n" +
            "uniform mat4 uMvp;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "  gl_Position = uMvp * aPos;\n" +
            "  vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;\n" +
            "}";

        private static final String FRAG_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform samplerExternalOES uSampler;\n" +
            "void main() { gl_FragColor = texture2D(uSampler, vTex); }";

        private void initGL() {
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, VERT_SHADER);
            int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SHADER);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program); program = 0;
                throw new RuntimeException("GL link failed: " + log);
            }
            aPosLoc = GLES20.glGetAttribLocation(program, "aPos");
            aTexLoc = GLES20.glGetAttribLocation(program, "aTex");
            uMvpLoc = GLES20.glGetUniformLocation(program, "uMvp");
            uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix");
            uSamplerLoc = GLES20.glGetUniformLocation(program, "uSampler");

            float[] verts = { -1f, -1f,  1f, -1f, -1f,  1f,  1f,  1f };
            float[] texs =  {  0f,  0f,  1f,  0f,  0f,  1f,  1f,  1f };
            quadVerts = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quadVerts.put(verts).position(0);
            quadTex = ByteBuffer.allocateDirect(texs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quadTex.put(texs).position(0);
            Matrix.setIdentityM(mvpMatrix, 0);
            Matrix.setIdentityM(texMatrix, 0);
            recordStep("GL_PROGRAM_READY");
        }

        private int compileShader(int type, String src) {
            int id = GLES20.glCreateShader(type);
            GLES20.glShaderSource(id, src);
            GLES20.glCompileShader(id);
            int[] status = new int[1];
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(id);
                GLES20.glDeleteShader(id);
                throw new RuntimeException("Shader compile failed: " + log);
            }
            return id;
        }

        private void createVideoTexture() {
            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            oesTexId = tex[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            videoTexture = new SurfaceTexture(oesTexId);
            videoTexture.setOnFrameAvailableListener(this);
            videoSurface = new Surface(videoTexture);
            recordStep("GL_VIDEO_TEXTURE_READY");
        }

        private void releaseGL() {
            try { if (videoSurface != null) videoSurface.release(); } catch (Throwable ignored) {}
            try { if (videoTexture != null) videoTexture.release(); } catch (Throwable ignored) {}
            if (program != 0) GLES20.glDeleteProgram(program);
            program = 0;
            videoSurface = null;
            videoTexture = null;
        }

        private void drawFrame() {
            int sw = surfaceW, sh = surfaceH;
            if (sw <= 0 || sh <= 0) return;
            GLES20.glViewport(0, 0, sw, sh);
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            // Apply fit mode via texture matrix crop around center (0.5, 0.5).
            SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String mode = prefs.getString(AetherXLiveWallpaperPlugin.KEY_FIT_MODE, "cover");
            float[] finalTexMatrix = texMatrix;
            if (videoW > 0 && videoH > 0 && !"stretch".equals(mode)) {
                float va = (float) videoW / (float) videoH;
                float sa = (float) sw / (float) sh;
                float sx = 1f, sy = 1f;
                if ("cover".equals(mode)) {
                    // Sample only the central strip so the video fills the surface.
                    if (va > sa) sx = sa / va; else sy = va / sa;
                } else if ("contain".equals(mode)) {
                    // Expand sampling so the entire video fits (letterbox with black bars).
                    if (va > sa) sy = sa / va; else sx = va / sa;
                }
                float[] crop = new float[16];
                Matrix.setIdentityM(crop, 0);
                Matrix.translateM(crop, 0, 0.5f, 0.5f, 0f);
                Matrix.scaleM(crop, 0, sx, sy, 1f);
                Matrix.translateM(crop, 0, -0.5f, -0.5f, 0f);
                float[] combined = new float[16];
                Matrix.multiplyMM(combined, 0, texMatrix, 0, crop, 0);
                finalTexMatrix = combined;
            }

            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadVerts);
            GLES20.glEnableVertexAttribArray(aPosLoc);
            GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, quadTex);
            GLES20.glEnableVertexAttribArray(aTexLoc);
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, finalTexMatrix, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
            GLES20.glUniform1i(uSamplerLoc, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        // ------------- Media source -------------

        private void checkSourceChange() {
            SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String newPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
            String newUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
            boolean pathChanged = !eq(newPath, activePath);
            boolean uriChanged = !eq(newUri, activeUri) && (newPath == null);
            if (pathChanged || uriChanged) {
                recordStep("SOURCE_RELOAD_DETECTED path=" + newPath);
                openSource(newPath, newUri);
            }
        }

        private void openSource(String explicitPath, String explicitUri) {
            releaseMedia();
            SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String path = explicitPath != null ? explicitPath : prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
            String uri = explicitUri != null ? explicitUri : prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);

            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setSurface(videoSurface);
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f);
                boolean set = false;

                if (path != null) {
                    File f = new File(path);
                    if (f.exists() && f.canRead() && f.length() > 0) {
                        mediaPlayer.setDataSource(f.getAbsolutePath());
                        activePath = path; activeUri = null;
                        recordStep("MEDIA_SOURCE_FILE len=" + f.length());
                        set = true;
                    }
                }
                if (!set && uri != null && !uri.isEmpty()) {
                    try {
                        mediaPlayer.setDataSource(AetherXLiveWallpaperService.this, Uri.parse(uri));
                        activePath = null; activeUri = uri;
                        recordStep("MEDIA_SOURCE_URI " + uri);
                        set = true;
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_URI_OPEN_FAIL", t);
                    }
                }
                if (!set) {
                    currentAfd = getResources().openRawResourceFd(R.raw.testwallpaper);
                    if (currentAfd == null) { recordStep("MEDIA_RAW_AFD_NULL"); return; }
                    mediaPlayer.setDataSource(currentAfd.getFileDescriptor(), currentAfd.getStartOffset(), currentAfd.getLength());
                    activePath = null; activeUri = null;
                    recordStep("MEDIA_SOURCE_RAW");
                }

                mediaPlayer.setOnVideoSizeChangedListener((mp, w, h) -> {
                    videoW = w; videoH = h;
                    recordStep("MEDIA_VIDEO_SIZE " + w + "x" + h);
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    persistNativeException("MEDIA_ERROR what=" + what + " extra=" + extra, null);
                    return true;
                });
                mediaPlayer.setOnPreparedListener(mp -> {
                    try {
                        mp.start();
                        recordStep("MEDIA_STARTED");
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_START_FAIL", t);
                    }
                });
                mediaPlayer.prepareAsync();
                recordStep("MEDIA_PREPARE_ASYNC");
            } catch (Throwable t) {
                persistNativeException("MEDIA_INIT_FAIL", t);
                releaseMedia();
            }
        }

        private void releaseMedia() {
            try { if (mediaPlayer != null) { mediaPlayer.reset(); mediaPlayer.release(); } } catch (Throwable ignored) {}
            mediaPlayer = null;
            try { if (currentAfd != null) currentAfd.close(); } catch (Throwable ignored) {}
            currentAfd = null;
        }

        private boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
    }
}
