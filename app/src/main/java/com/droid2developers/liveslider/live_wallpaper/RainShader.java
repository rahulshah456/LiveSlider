package com.droid2developers.liveslider.live_wallpaper;

import android.opengl.GLES20;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

/**
 * Animated rain-on-glass overlay, ported from a WebGL fullscreen fragment shader.
 *
 * u_tex0 is sampled with plain [0,1] UVs matched 1:1 to gl_FragCoord — it must be
 * the ALREADY-CROPPED/PANNED scene (i.e. what the camera currently shows), not the
 * raw wallpaper bitmap. Wallpaper's own crop/pan/bias lives entirely in its MVP
 * matrix, which this shader has no matrix uniform for — sampling the raw bitmap
 * directly would ignore that crop and use a mismatched vertical convention (texture
 * space vs. framebuffer space), which is what caused the earlier upside-down/
 * uncropped bug. Caller must render the wallpaper into beginScene()'s FBO first via
 * LiveWallpaperRenderer's normal Wallpaper.draw() call, then draw() this shader —
 * same two-step shape as Blur.beginScene()/blurAndCompose().
 *
 * GLSL ES 1.00 (GLES2) port notes vs. the original WebGL source:
 *  - u_blur_iterations already used a constant-bound for-loop with a runtime
 *    `if (m > n) break;` guard in the source — that pattern is legal GLES2 as-is,
 *    a true uniform loop bound (`m < u_blur_iterations`) would not be.
 *  - CHEAP_NORMALS (dFdx/dFdy, GL_OES_standard_derivatives) dropped — always uses
 *    the manual central-difference normals the source already had as a fallback,
 *    so no extension check is needed and the shader is portable to any GLES2 device.
 */
class RainShader {
    private static final String VERTEX_SHADER = ""
            + "attribute vec2 aPos;"
            + "void main(){"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);"
            + "}";

    private static final String FRAGMENT_SHADER = ""
            + "precision highp float;"
            + "uniform sampler2D u_tex0;"
            + "uniform vec2 u_tex0_resolution;"
            + "uniform float u_time;"
            + "uniform vec2 u_resolution;"
            + "uniform float u_speed;"
            + "uniform float u_intensity;"
            + "uniform float u_normal;"
            + "uniform float u_brightness;"
            + "uniform float u_blur_intensity;"
            + "uniform float u_zoom;"
            + "uniform float u_opacity;"
            + "uniform int u_blur_iterations;"
            + "uniform bool u_panning;"
            + "uniform bool u_post_processing;"
            + "uniform bool u_lightning;"
            + "uniform bool u_texture_fill;"

            + "vec3 N13(float p){"
            + "  vec3 p3 = fract(vec3(p) * vec3(.1031, .11369, .13787));"
            + "  p3 += dot(p3, p3.yzx + 19.19);"
            + "  return fract(vec3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));"
            + "}"
            + "float N(float t){"
            + "  return fract(sin(t * 12345.564) * 7658.76);"
            + "}"
            + "float Saw(float b, float t){"
            + "  return smoothstep(0., b, t) * smoothstep(1., b, t);"
            + "}"

            + "vec2 DropLayer2(vec2 uv, float t){"
            + "  vec2 UV = uv;"
            + "  uv.y += t * 0.75;"
            + "  vec2 a = vec2(6., 1.);"
            + "  vec2 grid = a * 2.;"
            + "  vec2 id = floor(uv * grid);"
            + "  float colShift = N(id.x);"
            + "  uv.y += colShift;"
            + "  id = floor(uv * grid);"
            + "  vec3 n = N13(id.x * 35.2 + id.y * 2376.1);"
            + "  vec2 st = fract(uv * grid) - vec2(.5, 0);"
            + "  float x = n.x - .5;"
            + "  float y = UV.y * 20.;"
            + "  float wiggle = sin(y + sin(y));"
            + "  x += wiggle * (.5 - abs(x)) * (n.z - .5);"
            + "  x *= .7;"
            + "  float ti = fract(t + n.z);"
            + "  y = (Saw(.85, ti) - .5) * .9 + .5;"
            + "  vec2 p = vec2(x, y);"
            + "  float d = length((st - p) * a.yx);"
            + "  float mainDrop = smoothstep(.4, .0, d);"
            + "  float r = sqrt(smoothstep(1., y, st.y));"
            + "  float cd = abs(st.x - x);"
            + "  float trail = smoothstep(.23 * r, .15 * r * r, cd);"
            + "  float trailFront = smoothstep(-.02, .02, st.y - y);"
            + "  trail *= trailFront * r * r;"
            + "  y = UV.y;"
            + "  float trail2 = smoothstep(.2 * r, .0, cd);"
            + "  float droplets = max(0., (sin(y * (1. - y) * 120.) - st.y)) * trail2 * trailFront * n.z;"
            + "  y = fract(y * 10.) + (st.y - .5);"
            + "  float dd = length(st - vec2(x, y));"
            + "  droplets = smoothstep(.3, 0., dd);"
            + "  float m = mainDrop + droplets * r * trailFront;"
            + "  return vec2(m, trail);"
            + "}"

            + "float StaticDrops(vec2 uv, float t){"
            + "  uv *= 40.;"
            + "  vec2 id = floor(uv);"
            + "  uv = fract(uv) - .5;"
            + "  vec3 n = N13(id.x * 107.45 + id.y * 3543.654);"
            + "  vec2 p = (n.xy - .5) * .7;"
            + "  float d = length(uv - p);"
            + "  float fade = Saw(.025, fract(t + n.z));"
            + "  float c = smoothstep(.3, 0., d) * fract(n.z * 10.) * fade;"
            + "  return c;"
            + "}"

            + "vec2 Drops(vec2 uv, float t, float l0, float l1, float l2){"
            + "  float s = StaticDrops(uv, t) * l0;"
            + "  vec2 m1 = DropLayer2(uv, t) * l1;"
            + "  vec2 m2 = DropLayer2(uv * 1.85, t) * l2;"
            + "  float c = s + m1.x + m2.x;"
            + "  c = smoothstep(.3, 1., c);"
            + "  return vec2(c, max(m1.y * l0, m2.y * l1));"
            + "}"

            + "float N21(vec2 p){"
            + "  p = fract(p * vec2(123.34, 345.45));"
            + "  p += dot(p, p + 34.345);"
            + "  return fract(p.x * p.y);"
            + "}"

            + "void main(){"
            + "  vec2 uv = (gl_FragCoord.xy - .5 * u_resolution.xy) / u_resolution.y;"
            + "  vec2 UV = gl_FragCoord.xy / u_resolution.xy;"
            + "  float T = u_time;"

            + "  if (u_texture_fill){"
            + "    float screenAspect = u_resolution.x / u_resolution.y;"
            + "    float textureAspect = u_tex0_resolution.x / u_tex0_resolution.y;"
            + "    float scaleX = 1., scaleY = 1.;"
            + "    if (textureAspect > screenAspect) scaleX = screenAspect / textureAspect;"
            + "    else scaleY = textureAspect / screenAspect;"
            + "    UV = vec2(scaleX, scaleY) * (UV - 0.5) + 0.5;"
            + "  }"

            + "  float t = T * .2 * u_speed;"
            + "  float rainAmount = u_intensity;"
            + "  float zoom = u_panning ? -cos(T * .2) : 0.;"
            + "  uv *= (.7 + zoom * .3) * u_zoom;"

            + "  float staticDrops = smoothstep(-.5, 1., rainAmount) * 2.;"
            + "  float layer1 = smoothstep(.25, .75, rainAmount);"
            + "  float layer2 = smoothstep(.0, .5, rainAmount);"

            + "  vec2 c = Drops(uv, t, staticDrops, layer1, layer2);"
            + "  vec2 e = vec2(.001, 0.) * u_normal;"
            + "  float cx = Drops(uv + e, t, staticDrops, layer1, layer2).x;"
            + "  float cy = Drops(uv + e.yx, t, staticDrops, layer1, layer2).x;"
            + "  vec2 n = vec2(cx - c.x, cy - c.x);"

            + "  vec3 col = texture2D(u_tex0, UV + n).rgb;"
            + "  vec2 texCoord = vec2(UV.x + n.x, UV.y + n.y);"

            + "  if (u_blur_iterations != 1){"
            + "    float blur = u_blur_intensity * 0.01;"
            + "    float a = N21(gl_FragCoord.xy) * 6.2831;"
            + "    for (int m = 0; m < 64; m++){"
            + "      if (m > u_blur_iterations) break;"
            + "      vec2 offs = vec2(sin(a), cos(a)) * blur;"
            + "      float d = fract(sin((float(m) + 1.) * 546.) * 5424.);"
            + "      d = sqrt(d);"
            + "      offs *= d;"
            + "      col += texture2D(u_tex0, texCoord + offs).xyz;"
            + "      a++;"
            + "    }"
            + "    col /= float(u_blur_iterations);"
            + "  }"

            + "  t = (T + 3.) * .5;"
            + "  if (u_post_processing){"
            + "    col *= mix(vec3(1.), vec3(.8, .9, 1.3), 1.);"
            + "  }"
            + "  float fade = smoothstep(0., 10., T);"

            + "  if (u_lightning){"
            + "    float lightning = sin(t * sin(t * 10.));"
            + "    lightning *= pow(max(0., sin(t + sin(t))), 10.);"
            + "    col *= 1. + lightning * fade * mix(1., .1, 0.);"
            + "  }"
            + "  vec2 vc = UV - .5;"
            + "  col *= 1. - dot(vc, vc);"

            // u_opacity < 1 only while the renderer fades the effect around a
            // wallpaper transition (plain wallpaper is drawn underneath then).
            + "  gl_FragColor = vec4(col * u_brightness, u_opacity);"
            + "}";

    // Fullscreen quad as a triangle strip: BL, BR, TL, TR
    private static final float[] QUAD = {-1, -1, 1, -1, -1, 1, 1, 1};

    private int program;
    private int aPos;
    private int uTex0, uTex0Resolution, uTime, uResolution, uSpeed, uIntensity,
            uNormal, uBrightness, uBlurIntensity, uZoom, uBlurIterations,
            uPanning, uPostProcessing, uLightning, uTextureFill, uOpacity;
    private FloatBuffer quadBuffer;

    // Holds the wallpaper exactly as the camera/crop matrix rendered it, at full
    // screen resolution, so u_tex0 needs no matrix of its own.
    private final SceneCapture scene = new SceneCapture();

    /** Compiles the program in the CURRENT EGL context. Call again after context loss. */
    void ensure() {
        if (program != 0) return;
        int vs = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLUtil.createAndLinkProgram(vs, fs, null);

        aPos = GLES20.glGetAttribLocation(program, "aPos");
        uTex0 = GLES20.glGetUniformLocation(program, "u_tex0");
        uTex0Resolution = GLES20.glGetUniformLocation(program, "u_tex0_resolution");
        uTime = GLES20.glGetUniformLocation(program, "u_time");
        uResolution = GLES20.glGetUniformLocation(program, "u_resolution");
        uSpeed = GLES20.glGetUniformLocation(program, "u_speed");
        uIntensity = GLES20.glGetUniformLocation(program, "u_intensity");
        uNormal = GLES20.glGetUniformLocation(program, "u_normal");
        uBrightness = GLES20.glGetUniformLocation(program, "u_brightness");
        uBlurIntensity = GLES20.glGetUniformLocation(program, "u_blur_intensity");
        uZoom = GLES20.glGetUniformLocation(program, "u_zoom");
        uBlurIterations = GLES20.glGetUniformLocation(program, "u_blur_iterations");
        uPanning = GLES20.glGetUniformLocation(program, "u_panning");
        uPostProcessing = GLES20.glGetUniformLocation(program, "u_post_processing");
        uLightning = GLES20.glGetUniformLocation(program, "u_lightning");
        uTextureFill = GLES20.glGetUniformLocation(program, "u_texture_fill");
        uOpacity = GLES20.glGetUniformLocation(program, "u_opacity");

        quadBuffer = GLUtil.asFloatBuffer(QUAD);
    }

    /**
     * (Re)creates the capture FBO for the given screen size if needed.
     * Returns false if FBO rendering is unavailable — caller should skip the effect.
     */
    boolean ensureScene(int screenW, int screenH) {
        return scene.ensure(screenW, screenH);
    }

    /** Binds the capture FBO. Caller draws the wallpaper (with its normal camera
     *  matrix) into it, then calls draw() to composite the rain effect on top. */
    void beginScene() {
        scene.begin();
    }

    /**
     * Draws the rain effect, sampling the scene captured by beginScene(), into the
     * current (default) framebuffer at screenW x screenH.
     */
    void draw(int screenW, int screenH, float time,
              float speed, float intensity, float brightness, boolean lightning,
              float opacity) {
        if (program == 0) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scene.textureHandle());
        GLES20.glUniform1i(uTex0, 0);
        // Scene texture is already screen-sized and already cropped/panned — UVs
        // map 1:1 to gl_FragCoord, so texture_fill's aspect-correction must be a
        // no-op (resolution == screen resolution).
        GLES20.glUniform2f(uTex0Resolution, screenW, screenH);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform2f(uResolution, screenW, screenH);
        GLES20.glUniform1f(uSpeed, speed);
        GLES20.glUniform1f(uIntensity, intensity);
        GLES20.glUniform1f(uNormal, 1.0f);
        GLES20.glUniform1f(uBrightness, brightness);
        GLES20.glUniform1f(uBlurIntensity, 0f);
        GLES20.glUniform1f(uZoom, 1.0f);
        GLES20.glUniform1i(uBlurIterations, 1); // 1 == blur pass skipped, see shader
        GLES20.glUniform1i(uPanning, 0);
        GLES20.glUniform1i(uPostProcessing, 0);
        GLES20.glUniform1i(uLightning, lightning ? 1 : 0);
        GLES20.glUniform1i(uTextureFill, 0);
        GLES20.glUniform1f(uOpacity, opacity);

        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
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
