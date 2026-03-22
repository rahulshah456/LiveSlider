package com.droid2developers.liveslider.live_wallpaper;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.droid2developers.liveslider.models.BiasChangeEvent;
import com.droid2developers.liveslider.models.FaceRotationEvent;
import com.droid2developers.liveslider.utils.Constant;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.greenrobot.eventbus.EventBus;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;

public class LiveWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static int REFRESH_RATE = 60;
    private final static float MAX_BIAS_RANGE = 0.006f;
    private final static String TAG = LiveWallpaperRenderer.class.getSimpleName();

    // Transition state — all animation runs on the GL thread inside onDrawFrame()
    private float transitionAlpha = 1.0f;
    private float transitionProgress = 0f;
    private boolean transitionActive = false;
    private boolean transitionFadeOutDone = false;
    private boolean transitionPendingLoad = false;
    private long transitionLoadAfter = 0L;
    private int chosenEffect = 0;   // user's saved preference — written from any thread
    private int currentEffect = 0;  // active shader effect — only touched on GL thread

    // Pending wallpaper-change request — written from service thread, consumed on GL thread
    private volatile boolean hasPendingRefresh = false;
    private volatile String pendingWallpaperPath = null;
    private volatile boolean pendingIsDefault = false;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final Context mContext;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private float scrollStep = 1f;
    private final Queue<Float> scrollOffsetXQueue = new CircularFifoQueue<>(10);
    private float scrollOffsetX = 0.5f;// , offsetY = 0.5f;
    private float scrollOffsetXBackup = 0.5f;
    private float currentOrientationOffsetX, currentOrientationOffsetY;
    private float orientationOffsetX, orientationOffsetY;
    private final Callbacks mCallbacks;
    private float screenAspectRatio;
    private int screenH;
    private float wallpaperAspectRatio;
    private final Runnable transition = new Runnable() {
        @Override
        public void run() {
            transitionCal();
        }
    };
    private ScheduledFuture<?> transitionHandle;
    private float preA;
    private float preB;

    // Important mutable parameters for live wallpaper
    private Wallpaper wallpaper;
    private Wallpaper previousWallpaper; // held alive during effects 1 & 2 crossfade
    private String localWallpaperPath = null;
    private int delay = 1;
    private float biasRange;
    private float scrollRange;
    private boolean scrollMode = true;
    private boolean needsRefreshWallpaper;
    private boolean isDefaultWallpaper;
    private int wallpaperType;


    LiveWallpaperRenderer(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
    }

    void release() {
        if (wallpaper != null) wallpaper.destroy();
        if (previousWallpaper != null) { previousWallpaper.destroy(); previousWallpaper = null; }
        stopTransition();
        scheduler.shutdown();
    }

    // The Surface is created/init()
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        Wallpaper.initGl();
    }


    // Transition methods
    void startTransition() {
        stopTransition();
        transitionHandle = scheduler.scheduleWithFixedDelay(transition,
                0, 1000 / REFRESH_RATE, TimeUnit.MILLISECONDS);
    }
    void stopTransition() {
        if (transitionHandle != null) transitionHandle.cancel(true);
    }

    // Here we do our drawing
    private boolean hasLoggedNullWallpaper = false;
    @Override
    public void onDrawFrame(GL10 gl) {

        // Consume any pending wallpaper-change request first — sets up transition
        // state atomically on the GL thread before the tick or draw logic reads it.
        consumePendingRefresh();

        // --- Transition tick (runs entirely on GL thread) ---
        if (transitionActive) {
            if (currentEffect == 0) {
                // ---- Effect 0: two-phase alpha fade (fade-out → swap → fade-in) ----
                if (!transitionFadeOutDone) {
                    transitionAlpha -= 0.005f; // 0.1x debug speed (was 0.05f)
                    if (transitionAlpha <= 0.0f) {
                        transitionAlpha       = 0.0f;
                        transitionFadeOutDone = true;
                        localWallpaperPath    = pendingWallpaperPath;
                        isDefaultWallpaper    = pendingIsDefault;
                        needsRefreshWallpaper = true;
                        transitionPendingLoad = true;
                        transitionLoadAfter   = SystemClock.elapsedRealtime() + 50;
                    }
                    mCallbacks.requestRender();
                } else if (transitionPendingLoad) {
                    if (SystemClock.elapsedRealtime() >= transitionLoadAfter) {
                        transitionPendingLoad = false;
                    }
                    mCallbacks.requestRender();
                } else {
                    transitionAlpha += 0.004f; // 0.1x debug speed (was 0.04f)
                    if (transitionAlpha >= 1.0f) {
                        transitionAlpha  = 1.0f;
                        transitionActive = false;
                    } else {
                        mCallbacks.requestRender();
                    }
                }
                transitionProgress = transitionAlpha;

            } else {
                // ---- Effects 1 & 2: true crossfade — new texture dissolves/pixelates in
                //      over the old one. previousWallpaper drawn at full alpha underneath,
                //      wallpaper drawn on top with progress 0→1.
                transitionProgress += 0.004f; // 0.1x debug speed (was 0.04f)
                if (transitionProgress >= 1.0f) {
                    transitionProgress = 1.0f;
                    transitionAlpha    = 1.0f;
                    transitionActive   = false;
                    // New texture fully revealed — discard the old one
                    if (previousWallpaper != null) {
                        previousWallpaper.destroy();
                        previousWallpaper = null;
                    }
                    // Reset to plain effect so subsequent draws don't run the
                    // dissolve/pixelate shader on a fully-revealed static wallpaper
                    currentEffect = 0;
                } else {
                    mCallbacks.requestRender();
                }
                transitionAlpha = 1.0f; // new texture always present; shader controls visibility
            }
        }
        // --- End transition tick ---

        if (needsRefreshWallpaper) {
            loadTexture();
            needsRefreshWallpaper = false;
        }

        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Only proceed if preA and preB are valid numbers
        if (Float.isNaN(preA) || Float.isNaN(preB) || Float.isInfinite(preA) || Float.isInfinite(preB)) {
            if (!hasLoggedNullWallpaper) {
                Log.w(TAG, "onDrawFrame: Invalid matrix parameters (preA/preB), skipping draw");
                hasLoggedNullWallpaper = true;
            }
            return;
        }

        // Set the camera position (View matrix)
        float x = preA * (-2 * scrollOffsetX + 1) + currentOrientationOffsetX;
        float y = currentOrientationOffsetY;
        Matrix.setLookAtM(mViewMatrix, 0, x, y, preB, x, y, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        // Draw previous wallpaper first (full alpha, no effect) during effects 1 & 2
        if (previousWallpaper != null && currentEffect != 0) {
            previousWallpaper.draw(mMVPMatrix, 1.0f, 1.0f, 0); // always plain, always opaque
        }

        // Draw current (incoming) wallpaper on top
        if (wallpaper != null) {
            hasLoggedNullWallpaper = false;
            wallpaper.draw(mMVPMatrix, transitionAlpha, transitionProgress, currentEffect);
        } else {
            if (!hasLoggedNullWallpaper) {
                Log.w(TAG, "onDrawFrame: wallpaper is null, skipping draw");
                hasLoggedNullWallpaper = true;
            }
        }
    }

    private void preCalculate() {
        // Guard against zero or invalid aspect ratios
        if (screenAspectRatio == 0 || Float.isNaN(screenAspectRatio) || Float.isInfinite(screenAspectRatio)) {
            preA = Float.NaN;
            preB = Float.NaN;
            return;
        }
        if (wallpaperAspectRatio == 0 || Float.isNaN(wallpaperAspectRatio) || Float.isInfinite(wallpaperAspectRatio)) {
            preA = Float.NaN;
            preB = Float.NaN;
            return;
        }
        if (scrollStep > 0) {
            if (wallpaperAspectRatio > (1 + 1 / (3 * scrollStep)) * screenAspectRatio) {
                scrollRange = 1 + 1 / (3 * scrollStep);
            } else if (wallpaperAspectRatio >= screenAspectRatio) {
                scrollRange = wallpaperAspectRatio / screenAspectRatio;
            } else {
                scrollRange = 1;
            }
        } else {
            scrollRange = 1;
        }
        // ------------------------------------------------------
        preA = screenAspectRatio * (scrollRange - 1);
        // preB = -1f;
        if (screenAspectRatio < 1)
            preB = -1.0f + (biasRange / screenAspectRatio);
        else
            preB = -1.0f + (biasRange * screenAspectRatio);
    }

    // If the surface changes, reset the view
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (height == 0) { // Prevent A Divide By Zero By
            height = 1; // Making Height Equal One
        }

        screenAspectRatio = (float) width / (float) height;
        screenH = height;

        GLES20.glViewport(0, 0, width, height);

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -0.1f * screenAspectRatio,
                0.1f * screenAspectRatio, -0.1f, 0.1f, 0.1f, 2);

        needsRefreshWallpaper = true;
//        loadTexture();
        mCallbacks.requestRender();
    }


    // Setters for the changes in the localWallpaperPath and refresh for the same
    void setOffset(float offsetX, float offsetY) {
        if (scrollMode) {
            scrollOffsetXBackup = offsetX;
            scrollOffsetXQueue.offer(offsetX);
        } else {
            scrollOffsetXBackup = offsetX;
        }
    }
    void setOffsetStep(float offsetStepX, float offsetStepY) {
        if (scrollStep != offsetStepX) {
            scrollStep = offsetStepX;
            preCalculate();
        }
    }

    void setOrientationAngle(float roll, float pitch) {
        orientationOffsetX = (float) (biasRange * Math.sin(roll));
        orientationOffsetY = (float) (biasRange * Math.sin(pitch));
    }

    void setNewFaceRotation(int face) {
        // Fire FaceRotationEvent for UI updates
        EventBus.getDefault().post(new FaceRotationEvent(face));
    }

    void setBiasRange(int multiples) {
        if (multiples == 0) {
            stopTransition();
            biasRange = 0f;
            currentOrientationOffsetX = 0f;
            currentOrientationOffsetY = 0f;
            orientationOffsetX = 0f;
            orientationOffsetY = 0f;
        } else {
            biasRange = multiples * MAX_BIAS_RANGE + 0.03f;
            startTransition();
        }
        preCalculate();
        mCallbacks.requestRender();
    }
    void setDelay(int delay) {
        this.delay = delay;
    }
    void setScrollMode(boolean scrollMode) {
        this.scrollMode = scrollMode;
        if (scrollMode)
            scrollOffsetXQueue.offer(scrollOffsetXBackup);
        else {
            scrollOffsetXQueue.clear();
            scrollOffsetXQueue.offer(0.5f);
        }
//        noScroll = false;
    }
    void setLocalWallpaperPath(String name){
        localWallpaperPath = name;
    }
    void setIsDefaultWallpaper(boolean isDefault) {
        isDefaultWallpaper = isDefault;
    }
    void setWallpaperType(int wallpaperType){
        this.wallpaperType = wallpaperType;
    }

    // Effect 0 = fade (default), 1 = dissolve, 2 = pixelate
    void setTransitionEffect(int effect) {
        chosenEffect = effect;
    }



    // refreshes current wallpaper and update canvas
    // Called from service/main thread — only writes volatile fields, no GL state.
    void refreshWallpaper(String wallpaperPath, boolean isDefault) {
        pendingWallpaperPath = wallpaperPath;
        pendingIsDefault     = isDefault;
        hasPendingRefresh    = true;   // GL thread picks this up at top of next frame
        mCallbacks.requestRender();
    }

    // Consumes a pending refresh request atomically on the GL thread.
    // Must be called at the very top of onDrawFrame before any tick or draw logic.
    private void consumePendingRefresh() {
        if (!hasPendingRefresh) return;
        hasPendingRefresh = false;  // clear flag first — GL thread owns state from here

        // Snapshot chosen effect for this transition
        currentEffect = chosenEffect;

        if (currentEffect == 0) {
            // Fade: two-phase — fade out current, load new, fade in
            transitionAlpha       = 1.0f;
            transitionProgress    = 0.0f;
            transitionFadeOutDone = false;
            transitionPendingLoad = false;
        } else {
            // Dissolve / Pixelate: keep old texture alive as previousWallpaper,
            // load new texture immediately, animate progress 0→1.
            if (previousWallpaper != null) {
                previousWallpaper.destroy();
            }
            previousWallpaper     = wallpaper;       // old texture is the backdrop
            wallpaper             = null;             // cleared; loadTexture() repopulates
            localWallpaperPath    = pendingWallpaperPath;
            isDefaultWallpaper    = pendingIsDefault;
            needsRefreshWallpaper = true;
            transitionProgress    = 0.0f;
            transitionAlpha       = 1.0f;
            transitionFadeOutDone = true;
            transitionPendingLoad = false;
        }

        transitionActive = true;
    }


    // Calculate the transition offsets and refresh smooth effect in canvas
    private void transitionCal() {
        boolean needRefresh = false;
//        Log.d(TAG, "transitionCal: DELAY:" + delay);
        if (Math.abs(currentOrientationOffsetX - orientationOffsetX) > .0001
                || Math.abs(currentOrientationOffsetY - orientationOffsetY) > .0001) {

            if(delay == 1) {
                currentOrientationOffsetX += (orientationOffsetX - currentOrientationOffsetX) * 0.8f;
                currentOrientationOffsetY += (orientationOffsetY - currentOrientationOffsetY) * 0.8f;
            } else {
                float transitionStep = REFRESH_RATE / LiveWallpaperService.SENSOR_RATE;
                float tinyOffsetX = (orientationOffsetX - currentOrientationOffsetX)
                        / (transitionStep * delay);
                float tinyOffsetY = (orientationOffsetY - currentOrientationOffsetY)
                        / (transitionStep * delay);
                currentOrientationOffsetX += tinyOffsetX;
                currentOrientationOffsetY += tinyOffsetY;
            }
            EventBus.getDefault().post(new BiasChangeEvent(currentOrientationOffsetX / biasRange,
                    currentOrientationOffsetY / biasRange));
            needRefresh = true;
        }
        if (!scrollOffsetXQueue.isEmpty()) {
            //noinspection ConstantConditions
            scrollOffsetX = scrollOffsetXQueue.poll();
            needRefresh = true;
        }
        if (needRefresh) mCallbacks.requestRender();
    }


    // Create and loads new Wallpaper from the required settings
    private void loadTexture() {
        System.gc();
        FileInputStream is = null;
        if (wallpaperType == TYPE_SINGLE){
            if (!isDefaultWallpaper) {
                try {
                    is = new FileInputStream(localWallpaperPath);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "loadTexture: FileNotFoundException for path: " + localWallpaperPath, e);
                    refreshWallpaper(DEFAULT_LOCAL_PATH,true);
                }
            } else {
                try {
                    AssetFileDescriptor fileDescriptor = mContext.getAssets().openFd(Constant.DEFAULT_WALLPAPER_NAME);
                    is = fileDescriptor.createInputStream();
                } catch (IOException e) {
                    Log.e(TAG, "loadTexture: IOException loading default wallpaper", e);
                }
            }
        } else {
            try {
                is = new FileInputStream(localWallpaperPath);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "loadTexture: FileNotFoundException for path: " + localWallpaperPath, e);
                refreshWallpaper(DEFAULT_LOCAL_PATH,true);
            }
        }
        if (is == null) {
            Log.e(TAG, "loadTexture: InputStream is null, cannot load wallpaper");
            return;
        }
        if (wallpaper != null)
            wallpaper.destroy();
        Bitmap bmp = cropBitmap(is);
        if (bmp == null) {
            Log.e(TAG, "loadTexture: cropBitmap returned null, cannot create wallpaper");
            return;
        }
        wallpaper = new Wallpaper(bmp);
        preCalculate();
        try {
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "loadTexture: IOException closing InputStream", e);
        }
        System.gc();
    }
    private Bitmap cropBitmap(InputStream is) {
        Bitmap src = BitmapFactory.decodeStream(is);
        if (src == null) {
            Log.e(TAG, "cropBitmap: BitmapFactory.decodeStream returned null");
            return null;
        }
        final float width = src.getWidth();
        final float height = src.getHeight();
        if (height == 0) {
            Log.e(TAG, "cropBitmap: height is zero, cannot calculate aspect ratio");
            src.recycle();
            return null;
        }
        wallpaperAspectRatio = width / height;
        if (wallpaperAspectRatio < screenAspectRatio) {
            scrollRange = 1;
            Bitmap tmp = Bitmap.createBitmap(src, 0,
                    (int) (height - width / screenAspectRatio) / 2,
                    (int) width, (int) (width / screenAspectRatio));
            src.recycle();
            if (tmp.getHeight() > 1.1 * screenH) {
                Bitmap result = Bitmap.createScaledBitmap(tmp,
                        (int) (1.1 * screenH * screenAspectRatio),
                        (int) (1.1 * screenH), true);
                tmp.recycle();
                return result;
            } else
                return tmp;
        } else {
            if (src.getHeight() > 1.1 * screenH) {
                Bitmap result = Bitmap.createScaledBitmap(src,
                        (int) (1.1 * screenH * wallpaperAspectRatio),
                        (int) (1.1 * screenH), true);
                src.recycle();
                return result;
            } else
                return src;
        }
    }


    // Internal Callback for refreshing the current canvas
    interface Callbacks {
        void requestRender();
    }
}
