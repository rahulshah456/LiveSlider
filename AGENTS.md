# LiveSlider – Agent Guide

## Project Overview
Android live wallpaper app (`minSdk 28`, `targetSdk 36`) that renders an OpenGL ES 2.0 parallax effect driven by the device's rotation sensor, with a slideshow/playlist system on top. Package root: `com.droid2developers.liveslider`.

## Architecture: Component Map

```
RotationSensor.java  ──EventBus──►  LiveWallpaperRenderer.java  ◄── LiveWallpaperService.java
   (sensor/calibration)               (OpenGL ES 2 rendering)         (GLWallpaperService engine)
                                                                              │
                          SharedPreferences (all settings)  ◄───────────────┘
                          Room DB ◄── Repositories ◄── ViewModels ◄── Fragments/Activities
                          WorkManager (PlaylistWorker.kt) — pre-processes/compresses images
```

- **`live_wallpaper/`** – Java only. `LiveWallpaperService` extends `GLWallpaperService` (from `net.rbgrn.android.glwallpaperservice`). Its inner `ParallaxEngine` owns the renderer and sensor.
- **`models/`** – EventBus event POJOs: `BiasChangeEvent` (parallax x/y offset, clamped –1..1) and `FaceRotationEvent` (device orientation face). The renderer subscribes to these via `@Subscribe`.
- **`database/`** – Room DB (`LiveWallpaperDatabase.java`), two entities (`LocalWallpaper.java` / `Playlist.kt`), two DAOs, two Kotlin repositories, two ViewModels.
- **`background/PlaylistWorker.kt`** – `CoroutineWorker` that compresses and copies user-picked images into internal storage per playlist. Triggered via `processPlaylistWorker()` in `WorkerUtils.kt`.
- **`utils/Constant.java`** – Single source of truth for all magic strings and ints used across layers (pref keys, type flags, transition IDs, etc.).

## Critical Initialization Order in `LiveWallpaperService`
Renderer fields **must** be pre-populated before `setRenderer()` is called; otherwise the GL thread fires `onSurfaceChanged` with null paths and falls back to the default asset wallpaper:
```java
renderer.setIsDefaultWallpaper(...);
renderer.setLocalWallpaperPath(...);
renderer.setWallpaperType(...);
setRenderer(renderer);   // GL thread starts HERE
```

## Key Constants & Sentinel Values (`utils/Constant.java`)
| Symbol | Value | Meaning |
|---|---|---|
| `PLAYLIST_NONE` | `"none"` | No playlist selected |
| `DEFAULT` / `CUSTOM` | `"Default"` / `"Custom"` | PlaylistId used for built-in / single-image mode |
| `TYPE_SINGLE/AUTO/SLIDESHOW` | `0/1/2` | Wallpaper mode stored in pref key `"type"` |
| `TRANSITION_FADE…ZOOM` | `0–5` | Pref key `"transition_effect"` |
| `DEFAULT_LOCAL_PATH` | `file:///android_asset/wallpaper_default.jpg` | Fallback image path |
| `WORKER_KEY_PLAYLIST_ID` | `"playlist_id"` | WorkManager input data key |

## Inter-Component Communication Patterns
- **Sensor → Renderer**: `RotationSensor` posts `BiasChangeEvent` / `FaceRotationEvent` via EventBus; `LiveWallpaperRenderer` consumes them on the GL thread.
- **Settings → Service**: `ParallaxEngine` implements `SharedPreferences.OnSharedPreferenceChangeListener`; every pref change is handled live without restarting the service.
- **UI → DB**: Fragments use `PlaylistViewModel` / `WallpaperViewModel` (both `AndroidViewModel`) observing `LiveData` from Room repositories.
- **Slideshow timer**: A `Handler` + `Runnable` (`slideshow`) inside `ParallaxEngine` fires `incrementWallpaper()` + `changeWallpaper()` based on `prefs.getLong("slideshow_timer", …)`.

## Mixed Java / Kotlin Conventions
- Core wallpaper pipeline (`live_wallpaper/`, `models/`) is **Java**.
- Everything added later (Kotlin): `PlaylistWorker`, `PlaylistViewModel`, `Playlist` entity, repositories, most fragments and activities.
- Room uses `annotationProcessor` (not KSP) for both Room compiler and Glide compiler — do **not** switch to `ksp`.

## Build & Dependency Notes
- Version catalog: `gradle/libs.versions.toml`. Add new deps there, reference with `libs.*` in `app/build.gradle`.
- Release build uses `minifyEnabled true` + `shrinkResources true`; check `proguard-rules.pro` when adding new reflective libraries.
- No internet permission is declared — network-dependent features are not supported.

## Wallpaper Storage Flow
1. User picks images → `ChangeWallpaperActivity` / `SlideshowFragment` enqueues `PlaylistWorker` via `processPlaylistWorker(playlistId, tag)`.
2. `PlaylistWorker` loads via Glide, compresses with `me.shouheng.compress` (Compressor strategy), writes to internal storage using `FileUtil`.
3. `LiveWallpaperService` reads `LocalWallpaper.getLocalPath()` from DB at runtime and passes it to `renderer.refreshWallpaperFresh(path, isDefault)`.

## Key Files to Read First
- `live_wallpaper/LiveWallpaperService.java` – engine lifecycle, all pref wiring, dual-playlist logic
- `live_wallpaper/LiveWallpaperRenderer.java` – OpenGL state machine, transition system, retry logic
- `live_wallpaper/RotationSensor.java` – calibration modes (`DEFAULT/VERTICAL/DYNAMIC`), face detection, animation
- `live_wallpaper/Wallpaper.java` – inline GLSL shaders for all 6 transition effects
- `utils/Constant.java` – all shared constants

