package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;
import android.util.Log;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

/**
 * Offscreen blur pipeline for the blur wallpaper transition.
 *
 * The scene is rendered into a quarter-resolution FBO, blurred with a two-pass
 * separable 9-tap Gaussian (horizontal then vertical, ping-pong between two
 * FBOs), then composited over the sharp full-res image with variable alpha.
 * Downsampling makes the passes cheap AND widens the effective radius; the
 * alpha crossfade plus a progress-scaled sample spacing animates depth without
 * the ghosting a sparse single-pass kernel produces at large radii.
 *
 * Owns GL objects valid only in the EGL context that created them — the
 * renderer must drop its reference on onSurfaceCreated(), like CropOverlay.
 */
class Blur {
    private static final String TAG = Blur.class.getSimpleName();
    private static final int DOWNSCALE = 8;
    // Max sample spacing in low-res texels at full blur. 2.0 keeps the 9-tap
    // kernel dense enough to stay artefact-free while the ⅛-res buffer
    // multiplies the footprint ×8 in screen pixels.
    private static final float MAX_SPREAD_TEXELS = 2.0f;
    // Two H+V rounds ≈ σ×√2 — with ⅛ res gives ~σ 40-60 screen px at peak.
    private static final int ITERATIONS = 2;

    private static final String VERTEX_SHADER = ""
            + "attribute vec2 aPos;"
            + "varying vec2 vUV;"
            + "void main(){"
            + "  vUV = aPos * 0.5 + 0.5;"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);"
            + "}";

    // Classic linear-Gaussian weights (sum = 1.0). uOffset is the directional
    // one-tap step: (spread/width, 0) for the horizontal pass, (0, spread/height)
    // for the vertical pass, (0,0) for the identity composite pass.
    private static final String FRAGMENT_SHADER = ""
            + "precision mediump float;"
            + "uniform sampler2D uTex;"
            + "uniform vec2 uOffset;"
            + "uniform float uAlpha;"
            + "varying vec2 vUV;"
            + "void main(){"
            + "  vec3 c = texture2D(uTex, vUV).rgb * 0.227027;"
            + "  c += (texture2D(uTex, vUV + uOffset).rgb       + texture2D(uTex, vUV - uOffset).rgb)       * 0.1945946;"
            + "  c += (texture2D(uTex, vUV + uOffset * 2.0).rgb + texture2D(uTex, vUV - uOffset * 2.0).rgb) * 0.1216216;"
            + "  c += (texture2D(uTex, vUV + uOffset * 3.0).rgb + texture2D(uTex, vUV - uOffset * 3.0).rgb) * 0.054054;"
            + "  c += (texture2D(uTex, vUV + uOffset * 4.0).rgb + texture2D(uTex, vUV - uOffset * 4.0).rgb) * 0.0162162;"
            + "  gl_FragColor = vec4(c, uAlpha);"
            + "}";

    // Fullscreen quad as a triangle strip: BL, BR, TL, TR
    private static final float[] QUAD = {-1, -1, 1, -1, -1, 1, 1, 1};

    private final int[] fbo = new int[2];
    private final int[] tex = new int[2];
    private int program;
    private int aPos;
    private int uOffset;
    private int uAlpha;
    private FloatBuffer quadBuffer;
    private int fboW, fboH;
    private boolean failed;

    /**
     * (Re)creates GL objects for the given screen size if needed.
     * Returns false if FBO rendering is unavailable — caller should fall back
     * to drawing the sharp image.
     */
    boolean ensure(int screenW, int screenH) {
        if (failed) return false;
        int w = Math.max(1, screenW / DOWNSCALE);
        int h = Math.max(1, screenH / DOWNSCALE);
        if (program != 0 && w == fboW && h == fboH) return true;
        release();

        int vs = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLUtil.createAndLinkProgram(vs, fs, null);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        uOffset = GLES20.glGetUniformLocation(program, "uOffset");
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");
        int uTex = GLES20.glGetUniformLocation(program, "uTex");
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(uTex, 0);
        quadBuffer = GLUtil.asFloatBuffer(QUAD);

        GLES20.glGenFramebuffers(2, fbo, 0);
        GLES20.glGenTextures(2, tex, 0);
        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, tex[i], 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "ensure: framebuffer incomplete, blur disabled");
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                release();
                failed = true;
                return false;
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        fboW = w;
        fboH = h;
        return true;
    }

    /** Binds the low-res scene FBO. Caller draws the wallpaper, then calls blurAndCompose(). */
    void beginScene() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glViewport(0, 0, fboW, fboH);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Blurs the captured scene (strength ∝ t) and composites it over the
     * default framebuffer with alpha = t. t in [0,1]; 0 = sharp, 1 = full blur.
     */
    void blurAndCompose(float t, int screenW, int screenH) {
        float spread = MAX_SPREAD_TEXELS * t;
        // Horizontal: tex[0] → fbo[1], vertical: tex[1] → fbo[0] — ping-pong
        // ends back in tex[0], so iterations just repeat the pair.
        for (int i = 0; i < ITERATIONS; i++) {
            pass(tex[0], fbo[1], spread / fboW, 0f, 1f);
            pass(tex[1], fbo[0], 0f, spread / fboH, 1f);
        }
        // Composite blurred result over the sharp image on screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        drawQuad(tex[0], 0f, 0f, t);
    }

    private void pass(int srcTex, int dstFbo, float offX, float offY, float alpha) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dstFbo);
        GLES20.glViewport(0, 0, fboW, fboH);
        drawQuad(srcTex, offX, offY, alpha);
    }

    private void drawQuad(int srcTex, float offX, float offY, float alpha) {
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uOffset, offX, offY);
        GLES20.glUniform1f(uAlpha, alpha);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
    }

    /** Frees GL objects. Safe to call with a live context only (resize path). */
    void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        if (tex[0] != 0) {
            GLES20.glDeleteTextures(2, tex, 0);
            tex[0] = tex[1] = 0;
        }
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(2, fbo, 0);
            fbo[0] = fbo[1] = 0;
        }
        fboW = fboH = 0;
    }
}
