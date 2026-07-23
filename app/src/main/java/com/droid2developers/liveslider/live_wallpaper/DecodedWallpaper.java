package com.droid2developers.liveslider.live_wallpaper;

import android.graphics.Bitmap;

/** Decoded bitmap plus the aspect ratio cropBitmap() computes from it — both
 *  are needed on the GL thread to finish what loadTexture() used to do in one
 *  synchronous call. scrollRange is NOT carried here: preCalculate() (called
 *  right after upload) recomputes it from aspectRatio/screenAspectRatio, same
 *  as the original code path — cropBitmap's own scrollRange write was always
 *  a throwaway immediately superseded by that recompute. */
final class DecodedWallpaper {
    final Bitmap bitmap;
    final float aspectRatio;
    DecodedWallpaper(Bitmap bitmap, float aspectRatio) {
        this.bitmap = bitmap;
        this.aspectRatio = aspectRatio;
    }
}
