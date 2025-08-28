package com.droid2developers.liveslider.live_wallpaper;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
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
import androidx.lifecycle.MutableLiveData;

public class LiveWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static int REFRESH_RATE = 60;
    private final static float MAX_BIAS_RANGE = 0.006f;
    private final static String TAG = LiveWallpaperRenderer.class.getSimpleName();

    private MutableLiveData<Float> mutableAlfa = null;  // Initial alpha value
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
    private String localWallpaperPath = null;
    private int delay = 1;
    private float biasRange;
    private float scrollRange;
    private boolean scrollMode = true;
    private boolean needsRefreshWallpaper;
    private boolean isDefaultWallpaper;
    private int wallpaperType;

    private final Handler animationHandler = new Handler(Looper.getMainLooper());
    
    // Transition settings
    private int transitionMode = Constant.TRANSITION_FADE_TO_BLACK;
    private float transitionSpeed = Constant.TRANSITION_SPEED_NORMAL;

    LiveWallpaperRenderer(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
        mutableAlfa = new MutableLiveData<>(1.0f);
    }

    void release() {
        // TODO stuff to release
        if (wallpaper != null)
            wallpaper.destroy();
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

        // Draw square
        if (wallpaper != null) {
            hasLoggedNullWallpaper = false;
            if(mutableAlfa.getValue() == null) {
                wallpaper.draw(mMVPMatrix, 1.0f);
            } else {
                wallpaper.draw(mMVPMatrix, mutableAlfa.getValue());
            }
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
    void setTransitionMode(int transitionMode) {
        this.transitionMode = transitionMode;
    }
    void setTransitionSpeed(float transitionSpeed) {
        this.transitionSpeed = transitionSpeed;
    }



    // refreshes current wallpaper and update canvas
    void refreshWallpaper(String wallpaperPath, boolean isDefault){
        if (transitionMode == Constant.TRANSITION_CROSSFADE) {
            // Direct crossfade transition - no fade to black
            Runnable crossfadeRunnable = new Runnable() {
                private float alpha = 1.0f;
                private boolean wallpaperChanged = false;

                @Override
                public void run() {
                    if (!wallpaperChanged) {
                        // Change wallpaper immediately, then fade in
                        setLocalWallpaperPath(wallpaperPath);
                        setIsDefaultWallpaper(isDefault);
                        needsRefreshWallpaper = true;
                        wallpaperChanged = true;
                        alpha = 0.0f; // Start from transparent
                        
                        // Small delay to ensure wallpaper is loaded
                        animationHandler.postDelayed(this, 50);
                        mutableAlfa.setValue(alpha);
                        mCallbacks.requestRender();
                        return;
                    }

                    // Fade in the new wallpaper
                    alpha += 0.04f * transitionSpeed; // Apply speed multiplier

                    if (alpha >= 1.0f) {
                        alpha = 1.0f;
                        mutableAlfa.setValue(alpha);
                        mCallbacks.requestRender();
                        // Animation complete
                        return;
                    }

                    mutableAlfa.setValue(alpha);
                    mCallbacks.requestRender();

                    // Continue animation
                    animationHandler.postDelayed(this, 16); // 60 FPS
                }
            };

            animationHandler.post(crossfadeRunnable);
        } else {
            // Original fade-to-black transition with configurable speed
            Runnable fadeOutThenInRunnable = new Runnable() {
                private float alpha = 1.0f;
                private boolean fadeOutComplete = false;
                private boolean wallpaperChanged = false;

                @Override
                public void run() {
                    if (!fadeOutComplete) {
                        // Fade out phase
                        alpha -= 0.05f * transitionSpeed; // Apply speed multiplier

                        if (alpha <= 0.0f) {
                            alpha = 0.0f;
                            fadeOutComplete = true;

                            // Change wallpaper when screen is completely dark
                            if (!wallpaperChanged) {
                                setLocalWallpaperPath(wallpaperPath);
                                setIsDefaultWallpaper(isDefault);
                                needsRefreshWallpaper = true;
                                wallpaperChanged = true;

                                // Small delay to ensure wallpaper is loaded before fade-in
                                animationHandler.postDelayed(this, 50);
                                mutableAlfa.setValue(alpha);
                                mCallbacks.requestRender();
                                return;
                            }
                        }
                    } else {
                        // Fade in phase - show new wallpaper
                        alpha += 0.04f * transitionSpeed; // Apply speed multiplier

                        if (alpha >= 1.0f) {
                            alpha = 1.0f;
                            mutableAlfa.setValue(alpha);
                            mCallbacks.requestRender();
                            // Animation complete
                            return;
                        }
                    }

                    mutableAlfa.setValue(alpha);
                    mCallbacks.requestRender();

                    // Continue animation
                    animationHandler.postDelayed(this, 16); // 60 FPS
                }
            };

            animationHandler.post(fadeOutThenInRunnable);
        }
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
