package com.droid2developers.liveslider.live_wallpaper;

import static com.droid2developers.liveslider.utils.Constant.FACE_LANDSCAPE_RIGHT;
import static com.droid2developers.liveslider.utils.Constant.FACE_LANDSCAPE_LEFT;
import static com.droid2developers.liveslider.utils.Constant.FACE_PORTRAIT_UP;
import static com.droid2developers.liveslider.utils.Constant.FACE_PORTRAIT_DOWN;
import static com.droid2developers.liveslider.utils.Constant.FACE_FLAT_UP;
import static com.droid2developers.liveslider.utils.Constant.FACE_FLAT_DOWN;
import static com.droid2developers.liveslider.utils.Constant.FACE_UNKNOWN;
import static com.droid2developers.liveslider.utils.Constant.getFaceName;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;
import android.util.Log;
import com.droid2developers.liveslider.R;

public class RotationSensor implements SensorEventListener {
    private static final String TAG = RotationSensor.class.getSimpleName();

    // Face detection thresholds
    private static final float ROLL_THRESHOLD = (float) Math.PI / 4;    // 45 degrees
    private static final float FLAT_THRESHOLD = (float) Math.PI / 3;    // 60 degrees for flat detection

    // Stability requirements to prevent rapid face switching
    private static final int FACE_STABLE_COUNT = 5;
    private static final long FACE_DETECTION_DEBOUNCE_MS = 100;

    private final int sampleRate;
    private final Callback callback;
    private final SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean listenerRegistered = false;

    // Face detection state tracking
    private float[] initialRotationMatrix = null;
    private int currentPhoneFace = -1;
    private int candidatePhoneFace = -1;
    private int candidateStableCount = 0;
    private long lastFaceDetectionCheck = 0;

    // Sensor data storage
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationValues = new float[3];

    RotationSensor(Context context, Callback callback, int sampleRate) {
        this.sampleRate = sampleRate;
        this.callback = callback;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        if (rotationSensor == null) {
            Log.e(TAG, "Rotation vector sensor not available");
            Toast.makeText(context, context.getText(R.string.toast_sensor_error), Toast.LENGTH_LONG).show();
        }

        Log.d(TAG, "RotationSensor initialized with orientation-based face detection");
    }

    void register() {
        if (listenerRegistered) return;

        // Reset face detection state
        initialRotationMatrix = null;
        currentPhoneFace = FACE_UNKNOWN;
        candidatePhoneFace = FACE_UNKNOWN;
        candidateStableCount = 0;

        boolean success = sensorManager.registerListener(this,
                rotationSensor, 1000000 / sampleRate);
        listenerRegistered = success;

        if (!success) {
            Log.e(TAG, "Failed to register rotation sensor");
        }
    }

    void unregister() {
        if (!listenerRegistered) return;
        sensorManager.unregisterListener(this);
        listenerRegistered = false;
        initialRotationMatrix = null;
        currentPhoneFace = FACE_UNKNOWN;
    }

    public void recalibrate() {
        initialRotationMatrix = null;
        currentPhoneFace = FACE_UNKNOWN;
        candidatePhoneFace = FACE_UNKNOWN;
        candidateStableCount = 0;
        Log.d(TAG, "Manual recalibration triggered");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }

        // Convert sensor data to rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

        if (!isValidMatrix(rotationMatrix)) {
            Log.w(TAG, "Invalid rotation matrix detected, skipping frame");
            return;
        }

        // Extract orientation angles (azimuth, pitch, roll) from rotation matrix
        SensorManager.getOrientation(rotationMatrix, orientationValues);

        // Detect phone face based on pitch and roll angles
        int detectedFace = detectPhoneFace(orientationValues);

        // Handle face changes with stability checking to prevent rapid switching
        handleFaceDetection(detectedFace);

        // Calculate parallax effect if we have established a reference orientation
        if (initialRotationMatrix != null) {
            calculateParallax();
        }
    }

    // Analyze orientation angles to determine which face of the phone is active
    private int detectPhoneFace(float[] orientation) {
        float pitch = orientation[1];    // Forward/backward tilt
        float roll = orientation[2];     // Sideways tilt

        // Check for flat orientations first (high pitch values indicate flat positioning)
        if (Math.abs(pitch) > FLAT_THRESHOLD) {
            return pitch > 0 ? FACE_FLAT_DOWN : FACE_FLAT_UP;
        }

        // For non-flat orientations, use roll angle to determine portrait/landscape
        float absRoll = Math.abs(roll);

        if (absRoll < ROLL_THRESHOLD) {
            return FACE_PORTRAIT_UP;
        } else if (absRoll > Math.PI - ROLL_THRESHOLD) {
            return FACE_PORTRAIT_DOWN;
        } else {
            return roll > 0 ? FACE_LANDSCAPE_RIGHT : FACE_LANDSCAPE_LEFT;
        }
    }

    // Manage face change detection with debouncing and stability requirements
    private void handleFaceDetection(int detectedFace) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastFaceDetectionCheck < FACE_DETECTION_DEBOUNCE_MS) {
            return;
        }
        lastFaceDetectionCheck = currentTime;

        // Process face change candidates with stability checking
        if (detectedFace != currentPhoneFace) {
            if (candidatePhoneFace == detectedFace) {
                candidateStableCount++;

                if (candidateStableCount >= FACE_STABLE_COUNT) {
                    switchToNewFace(detectedFace);
                }
            } else {
                candidatePhoneFace = detectedFace;
                candidateStableCount = 1;
            }
        } else {
            candidatePhoneFace = FACE_UNKNOWN;
            candidateStableCount = 0;
        }
    }

    // Switch to new phone face and establish new reference matrix for parallax calculations
    private void switchToNewFace(int newFace) {
        Log.i(TAG, "Face changed to: " + getFaceName(newFace));
        currentPhoneFace = newFace;
        candidatePhoneFace = FACE_UNKNOWN;
        candidateStableCount = 0;
        initialRotationMatrix = rotationMatrix.clone();
        callback.onFaceChanged(newFace);
    }

    // Calculate parallax movement using angle differences from reference orientation
    private void calculateParallax() {
        float[] angleChange = new float[3];
        SensorManager.getAngleChange(angleChange, rotationMatrix, initialRotationMatrix);

        if (isValidAngles(angleChange)) {
            callback.onSensorChanged(angleChange);
        } else {
            Log.w(TAG, "Invalid angle changes detected, skipping parallax");
        }
    }

    // Utility methods for validation and information
    private boolean isValidMatrix(float[] matrix) {
        for (float value : matrix) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidAngles(float[] angles) {
        for (float angle : angles) {
            if (Float.isNaN(angle) || Float.isInfinite(angle)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            Log.w(TAG, "Sensor accuracy low: " + accuracy);
        }
    }

    public interface Callback {
        void onSensorChanged(float[] angle);
        void onFaceChanged(int face);
    }
}
