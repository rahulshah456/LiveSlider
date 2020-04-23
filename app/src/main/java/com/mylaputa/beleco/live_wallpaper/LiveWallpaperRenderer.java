package com.mylaputa.beleco.live_wallpaper;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.mylaputa.beleco.utils.Constant;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
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

import static com.mylaputa.beleco.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.mylaputa.beleco.utils.Constant.TYPE_SINGLE;

public class LiveWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static int REFRESH_RATE = 60;
    private final static float MAX_BIAS_RANGE = 0.003f;
    private final static String TAG = LiveWallpaperRenderer.class.getSimpleName();

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final Context mContext;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private float scrollStep = 1f;
    private Queue<Float> scrollOffsetXQueue = new CircularFifoQueue<>(10);
    private float scrollOffsetX = 0.5f;// , offsetY = 0.5f;
    private float scrollOffsetXBackup = 0.5f;
    private float currentOrientationOffsetX, currentOrientationOffsetY;
    private float orientationOffsetX, orientationOffsetY;
    private Callbacks mCallbacks;
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
    private int delay = 3;
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
        transitionHandle = scheduler.scheduleAtFixedRate(transition,
                0, 1000 / REFRESH_RATE, TimeUnit.MILLISECONDS);
    }
    void stopTransition() {
        if (transitionHandle != null) transitionHandle.cancel(true);
    }

    // Here we do our drawing
    @Override
    public void onDrawFrame(GL10 gl) {
        if (needsRefreshWallpaper) {
            loadTexture();
            needsRefreshWallpaper = false;
        }
        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set the camera position (View matrix)
        float x = preA * (-2 * scrollOffsetX + 1) + currentOrientationOffsetX;
        float y = currentOrientationOffsetY;
        Matrix.setLookAtM(mViewMatrix, 0, x, y, preB, x, y, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        // Draw square
        wallpaper.draw(mMVPMatrix);

    }

    private void preCalculate() {
        if (scrollStep > 0) {
            if (wallpaperAspectRatio > (1 + 1 / (3 * scrollStep))
                    * screenAspectRatio) {
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
    void setBiasRange(int multiples) {
        // Log.d("tinyOffset", tinyOffsetX + ", " + tinyOffsetY);
        biasRange = multiples * MAX_BIAS_RANGE + 0.03f;
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



    // refreshes current wallpaper and update canvas
    void refreshWallpaper(String wallpaperPath, boolean isDefault){
        setLocalWallpaperPath(wallpaperPath);
        setIsDefaultWallpaper(isDefault);
        needsRefreshWallpaper = true;
        mCallbacks.requestRender();
    }


    // Calculate the transition offsets and refresh smooth effect in canvas
    private void transitionCal() {
        boolean needRefresh = false;
        if (Math.abs(currentOrientationOffsetX - orientationOffsetX) > .0001
                || Math.abs(currentOrientationOffsetY - orientationOffsetY) > .0001) {
            float transitionStep = REFRESH_RATE / LiveWallpaperService.SENSOR_RATE;
            float tinyOffsetX = (orientationOffsetX - currentOrientationOffsetX)
                    / (transitionStep * delay);
            float tinyOffsetY = (orientationOffsetY - currentOrientationOffsetY)
                    / (transitionStep * delay);
            currentOrientationOffsetX += tinyOffsetX;
            currentOrientationOffsetY += tinyOffsetY;
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
        Log.d(TAG, "loadTexture: default_wallpaper = " + isDefaultWallpaper);
        Log.d(TAG, "loadTexture: local_wallpaper_path = " + localWallpaperPath);
        //InputStream is = null;
        FileInputStream is = null;

        if (wallpaperType == TYPE_SINGLE){
            if (!isDefaultWallpaper) {
                try {
                    is = new FileInputStream(localWallpaperPath);
                    //is = mContext.openFileInput(localWallpaperPath);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    // resetting to default wallpaper
                    refreshWallpaper(DEFAULT_LOCAL_PATH,true);
                }
            } else {
                try {
                    AssetFileDescriptor fileDescriptor = mContext.getAssets().openFd(Constant.DEFAULT_WALLPAPER_NAME);
                    is = fileDescriptor.createInputStream();
                    //is = mContext.getAssets().open(Constant.DEFAULT_WALLPAPER);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                is = new FileInputStream(localWallpaperPath);
                //is = mContext.openFileInput(localWallpaperPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                // resetting to default wallpaper
                refreshWallpaper(DEFAULT_LOCAL_PATH,true);
            }
        }



        if (is == null) return;
        if (wallpaper != null)
            wallpaper.destroy();
        wallpaper = new Wallpaper(cropBitmap(is));
        preCalculate();
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.gc();
    }
    private Bitmap cropBitmap(InputStream is) {
        Bitmap src = BitmapFactory.decodeStream(is);
        if (src == null) return null;
        final float width = src.getWidth();
        final float height = src.getHeight();
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
    // Change the current rotation parameters
    public static class BiasChangeEvent {
        float x, y;

        BiasChangeEvent(float x, float y) {
            if (x > 1) this.x = 1;
            else if (x < -1) this.x = -1;
            else this.x = x;
            if (y > 1) this.y = 1;
            else if (y < -1) this.y = -1;
            else this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }
}
