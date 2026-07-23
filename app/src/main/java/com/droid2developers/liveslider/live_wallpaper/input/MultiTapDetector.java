package com.droid2developers.liveslider.live_wallpaper.input;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Detects double and triple taps from a raw MotionEvent stream.
 *
 * Replaces GestureDetector, which fires onDoubleTap on the SECOND down — making
 * it impossible to tell a double tap from the first two taps of a triple tap.
 * Here the double-tap callback is deferred by one tap window: if a third tap
 * lands in time it upgrades to triple and double never fires. The cost is that
 * a double tap resolves ~300ms after the second tap — that latency is the price
 * of disambiguation and matches what launchers do for the same problem.
 *
 * A "tap" only counts if: single finger, released before the long-press
 * timeout, and moved less than touch slop. Anything else (scroll, fling,
 * pinch, long press) resets the sequence.
 */
public class MultiTapDetector {

    public interface Listener {
        void onDoubleTap();
        void onTripleTap();
    }

    private static final int MAX_TAPS = 3;

    private final int tapWindowMs = ViewConfiguration.getDoubleTapTimeout();
    private final int longPressMs = ViewConfiguration.getLongPressTimeout();
    private final int touchSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Runnable resolveRunnable = this::resolve;

    private int tapCount = 0;
    private long downTime;
    private float downX, downY;
    private boolean tapCancelled;

    public MultiTapDetector(Context context, Listener listener) {
        this.listener = listener;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Finger is down again — hold any pending resolve until we see
                // whether this becomes another qualifying tap.
                handler.removeCallbacks(resolveRunnable);
                downTime = e.getEventTime();
                downX = e.getX();
                downY = e.getY();
                tapCancelled = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Second finger — this is a pinch/multi-touch, not a tap
                tapCancelled = true;
                reset();
                break;

            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > touchSlop
                        || Math.abs(e.getY() - downY) > touchSlop) {
                    tapCancelled = true; // scroll/fling, not a tap
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                tapCancelled = true;
                reset();
                break;

            case MotionEvent.ACTION_UP:
                if (tapCancelled || e.getEventTime() - downTime > longPressMs) {
                    reset();
                    break;
                }
                tapCount++;
                if (tapCount >= MAX_TAPS) {
                    resolve(); // triple can't be upgraded further — fire now
                } else {
                    handler.postDelayed(resolveRunnable, tapWindowMs);
                }
                break;

            default:
                break;
        }
    }

    private void resolve() {
        handler.removeCallbacks(resolveRunnable);
        int taps = tapCount;
        tapCount = 0;
        if (taps == 2) {
            listener.onDoubleTap();
        } else if (taps >= MAX_TAPS) {
            listener.onTripleTap();
        }
    }

    private void reset() {
        handler.removeCallbacks(resolveRunnable);
        tapCount = 0;
    }
}
