package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

/**
 * Animated rain-drop ripple distortion, ported from the Shadertoy "Water"
 * shader by Zavie/Ctrl-Alt-Test (used in the "H - Immersion" 64kB intro).
 *
 * Same FBO-capture shape as RainShader/SceneCapture: samples the wallpaper
 * exactly as the camera/crop matrix rendered it, so no separate crop math or
 * UV-convention risk. See RainShader's class doc for why that matters.
 *
 * Performance: the original's iMouse-controlled zoom has no mobile touch
 * equivalent, so it's replaced with a fixed u_cell_size uniform (a "Ripple
 * Size" slider). The 5x5 = 25-cell double loop is already cheap — a handful
 * of hash/sin calls per cell, no octave-style noise stacking like the clouds
 * shader had — so MAX_RADIUS is kept at the original's value (2) with no
 * further cuts needed for mobile.
 *
 * Touch-only mode (u_touch_only): replaces the always-on procedural 25-cell
 * grid with ripples evaluated ONLY at up to TOUCH_SLOTS recent touch points
 * (a small ring buffer of position+age, fed by setTouch()). This is cheaper
 * than the ambient mode, not more expensive — at most TOUCH_SLOTS ripple
 * evaluations per pixel instead of 25.
 */
class RippleShader {
    private static final int MAX_RADIUS = 2;
    private static final int TOUCH_SLOTS = 4;
    // How long a touch ripple stays visible before its slot is reusable.
    private static final float TOUCH_LIFETIME_SECONDS = 2.0f;

    private static final String VERTEX_SHADER = ""
            + "attribute vec2 aPos;"
            + "void main(){"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);"
            + "}";

    private static final String FRAGMENT_SHADER = ""
            + "precision highp float;"
            + "uniform sampler2D u_tex0;"
            + "uniform vec2 u_resolution;"
            + "uniform float u_time;"
            + "uniform float u_speed;"
            + "uniform float u_cell_size;"
            + "uniform float u_strength;"
            + "uniform bool u_touch_only;"
            + "uniform vec2 u_touch_pos[" + TOUCH_SLOTS + "];"
            + "uniform float u_touch_age[" + TOUCH_SLOTS + "];"
            + "uniform bool u_rain_lines;"
            + "uniform float u_opacity;"
            + "uniform float u_rain_lines_strength;"
            + "uniform float u_rain_lines_speed;"
            + "uniform float u_rain_lines_angle;"

            + "vec3 hash33(vec2 p){"
            + "  vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973));"
            + "  p3 += dot(p3, p3.yzx + 19.19);"
            + "  return fract((p3.xxy + p3.yzz) * p3.zyx);"
            + "}"
            + "vec2 hash22(vec2 p){"
            + "  vec3 h = hash33(p);"
            + "  return h.xy;"
            + "}"
            + "float hash12(vec2 p){"
            + "  return hash33(p).x;"
            + "}"

            // Angled falling-rain streaks — cheap screen-space overlay, NOT part of
            // the distortion field (rippleWave/circles above): streaks only add
            // brightness, they never bend the sampled UV. Shears space along a
            // diagonal (SHEAR), tiles it into thin vertical bands, and gives each
            // band a length/brightness from a single hash — one hash12 call and a
            // few smoothsteps per pixel, far cheaper than the ripple grid itself.
            + "float rainStreaks(vec2 uv, float t, float shear){"
            + "  const float BANDS = 60.0;"     // streak columns across the sheared space
            + "  vec2 s = vec2(uv.x + uv.y * shear, uv.y);"
            // gl_FragCoord/UV is Y-up (0 at bottom, 1 at top) — rain falling
            // DOWN the screen means sampling further UP the pattern as time
            // increases, i.e. s.y must INCREASE, not decrease.
            + "  s.y += t;"                       // falling motion
            + "  s.x *= BANDS;"
            + "  float col = floor(s.x);"
            + "  float within = fract(s.x) - 0.5;"
            + "  float seed = hash12(vec2(col, floor(s.y * 3.0)));"
            + "  float streakY = fract(s.y * 3.0 + seed);"
            + "  float len = mix(0.15, 0.45, fract(seed * 7.13));"
            + "  float body = smoothstep(0.0, len, streakY) * smoothstep(len + 0.08, len, streakY);"
            + "  float thinness = smoothstep(0.09, 0.0, abs(within));"
            + "  float visible = step(0.5, fract(seed * 3.71));" // ~half the bands lit at once
            + "  return body * thinness * visible;"
            + "}"

            // One ripple's contribution at distance-from-center d, ring progress rt
            // (0 = just spawned, 1 = fully expanded/faded) — same ring-wave shape
            // used by both the ambient grid and touch-driven ripples below.
            + "vec2 rippleWave(vec2 v, float d, float rt){"
            + "  float h = 1e-3;"
            + "  float d1 = d - h;"
            + "  float d2 = d + h;"
            + "  float p1 = sin(31.0 * d1) * smoothstep(-0.6, -0.3, d1) * smoothstep(0.0, -0.3, d1);"
            + "  float p2 = sin(31.0 * d2) * smoothstep(-0.6, -0.3, d2) * smoothstep(0.0, -0.3, d2);"
            + "  return 0.5 * normalize(v + 1e-6) * ((p2 - p1) / (2.0 * h) * (1.0 - rt) * (1.0 - rt));"
            + "}"

            + "void main(){"
            + "  vec2 uv = gl_FragCoord.xy / u_resolution.y * u_cell_size;"
            + "  float t = u_time * u_speed;"

            + "  vec2 circles = vec2(0.0);"
            + "  if (u_touch_only){"
            // Touch mode: at most TOUCH_SLOTS ripple evaluations per pixel —
            // cheaper than the (2*MAX_RADIUS+1)^2 ambient grid below.
            + "    for (int k = 0; k < " + TOUCH_SLOTS + "; k++){"
            + "      float age = u_touch_age[k];"
            + "      if (age < 0.0 || age > " + TOUCH_LIFETIME_SECONDS + ") continue;"
            + "      float rt = age / " + TOUCH_LIFETIME_SECONDS + ";"
            + "      vec2 p = u_touch_pos[k] * u_cell_size;"
            + "      vec2 v = p - uv;"
            + "      float d = length(v) - (float(" + MAX_RADIUS + ") + 1.0) * rt;"
            + "      circles += rippleWave(v, d, rt);"
            + "    }"
            + "  } else {"
            + "    vec2 p0 = floor(uv);"
            + "    for (int j = -" + MAX_RADIUS + "; j <= " + MAX_RADIUS + "; j++){"
            + "      for (int i = -" + MAX_RADIUS + "; i <= " + MAX_RADIUS + "; i++){"
            + "        vec2 pi = p0 + vec2(float(i), float(j));"
            + "        vec2 p = pi + hash22(pi);"
            + "        float rt = fract(0.3 * t + hash12(pi));"
            + "        vec2 v = p - uv;"
            + "        float d = length(v) - (float(" + MAX_RADIUS + ") + 1.0) * rt;"
            + "        circles += rippleWave(v, d, rt);"
            + "      }"
            + "    }"
            + "    circles /= float((" + MAX_RADIUS + "*2+1)*(" + MAX_RADIUS + "*2+1));"
            + "  }"
            + "  circles *= u_strength;"

            + "  vec3 n = vec3(circles, sqrt(max(0.0, 1.0 - dot(circles, circles))));"
            + "  vec2 sampleUV = gl_FragCoord.xy / u_resolution.xy - 0.03 * n.xy;"
            + "  vec3 color = texture2D(u_tex0, sampleUV).rgb"
            + "      + 3.0 * pow(clamp(dot(n, normalize(vec3(1.0, 0.7, 0.5))), 0.0, 1.0), 6.0);"

            + "  if (u_rain_lines){"
            // Screen-space UV (aspect-corrected, independent of u_cell_size) so
            // streak angle/spacing stays consistent regardless of ripple size.
            + "    vec2 streakUV = gl_FragCoord.xy / u_resolution.y;"
            + "    float streak = rainStreaks(streakUV, u_time * u_rain_lines_speed, u_rain_lines_angle);"
            // Blend toward the streak tint rather than adding to it — additive
            // brightening on a bright/white base clips to solid white; mixing
            // reads as a translucent line over the photo instead, and strength
            // now doubles as an opacity control (0 = invisible, 1 = fully opaque).
            + "    float opacity = clamp(streak * u_rain_lines_strength, 0.0, 1.0);"
            + "    color = mix(color, vec3(0.85, 0.9, 1.0), opacity * 0.6);"
            + "  }"

            // u_opacity < 1 only while the renderer fades the effect around a
            // wallpaper transition (plain wallpaper is drawn underneath then).
            + "  gl_FragColor = vec4(color, u_opacity);"
            + "}";

    // Fullscreen quad as a triangle strip: BL, BR, TL, TR
    private static final float[] QUAD = {-1, -1, 1, -1, -1, 1, 1, 1};

    private int program;
    private int aPos;
    private int uTex0, uResolution, uTime, uSpeed, uCellSize, uStrength,
            uTouchOnly, uTouchPos, uTouchAge, uRainLines, uRainLinesStrength,
            uRainLinesSpeed, uRainLinesAngle, uOpacity;
    private FloatBuffer quadBuffer;

    private final SceneCapture scene = new SceneCapture();

    // Touch ripple ring buffer — one slot per recent touch-down, in NORMALIZED
    // screen UV (0..1, origin bottom-left to match gl_FragCoord/u_resolution
    // convention already used elsewhere in this shader). spawnTime uses the same
    // clock as the `time` passed into draw() (elapsed seconds since effect start)
    // so age = time - spawnTime needs no extra clock plumbing.
    private final float[] touchX = new float[TOUCH_SLOTS];
    private final float[] touchY = new float[TOUCH_SLOTS];
    private final float[] touchSpawnTime = initSpawnTimes();
    private int nextTouchSlot = 0;

    private static float[] initSpawnTimes() {
        float[] t = new float[TOUCH_SLOTS];
        java.util.Arrays.fill(t, -1f);
        return t;
    }

    /** Records a new touch-driven ripple origin, in normalized UV (0..1, Y-up).
     *  Overwrites the oldest slot once all TOUCH_SLOTS are in use — no unbounded
     *  growth, no allocation. Safe to call from any thread (renderer forwards
     *  from the service's touch-event thread). */
    synchronized void addTouch(float u, float v) {
        touchX[nextTouchSlot] = u;
        touchY[nextTouchSlot] = v;
        touchSpawnTime[nextTouchSlot] = Float.NaN; // set to actual time on next draw()
        nextTouchSlot = (nextTouchSlot + 1) % TOUCH_SLOTS;
    }

    /** Compiles the program in the CURRENT EGL context. Call again after context loss. */
    void ensure() {
        if (program != 0) return;
        int vs = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLUtil.createAndLinkProgram(vs, fs, null);

        aPos = GLES20.glGetAttribLocation(program, "aPos");
        uTex0 = GLES20.glGetUniformLocation(program, "u_tex0");
        uResolution = GLES20.glGetUniformLocation(program, "u_resolution");
        uTime = GLES20.glGetUniformLocation(program, "u_time");
        uSpeed = GLES20.glGetUniformLocation(program, "u_speed");
        uCellSize = GLES20.glGetUniformLocation(program, "u_cell_size");
        uStrength = GLES20.glGetUniformLocation(program, "u_strength");
        uTouchOnly = GLES20.glGetUniformLocation(program, "u_touch_only");
        uTouchPos = GLES20.glGetUniformLocation(program, "u_touch_pos");
        uTouchAge = GLES20.glGetUniformLocation(program, "u_touch_age");
        uRainLines = GLES20.glGetUniformLocation(program, "u_rain_lines");
        uRainLinesStrength = GLES20.glGetUniformLocation(program, "u_rain_lines_strength");
        uRainLinesSpeed = GLES20.glGetUniformLocation(program, "u_rain_lines_speed");
        uRainLinesAngle = GLES20.glGetUniformLocation(program, "u_rain_lines_angle");
        uOpacity = GLES20.glGetUniformLocation(program, "u_opacity");

        quadBuffer = GLUtil.asFloatBuffer(QUAD);
    }

    /** (Re)creates the capture FBO for the given screen size if needed. */
    boolean ensureScene(int screenW, int screenH) {
        return scene.ensure(screenW, screenH);
    }

    /** Binds the capture FBO. Caller draws the wallpaper (with its normal camera
     *  matrix) into it, then calls draw() to composite the ripple effect on top. */
    void beginScene() {
        scene.begin();
    }

    private final float[] touchPosUpload = new float[TOUCH_SLOTS * 2];
    private final float[] touchAgeUpload = new float[TOUCH_SLOTS];

    /**
     * Draws the ripple effect, sampling the scene captured by beginScene(), into
     * the current (default) framebuffer at screenW x screenH. touchOnly gates
     * whether the shader uses the ambient procedural grid or only recent touches
     * (see class doc) — touches recorded via addTouch() are tracked either way,
     * so switching the toggle mid-frame doesn't lose in-flight ripples.
     */
    void draw(int screenW, int screenH, float time, float speed, float cellSize,
              float strength, boolean touchOnly,
              boolean rainLines, float rainLinesStrength, float rainLinesSpeed, float rainLinesAngle,
              float opacity) {
        if (program == 0) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scene.textureHandle());
        GLES20.glUniform1i(uTex0, 0);
        GLES20.glUniform2f(uResolution, screenW, screenH);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform1f(uSpeed, speed);
        GLES20.glUniform1f(uCellSize, cellSize);
        GLES20.glUniform1f(uStrength, strength);
        GLES20.glUniform1i(uTouchOnly, touchOnly ? 1 : 0);
        GLES20.glUniform1i(uRainLines, rainLines ? 1 : 0);
        GLES20.glUniform1f(uRainLinesStrength, rainLinesStrength);
        GLES20.glUniform1f(uRainLinesSpeed, rainLinesSpeed);
        GLES20.glUniform1f(uRainLinesAngle, rainLinesAngle);
        GLES20.glUniform1f(uOpacity, opacity);
        uploadTouchUniforms(time);

        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
    }

    /** Resolves any newly-added touches' spawn time against the current clock,
     *  computes each slot's age, and uploads the position/age arrays. Expired
     *  slots (age beyond TOUCH_LIFETIME_SECONDS) upload a negative age so the
     *  shader's `continue` skips them for free. */
    private synchronized void uploadTouchUniforms(float time) {
        for (int i = 0; i < TOUCH_SLOTS; i++) {
            if (Float.isNaN(touchSpawnTime[i])) {
                touchSpawnTime[i] = time; // first draw() after addTouch() stamps it
            }
            float age = touchSpawnTime[i] < 0f ? -1f : time - touchSpawnTime[i];
            touchAgeUpload[i] = age > TOUCH_LIFETIME_SECONDS ? -1f : age;
            touchPosUpload[i * 2] = touchX[i];
            touchPosUpload[i * 2 + 1] = touchY[i];
        }
        GLES20.glUniform2fv(uTouchPos, TOUCH_SLOTS, touchPosUpload, 0);
        GLES20.glUniform1fv(uTouchAge, TOUCH_SLOTS, touchAgeUpload, 0);
    }

    /** Frees GL objects. Safe to call with a live context only. */
    void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        scene.release();
    }
}
