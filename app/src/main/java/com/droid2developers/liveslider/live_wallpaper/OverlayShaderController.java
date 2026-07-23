package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;
import android.os.SystemClock;

import com.droid2developers.liveslider.utils.Constant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the rain/ripple/snow overlay shaders — the "which one is active",
 *  their lazily-created GL objects, and the generic per-shader param store.
 *  Only ONE of these can be active at a time (Constant.SHADER_NONE is the
 *  only "off" state). All methods are GL-thread only except setActiveShader/
 *  setParam/addTouchRipple, which just flip volatile/concurrent state that
 *  the GL thread picks up on the next onDrawFrame. */
final class OverlayShaderController {
    private RainShader rainShader;
    private RippleShader rippleShader;
    private SnowShader snowShader;
    private volatile String activeShaderId = Constant.SHADER_NONE;

    // Generic param store: key = Constant.ShaderParam.key (e.g. "intensity",
    // "speed"), value = the shader's own float unit (already mapped from the
    // 0-100 SeekBar progress by ShaderSettingsBottomSheet/LiveWallpaperService).
    // Booleans are stored as 0f/1f, same as the ShaderParam.TOGGLE convention.
    private final Map<String, Float> params = new ConcurrentHashMap<>();
    private final long startTime = SystemClock.elapsedRealtime();

    /** True if id differs from the current active shader — caller should mark
     *  a release pending (the old shader's GL objects still need freeing on
     *  the GL thread) before actually switching. */
    boolean willChange(String id) {
        return !id.equals(activeShaderId);
    }

    void setActiveShader(String id) {
        activeShaderId = id;
    }

    String activeShaderId() {
        return activeShaderId;
    }

    boolean isNone() {
        return Constant.SHADER_NONE.equals(activeShaderId);
    }

    void setParam(String key, float value) {
        params.put(key, value);
    }
    private float param(String key, float fallback) {
        Float v = params.get(key);
        return v != null ? v : fallback;
    }
    private boolean paramBool(String key, boolean fallback) {
        Float v = params.get(key);
        return v != null ? v >= 0.5f : fallback;
    }

    /** Ensures the active shader's GL objects + capture FBO exist for this frame.
     *  Returns false if no shader is active or FBO rendering is unavailable. */
    boolean beginFrame(int screenW, int screenH) {
        if (Constant.SHADER_RAIN.equals(activeShaderId)) {
            if (rainShader == null) rainShader = new RainShader();
            rainShader.ensure();
            return rainShader.ensureScene(screenW, screenH);
        } else if (Constant.SHADER_RIPPLE.equals(activeShaderId)) {
            if (rippleShader == null) rippleShader = new RippleShader();
            rippleShader.ensure();
            return rippleShader.ensureScene(screenW, screenH);
        }
        return false;
    }

    /** Binds the active shader's capture FBO — call BEFORE the wallpaper draw
     *  this frame so the capture receives it, then call drawEffect(). */
    void beginScene() {
        if (Constant.SHADER_RAIN.equals(activeShaderId) && rainShader != null) {
            rainShader.beginScene();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        } else if (Constant.SHADER_RIPPLE.equals(activeShaderId) && rippleShader != null) {
            rippleShader.beginScene();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

    /** Draws the active shader, sampling the scene captured by beginScene(). */
    void drawEffect(int screenW, int screenH, float shaderFade) {
        float time = (SystemClock.elapsedRealtime() - startTime) / 1000f;
        if (Constant.SHADER_RAIN.equals(activeShaderId) && rainShader != null) {
            rainShader.draw(screenW, screenH, time,
                    param("speed", 1.0f),
                    param("intensity", 0.5f),
                    param("brightness", 1.0f),
                    paramBool("lightning", false),
                    shaderFade);
        } else if (Constant.SHADER_RIPPLE.equals(activeShaderId) && rippleShader != null) {
            rippleShader.draw(screenW, screenH, time,
                    param("speed", 1.0f),
                    param("cellSize", 10f),
                    param("strength", 1.0f),
                    paramBool("touchOnly", false),
                    paramBool("rainLines", false),
                    param("rainLinesStrength", 0.7f),
                    param("rainLinesSpeed", 1.0f),
                    param("rainLinesAngle", 0.22f),
                    shaderFade);
        }
    }

    /** Draws snow if it's the active shader — self-contained (own low-res FBO,
     *  own composite pass), no wallpaper capture needed. Returns true if it drew
     *  (caller keeps rendering continuously while true, same as rain/ripple). */
    boolean drawSnow(int screenW, int screenH, float shaderFade) {
        if (!Constant.SHADER_SNOW.equals(activeShaderId) || shaderFade <= 0f) return false;
        if (snowShader == null) snowShader = new SnowShader();
        snowShader.ensure();
        if (!snowShader.ensureTarget(screenW, screenH)) return false;

        float time = (SystemClock.elapsedRealtime() - startTime) / 1000f;
        snowShader.draw(screenW, screenH, time,
                param("speed", 1.0f),
                param("density", 1.0f),
                param("flakeSize", 1.0f),
                param("opacity", 0.85f) * shaderFade);
        return true;
    }

    /** Forwards a touch-down position (normalized UV, Y-up) to the active
     *  shader, if it supports touch-driven ripples. Safe to call even when
     *  ripple isn't active or hasn't been GL-initialized yet — becomes a no-op. */
    void addTouch(float u, float v) {
        if (!Constant.SHADER_RIPPLE.equals(activeShaderId) || rippleShader == null) return;
        rippleShader.addTouch(u, v);
    }

    /** Frees every overlay shader that is not the active one (and all of them,
     *  plus every param, when releaseAll). Called from onDrawFrame when a
     *  release is pending. Returns true if anything was actually freed. */
    boolean releaseInactive(boolean releaseAll, boolean powerSaverActive) {
        boolean keepRain   = !releaseAll && Constant.SHADER_RAIN.equals(activeShaderId) && !powerSaverActive;
        boolean keepRipple = !releaseAll && Constant.SHADER_RIPPLE.equals(activeShaderId) && !powerSaverActive;
        boolean keepSnow   = !releaseAll && Constant.SHADER_SNOW.equals(activeShaderId) && !powerSaverActive;

        // Unbind any texture unit first — deleting a still-bound texture is deferred
        // on some drivers, which is one reason a release may not free memory promptly.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        boolean freed = false;
        if (!keepRain && rainShader != null) { rainShader.release(); rainShader = null; freed = true; }
        if (!keepRipple && rippleShader != null) { rippleShader.release(); rippleShader = null; freed = true; }
        if (!keepSnow && snowShader != null) { snowShader.release(); snowShader = null; freed = true; }
        return freed;
    }

    /** Releases every shader unconditionally — used by onSurfaceCreated when
     *  the GL context is being torn down/rebuilt. */
    void releaseAll() {
        if (rainShader != null) { rainShader.release(); rainShader = null; }
        if (rippleShader != null) { rippleShader.release(); rippleShader = null; }
        if (snowShader != null) { snowShader.release(); snowShader = null; }
    }
}
