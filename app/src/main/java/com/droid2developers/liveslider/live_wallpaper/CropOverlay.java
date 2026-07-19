package com.droid2developers.liveslider.live_wallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.opengl.GLES20;

import com.droid2developers.liveslider.utils.GLUtil;

import java.nio.FloatBuffer;

/**
 * Crop-adjust overlay drawn inside the wallpaper's GL surface:
 *   - ◀ / ▶ circles to nudge the crop horizontally, ✓ to save & dismiss
 *   - above them, a joined pill "◀ 7/10 ▶" to step between playlist wallpapers;
 *     drawn only when a playlist is active (total > 0)
 * The buttons are drawn into a transparent Canvas bitmap, uploaded as a texture,
 * and rendered on a full-screen NDC quad — no Views exist in a wallpaper surface.
 * The texture is rebuilt whenever the current/total numbers change.
 * The same layout constants drive drawing AND touch hit-testing, so they can
 * never drift apart. All GL calls must happen on the GL thread (draw/release);
 * hitTest is pure math and safe from any thread.
 */
class CropOverlay {

    static final int HIT_NONE = 0;
    static final int HIT_LEFT = 1;
    static final int HIT_RIGHT = 2;
    static final int HIT_DONE = 3;
    static final int HIT_PREV = 4;
    static final int HIT_NEXT = 5;

    // Crop buttons: centres as fractions of screen width/height; radii of width.
    // All three sit on one row: ◀ (0.32) — ✓ (0.50) — ▶ (0.68).
    private static final float LEFT_X = 0.32f, RIGHT_X = 0.68f, ARROWS_Y = 0.50f;
    private static final float DONE_X = 0.50f, DONE_Y = ARROWS_Y;
    private static final float RADIUS = 0.08f;      // visual size
    // Touch target slightly larger than visual, but capped at half the 0.18 spacing
    // between neighbouring buttons so hit zones can never overlap.
    private static final float HIT_RADIUS = 0.09f;

    // Playlist pill: centred horizontally; half-height as fraction of width so it
    // scales with the buttons
    private static final float PILL_Y = 0.36f;
    private static final float PILL_HALF_W = 0.22f;
    private static final float PILL_HALF_H = 0.06f;

    private static final String VERTEX_SHADER = ""
            + "attribute vec4 aPosition;"
            + "attribute vec2 aTexCoords;"
            + "varying vec2 vTexCoords;"
            + "void main(){ vTexCoords = aTexCoords; gl_Position = aPosition; }";

    // Unlike the wallpaper shader, this one keeps the texture's own alpha so the
    // transparent areas of the button bitmap stay transparent.
    private static final String FRAGMENT_SHADER = ""
            + "precision mediump float;"
            + "uniform sampler2D uTexture;"
            + "varying vec2 vTexCoords;"
            + "void main(){ gl_FragColor = texture2D(uTexture, vTexCoords); }";

    // Full-screen quad as a triangle strip: TL, BL, TR, BR
    private static final float[] QUAD_POS = {-1, 1, -1, -1, 1, 1, 1, -1};
    private static final float[] QUAD_TEX = {0, 0, 0, 1, 1, 0, 1, 1};

    private int program = -1;
    private int texture = -1;
    private int attribPosition;
    private int attribTexCoords;
    private int uniformTexture;
    private FloatBuffer posBuffer;
    private FloatBuffer texBuffer;
    private int lastCurrent = -1;
    private int lastTotal = -1;

    /**
     * Returns which button (if any) a tap at screen pixel (x, y) hits.
     * pillVisible must match what was drawn (playlist active) or the invisible
     * pill would swallow taps.
     */
    static int hitTest(float x, float y, int screenW, int screenH, boolean pillVisible) {
        if (pillVisible
                && Math.abs(y - PILL_Y * screenH) < PILL_HALF_H * screenW * 1.2f
                && Math.abs(x - 0.5f * screenW) < PILL_HALF_W * screenW) {
            if (x < screenW * (0.5f - PILL_HALF_W / 3f)) return HIT_PREV;
            if (x > screenW * (0.5f + PILL_HALF_W / 3f)) return HIT_NEXT;
            return HIT_NONE; // the number label — no action
        }
        float r = HIT_RADIUS * screenW;
        if (dist(x, y, LEFT_X * screenW, ARROWS_Y * screenH) < r) return HIT_LEFT;
        if (dist(x, y, RIGHT_X * screenW, ARROWS_Y * screenH) < r) return HIT_RIGHT;
        if (dist(x, y, DONE_X * screenW, DONE_Y * screenH) < r) return HIT_DONE;
        return HIT_NONE;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Draws the overlay (GL thread). current/total drive the "7/10" pill;
     * total <= 0 hides the pill entirely (static wallpaper, no playlist).
     * The texture is (re)built lazily on first draw and whenever the numbers change.
     */
    void draw(int screenW, int screenH, int current, int total) {
        if (screenW <= 0 || screenH <= 0) return;
        if (program == -1) initProgram();
        if (texture == -1 || current != lastCurrent || total != lastTotal) {
            if (texture != -1) GLES20.glDeleteTextures(1, new int[]{texture}, 0);
            texture = buildTexture(screenW, screenH, current, total);
            lastCurrent = current;
            lastTotal = total;
        }

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uniformTexture, 0);
        GLES20.glEnableVertexAttribArray(attribPosition);
        GLES20.glVertexAttribPointer(attribPosition, 2, GLES20.GL_FLOAT, false, 0, posBuffer);
        GLES20.glEnableVertexAttribArray(attribTexCoords);
        GLES20.glVertexAttribPointer(attribTexCoords, 2, GLES20.GL_FLOAT, false, 0, texBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(attribPosition);
        GLES20.glDisableVertexAttribArray(attribTexCoords);
    }

    private void initProgram() {
        program = GLUtil.createAndLinkProgram(
                GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER),
                GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER), null);
        attribPosition = GLES20.glGetAttribLocation(program, "aPosition");
        attribTexCoords = GLES20.glGetAttribLocation(program, "aTexCoords");
        uniformTexture = GLES20.glGetUniformLocation(program, "uTexture");
        posBuffer = GLUtil.asFloatBuffer(QUAD_POS);
        texBuffer = GLUtil.asFloatBuffer(QUAD_TEX);
    }

    private int buildTexture(int screenW, int screenH, int current, int total) {
        // Half-resolution bitmap is plenty for circles/strokes and quarters the memory
        int w = screenW / 2, h = screenH / 2;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float r = RADIUS * w;
        float lx = LEFT_X * w, rx = RIGHT_X * w, ay = ARROWS_Y * h;
        float dx = DONE_X * w, dy = DONE_Y * h;

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x99000000);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(r * 0.16f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);

        Paint doneBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        doneBg.setColor(0xCC2E7D32); // translucent green — the confirm/save action

        canvas.drawCircle(lx, ay, r, bg);
        canvas.drawCircle(rx, ay, r, bg);
        canvas.drawCircle(dx, dy, r, doneBg);

        float a = r * 0.35f;
        Path p = new Path(); // ◀ chevron
        p.moveTo(lx + a * 0.6f, ay - a);
        p.lineTo(lx - a * 0.6f, ay);
        p.lineTo(lx + a * 0.6f, ay + a);
        canvas.drawPath(p, line);
        p.reset();           // ▶ chevron
        p.moveTo(rx - a * 0.6f, ay - a);
        p.lineTo(rx + a * 0.6f, ay);
        p.lineTo(rx - a * 0.6f, ay + a);
        canvas.drawPath(p, line);
        p.reset();           // ✓ done
        p.moveTo(dx - a * 1.2f, dy);
        p.lineTo(dx - a * 0.3f, dy + a * 0.8f);
        p.lineTo(dx + a * 1.2f, dy - a * 0.7f);
        canvas.drawPath(p, line);

        // Plain-language hint above the row — users won't know what "bias" means
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(r * 0.45f);
        label.setFakeBoldText(true);
        label.setShadowLayer(r * 0.12f, 0, 0, 0xAA000000);
        canvas.drawText("Move wallpaper left or right", 0.5f * w, ay - r * 1.5f, label);

        if (total > 0) {
            // Joined pill: ◀ | current/total | ▶
            float pillY = PILL_Y * h;
            float halfW = PILL_HALF_W * w;
            float halfH = PILL_HALF_H * w;
            RectF pill = new RectF(0.5f * w - halfW, pillY - halfH,
                    0.5f * w + halfW, pillY + halfH);
            canvas.drawRoundRect(pill, halfH, halfH, bg);

            float pa = halfH * 0.5f;
            float chevronOffset = halfW * 0.62f;
            p.reset();       // ◀ prev
            p.moveTo(0.5f * w - chevronOffset + pa * 0.6f, pillY - pa);
            p.lineTo(0.5f * w - chevronOffset - pa * 0.6f, pillY);
            p.lineTo(0.5f * w - chevronOffset + pa * 0.6f, pillY + pa);
            canvas.drawPath(p, line);
            p.reset();       // ▶ next
            p.moveTo(0.5f * w + chevronOffset - pa * 0.6f, pillY - pa);
            p.lineTo(0.5f * w + chevronOffset + pa * 0.6f, pillY);
            p.lineTo(0.5f * w + chevronOffset - pa * 0.6f, pillY + pa);
            canvas.drawPath(p, line);

            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(halfH * 0.8f);
            text.setFakeBoldText(true);
            canvas.drawText(current + "/" + total, 0.5f * w,
                    pillY + halfH * 0.3f, text);
        }

        int tex = GLUtil.loadTexture(bmp);
        bmp.recycle();
        return tex;
    }
}
