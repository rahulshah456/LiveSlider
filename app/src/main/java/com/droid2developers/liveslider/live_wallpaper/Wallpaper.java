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
    private static final String VERTEX_SHADER_CODE = ""
            +
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" + "attribute vec4 aPosition;"
            + "attribute vec2 aTexCoords;" + "varying vec2 vTexCoords;"
            + "void main(){" + "  vTexCoords = aTexCoords;"
            + "  gl_Position = uMVPMatrix * aPosition;" + "}";

    private static final String FRAGMENT_SHADER_CODE = ""
            + "precision mediump float;" + "uniform sampler2D uTexture;"
            + "uniform float uAlpha;"
            + "varying vec2 vTexCoords;" + "void main(){"
            + "  gl_FragColor = texture2D(uTexture, vTexCoords);"
            + "  gl_FragColor.a = uAlpha;"
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
    private static int sMaxTextureSize;
    private static int sProgramHandle;
    private static int sAttribPositionHandle;
    private static int sAlphaUniformHandle;
    private static int sAttribTextureCoordsHandle;
    private static int sUniformTextureHandle;
    private static int sUniformMVPMatrixHandle;
    private final float[] mVertices = new float[COORDS_PER_VERTEX * VERTICES];
    private boolean mHasContent = false;
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTextureCoordsBuffer;
    private int mCols = 1;
    private int mRows = 1;
    private int mWidth = 0;
    private int mHeight = 0;
    private float mRatio;
    private int mTileSize = sMaxTextureSize;
    private int[] mTextureHandles;

    Wallpaper(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }

        mTileSize = sMaxTextureSize;
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

    static void initGl() {
        // Initialize shaders and create/link program
        int vertexShaderHandle = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER,
                VERTEX_SHADER_CODE);
        int fragShaderHandle = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER,
                FRAGMENT_SHADER_CODE);

        sProgramHandle = GLUtil.createAndLinkProgram(vertexShaderHandle,
                fragShaderHandle, null);
        sAlphaUniformHandle = GLES20.glGetUniformLocation(sProgramHandle,
                "uAlpha");
        sAttribPositionHandle = GLES20.glGetAttribLocation(sProgramHandle,
                "aPosition");
        sAttribTextureCoordsHandle = GLES20.glGetAttribLocation(sProgramHandle,
                "aTexCoords");
        sUniformMVPMatrixHandle = GLES20.glGetUniformLocation(sProgramHandle,
                "uMVPMatrix");
        sUniformTextureHandle = GLES20.glGetUniformLocation(sProgramHandle,
                "uTexture");

        // Compute max texture size
        int[] maxTextureSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
        sMaxTextureSize = maxTextureSize[0];
    }

    void draw(float[] mvpMatrix, float alpha) {
        if (!mHasContent) {
            return;
        }

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(sProgramHandle);

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(sUniformMVPMatrixHandle, 1, false, mvpMatrix,
                0);
        GLUtil.checkGlError("glUniformMatrix4fv");

        // Set up vertex buffer
        GLES20.glUniform1f(sAlphaUniformHandle, alpha);
        GLES20.glEnableVertexAttribArray(sAttribPositionHandle);
        GLES20.glVertexAttribPointer(sAttribPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, mVertexBuffer);

        // Set up texture stuff
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(sUniformTextureHandle, 0);
        GLES20.glVertexAttribPointer(sAttribTextureCoordsHandle,
                COORDS_PER_TEXTURE_VERTEX, GLES20.GL_FLOAT, false,
                TEXTURE_VERTEX_STRIDE_BYTES, mTextureCoordsBuffer);
        GLES20.glEnableVertexAttribArray(sAttribTextureCoordsHandle);
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

        GLES20.glDisableVertexAttribArray(sAttribPositionHandle);
        GLES20.glDisableVertexAttribArray(sAttribTextureCoordsHandle);
    }

    void destroy() {
        if (mTextureHandles != null) {
            GLES20.glDeleteTextures(mTextureHandles.length, mTextureHandles, 0);
            GLUtil.checkGlError("Destroy picture");
//            mTextureHandles = null;
        }
    }
}
