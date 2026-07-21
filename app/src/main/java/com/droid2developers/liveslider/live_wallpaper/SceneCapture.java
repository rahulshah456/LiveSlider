package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Shared "capture the wallpaper as the camera renders it, then let a fragment
 * shader sample that" FBO plumbing, factored out of RainShader/RippleShader —
 * both need the same full-screen-resolution capture (crop/pan already baked in
 * by the camera matrix, so the sampling shader needs no matrix of its own).
 */
class SceneCapture {
    private static final String TAG = SceneCapture.class.getSimpleName();

    private final int[] fbo = new int[1];
    private final int[] tex = new int[1];
    private int w, h;
    private boolean failed;

    int textureHandle() {
        return tex[0];
    }

    /** (Re)creates the capture FBO for the given size if needed.
     *  Returns false if FBO rendering is unavailable — caller should skip the effect. */
    boolean ensure(int width, int height) {
        if (failed) return false;
        if (fbo[0] != 0 && width == w && height == h) return true;
        release();

        GLES20.glGenFramebuffers(1, fbo, 0);
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        // RGB565 (16bpp) — this is a transient overlay capture, not the sharp
        // wallpaper, so half the memory with no perceptible quality loss under the
        // animated effect. No alpha channel needed (the wallpaper fills every pixel).
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, tex[0], 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "ensure: framebuffer incomplete, shader effect disabled");
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            release();
            failed = true;
            return false;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        w = width;
        h = height;
        return true;
    }

    /** Binds the capture FBO. Caller draws the wallpaper (with its normal camera
     *  matrix) into it, then samples textureHandle() from the default framebuffer. */
    void begin() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    /** Frees GL objects. Safe to call with a live context only. */
    void release() {
        if (tex[0] != 0) {
            GLES20.glDeleteTextures(1, tex, 0);
            tex[0] = 0;
        }
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(1, fbo, 0);
            fbo[0] = 0;
        }
        w = h = 0;
    }
}
