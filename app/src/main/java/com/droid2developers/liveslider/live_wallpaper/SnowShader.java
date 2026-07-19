package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;
import android.util.Log;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

/**
 * Falling-snow overlay, ported from a well-known WebGL "7 layers of snow"
 * shader (per-layer: hashed cell position via sin()/fract(), distance to a
 * jittered point, smoothstepped into a soft dot). Kept intentionally close to
 * the original — it's a proven, cheap pattern; the earlier from-scratch
 * attempts here (Worley noise, then a custom grid-circle version) both had
 * real bugs, this one doesn't reinvent the math.
 *
 * Performance: 7 snow() calls, each ~1 sin + 1 cos + 1 fract + 1 length — all
 * cheap scalar ops, no loops, no noise-octave stacking. Cheaper than the
 * Worley-based first attempt and comparable to the from-scratch circles
 * version, so still rendered into a half-resolution FBO for margin.
 *
 * Two adaptations from the original WebGL source:
 *   1. Original outputs vec4(vec3(c), 1.0) — fully OPAQUE, meant to paint
 *      snow as brightness added over a black canvas. Ported literally that
 *      would fully obscure the wallpaper (the same mistake the clouds effect
 *      made). Here the same c value drives ALPHA instead, so gaps between
 *      flakes show the photo through, and flake color is a fixed white.
 *   2. The background sky gradient term (`c = smoothstep(...)` before the
 *      snow() calls) is dropped — that's meant to draw a fake sky backdrop
 *      over nothing; for an overlay on a real photo it would just tint
 *      everything unless made near-invisible, so it's cut entirely rather
 *      than tuned down. Only the snow() layers remain.
 */
class SnowShader {
    private static final String TAG = SnowShader.class.getSimpleName();
    private static final int DOWNSCALE = 2;

    private static final String VERTEX_SHADER = ""
            + "attribute vec2 aPos;"
            + "void main(){"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);"
            + "}";

    private static final String FRAGMENT_SHADER = ""
            + "precision highp float;"
            + "uniform vec2 u_resolution;"
            + "uniform float u_time;"
            + "uniform float u_speed;"
            + "uniform float u_density;"
            + "uniform float u_flake_size;"

            // One layer of falling snow at the given scale (smaller scale =
            // bigger, slower, nearer flakes). Faithful to the original: hashed
            // cell offset via sin(), falling motion folded into uv before
            // tiling by scale, single nearest-point distance test per cell.
            + "float snow(vec2 uv, float scale, float t){"
            + "  float w = smoothstep(1.0, 0.0, -uv.y * (scale / 10.0));"
            + "  if (w < 0.1) return 0.0;"

            // t (and c, derived from it) grow without bound for a continuously-
            // rendered wallpaper. Mobile GPU sin()/cos() are commonly fast
            // polynomial approximations only accurate over a limited input
            // range — fed a huge, ever-growing value they degrade from
            // "looks random" into a smooth near-linear drift, which is what
            // turned the per-cell jitter into a persistent streak instead of
            // scattered falling dots. Wrap t into a large-but-bounded period
            // before it ever reaches a trig call; mod() keeps the same visual
            // motion (a repeating cycle few would notice) without the drift.
            + "  float wt = mod(t, 1000.0);"
            + "  float c = wt / scale;"
            + "  uv.y += c;"
            + "  uv.x -= c;"
            + "  uv.y += c * 2.0;"
            + "  uv.x += cos(uv.y + wt * 0.5) / scale;"
            + "  uv *= scale / u_flake_size;"

            + "  vec2 s = floor(uv);"
            + "  vec2 f = fract(uv);"

            + "  vec2 p = 0.5 + 0.35 * sin(11.0 * fract(sin((s + scale) * mat2(7.0, 3.0, 6.0, 5.0)) * 5.0)) - f;"
            + "  float d = length(p);"
            + "  float k = smoothstep(0.0, min(d, 3.0), sin(f.x + f.y) * 0.01);"
            + "  return k * w;"
            + "}"

            + "void main(){"
            + "  float size = mix(min(u_resolution.x, u_resolution.y), max(u_resolution.x, u_resolution.y), 0.5);"
            + "  vec2 uv = (gl_FragCoord.xy * 2.0 - u_resolution.xy) / size;"
            + "  float t = u_time * u_speed;"

            // 7 depth layers, same weighting as the source — small scale =
            // near/big/slow flakes, large scale = far/small/fast.
            + "  float c = 0.0;"
            + "  c += snow(uv, 30.0, t) * 0.3;"
            + "  c += snow(uv, 20.0, t) * 0.5;"
            + "  c += snow(uv, 15.0, t) * 0.8;"
            + "  c += snow(uv, 10.0, t);"
            + "  c += snow(uv, 8.0, t);"
            + "  c += snow(uv, 6.0, t);"
            + "  c += snow(uv, 5.0, t);"

            + "  float coverage = clamp(c * u_density, 0.0, 1.0);"
            + "  gl_FragColor = vec4(vec3(1.0), coverage);"
            + "}";

    // Fullscreen quad as a triangle strip: BL, BR, TL, TR
    private static final float[] QUAD = {-1, -1, 1, -1, -1, 1, 1, 1};

    private int program;
    private int aPos;
    private int uResolution, uTime, uSpeed, uDensity, uFlakeSize;
    private FloatBuffer quadBuffer;

    private final int[] fbo = new int[1];
    private final int[] tex = new int[1];
    private int fboW, fboH;
    private boolean failed;

    private int compositeProgram;
    private int compAPos, compUTex, compUOpacity;

    /** Compiles the program in the CURRENT EGL context. Call again after context loss. */
    void ensure() {
        if (program != 0) return;
        int vs = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLUtil.createAndLinkProgram(vs, fs, null);

        aPos = GLES20.glGetAttribLocation(program, "aPos");
        uResolution = GLES20.glGetUniformLocation(program, "u_resolution");
        uTime = GLES20.glGetUniformLocation(program, "u_time");
        uSpeed = GLES20.glGetUniformLocation(program, "u_speed");
        uDensity = GLES20.glGetUniformLocation(program, "u_density");
        uFlakeSize = GLES20.glGetUniformLocation(program, "u_flake_size");

        quadBuffer = GLUtil.asFloatBuffer(QUAD);
    }

    /** (Re)creates the low-res render target for the given screen size if needed. */
    boolean ensureTarget(int screenW, int screenH) {
        if (failed) return false;
        int w = Math.max(1, screenW / DOWNSCALE);
        int h = Math.max(1, screenH / DOWNSCALE);
        if (fbo[0] != 0 && w == fboW && h == fboH) return true;
        releaseTarget();

        GLES20.glGenFramebuffers(1, fbo, 0);
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, tex[0], 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "ensureTarget: framebuffer incomplete, snow effect disabled");
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            releaseTarget();
            failed = true;
            return false;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        fboW = w;
        fboH = h;
        return true;
    }

    /**
     * Renders snow into the low-res FBO, then upscale-composites it over the
     * current (default) framebuffer at screenW x screenH via GL_BLEND (already
     * enabled once in LiveWallpaperRenderer.onSurfaceCreated).
     */
    void draw(int screenW, int screenH, float time, float speed, float density,
              float flakeSize, float opacity) {
        if (program == 0) return;

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glViewport(0, 0, fboW, fboH);
        // Blend is enabled globally (renderer.onSurfaceCreated) with additive
        // alpha; left on here it accumulates each frame's flakes over the
        // last in this persistent texture, turning falling dots into solid
        // white trails. Overwrite instead — the quad covers every pixel.
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, fboW, fboH);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform1f(uSpeed, speed);
        GLES20.glUniform1f(uDensity, density);
        GLES20.glUniform1f(uFlakeSize, flakeSize);
        drawQuad();
        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        compositeTex(opacity);
    }

    private void ensureComposite() {
        if (compositeProgram != 0) return;
        String vs = ""
                + "attribute vec2 aPos;"
                + "varying vec2 vUV;"
                + "void main(){"
                + "  vUV = aPos * 0.5 + 0.5;"
                + "  gl_Position = vec4(aPos, 0.0, 1.0);"
                + "}";
        String fs = ""
                + "precision mediump float;"
                + "uniform sampler2D uTex;"
                + "uniform float uOpacity;"
                + "varying vec2 vUV;"
                + "void main(){"
                + "  vec4 c = texture2D(uTex, vUV);"
                + "  gl_FragColor = vec4(c.rgb, c.a * uOpacity);"
                + "}";
        int vsH = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int fsH = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        compositeProgram = GLUtil.createAndLinkProgram(vsH, fsH, null);
        compAPos = GLES20.glGetAttribLocation(compositeProgram, "aPos");
        compUTex = GLES20.glGetUniformLocation(compositeProgram, "uTex");
        compUOpacity = GLES20.glGetUniformLocation(compositeProgram, "uOpacity");
    }

    private void compositeTex(float opacity) {
        ensureComposite();
        GLES20.glUseProgram(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glUniform1i(compUTex, 0);
        GLES20.glUniform1f(compUOpacity, opacity);
        GLES20.glEnableVertexAttribArray(compAPos);
        GLES20.glVertexAttribPointer(compAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(compAPos);
    }

    private void drawQuad() {
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
    }

    private void releaseTarget() {
        if (tex[0] != 0) {
            GLES20.glDeleteTextures(1, tex, 0);
            tex[0] = 0;
        }
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(1, fbo, 0);
            fbo[0] = 0;
        }
        fboW = fboH = 0;
    }

    /** Frees GL objects. Safe to call with a live context only. */
    void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        if (compositeProgram != 0) {
            GLES20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
        releaseTarget();
    }
}
