/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.droid2developers.liveslider.live_wallpaper;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.opengl.GLES20;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

class Wallpaper {
    // Shader draw modes — the last arg to draw(). This is a SEPARATE vocabulary
    // from Constant.TRANSITION_* (which picks the animation): it selects which
    // branch the fragment shader takes for a single wallpaper draw. Two-phase
    // effects (pixelate, blur) use one mode per phase.
    static final int DRAW_PLAIN       = 0; // no effect / plain alpha fade
    static final int DRAW_DISSOLVE    = 1; // dissolve-in via noise, progress 0→1
    static final int DRAW_PIXELATE_IN = 2; // phase 2: blocky → sharp
    static final int DRAW_PIXELATE_OUT= 3; // phase 1: sharp → blocky
    static final int DRAW_WIPE        = 4; // wipe in left→right, progress 0→1
    static final int DRAW_BLUR_OUT    = 5; // phase 1: sharp → blurred
    static final int DRAW_BLUR_IN     = 6; // phase 2: blurred → sharp
    static final int DRAW_ZOOM        = 7; // grow from screen centre, progress 0→1

    private static final String VERTEX_SHADER_CODE = ""
            +
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" + "attribute vec4 aPosition;"
            + "attribute vec2 aTexCoords;" + "varying vec2 vTexCoords;"
            + "void main(){" + "  vTexCoords = aTexCoords;"
            + "  gl_Position = uMVPMatrix * aPosition;" + "}";

    private static final String FRAGMENT_SHADER_CODE = ""
            + "precision mediump float;"
            + "uniform sampler2D uTexture;"
            + "uniform float uAlpha;"      // used only by effect 0 (plain fade)
            + "uniform float uProgress;"   // 0.0→1.0: incoming texture revealing itself
            + "uniform int uEffect;"       // 0=fade  1=dissolve  2=pixelate
            + "varying vec2 vTexCoords;"

            // Mobile-safe hash — avoids sin() which overflows mediump on real devices
            // (Mali/Adreno/PowerVR) and produces INF/NaN → diagonal line artefacts.
            // Uses highp for intermediate arithmetic, pure multiply+fract only.
            + "float hash(vec2 p){"
            + "  highp vec2 q = p;"
            + "  q = fract(q * vec2(443.897, 441.423));"
            + "  q += dot(q, q.yx + 19.19);"
            + "  return fract((q.x + q.y) * q.x);"
            + "}"

            + "void main(){"

            // Effect 0 — plain alpha fade (original behaviour, unchanged)
            + "  if (uEffect == 0){"
            + "    gl_FragColor   = texture2D(uTexture, vTexCoords);"
            + "    gl_FragColor.a = uAlpha;"

            // Effect 1 — procedural noise dissolve (incoming texture)
            // Each fragment has a unique threshold = hash(uv).
            // When uProgress sweeps 0→1, fragments unlock in random order.
            // smoothstep(threshold-band, threshold+band, uProgress):
            //   uProgress < noise-0.04  → edge=0 (invisible)
            //   uProgress > noise+0.04  → edge=1 (fully visible)
            + "  } else if (uEffect == 1){"
            + "    float noise = hash(vTexCoords);"
            + "    float edge  = smoothstep(noise - 0.04, noise + 0.04, uProgress);"
            + "    gl_FragColor   = texture2D(uTexture, vTexCoords);"
            + "    gl_FragColor.a = edge;"

            // Effect 2 — pixelate phase 2 (incoming / new wallpaper)
            // Sample from block CENTRE (+0.5) not block corner — symmetric at all block
            // sizes, no UV drift at screen edges, no vertical shift artefact.
            + "  } else if (uEffect == 2){"
            + "    const float MIN_BLOCK = 32.0;"
            + "    const float MAX_BLOCK = 256.0;"
            + "    float blockSize = mix(MIN_BLOCK, MAX_BLOCK, uProgress);"
            + "    vec2 pixUV = (floor(vTexCoords * blockSize) + 0.5) / blockSize;"
            + "    gl_FragColor   = texture2D(uTexture, pixUV);"
            + "    gl_FragColor.a = 1.0;"

            // Effect 3 — pixelate phase 1 (outgoing / previous wallpaper)
            // Same centre-sample fix — both phases are symmetric.
            + "  } else if (uEffect == 3){"
            + "    const float MIN_BLOCK = 32.0;"
            + "    const float MAX_BLOCK = 256.0;"
            + "    float blockSize = mix(MAX_BLOCK, MIN_BLOCK, uProgress);"
            + "    vec2 pixUV = (floor(vTexCoords * blockSize) + 0.5) / blockSize;"
            + "    gl_FragColor   = texture2D(uTexture, pixUV);"
            + "    gl_FragColor.a = 1.0;"

            // Effect 4 — wipe (incoming texture)
            // A feathered vertical edge sweeps left→right as uProgress goes 0→1.
            // Pixels left of the edge (x < uProgress) are fully visible; pixels right
            // are still transparent so the static old backdrop shows through.
            // smoothstep band ±0.015 (~1.5 % of screen width) softens the hard line.
            + "  } else if (uEffect == 4){"
            + "    float edge = 1.0 - smoothstep(uProgress - 0.015, uProgress + 0.015, vTexCoords.x);"
            + "    gl_FragColor   = texture2D(uTexture, vTexCoords);"
            + "    gl_FragColor.a = edge;"

            // Effects 5/6 (in-shader blur) retired — the blur transition now uses
            // the offscreen separable-Gaussian pipeline in Blur.java. Effect 7
            // keeps its number so other call sites stay untouched.

            // Effect 7 — zoom in (incoming texture)
            // Re-maps UVs so the image scales up from the screen centre.
            // scale = progress (0→1). UV outside [0,1] → transparent, revealing old backdrop.
            // step() replaces any conditional discard for better mobile GPU performance.
            + "  } else if (uEffect == 7){"
            + "    float scale  = max(uProgress, 0.001);"
            + "    vec2  uv     = (vTexCoords - 0.5) / scale + 0.5;"
            + "    float inBounds = step(0.0, uv.x) * step(uv.x, 1.0)"
            + "                   * step(0.0, uv.y) * step(uv.y, 1.0);"
            + "    gl_FragColor   = texture2D(uTexture, clamp(uv, 0.0, 1.0));"
            + "    gl_FragColor.a = inBounds;"

            + "  }"
            + "}";

    // number of coordinates per vertex in this array
    private static final int COORDS_PER_VERTEX = 3;
    private static final int VERTEX_STRIDE_BYTES = COORDS_PER_VERTEX
            * GLUtil.BYTES_PER_FLOAT;
    private static final int VERTICES = 6; // TL, BL, BR, TL, BR, TR

    // S, T (or X, Y)
    private static final int COORDS_PER_TEXTURE_VERTEX = 2;
    private static final int TEXTURE_VERTEX_STRIDE_BYTES = COORDS_PER_TEXTURE_VERTEX
            * GLUtil.BYTES_PER_FLOAT;

    private static final float[] SQUARE_TEXTURE_VERTICES = {0, 0, // top left
            0, 1, // bottom left
            1, 1, // bottom right

            0, 0, // top left
            1, 1, // bottom right
            1, 0, // top right
    };
    /**
     * Per-EGL-context GL state. Program/uniform handles are only valid in the
     * context that compiled them, and each wallpaper engine (home, lock, preview)
     * has its own context — so these must NOT be static: a second engine calling
     * initGl() would clobber handles the first engine is still drawing with,
     * which intermittently renders black.
     */
    static class Shader {
        final int program;
        final int attribPosition;
        final int attribTextureCoords;
        final int uniformAlpha;
        final int uniformProgress;
        final int uniformEffect;
        final int uniformTexture;
        final int uniformMVPMatrix;
        final int maxTextureSize;

        private Shader() {
            int vertexShaderHandle = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER,
                    VERTEX_SHADER_CODE);
            int fragShaderHandle = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER,
                    FRAGMENT_SHADER_CODE);

            program = GLUtil.createAndLinkProgram(vertexShaderHandle,
                    fragShaderHandle, null);
            uniformAlpha = GLES20.glGetUniformLocation(program, "uAlpha");
            uniformProgress = GLES20.glGetUniformLocation(program, "uProgress");
            uniformEffect = GLES20.glGetUniformLocation(program, "uEffect");
            attribPosition = GLES20.glGetAttribLocation(program, "aPosition");
            attribTextureCoords = GLES20.glGetAttribLocation(program, "aTexCoords");
            uniformMVPMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            uniformTexture = GLES20.glGetUniformLocation(program, "uTexture");

            int[] maxSize = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);
            maxTextureSize = maxSize[0];
        }

        /** Frees the program. Drains the GL error queue in case the context changed. */
        void delete() {
            GLES20.glDeleteProgram(program);
            //noinspection StatementWithEmptyBody
            while (GLES20.glGetError() != GLES20.GL_NO_ERROR) ;
        }
    }

    private final float[] mVertices = new float[COORDS_PER_VERTEX * VERTICES];
    private boolean mHasContent = false;
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTextureCoordsBuffer;
    private int mCols = 1;
    private int mRows = 1;
    private int mWidth = 0;
    private int mHeight = 0;
    private float mRatio;
    private int mTileSize;
    private int[] mTextureHandles;

    Wallpaper(Bitmap bitmap, int maxTextureSize) {
        if (bitmap == null) {
            return;
        }

        mTileSize = maxTextureSize;
        mHasContent = true;
        mVertexBuffer = GLUtil.newFloatBuffer(mVertices.length);
        mTextureCoordsBuffer = GLUtil.asFloatBuffer(SQUARE_TEXTURE_VERTICES);

        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        mRatio = (float) mWidth / (float) mHeight;
        int leftoverHeight = mHeight % mTileSize;

        // Load m x n textures
        mCols = mWidth / (mTileSize + 1) + 1;
        mRows = mHeight / (mTileSize + 1) + 1;

        mTextureHandles = new int[mCols * mRows];
        if (mCols == 1 && mRows == 1) {
            mTextureHandles[0] = GLUtil.loadTexture(bitmap);
        } else {
            Rect rect = new Rect();
            for (int y = 0; y < mRows; y++) {
                for (int x = 0; x < mCols; x++) {
                    rect.set(x * mTileSize, (mRows - y - 1) * mTileSize,
                            (x + 1) * mTileSize, (mRows - y) * mTileSize);
                    // The bottom tiles must be full tiles for drawing, so only
                    // allow edge tiles
                    // at the top
                    if (leftoverHeight > 0) {
                        rect.offset(0, -mTileSize + leftoverHeight);
                    }
//                    rect.intersect(0, 0, mWidth, mHeight);
                    Bitmap subBitmap = Bitmap.createBitmap(bitmap, rect.left,
                            rect.top, rect.width(), rect.height());
                    mTextureHandles[y * mCols + x] = GLUtil
                            .loadTexture(subBitmap);
                    subBitmap.recycle();
                }
            }
        }
        bitmap.recycle();
    }

    /** Compiles the shader program in the CURRENT EGL context. The caller (renderer)
     *  owns the returned Shader and must pass it to every draw() call. */
    static Shader initGl() {
        return new Shader();
    }

    void draw(Shader shader, float[] mvpMatrix, float alpha, float progress, int effect) {
        if (!mHasContent || shader == null) {
            return;
        }

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(shader.program);

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(shader.uniformMVPMatrix, 1, false, mvpMatrix,
                0);
        GLUtil.checkGlError("glUniformMatrix4fv");

        // Set up vertex buffer
        GLES20.glUniform1f(shader.uniformAlpha, alpha);
        GLES20.glUniform1f(shader.uniformProgress, progress);
        GLES20.glUniform1i(shader.uniformEffect, effect);
        GLES20.glEnableVertexAttribArray(shader.attribPosition);
        GLES20.glVertexAttribPointer(shader.attribPosition, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, mVertexBuffer);

        // Set up texture stuff
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(shader.uniformTexture, 0);
        GLES20.glVertexAttribPointer(shader.attribTextureCoords,
                COORDS_PER_TEXTURE_VERTEX, GLES20.GL_FLOAT, false,
                TEXTURE_VERTEX_STRIDE_BYTES, mTextureCoordsBuffer);
        GLES20.glEnableVertexAttribArray(shader.attribTextureCoords);
        // Log.i("Wallpaper", "mRatio=" + mRatio);
        // mRatio = 1;
        // Draw tiles
        for (int y = 0; y < mRows; y++) {
            for (int x = 0; x < mCols; x++) {
                // Pass in the vertex information
                mVertices[0] = mVertices[3] = mVertices[9] = -mRatio
                        * Math.min(-1 + 2f * 1 * x * mTileSize / mWidth, 1); // left
                mVertices[1] = mVertices[10] = mVertices[16] = Math.min(-1 + 2f
                        * (y + 1) * mTileSize / mHeight, 1); // top
                mVertices[6] = mVertices[12] = mVertices[15] = -mRatio
                        * Math.min(-1 + 2f * 1 * (x + 1) * mTileSize / mWidth,
                        1); // right
                mVertices[4] = mVertices[7] = mVertices[13] = Math.min(-1 + 2f
                        * y * mTileSize / mHeight, 1); // bottom
                mVertexBuffer.put(mVertices);
                mVertexBuffer.position(0);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandles[y
                        * mCols + x]);
                GLUtil.checkGlError("glBindTexture");

                // Draw the two triangles
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mVertices.length
                        / COORDS_PER_VERTEX);
            }
        }

        GLES20.glDisableVertexAttribArray(shader.attribPosition);
        GLES20.glDisableVertexAttribArray(shader.attribTextureCoords);
    }

    void destroy() {
        // Clear content FIRST so a stale reference can never draw deleted texture
        // names (renders black on most drivers).
        mHasContent = false;
        if (mTextureHandles != null) {
            GLES20.glDeleteTextures(mTextureHandles.length, mTextureHandles, 0);
            mTextureHandles = null;
            GLUtil.checkGlError("Destroy picture");
        }
    }
}
