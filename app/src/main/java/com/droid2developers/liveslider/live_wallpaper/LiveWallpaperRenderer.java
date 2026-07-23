package com.droid2developers.liveslider.live_wallpaper;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.droid2developers.liveslider.models.BiasChangeEvent;
import com.droid2developers.liveslider.models.FaceRotationEvent;
import com.droid2developers.liveslider.utils.Constant;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.greenrobot.eventbus.EventBus;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_FADE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_DISSOLVE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_PIXELATE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_WIPE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_BLUR;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_ZOOM;

public class LiveWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static int REFRESH_RATE = 60;
    private final static float MAX_BIAS_RANGE = 0.006f;
    private final static String TAG = LiveWallpaperRenderer.class.getSimpleName();

    // Transition state — all animation runs on the GL thread inside onDrawFrame()
    private float transitionAlpha = 1.0f;
    private float transitionProgress = 0f;
    private boolean transitionActive = false;
    private boolean transitionFadeOutDone = false;
    private boolean transitionPendingLoad = false;
    private long transitionLoadAfter = 0L;
    private boolean transitionMidpointReached = false; // pixelate: has phase-2 started?
    private int chosenEffect = TRANSITION_FADE;   // user's saved preference — written from any thread
    private int currentEffect = TRANSITION_FADE;  // active shader effect — only touched on GL thread

    // Pixelate/blur only: the midpoint swap used to call loadTexture() synchronously
    // ON the GL thread — a full decode+crop+GC stalling the frame right as phase 2
    // should start smoothly. Instead the decode is kicked off on `scheduler` (a plain
    // background thread, not GL) as soon as phase 1 begins, so it runs in parallel
    // with the phase-1 animation; only the cheap GL texture upload happens at the
    // midpoint. requestToken guards against a superseded/cancelled request writing
    // its stale result after a newer transition has already started.
    private int prefetchRequestToken = 0;
    private volatile int prefetchResultToken = -1;
    private volatile DecodedWallpaper prefetchResult = null;

    // Real-time progress step, recomputed once per frame (updateFrameStep). The old
    // fixed per-frame constants made transition speed depend on display refresh
    // rate, and the synchronous texture decode at a midpoint swap consumed frames
    // that phase 2 of blur/pixelate needed — the new wallpaper then "popped".
    private long lastFrameNanos = 0L;
    private float frameStep = 0f;
    // Overlay-shader fade: ramps to 0 before a wallpaper transition starts and back
    // to 1 after it finishes, so rain/ripple/snow never vanish/reappear in one frame.
    private float shaderFade = 1.0f;
    // True between onSurfaceCreated and the next onSurfaceChanged — distinguishes a
    // rotation (context survived, animate the re-crop) from context loss (hard reload).
    private boolean contextLost = true;

    // Pending wallpaper-change request — written from service thread, consumed on GL thread
    private volatile boolean hasPendingRefresh = false;
    private volatile String pendingWallpaperPath = null;
    private volatile boolean pendingIsDefault = false;
    private volatile float pendingCropBias = 0f;
    private volatile boolean pendingInstant = false; // swap with no animation (screen-off change)

    // Retry state — when external storage is not yet available on boot, we retry
    // loading the user's wallpaper a few times before falling back to default.
    private static final int MAX_LOAD_RETRIES = 5;
    private static final long RETRY_DELAY_MS  = 3000L; // 3 s between retries
    private int loadRetryCount = 0;
    private volatile ScheduledFuture<?> retryHandle;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final Context mContext;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private float scrollStep = 1f;
    private final Queue<Float> scrollOffsetXQueue = new CircularFifoQueue<>(10);
    private float scrollOffsetX = 0.5f;// , offsetY = 0.5f;
    private float scrollOffsetXBackup = 0.5f;
    private float currentOrientationOffsetX, currentOrientationOffsetY;
    private float orientationOffsetX, orientationOffsetY;
    private final Callbacks mCallbacks;
    private float screenAspectRatio;
    private int screenW;
    private int screenH;

    // Crop-adjust overlay (triple-tap feature) — visibility/bias written from the
    // service thread, read on the GL thread. Bias is a camera-x offset in world
    // units; phase 1 only, not persisted.
    private volatile boolean cropOverlayVisible = false;
    private volatile float cropBias = 0f;
    private CropOverlay cropOverlay;
    // 1-based position and size of the active playlist, shown in the overlay's
    // "7/10" pill; total 0 = no playlist (pill hidden). Written from service thread.
    private volatile int playlistCurrent = 0;
    private volatile int playlistTotal = 0;
    // Offscreen blur pipeline for the blur transition — per-EGL-context, rebuilt
    // lazily like cropOverlay.
    private Blur blur;

    // --- Active overlay shader (rain / ripple / snow / none) --------------------
    // Only ONE of these can be active at a time (enforced by Constant.SHADER_NONE
    // being the only "off" state — see setActiveShader()). Shader instances are
    // lazily created/rebuilt per-EGL-context like blur/cropOverlay above. Drawn
    // every frame while active, so it needs continuous rendering (see the
    // requestRender() calls in onDrawFrame's static-draw branch).
    private final OverlayShaderController overlayShaders = new OverlayShaderController();
    // Set from any thread when a shader/Blur should be freed; consumed on the GL
    // thread at the top of onDrawFrame (glDelete* needs the context current).
    // "all" also frees Blur; otherwise every shader that isn't activeShaderId is freed.
    private volatile boolean pendingShaderRelease = false;
    private volatile boolean pendingReleaseAll = false;
    // Set when this engine goes invisible (screen off, or the OTHER engine is on
    // screen — home/lock are never both visible at once). Frees the wallpaper GL
    // texture(s) too — the single biggest per-engine GPU allocation — not just the
    // overlay shaders. Path/crop/playlist fields are untouched, so becoming visible
    // again just reloads the same texture from what's already known; nothing about
    // "which wallpaper, what crop, playlist position" is lost, only the GPU bytes.
    private volatile boolean pendingWallpaperRelease = false;
    // GL-thread only. True after the release above actually freed the wallpaper —
    // the reload decision must happen HERE on the GL thread: the service thread's
    // "wallpaper == null?" check in setEngineVisible(true) can race the free and
    // read a stale non-null wallpaper, flagging no reload → permanent black frame.
    private boolean wallpaperReleasedWhileHidden = false;
    // Suppress heavy overlay shaders (rain/ripple/snow) when battery saver is on.
    private volatile boolean powerSaverActive = false;
    // False while this engine's surface is not visible (the OTHER screen is showing).
    // An animated shader keeps requesting frames forever; gating that request on
    // visibility lets the GL thread idle for the hidden engine — the big win when
    // home + lock are both active but only one is ever on screen at a time.
    private volatile boolean engineVisible = true;
    // Shader animation frame cap: overlay shaders re-request a frame every draw,
    // pinning them to display refresh (60/120Hz). 30fps is visually fine for
    // rain/snow and halves their GPU/CPU cost. Gates only the shader's self-render
    // request, never wallpaper transitions or parallax.
    private static final long SHADER_FRAME_INTERVAL_MS = 33; // ~30 fps
    private long lastShaderFrameMs = 0L;
    private float wallpaperAspectRatio;
    private final Runnable transition = new Runnable() {
        @Override
        public void run() {
            transitionCal();
        }
    };
    private ScheduledFuture<?> transitionHandle;
    private float preA;
    private float preB;
    // Snapshotted camera for previousWallpaper during transitions — prevents position
    // shift when loadTexture()/cropBias switch mid-animation to the new texture's values.
    private float prevPreA;
    private float prevPreB;
    private float prevCropBias;
    private float prevSlack;

    // Important mutable parameters for live wallpaper
    // Per-context shader state owned by THIS renderer/engine — never shared across
    // engines (home/lock/preview each have their own EGL context).
    private Wallpaper.Shader shader;
    private Wallpaper wallpaper;
    private Wallpaper previousWallpaper; // held alive during effects 1 & 2 crossfade
    private String localWallpaperPath = null;
    private int delay = 1;
    private float biasRange;
    private float scrollRange;
    private boolean scrollMode = true;
    private boolean needsRefreshWallpaper;
    private boolean isDefaultWallpaper;
    // Read from the prefetch background thread (openWallpaperStream overload) as
    // well as the GL thread — volatile so that cross-thread read is well-defined.
    private volatile int wallpaperType;
    // Bitmap.Config the wallpaper is decoded/uploaded with (user "wallpaper quality"
    // pref). Default full-quality; RGB_565 halves texture memory. Read on the GL
    // thread in cropBitmap; written from the service thread — volatile is enough.
    private volatile Bitmap.Config wallpaperConfig = Bitmap.Config.ARGB_8888;
    /** This service's own default asset path (home vs. lock) — see LiveWallpaperService#getDefaultWallpaperPath(). */
    private final String defaultWallpaperPath;


    LiveWallpaperRenderer(Context context, Callbacks callbacks, String defaultWallpaperPath) {
        mContext = context;
        mCallbacks = callbacks;
        this.defaultWallpaperPath = defaultWallpaperPath;
    }

    void release() {
        if (wallpaper != null) wallpaper.destroy();
        if (previousWallpaper != null) { previousWallpaper.destroy(); previousWallpaper = null; }
        cancelPendingPrefetch();
        stopTransition();
        scheduler.shutdown();
    }

    // The Surface is created/init()
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        // onSurfaceCreated fires on EVERY resume (surface recreated), but this GL
        // wrapper REUSES the EGL context across pause/resume — it is NOT lost (see
        // EglHelper.start(): "create context only once"). So the previous frame's GL
        // objects are still alive in the current context. Nulling them without
        // deleting orphans their programs/FBOs/textures into the live context — a
        // leak of a full shader set every screen-off/on cycle, which is why Graphics
        // memory never came back down. Explicitly release, then drop the references.
        if (shader != null) shader.delete();
        shader = Wallpaper.initGl();
        contextLost = true; // textures are dead — next onSurfaceChanged must hard-reload
        // A transition caught mid-flight at resume leaves previousWallpaper holding a
        // full wallpaper texture that loadTexture() (which only frees `wallpaper`)
        // never reclaims. Free it here so it can't linger for the context's lifetime.
        if (previousWallpaper != null) { previousWallpaper.destroy(); previousWallpaper = null; }
        transitionActive = false;
        // transitionActive=false permanently skips the onDrawFrame tick that would
        // otherwise consume a pixelate/blur prefetch — without this, a result that
        // lands after this point sits in prefetchResult forever, unrecycled.
        cancelPendingPrefetch();
        if (cropOverlay != null) { cropOverlay.release(); cropOverlay = null; }
        if (blur != null) { blur.release(); blur = null; }
        overlayShaders.releaseAll();
        // Objects a pending release targeted were just freed above — clear the flags.
        pendingShaderRelease = false;
        pendingReleaseAll = false;
    }


    // Transition methods
    void startTransition() {
        stopTransition();
        transitionHandle = scheduler.scheduleWithFixedDelay(transition,
                0, 1000 / REFRESH_RATE, TimeUnit.MILLISECONDS);
    }
    void stopTransition() {
        if (transitionHandle != null) transitionHandle.cancel(true);
    }

    // Here we do our drawing
    private boolean hasLoggedNullWallpaper = false;
    // Guards the default-wallpaper self-heal retry (see the null-wallpaper branch
    // below) so a genuinely broken default asset retries once per null-streak,
    // not every single frame. Cleared as soon as loadTexture() succeeds.
    private boolean defaultRecoveryTried = false;
    @Override
    public void onDrawFrame(GL10 gl) {

        updateFrameStep();

        // Free any shader/Blur GL objects flagged for release on another thread —
        // must run here where the EGL context is current.
        consumeShaderRelease();
        consumeWallpaperRelease();

        // The wallpaper texture was freed while hidden and we're visible again —
        // reload it now, on the GL thread, where wallpaper's null-ness is authoritative.
        if (wallpaperReleasedWhileHidden && engineVisible) {
            wallpaperReleasedWhileHidden = false;
            if (wallpaper == null && !hasPendingRefresh) needsRefreshWallpaper = true;
        }

        // Fade the overlay shader out if it should be off (battery saver active OR
        // a pending wallpaper refresh), and back in once it's safe to draw.
        // Instant changes (screen-off swap) skip the fade.
        boolean shaderOn = !overlayShaders.isNone() && wallpaper != null && !powerSaverActive;

        if (shaderFade > 0f && (!shaderOn || (hasPendingRefresh && !pendingInstant))) {
            shaderFade = Math.max(0f, shaderFade - 4f * frameStep);
            mCallbacks.requestRender();
        } else if (shaderOn && !hasPendingRefresh && !transitionActive && shaderFade < 1f) {
            shaderFade = Math.min(1f, shaderFade + 4f * frameStep);
            mCallbacks.requestRender();
        }

        // Consume any pending wallpaper-change request first — sets up transition
        // state atomically on the GL thread before the tick or draw logic reads it.
        // Held back only while the shader fade-out above is still running.
        if (!shaderOn || pendingInstant || shaderFade <= 0f) {
            consumePendingRefresh();
        }

        // Advance transition state for this frame (GL thread only).
        if (transitionActive) tickTransition();

        if (needsRefreshWallpaper) {
            loadTexture();
            needsRefreshWallpaper = false;
        }

        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Only proceed if preA and preB are valid numbers
        if (Float.isNaN(preA) || Float.isNaN(preB) || Float.isInfinite(preA) || Float.isInfinite(preB)) {
            if (!hasLoggedNullWallpaper) {
                Log.w(TAG, "onDrawFrame: Invalid matrix parameters (preA/preB), skipping draw");
                hasLoggedNullWallpaper = true;
            }
            return;
        }

        // Set the camera position (View matrix)
        // cropBias shifts the visible crop window horizontally; clamp scroll+bias to
        // the image's real slack so we never pan past the wallpaper's edge.
        float slack = Math.max(0f, wallpaperAspectRatio - screenAspectRatio);
        float base = preA * (-2 * scrollOffsetX + 1) + cropBias;
        base = Math.max(-slack, Math.min(slack, base));
        float x = base + currentOrientationOffsetX;
        float y = currentOrientationOffsetY;
        Matrix.setLookAtM(mViewMatrix, 0, x, y, preB, x, y, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        drawWallpaper();

        // Crop-adjust buttons render on top of everything
        if (cropOverlayVisible) {
            if (cropOverlay == null) cropOverlay = new CropOverlay();
            cropOverlay.draw(screenW, screenH, playlistCurrent, playlistTotal);
        }
    }

    /** Advances the active transition's state by one frame. GL-thread only;
     *  called from onDrawFrame while transitionActive. Every branch requests the
     *  next render itself and clears transitionActive (resetting currentEffect to
     *  TRANSITION_FADE for the crossfade/wipe/zoom effects) when it completes. */
    private void tickTransition() {
        if (currentEffect == TRANSITION_FADE) {
            // ---- Fade: two-phase alpha fade ----
            if (!transitionFadeOutDone) {
                transitionAlpha -= frameStep;
                if (transitionAlpha <= 0.0f) {
                    transitionAlpha       = 0.0f;
                    transitionFadeOutDone = true;
                    localWallpaperPath    = pendingWallpaperPath;
                    isDefaultWallpaper    = pendingIsDefault;
                    cropBias              = pendingCropBias;
                    needsRefreshWallpaper = true;
                    transitionPendingLoad = true;
                    transitionLoadAfter   = SystemClock.elapsedRealtime() + 50;
                }
                mCallbacks.requestRender();
            } else if (transitionPendingLoad) {
                if (SystemClock.elapsedRealtime() >= transitionLoadAfter) {
                    transitionPendingLoad = false;
                }
                mCallbacks.requestRender();
            } else {
                transitionAlpha += frameStep;
                if (transitionAlpha >= 1.0f) {
                    transitionAlpha  = 1.0f;
                    transitionActive = false;
                    mCallbacks.requestRender(); // let the shader fade-in check run next frame
                } else {
                    mCallbacks.requestRender();
                }
            }
            transitionProgress = transitionAlpha;

        } else if (currentEffect == TRANSITION_DISSOLVE) {
            // ---- Dissolve crossfade ----
            // previousWallpaper = static opaque backdrop
            // wallpaper (new)   = dissolves in, progress 0→1
            transitionProgress += frameStep;
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                transitionActive   = false;
                if (previousWallpaper != null) {
                    previousWallpaper.destroy();
                    previousWallpaper = null;
                }
                currentEffect = TRANSITION_FADE; // reset so static wallpaper renders plain
                // One more frame so the shader-fade-in check above (which already ran
                // this frame with the stale transitionActive=true) gets to fire.
                mCallbacks.requestRender();
            } else {
                mCallbacks.requestRender();
            }

        } else if (currentEffect == TRANSITION_PIXELATE) {
            // ---- Pixelate — strict two-phase midpoint swap ----
            // Phase 1 (transitionProgress 0.0 → 0.5):
            //   Only previousWallpaper drawn with DRAW_PIXELATE_OUT (sharp → blocky).
            //   localProgress = transitionProgress / 0.5  (0→1)
            // At 0.5: destroy previousWallpaper, load new texture.
            // Phase 2 (transitionProgress 0.5 → 1.0):
            //   Only wallpaper drawn with DRAW_PIXELATE_IN (blocky → sharp).
            //   localProgress = (transitionProgress - 0.5) / 0.5  (0→1)

            // Half-step: each phase gets the full duration, matching fade's feel.
            transitionProgress += 0.5f * frameStep;

            if (!transitionMidpointReached && transitionProgress >= 0.5f) {
                if (!prefetchReady()) {
                    // Decode still running — hold here at the clamped midpoint
                    // progress and keep drawing previousWallpaper (phase 1's last
                    // frame) instead of destroying it and flipping to phase 2 with
                    // no replacement texture yet, which would draw nothing (black
                    // frame) and then force a redundant synchronous decode anyway.
                    transitionProgress = 0.5f;
                    mCallbacks.requestRender();
                } else {
                    // Clamp so phase-2 localProgress starts cleanly at 0
                    transitionProgress        = 0.5f;
                    transitionMidpointReached = true;
                    if (previousWallpaper != null) {
                        previousWallpaper.destroy();
                        previousWallpaper = null;
                    }
                    localWallpaperPath    = pendingWallpaperPath;
                    isDefaultWallpaper    = pendingIsDefault;
                    cropBias              = pendingCropBias;
                    consumePrefetchedWallpaper();
                    // Do not check completion this frame — let phase 2 start next frame
                    mCallbacks.requestRender();
                }
            } else if (transitionMidpointReached && transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                transitionActive   = false;
                currentEffect      = TRANSITION_FADE;
                mCallbacks.requestRender(); // let the shader fade-in check run next frame
            } else {
                mCallbacks.requestRender();
            }

        } else if (currentEffect == TRANSITION_WIPE) {
            // ---- Wipe ----
            // previousWallpaper = static opaque backdrop
            // wallpaper (new)   = wipes in left→right, progress 0→1
            transitionProgress += frameStep;
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                transitionActive   = false;
                if (previousWallpaper != null) {
                    previousWallpaper.destroy();
                    previousWallpaper = null;
                }
                currentEffect = TRANSITION_FADE;
                mCallbacks.requestRender(); // let the shader fade-in check run next frame
            } else {
                mCallbacks.requestRender();
            }

        } else if (currentEffect == TRANSITION_BLUR) {
            // ---- Blur — two-phase midpoint swap ----
            // Phase 1 (0.0 → 0.5): previousWallpaper blurs out to peak softness (DRAW_BLUR_OUT).
            // Phase 2 (0.5 → 1.0): new wallpaper sharpens in from peak blur (DRAW_BLUR_IN).
            // Half-step: each phase gets the full duration, matching fade's feel.
            transitionProgress += 0.5f * frameStep;

            if (!transitionMidpointReached && transitionProgress >= 0.5f) {
                if (!prefetchReady()) {
                    // Decode still running — hold at the midpoint, keep blurring
                    // previousWallpaper at peak softness instead of destroying it
                    // with no replacement ready. See pixelate for the full reasoning.
                    transitionProgress = 0.5f;
                    mCallbacks.requestRender();
                } else {
                    transitionProgress        = 0.5f;
                    transitionMidpointReached = true;
                    if (previousWallpaper != null) {
                        previousWallpaper.destroy();
                        previousWallpaper = null;
                    }
                    localWallpaperPath    = pendingWallpaperPath;
                    isDefaultWallpaper    = pendingIsDefault;
                    cropBias              = pendingCropBias;
                    consumePrefetchedWallpaper();
                    mCallbacks.requestRender();
                }
            } else if (transitionMidpointReached && transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                transitionActive   = false;
                currentEffect      = TRANSITION_FADE;
                mCallbacks.requestRender(); // let the shader fade-in check run next frame
            } else {
                mCallbacks.requestRender();
            }

        } else if (currentEffect == TRANSITION_ZOOM) {
            // ---- Zoom ----
            // previousWallpaper = static opaque backdrop
            // wallpaper (new)   = zooms in from screen centre, progress 0→1
            transitionProgress += frameStep;
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                transitionActive   = false;
                if (previousWallpaper != null) {
                    previousWallpaper.destroy();
                    previousWallpaper = null;
                }
                currentEffect = TRANSITION_FADE;
                mCallbacks.requestRender(); // let the shader fade-in check run next frame
            } else {
                mCallbacks.requestRender();
            }
        }
    }

    /** Draws the wallpaper(s) for the current frame. GL-thread only; reads the
     *  camera matrix (mMVPMatrix) computed by onDrawFrame. Each transition effect
     *  draws its own texture(s); the else branch is the static / plain-fade case
     *  where overlay shaders apply. */
    private void drawWallpaper() {
        if (currentEffect == TRANSITION_PIXELATE) {
            // Pixelate — draw only ONE texture per phase, never both simultaneously
            if (!transitionMidpointReached && previousWallpaper != null) {
                // Phase 1: previousWallpaper pixelates OUT using its own snapshotted
                // camera so the new texture's preA/preB/cropBias do not shift it.
                float localProgress = transitionProgress / 0.5f;
                previousWallpaper.draw(shader, prevMVPMatrix(), 1.0f, localProgress, Wallpaper.DRAW_PIXELATE_OUT);
            } else if (transitionMidpointReached && wallpaper != null) {
                // Phase 2: new wallpaper uses the normal current MVP matrix
                float localProgress = (transitionProgress - 0.5f) / 0.5f;
                wallpaper.draw(shader, mMVPMatrix, 1.0f, localProgress, Wallpaper.DRAW_PIXELATE_IN);
            }
        } else if (currentEffect == TRANSITION_DISSOLVE) {
            // Dissolve — previousWallpaper static backdrop, new wallpaper dissolves in on top
            if (previousWallpaper != null) {
                // Backdrop keeps ITS OWN snapshotted camera — mMVPMatrix already
                // describes the incoming wallpaper's crop/aspect.
                previousWallpaper.draw(shader, prevMVPMatrix(), 1.0f, 1.0f, Wallpaper.DRAW_PLAIN);
            }
            if (wallpaper != null) {
                wallpaper.draw(shader, mMVPMatrix, 1.0f, transitionProgress, Wallpaper.DRAW_DISSOLVE);
            }
        } else if (currentEffect == TRANSITION_WIPE) {
            // Wipe — previousWallpaper static backdrop, new wallpaper wipes in left→right
            if (previousWallpaper != null) {
                // Backdrop keeps ITS OWN snapshotted camera — mMVPMatrix already
                // describes the incoming wallpaper's crop/aspect.
                previousWallpaper.draw(shader, prevMVPMatrix(), 1.0f, 1.0f, Wallpaper.DRAW_PLAIN);
            }
            if (wallpaper != null) {
                wallpaper.draw(shader, mMVPMatrix, 1.0f, transitionProgress, Wallpaper.DRAW_WIPE);
            }
        } else if (currentEffect == TRANSITION_BLUR) {
            // Blur — phase 1: old blurs out to peak softness, phase 2: new sharpens in.
            // Real blur: scene → ¼-res FBO → separable Gaussian → composite over sharp.
            if (!transitionMidpointReached && previousWallpaper != null) {
                float localProgress = transitionProgress / 0.5f;
                drawBlurred(previousWallpaper, prevMVPMatrix(), localProgress);
            } else if (transitionMidpointReached && wallpaper != null) {
                float localProgress = (transitionProgress - 0.5f) / 0.5f;
                drawBlurred(wallpaper, mMVPMatrix, 1.0f - localProgress);
            }
        } else if (currentEffect == TRANSITION_ZOOM) {
            // Zoom — previousWallpaper static backdrop, new wallpaper grows from centre
            if (previousWallpaper != null) {
                // Backdrop keeps ITS OWN snapshotted camera — mMVPMatrix already
                // describes the incoming wallpaper's crop/aspect.
                previousWallpaper.draw(shader, prevMVPMatrix(), 1.0f, 1.0f, Wallpaper.DRAW_PLAIN);
            }
            if (wallpaper != null) {
                wallpaper.draw(shader, mMVPMatrix, 1.0f, transitionProgress, Wallpaper.DRAW_ZOOM);
            }
        } else {
            // Fade or post-transition static draw.
            // The active overlay shader only applies here — the static,
            // non-transitioning case — so two different textures are never
            // mid-composite under it.
            boolean shaderActive = wallpaper != null && shaderFade > 0f && overlayShaders.beginFrame(screenW, screenH);
            if (wallpaper != null) {
                hasLoggedNullWallpaper = false;
                if (shaderActive) {
                    // Render the wallpaper (with its real camera/crop matrix) into an
                    // offscreen capture, THEN draw the shader sampling that — guarantees
                    // it sees exactly the cropped/panned frame the camera produces,
                    // pixel space matched 1:1, no separate UV math.
                    overlayShaders.beginScene();
                    wallpaper.draw(shader, mMVPMatrix, transitionAlpha, transitionProgress, Wallpaper.DRAW_PLAIN);
                    if (shaderFade < 1f) {
                        // Mid-fade the effect composites with partial alpha, so the
                        // plain wallpaper must already be on screen underneath it.
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                        GLES20.glViewport(0, 0, screenW, screenH);
                        wallpaper.draw(shader, mMVPMatrix, transitionAlpha, transitionProgress, Wallpaper.DRAW_PLAIN);
                    }
                    overlayShaders.drawEffect(screenW, screenH, shaderFade);
                    // u_time-driven — keep the effect animating, paced to ~30fps and
                    // suppressed while this engine is hidden.
                    requestShaderFrame();
                } else {
                    wallpaper.draw(shader, mMVPMatrix, transitionAlpha, transitionProgress, Wallpaper.DRAW_PLAIN);
                }
            } else {
                if (!hasLoggedNullWallpaper) {
                    Log.w(TAG, "onDrawFrame: wallpaper is null, skipping draw");
                    hasLoggedNullWallpaper = true;
                }
                // Self-heal: static state with no wallpaper and nothing already
                // scheduled to fix it would otherwise stay black forever. Must match
                // openWallpaperStream()'s own "load the built-in asset" condition
                // exactly — using isDefaultWallpaper alone is wrong, since playlists
                // leave that flag at its stale/default value while a real playlist
                // path is what's actually loaded (see WallpapersListAdapter). And
                // retryHandle already tracks an in-flight async retry for a user/
                // playlist file (openWallpaperStream, 3s delay) — firing this while
                // that's pending clobbers localWallpaperPath with the default asset
                // before the real retry ever runs.
                boolean isDefaultAssetPath = defaultWallpaperPath.equals(localWallpaperPath)
                        || (wallpaperType == TYPE_SINGLE && isDefaultWallpaper);
                boolean retryInFlight = retryHandle != null && !retryHandle.isDone();
                if (engineVisible && isDefaultAssetPath && !needsRefreshWallpaper
                        && !hasPendingRefresh && !retryInFlight && !defaultRecoveryTried) {
                    defaultRecoveryTried = true;
                    Log.w(TAG, "onDrawFrame: retrying default wallpaper load after null texture");
                    needsRefreshWallpaper = true;
                    mCallbacks.requestRender();
                }
            }

            // Snow composites on top of whatever was just drawn — it doesn't
            // sample the wallpaper (no capture/matrix needed, unlike rain/ripple),
            // just alpha-blends a procedural overlay, so it draws unconditionally
            // here rather than through the capture-scene dispatch above.
            if (wallpaper != null && overlayShaders.drawSnow(screenW, screenH, shaderFade)) {
                requestShaderFrame();
            }
        }
    }

    /** Switches the active overlay shader. id must be Constant.SHADER_NONE,
     *  SHADER_RAIN, SHADER_RIPPLE, or SHADER_SNOW — enforced as the only "which
     *  shader is on" state, so at most one can ever be active. */
    void setActiveShader(String id) {
        if (overlayShaders.willChange(id)) {
            // The shader we're leaving still holds a full-screen FBO/texture; free it
            // on the GL thread (releaseInactive keeps only the new active shader).
            overlayShaders.setActiveShader(id);
            pendingShaderRelease = true;
        }
        mCallbacks.requestRender();
    }

    /** Frees ALL shader + Blur GL objects on the GL thread — called when the engine
     *  goes invisible (screen off / another app foreground). ensure() lazily
     *  recompiles them on the next visible frame. */
    void releaseAllShaders() {
        pendingReleaseAll = true;
        pendingShaderRelease = true;
        mCallbacks.requestRender();
    }

    /** Called from the engine's onVisibilityChanged. When this engine's screen is
     *  not showing, animated shaders stop requesting frames so the GL thread idles.
     *  Also frees the wallpaper GL texture(s) while hidden — see pendingWallpaperRelease
     *  — and reloads them lazily from the already-known path/crop the moment this
     *  engine becomes visible again. */
    void setEngineVisible(boolean visible) {
        engineVisible = visible;
        if (visible) {
            // Texture was dropped while hidden (or never loaded yet) — reload it now.
            if (wallpaper == null) needsRefreshWallpaper = true;
            mCallbacks.requestRender();
        } else {
            pendingWallpaperRelease = true;
            mCallbacks.requestRender(); // one more frame so the GL thread can consume it
        }
    }

    /** Requests the next animated-shader frame, paced to ~30fps and suppressed while
     *  the engine is hidden. Under RENDERMODE_WHEN_DIRTY the GL thread only draws
     *  when a render is requested, so NOT requesting is what actually caps the rate:
     *  if the cap hasn't elapsed, schedule a one-shot catch-up render at the next
     *  slot instead of a same-frame re-request (which would run at full refresh). */
    private void requestShaderFrame() {
        if (!engineVisible) return;
        long now = SystemClock.elapsedRealtime();
        long since = now - lastShaderFrameMs;
        if (since >= SHADER_FRAME_INTERVAL_MS) {
            lastShaderFrameMs = now;
            mCallbacks.requestRender();
        } else {
            // Too soon — wake the GL thread once when the next 30fps slot is due.
            scheduler.schedule(mCallbacks::requestRender,
                    SHADER_FRAME_INTERVAL_MS - since, TimeUnit.MILLISECONDS);
        }
    }

    /** Toggles suppression of heavy shaders and gyro bias during battery saver mode. */
    void setPowerSaverActive(boolean active) {
        this.powerSaverActive = active;
        if (active) {
            // Reset gyro bias target to center when power saving is active.
            setOrientationAngle(0, 0);
            // Heavy overlay shaders are suppressed while saving power — free their
            // FBOs/textures too instead of leaving them resident.
            pendingShaderRelease = true;
        }
        mCallbacks.requestRender();
    }

    /** GL-thread only. Frees every overlay shader that is not the active one (and
     *  Blur too when releaseAll). Called from onDrawFrame when a release is pending. */
    private void consumeShaderRelease() {
        if (!pendingShaderRelease) return;
        pendingShaderRelease = false;
        boolean all = pendingReleaseAll;
        pendingReleaseAll = false;

        boolean freed = overlayShaders.releaseInactive(all, powerSaverActive);
        // Blur is only used by the wallpaper transition, not an overlay shader — free
        // it only on a full release (screen off), never on a shader swap.
        if (all && blur != null) { blur.release(); blur = null; freed = true; }
        // TEMP diagnostic — confirm via logcat whether releases actually fire. The
        // meminfo "Graphics" line lags glDelete* (driver keeps freed GPU memory in
        // its own reuse pool), so absence of a meminfo drop does NOT mean this didn't run.
        if (freed) Log.d(TAG, "consumeShaderRelease: freed shaders (all=" + all
                + ", active=" + overlayShaders.activeShaderId() + ", powerSaver=" + powerSaverActive + ")");
    }

    /** GL-thread only. Frees the wallpaper texture(s) when this engine has gone
     *  invisible — the single biggest per-engine GPU allocation, so an engine that
     *  isn't on screen (device asleep, or the other engine is showing) holds none
     *  of it. Cancels any in-flight transition first: a transition mid-flight is
     *  holding previousWallpaper as a second live texture, and there is no visible
     *  frame to finish animating anyway while hidden. Path/crop/playlist fields
     *  are untouched — setEngineVisible(true) flags a plain reload from them. */
    private void consumeWallpaperRelease() {
        if (!pendingWallpaperRelease) return;
        pendingWallpaperRelease = false;
        // Engine became visible again before this frame ran — keep the texture.
        // Without this guard the ordering "setEngineVisible(false) →
        // setEngineVisible(true) (wallpaper still non-null, no reload flagged) →
        // this free" left a permanent black frame on lock/home reactivation.
        if (engineVisible) return;
        if (wallpaper == null && previousWallpaper == null) return;

        transitionActive = false;
        transitionAlpha = 1.0f;
        transitionProgress = 1.0f;
        currentEffect = TRANSITION_FADE;

        if (wallpaper != null) { wallpaper.destroy(); wallpaper = null; }
        if (previousWallpaper != null) { previousWallpaper.destroy(); previousWallpaper = null; }
        wallpaperReleasedWhileHidden = true;
    }

    /** Sets a single parameter (by Constant.ShaderParam.key) for whichever
     *  shader currently owns that key. Booleans are passed as 0f/1f. */
    void setShaderParam(String key, float value) {
        overlayShaders.setParam(key, value);
    }

    /** Forwards a touch-down position (screen pixels) to the active shader, if it
     *  supports touch-driven ripples. Safe to call even when ripple isn't active
     *  or hasn't been GL-initialized yet — becomes a no-op. Called from the
     *  service's onTouchEvent, so this runs on the UI/input thread, not the GL
     *  thread; RippleShader.addTouch() is internally synchronized for that. */
    void addTouchRipple(float screenX, float screenY) {
        if (screenW <= 0 || screenH <= 0) return;
        // Normalize to 0..1, Y-up (gl_FragCoord/u_resolution convention) — screen
        // Y grows downward, GL fragment coords grow upward, so flip here once.
        float u = screenX / screenW;
        float v = 1f - (screenY / screenH);
        overlayShaders.addTouch(u, v);
        mCallbacks.requestRender();
    }

    /**
     * Draws {@code w} sharp, then overlays a real Gaussian blur of it with
     * strength/alpha {@code t} (0 = fully sharp, 1 = peak blur). Falls back to
     * the plain sharp draw if FBO rendering is unavailable.
     */
    private void drawBlurred(Wallpaper w, float[] mvp, float t) {
        w.draw(shader, mvp, 1.0f, 1.0f, Wallpaper.DRAW_PLAIN);
        if (t <= 0.0f) return;
        if (blur == null) blur = new Blur();
        if (!blur.ensure(screenW, screenH)) return;
        blur.beginScene();
        w.draw(shader, mvp, 1.0f, 1.0f, Wallpaper.DRAW_PLAIN);
        blur.blurAndCompose(t, screenW, screenH);
    }

    /** Captures the CURRENT (soon-to-be-old) wallpaper's camera parameters before a
     *  transition overwrites them with the incoming wallpaper's. */
    private void snapshotPrevCamera() {
        prevPreA     = preA;
        prevPreB     = preB;
        prevCropBias = cropBias;
        prevSlack    = Math.max(0f, wallpaperAspectRatio - screenAspectRatio);
    }

    /** Camera matrix for the OUTGOING wallpaper during a transition, built from the
     *  snapshot so the old image keeps its own crop position while cropBias/preA/preB
     *  already describe the incoming one. */
    private float[] prevMVPMatrix() {
        float base = prevPreA * (-2 * scrollOffsetX + 1) + prevCropBias;
        base = Math.max(-prevSlack, Math.min(prevSlack, base));
        float px = base + currentOrientationOffsetX;
        float py = currentOrientationOffsetY;
        float[] view = new float[16];
        float[] mvp = new float[16];
        Matrix.setLookAtM(view, 0, px, py, prevPreB, px, py, 0f, 0f, 1.0f, 0.0f);
        Matrix.multiplyMM(mvp, 0, mProjectionMatrix, 0, view, 0);
        return mvp;
    }

    private void preCalculate() {
        // Guard against zero or invalid aspect ratios
        if (screenAspectRatio == 0 || Float.isNaN(screenAspectRatio) || Float.isInfinite(screenAspectRatio)) {
            preA = Float.NaN;
            preB = Float.NaN;
            return;
        }
        if (wallpaperAspectRatio == 0 || Float.isNaN(wallpaperAspectRatio) || Float.isInfinite(wallpaperAspectRatio)) {
            preA = Float.NaN;
            preB = Float.NaN;
            return;
        }
        if (scrollStep > 0) {
            if (wallpaperAspectRatio > (1 + 1 / (3 * scrollStep)) * screenAspectRatio) {
                scrollRange = 1 + 1 / (3 * scrollStep);
            } else if (wallpaperAspectRatio >= screenAspectRatio) {
                scrollRange = wallpaperAspectRatio / screenAspectRatio;
            } else {
                scrollRange = 1;
            }
        } else {
            scrollRange = 1;
        }
        // ------------------------------------------------------
        preA = screenAspectRatio * (scrollRange - 1);
        // preB = -1f;
        if (screenAspectRatio < 1)
            preB = -1.0f + (biasRange / screenAspectRatio);
        else
            preB = -1.0f + (biasRange * screenAspectRatio);
    }

    // If the surface changes, reset the view
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (height == 0) { // Prevent A Divide By Zero By
            height = 1; // Making Height Equal One
        }

        // Same GL context but new dimensions = device rotation. Re-crop through the
        // user's chosen transition instead of a hard reload — the old path (stale
        // camera for a few frames, then an instant re-cropped texture) read as
        // jitter. cropBias needs no orientation special-casing: the draw-time clamp
        // against the current orientation's slack already bounds it every frame.
        boolean rotated = !contextLost && wallpaper != null && screenW > 0
                && (width != screenW || height != screenH);
        contextLost = false;

        screenAspectRatio = (float) width / (float) height;
        screenW = width;
        screenH = height;

        GLES20.glViewport(0, 0, width, height);

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -0.1f * screenAspectRatio,
                0.1f * screenAspectRatio, -0.1f, 0.1f, 0.1f, 2);

        if (rotated) {
            refreshWallpaper(localWallpaperPath, isDefaultWallpaper, cropBias);
        } else {
            needsRefreshWallpaper = true;
        }
        mCallbacks.requestRender();
    }


    // Setters for the changes in the localWallpaperPath and refresh for the same
    void setOffset(float offsetX, float offsetY) {
        if (scrollMode) {
            scrollOffsetXBackup = offsetX;
            scrollOffsetXQueue.offer(offsetX);
        } else {
            scrollOffsetXBackup = offsetX;
        }
    }
    void setOffsetStep(float offsetStepX, float offsetStepY) {
        if (scrollStep != offsetStepX) {
            scrollStep = offsetStepX;
            preCalculate();
        }
    }

    void setOrientationAngle(float roll, float pitch) {
        float newX = (float) (biasRange * Math.sin(roll));
        float newY = (float) (biasRange * Math.sin(pitch));
        // Deadband against rotation-sensor noise: a phone lying still on a table
        // still emits micro-jitter every sample. Without this, each jitter moves the
        // target enough to clear transitionCal's threshold, waking the GPU 60x/sec
        // for sub-perceptible motion — a real continuous-render battery drain. Only
        // accept a new target once it differs by ~half a pixel of on-screen parallax.
        // ponytail: fixed 0.0002 deadband; derive from screenW/biasRange if a device
        // ever needs it finer, but a constant is plenty here.
        float deadband = 0.0002f;
        if (Math.abs(newX - orientationOffsetX) < deadband
                && Math.abs(newY - orientationOffsetY) < deadband) {
            return;
        }
        orientationOffsetX = newX;
        orientationOffsetY = newY;
    }

    void setNewFaceRotation(int face) {
        // Fire FaceRotationEvent for UI updates
        EventBus.getDefault().post(new FaceRotationEvent(face));
    }

    void setBiasRange(int multiples) {
        if (multiples == 0) {
            stopTransition();
            biasRange = 0f;
            currentOrientationOffsetX = 0f;
            currentOrientationOffsetY = 0f;
            orientationOffsetX = 0f;
            orientationOffsetY = 0f;
        } else {
            biasRange = multiples * MAX_BIAS_RANGE + 0.03f;
            startTransition();
        }
        preCalculate();
        mCallbacks.requestRender();
    }
    void setDelay(int delay) {
        this.delay = delay;
    }
    void setScrollMode(boolean scrollMode) {
        this.scrollMode = scrollMode;
        if (scrollMode)
            scrollOffsetXQueue.offer(scrollOffsetXBackup);
        else {
            scrollOffsetXQueue.clear();
            scrollOffsetXQueue.offer(0.5f);
        }
//        noScroll = false;
    }
    void setLocalWallpaperPath(String name){
        localWallpaperPath = name;
    }
    void setIsDefaultWallpaper(boolean isDefault) {
        isDefaultWallpaper = isDefault;
    }
    void setWallpaperType(int wallpaperType){
        this.wallpaperType = wallpaperType;
    }
    /** Sets the decode/upload bit depth for the NEXT wallpaper load. The caller
     *  triggers a reload if it wants the change applied to the current wallpaper. */
    void setWallpaperConfig(Bitmap.Config config) {
        this.wallpaperConfig = config;
    }

    /** effect is a Constant.TRANSITION_* id (FADE default, DISSOLVE, PIXELATE, WIPE, BLUR, ZOOM). */
    void setTransitionEffect(int effect) {
        chosenEffect = effect;
    }

    // --- Crop-adjust overlay (triple-tap) — called from the service thread ---
    void showCropOverlay() {
        cropOverlayVisible = true;
        mCallbacks.requestRender();
    }
    /** Position shown in the overlay's pill; (0, 0) hides the pill (no playlist). */
    void setPlaylistInfo(int current, int total) {
        playlistCurrent = current;
        playlistTotal = total;
        if (cropOverlayVisible) mCallbacks.requestRender();
    }
    void hideCropOverlay() {
        cropOverlayVisible = false;
        mCallbacks.requestRender();
    }
    /**
     * Nudges the crop window by 5% of the image's horizontal slack per tap
     * (fine-grained — 20 taps from centre to edge).
     * direction +1 = show more of the image's LEFT side (camera x is positive at
     * scroll offset 0, which is the leftmost page — geometry x is mirrored).
     */
    void nudgeCrop(int direction) {
        float slack = Math.max(0f, wallpaperAspectRatio - screenAspectRatio);
        if (slack == 0f) return; // image no wider than screen — nothing to reveal
        cropBias = Math.max(-slack, Math.min(slack, cropBias + direction * slack * 0.05f));
        mCallbacks.requestRender();
    }
    /** Current bias for persisting when the user taps Done. */
    float getCropBias() {
        return cropBias;
    }
    /** Path of the wallpaper currently on screen — the row a saved crop belongs to.
     *  Written on the GL thread; a marginally stale read is harmless for saving. */
    String getCurrentWallpaperPath() {
        return localWallpaperPath;
    }

    // Animation speed multiplier — maps Constant.ANIMATION_SPEED_* to a float factor
    private int animationSpeed = 1; // default = ANIMATION_SPEED_NORMAL

    void setAnimationSpeed(int speed) {
        animationSpeed = speed;
    }

    /** Wall-clock duration in seconds of one full 0→1 progress ramp for the
     *  chosen animation speed. */
    private float durationSeconds() {
        switch (animationSpeed) {
            case 4: return 2.4f;   // 0.25x (ANIMATION_SPEED_QUARTER)
            case 0: return 1.2f;   // 0.5x  (ANIMATION_SPEED_HALF)
            case 2: return 0.3f;   // 2x    (ANIMATION_SPEED_DOUBLE)
            case 3: return 0.15f;  // 3x    (ANIMATION_SPEED_TRIPLE)
            case 5: return 0.075f; // 4x    (ANIMATION_SPEED_STUPID)
            default: return 0.6f;  // 1x    (ANIMATION_SPEED_NORMAL)
        }
    }

    /** Computes this frame's progress step from real elapsed time, so transitions
     *  run at the same wall-clock speed on 60Hz and 120Hz panels. dt is clamped:
     *  a long stall (the midpoint texture decode, or the first frame after idle)
     *  advances progress by at most one normal frame instead of jumping — an
     *  unclamped jump is what made blur/pixelate phase 2 "pop" past its animation. */
    private void updateFrameStep() {
        long now = System.nanoTime();
        float dt = (lastFrameNanos == 0L) ? (1f / 60f) : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        frameStep = Math.min(dt, 1f / 30f) / durationSeconds();
    }



    // refreshes current wallpaper and update canvas
    // Called from service/main thread — only writes volatile fields, no GL state.
    void refreshWallpaper(String wallpaperPath, boolean isDefault, float savedCropBias) {
        pendingWallpaperPath = wallpaperPath;
        pendingIsDefault     = isDefault;
        pendingCropBias      = savedCropBias;
        pendingInstant       = false;
        hasPendingRefresh    = true;   // GL thread picks this up at top of next frame
        mCallbacks.requestRender();
    }

    // Screen-off change from the service — swaps with NO animation, so the new
    // wallpaper is simply there the next time the screen turns on.
    void refreshWallpaperInstant(String wallpaperPath, boolean isDefault, float savedCropBias) {
        if (retryHandle != null) retryHandle.cancel(false);
        loadRetryCount = 0;
        pendingWallpaperPath = wallpaperPath;
        pendingIsDefault     = isDefault;
        pendingCropBias      = savedCropBias;
        pendingInstant       = true;
        hasPendingRefresh    = true;
        mCallbacks.requestRender();
    }

    // External (user-initiated) wallpaper change — resets the boot-retry counter so the
    // new path gets its full quota of retries independent of any previous attempt.
    void refreshWallpaperFresh(String wallpaperPath, boolean isDefault, float savedCropBias) {
        // Any pending retry is now stale — cancel it so it can't fire seconds later
        // and clobber the wallpaper the user just changed to.
        if (retryHandle != null) retryHandle.cancel(false);
        loadRetryCount = 0;
        refreshWallpaper(wallpaperPath, isDefault, savedCropBias);
    }

    // Consumes a pending refresh request atomically on the GL thread.
    // Must be called at the very top of onDrawFrame before any tick or draw logic.
    private void consumePendingRefresh() {
        if (!hasPendingRefresh) return;
        hasPendingRefresh = false;

        // A new refresh always supersedes whatever pixelate/blur prefetch was in
        // flight for a previous, now-abandoned transition — bump the token so that
        // decode's result (whenever it lands) is recognised as stale and its bitmap
        // gets recycled instead of silently sitting in prefetchResult forever, and
        // recycle anything that had ALREADY landed but was never consumed.
        cancelPendingPrefetch();

        if (pendingInstant) {
            // No animation: load the new texture this frame and cancel any
            // transition state. Used for screen-off changes.
            pendingInstant        = false;
            localWallpaperPath    = pendingWallpaperPath;
            isDefaultWallpaper    = pendingIsDefault;
            cropBias              = pendingCropBias;
            needsRefreshWallpaper = true;
            transitionActive      = false;
            transitionAlpha       = 1.0f;
            transitionProgress    = 1.0f;
            currentEffect         = 0;
            if (previousWallpaper != null) {
                previousWallpaper.destroy();
                previousWallpaper = null;
            }
            return;
        }

        // Snapshot chosen effect for this transition
        currentEffect = chosenEffect;
        // NOTE: cropBias is NOT reset here — it switches to pendingCropBias at the
        // same moment each effect swaps to the new texture, so the outgoing image
        // keeps its own crop until it's gone.

        if (currentEffect == TRANSITION_FADE) {
            // Fade: two-phase — fade out current, load new, fade in
            transitionAlpha          = 1.0f;
            transitionProgress       = 0.0f;
            transitionFadeOutDone    = false;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;

        } else if (currentEffect == TRANSITION_DISSOLVE) {
            // Dissolve: keep old texture as static backdrop, load new immediately,
            // animate progress 0→1 (new texture reveals through noise pattern).
            snapshotPrevCamera();
            if (previousWallpaper != null) previousWallpaper.destroy();
            previousWallpaper        = wallpaper;
            wallpaper                = null;
            localWallpaperPath       = pendingWallpaperPath;
            isDefaultWallpaper       = pendingIsDefault;
            cropBias                 = pendingCropBias;
            needsRefreshWallpaper    = true;   // load new texture on next frame
            transitionProgress       = 0.0f;
            transitionAlpha          = 1.0f;
            transitionFadeOutDone    = true;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;

        } else if (currentEffect == TRANSITION_PIXELATE) {
            // Pixelate: two-phase midpoint swap.
            // Snapshot old matrix BEFORE loadTexture() overwrites preA/preB.
            snapshotPrevCamera();
            if (previousWallpaper != null) previousWallpaper.destroy();
            previousWallpaper        = wallpaper;  // old texture drives phase 1
            wallpaper                = null;        // will be loaded at midpoint
            // Do NOT set needsRefreshWallpaper here — load happens at progress=0.5.
            // Kick off the decode NOW instead, on a background thread, so it runs
            // in parallel with phase 1 and is (usually) already done by the midpoint.
            prefetchWallpaperForMidpoint();
            transitionProgress       = 0.0f;
            transitionAlpha          = 1.0f;
            transitionFadeOutDone    = true;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;

        } else if (currentEffect == TRANSITION_WIPE) {
            // Wipe: keep old texture as static backdrop, load new immediately,
            // animate progress 0→1 (new texture sweeps in left→right).
            snapshotPrevCamera();
            if (previousWallpaper != null) previousWallpaper.destroy();
            previousWallpaper        = wallpaper;
            wallpaper                = null;
            localWallpaperPath       = pendingWallpaperPath;
            isDefaultWallpaper       = pendingIsDefault;
            cropBias                 = pendingCropBias;
            needsRefreshWallpaper    = true;
            transitionProgress       = 0.0f;
            transitionAlpha          = 1.0f;
            transitionFadeOutDone    = true;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;

        } else if (currentEffect == TRANSITION_BLUR) {
            // Blur: two-phase midpoint swap (same pattern as pixelate).
            // Snapshot old matrix so the blur-out phase uses the correct camera position.
            snapshotPrevCamera();
            if (previousWallpaper != null) previousWallpaper.destroy();
            previousWallpaper        = wallpaper;  // old texture drives phase 1 (blur out)
            wallpaper                = null;        // will be loaded at midpoint
            prefetchWallpaperForMidpoint(); // decode in parallel with phase 1 — see pixelate
            transitionProgress       = 0.0f;
            transitionAlpha          = 1.0f;
            transitionFadeOutDone    = true;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;

        } else if (currentEffect == TRANSITION_ZOOM) {
            // Zoom: keep old texture as static backdrop, load new immediately,
            // animate progress 0→1 (new texture grows from screen centre).
            snapshotPrevCamera();
            if (previousWallpaper != null) previousWallpaper.destroy();
            previousWallpaper        = wallpaper;
            wallpaper                = null;
            localWallpaperPath       = pendingWallpaperPath;
            isDefaultWallpaper       = pendingIsDefault;
            cropBias                 = pendingCropBias;
            needsRefreshWallpaper    = true;
            transitionProgress       = 0.0f;
            transitionAlpha          = 1.0f;
            transitionFadeOutDone    = true;
            transitionPendingLoad    = false;
            transitionMidpointReached = false;
        }

        transitionActive = true;
    }


    // Calculate the transition offsets and refresh smooth effect in canvas
    private void transitionCal() {
        boolean needRefresh = false;
//        Log.d(TAG, "transitionCal: DELAY:" + delay);
        if (Math.abs(currentOrientationOffsetX - orientationOffsetX) > .0001
                || Math.abs(currentOrientationOffsetY - orientationOffsetY) > .0001) {

            if(delay == 1) {
                currentOrientationOffsetX += (orientationOffsetX - currentOrientationOffsetX) * 0.8f;
                currentOrientationOffsetY += (orientationOffsetY - currentOrientationOffsetY) * 0.8f;
            } else {
                float transitionStep = REFRESH_RATE / LiveWallpaperService.SENSOR_RATE;
                float tinyOffsetX = (orientationOffsetX - currentOrientationOffsetX)
                        / (transitionStep * delay);
                float tinyOffsetY = (orientationOffsetY - currentOrientationOffsetY)
                        / (transitionStep * delay);
                currentOrientationOffsetX += tinyOffsetX;
                currentOrientationOffsetY += tinyOffsetY;
            }
            EventBus.getDefault().post(new BiasChangeEvent(currentOrientationOffsetX / biasRange,
                    currentOrientationOffsetY / biasRange));
            needRefresh = true;
        }
        if (!scrollOffsetXQueue.isEmpty()) {
            //noinspection ConstantConditions
            scrollOffsetX = scrollOffsetXQueue.poll();
            needRefresh = true;
        }
        if (needRefresh) mCallbacks.requestRender();
    }


    // Create and loads new Wallpaper from the required settings
    private void loadTexture() {
        // cropBitmap() decodes a full-res bitmap then allocates a second scaled/
        // cropped one per change — tens of MB of native churn. recycle() frees the
        // backing buffers but the gc reclaims the intermediate objects promptly so
        // total memory doesn't climb across changes. This is load-bearing, not cargo.
        System.gc();
        DecodedWallpaper decoded = decodeCurrentWallpaper();
        if (decoded == null) {
            // A corrupt user/playlist file must enter the same retry/fallback chain as
            // a missing one — before this, a failed decode with no texture on screen
            // (e.g. wake after the hidden-engine release) stayed black forever. The
            // built-in asset is exempt: if IT won't decode the build is broken and
            // retrying can only loop.
            boolean isDefaultAsset = defaultWallpaperPath.equals(localWallpaperPath)
                    || (wallpaperType == TYPE_SINGLE && isDefaultWallpaper);
            if (!isDefaultAsset) scheduleRetryOrReportFailure("decode failed");
            return;
        }
        uploadDecodedWallpaper(decoded);
    }

    /** Off-GL-thread-safe: opens the stream and decodes/crops the bitmap for
     *  whatever localWallpaperPath/isDefaultWallpaper/wallpaperType/wallpaperConfig
     *  are RIGHT NOW. Callers that run this on a background thread must snapshot
     *  those fields as needed beforehand — this method only reads, never writes,
     *  renderer state, so it's safe to run off the GL thread. */
    private DecodedWallpaper decodeCurrentWallpaper() {
        InputStream is = openWallpaperStream();
        if (is == null) {
            Log.e(TAG, "decodeCurrentWallpaper: InputStream is null, cannot load wallpaper");
            return null;
        }
        DecodedWallpaper decoded = cropBitmap(is);
        try {
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "decodeCurrentWallpaper: IOException closing InputStream", e);
        }
        if (decoded == null) {
            Log.e(TAG, "decodeCurrentWallpaper: cropBitmap returned null, cannot create wallpaper");
        }
        return decoded;
    }

    /** GL-thread only: uploads an already-decoded bitmap as the new wallpaper
     *  texture and applies the aspect ratio cropBitmap() computed for it (scrollRange
     *  is recomputed by preCalculate() below, same as the original synchronous path).
     *  This is the cheap half of what loadTexture() used to do in one call — safe
     *  to run at a transition midpoint. */
    private void uploadDecodedWallpaper(DecodedWallpaper decoded) {
        wallpaperAspectRatio = decoded.aspectRatio;
        if (wallpaper != null)
            wallpaper.destroy();
        wallpaper = new Wallpaper(decoded.bitmap, shader != null ? shader.maxTextureSize : 2048);
        defaultRecoveryTried = false; // loaded fine — re-arm the self-heal for next time
        loadRetryCount = 0; // full success (open AND decode) — reset the retry quota
        mCallbacks.onWallpaperLoadSucceeded();
        preCalculate();
        System.gc(); // reclaim the decode/scale bitmap churn now, not on the next collection
    }

    /** Fully self-contained decode: takes every value it needs as a parameter
     *  instead of reading instance fields, so it's safe to run on `scheduler`
     *  (a plain background thread) while the GL thread keeps animating phase 1
     *  of a pixelate/blur transition. Never calls scheduleRetryOrReportFailure —
     *  see openWallpaperStream(reportFailure=false); a failed prefetch just falls
     *  back to the synchronous path at the midpoint. */
    private DecodedWallpaper decodeWallpaperAt(String path, boolean isDefault,
                                                float targetAspectRatio, int targetScreenH) {
        InputStream is = openWallpaperStream(path, isDefault, false);
        if (is == null) return null;
        DecodedWallpaper decoded = cropBitmap(is, targetAspectRatio, targetScreenH);
        try {
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "decodeWallpaperAt: IOException closing InputStream", e);
        }
        return decoded;
    }

    /** Kicks off the pixelate/blur midpoint's decode early — right as phase 1
     *  starts — on `scheduler` instead of the GL thread, so the expensive
     *  BitmapFactory.decodeStream + crop/scale work runs in parallel with the
     *  phase-1 animation instead of stalling it. Result is picked up by
     *  consumePrefetchedWallpaper() at the midpoint.
     *  requestToken/prefetchResultToken guard against a superseded prefetch (rapid
     *  wallpaper changes, or the effect getting cancelled) writing a stale result:
     *  only the result whose token still matches the latest request is honoured;
     *  any other decoded bitmap is recycled immediately so it can't leak. */
    private void prefetchWallpaperForMidpoint() {
        prefetchResult = null;
        final int token = ++prefetchRequestToken;
        prefetchResultToken = -1;
        final String path = pendingWallpaperPath;
        final boolean isDefault = pendingIsDefault;
        final float targetAspectRatio = screenAspectRatio;
        final int targetScreenH = screenH;
        scheduler.execute(() -> {
            DecodedWallpaper decoded = decodeWallpaperAt(path, isDefault, targetAspectRatio, targetScreenH);
            if (token != prefetchRequestToken) {
                // Superseded while decoding — this result is stale, drop it so it
                // can't be applied later and can't leak the bitmap it holds.
                if (decoded != null) decoded.bitmap.recycle();
                return;
            }
            // decoded == null (failure) leaves prefetchResult null — consumePrefetchedWallpaper()
            // reads that as "prefetch failed" and falls back to the synchronous retry path.
            prefetchResult = decoded;
            prefetchResultToken = token;
        });
    }

    /** GL-thread only. True once the background decode for the CURRENT prefetch
     *  request has landed (success or failure) — the midpoint gate calls this
     *  before advancing past phase 1, so previousWallpaper is never destroyed
     *  before its replacement is actually ready. */
    private boolean prefetchReady() {
        return prefetchResultToken == prefetchRequestToken;
    }

    /** GL-thread only, called at the pixelate/blur midpoint — ONLY once
     *  prefetchReady() is true. Applies the background-decoded bitmap; if the
     *  prefetch failed (missing/corrupt file) falls back to the original
     *  synchronous loadTexture() path so the existing retry/fallback chain
     *  (scheduleRetryOrReportFailure) runs. */
    private void consumePrefetchedWallpaper() {
        if (prefetchResult != null) {
            DecodedWallpaper decoded = prefetchResult;
            prefetchResult = null;
            uploadDecodedWallpaper(decoded);
        } else {
            needsRefreshWallpaper = true;
        }
    }

    /** Invalidates any in-flight or landed-but-unconsumed pixelate/blur prefetch.
     *  Bumping the token makes a still-decoding background task's result get
     *  recycled instead of applied once it lands (see prefetchWallpaperForMidpoint);
     *  a result that already landed is recycled right here — otherwise its Bitmap
     *  would never be freed (nothing else references or consumes it once the
     *  transition it was for has been abandoned). GL-thread only. */
    private void cancelPendingPrefetch() {
        prefetchRequestToken++;
        if (prefetchResult != null) {
            prefetchResult.bitmap.recycle();
            prefetchResult = null;
        }
    }

    // Opens the right stream for the current path. The built-in default lives in
    // assets — a FileInputStream can never open the file:///android_asset/ URI, so
    // that path must go through AssetManager regardless of wallpaper type.
    private InputStream openWallpaperStream() {
        return openWallpaperStream(localWallpaperPath, isDefaultWallpaper, true);
    }

    /** reportFailure=false is used by the background prefetch: it must not call
     *  scheduleRetryOrReportFailure (that reads/writes instance retry state from a
     *  non-GL thread) — a prefetch failure just falls back to the synchronous
     *  loadTexture() path at the midpoint, which retries/reports normally. */
    private InputStream openWallpaperStream(String path, boolean isDefault, boolean reportFailure) {
        if (defaultWallpaperPath.equals(path)
                || (wallpaperType == TYPE_SINGLE && isDefault)) {
            // Always load THIS service's own default asset (home vs. lock), never assume home's.
            String assetName = Constant.DEFAULT_LOCK_LOCAL_PATH.equals(defaultWallpaperPath)
                    ? Constant.DEFAULT_LOCK_WALLPAPER_NAME
                    : Constant.DEFAULT_WALLPAPER_NAME;
            try {
                AssetFileDescriptor fileDescriptor = mContext.getAssets().openFd(assetName);
                return fileDescriptor.createInputStream();
            } catch (IOException e) {
                Log.e(TAG, "openWallpaperStream: IOException loading default wallpaper", e);
                return null;
            }
        }
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "openWallpaperStream: FileNotFoundException for path: " + path, e);
            if (reportFailure) scheduleRetryOrReportFailure("file missing");
            return null;
        }
    }

    // Shared self-heal for a user/playlist wallpaper that failed to load — the file
    // is missing (storage not mounted yet post-boot, entry deleted) or present but
    // undecodable (corrupt, or still being written by the crop worker). Retry the
    // SAME path a few times, then hand the decision to the service via the callback:
    // it advances the playlist past the dead entry, or falls back to the default
    // wallpaper — either beats a permanently black frame.
    private void scheduleRetryOrReportFailure(String reason) {
        if (loadRetryCount < MAX_LOAD_RETRIES) {
            loadRetryCount++;
            final String retryPath = localWallpaperPath;
            final boolean retryDef = isDefaultWallpaper;
            final float retryBias  = cropBias;
            Log.w(TAG, "scheduleRetryOrReportFailure (" + reason + "): retry " + loadRetryCount
                    + "/" + MAX_LOAD_RETRIES + " in " + RETRY_DELAY_MS + "ms for path: " + retryPath);
            if (retryHandle != null) retryHandle.cancel(false);
            retryHandle = scheduler.schedule(() -> refreshWallpaper(retryPath, retryDef, retryBias),
                    RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            Log.e(TAG, "scheduleRetryOrReportFailure (" + reason + "): giving up after "
                    + MAX_LOAD_RETRIES + " retries for path: " + localWallpaperPath);
            loadRetryCount = 0;
            mCallbacks.onWallpaperLoadFailed(localWallpaperPath);
        }
    }
    /** Reads only volatile/immutable state (wallpaperConfig) plus the screen
     *  dimensions passed in as locals — never touches non-volatile renderer
     *  fields, so this overload is safe to call from a background thread. The
     *  GL-thread call site below passes its own current screenAspectRatio/screenH. */
    private DecodedWallpaper cropBitmap(InputStream is) {
        return cropBitmap(is, screenAspectRatio, screenH);
    }

    private DecodedWallpaper cropBitmap(InputStream is, float targetAspectRatio, int targetScreenH) {
        // Decode at the user-chosen bit depth. RGB_565 halves the resulting GL
        // texture memory (GLUtils.texImage2D picks format from the bitmap config);
        // createBitmap/createScaledBitmap below preserve this config, so setting it
        // once here covers the whole chain.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = wallpaperConfig;
        Bitmap src = BitmapFactory.decodeStream(is, null, opts);
        if (src == null) {
            Log.e(TAG, "cropBitmap: BitmapFactory.decodeStream returned null");
            return null;
        }
        final float width = src.getWidth();
        final float height = src.getHeight();
        if (height == 0) {
            Log.e(TAG, "cropBitmap: height is zero, cannot calculate aspect ratio");
            src.recycle();
            return null;
        }
        float aspectRatio = width / height;
        if (aspectRatio < targetAspectRatio) {
            Bitmap tmp = Bitmap.createBitmap(src, 0,
                    (int) (height - width / targetAspectRatio) / 2,
                    (int) width, (int) (width / targetAspectRatio));
            src.recycle();
            if (tmp.getHeight() > 1.1 * targetScreenH) {
                Bitmap result = Bitmap.createScaledBitmap(tmp,
                        (int) (1.1 * targetScreenH * targetAspectRatio),
                        (int) (1.1 * targetScreenH), true);
                tmp.recycle();
                return new DecodedWallpaper(result, aspectRatio);
            } else
                return new DecodedWallpaper(tmp, aspectRatio);
        } else {
            if (src.getHeight() > 1.1 * targetScreenH) {
                Bitmap result = Bitmap.createScaledBitmap(src,
                        (int) (1.1 * targetScreenH * aspectRatio),
                        (int) (1.1 * targetScreenH), true);
                src.recycle();
                return new DecodedWallpaper(result, aspectRatio);
            } else
                return new DecodedWallpaper(src, aspectRatio);
        }
    }


    // Internal Callback for refreshing the current canvas
    interface Callbacks {
        void requestRender();
        /** Every retry for the current path is exhausted (file missing or
         *  undecodable). The service decides what heals the screen: next playlist
         *  entry, or the default wallpaper. Called from the GL/scheduler thread —
         *  implementers must hop to their own thread. */
        default void onWallpaperLoadFailed(String path) {}
        /** A wallpaper texture loaded successfully (GL thread). */
        default void onWallpaperLoadSucceeded() {}
    }
}
