package com.droid2developers.liveslider.live_wallpaper;

import static com.droid2developers.liveslider.utils.Constant.FACE_LANDSCAPE_RIGHT;
import static com.droid2developers.liveslider.utils.Constant.FACE_LANDSCAPE_LEFT;
import static com.droid2developers.liveslider.utils.Constant.FACE_PORTRAIT_UP;
import static com.droid2developers.liveslider.utils.Constant.FACE_PORTRAIT_DOWN;
import static com.droid2developers.liveslider.utils.Constant.FACE_FLAT_UP;
import static com.droid2developers.liveslider.utils.Constant.FACE_FLAT_DOWN;
import static com.droid2developers.liveslider.utils.Constant.FACE_UNKNOWN;
import static com.droid2developers.liveslider.utils.Constant.CALIBRATION_DEFAULT;
import static com.droid2developers.liveslider.utils.Constant.CALIBRATION_VERTICAL;
import static com.droid2developers.liveslider.utils.Constant.CALIBRATION_DYNAMIC;
import static com.droid2developers.liveslider.utils.Constant.getFaceName;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;
import android.util.Log;
import com.droid2developers.liveslider.R;

/**
 * Rotation sensor that handles phone orientation detection and parallax calculations
 * Supports three calibration modes: Default, Vertical, and Dynamic
 */
public class RotationSensor implements SensorEventListener {
    private static final String TAG = RotationSensor.class.getSimpleName();
    private static final float ROLL_THRESHOLD = (float) Math.PI / 4;
    private static final float FLAT_THRESHOLD = (float) Math.PI / 3;
    private static final int FACE_STABLE_COUNT = 5;
    private static final long FACE_DETECTION_DEBOUNCE_MS = 100;

    private final int sampleRate;
    private final Callback callback;
    private final SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean listenerRegistered = false;
    private int calibrationMode = CALIBRATION_DEFAULT;

    private float[] initialRotationMatrix = null;
    private int currentPhoneFace = -1;
    private int candidatePhoneFace = -1;
    private int candidateStableCount = 0;
    private long lastFaceDetectionCheck = 0;

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
    }

    void register() {
        if (listenerRegistered) return;

        initialRotationMatrix = null;
        currentPhoneFace = FACE_UNKNOWN;
        candidatePhoneFace = FACE_UNKNOWN;
        candidateStableCount = 0;

        boolean success = sensorManager.registerListener(this, rotationSensor, 1000000 / sampleRate);
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

        // Handle different calibration modes
        switch (calibrationMode) {
            case CALIBRATION_DEFAULT:
                handleDefaultCalibration();
                break;
            case CALIBRATION_VERTICAL:
                handleVerticalCalibration();
                break;
            case CALIBRATION_DYNAMIC:
                handleDynamicCalibration();
                break;
        }
    }

    // Original simple implementation - sets initial matrix once and calculates angle changes
    private void handleDefaultCalibration() {
        if (initialRotationMatrix == null) {
            initialRotationMatrix = rotationMatrix.clone();
            return;
        }

        float[] angleChange = new float[3];
        SensorManager.getAngleChange(angleChange, rotationMatrix, initialRotationMatrix);
        
        if (isValidAngles(angleChange)) {
            callback.onSensorChanged(angleChange);
        }
    }

    // Vertical mode - only updates matrix when switching between portrait up/down faces
    private void handleVerticalCalibration() {
        // Extract orientation angles for vertical face detection
        SensorManager.getOrientation(rotationMatrix, orientationValues);

        // Detect if phone is in top or bottom orientation
        int detectedVerticalFace = detectVerticalFace(orientationValues);

        // Handle vertical face changes with stability checking
        handleVerticalFaceDetection(detectedVerticalFace);

        // Calculate parallax if we have established reference
        if (initialRotationMatrix != null) {
            calculateParallax();
        }
    }

    // Dynamic mode - full face detection with automatic matrix updates for all orientations
    private void handleDynamicCalibration() {
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

    // Detects only portrait orientations for vertical calibration mode
    private int detectVerticalFace(float[] orientation) {
        float roll = orientation[2];
        float absRoll = Math.abs(roll);

        if (absRoll < ROLL_THRESHOLD) {
            return FACE_PORTRAIT_UP;
        } else if (absRoll > Math.PI - ROLL_THRESHOLD) {
            return FACE_PORTRAIT_DOWN;
        }

        // If not in vertical orientation, return current face or default to portrait up
        return currentPhoneFace != FACE_UNKNOWN ? currentPhoneFace : FACE_PORTRAIT_UP;
    }

    // Handles vertical face detection with stability checking
    private void handleVerticalFaceDetection(int detectedFace) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastFaceDetectionCheck < FACE_DETECTION_DEBOUNCE_MS) {
            return;
        }

        lastFaceDetectionCheck = currentTime;

        // Only process portrait up/down face changes
        if ((detectedFace == FACE_PORTRAIT_UP || detectedFace == FACE_PORTRAIT_DOWN) &&
            detectedFace != currentPhoneFace) {

            if (candidatePhoneFace == detectedFace) {
                candidateStableCount++;

                if (candidateStableCount >= FACE_STABLE_COUNT) {
                    switchToNewFace(detectedFace);
                }
            } else {
                candidatePhoneFace = detectedFace;
                candidateStableCount = 1;
            }
        } else if (detectedFace == currentPhoneFace) {
            // Reset candidate when returning to current face
            candidatePhoneFace = FACE_UNKNOWN;
            candidateStableCount = 0;
        }
    }

    // Detects all phone orientations for dynamic calibration mode
    private int detectPhoneFace(float[] orientation) {
        float pitch = orientation[1];
        float roll = orientation[2];

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

    // Manages face change detection with debouncing and stability requirements
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

    // Updates to new phone face and establishes new reference matrix
    private void switchToNewFace(int newFace) {
        Log.i(TAG, "Face changed to: " + getFaceName(newFace));
        currentPhoneFace = newFace;
        candidatePhoneFace = FACE_UNKNOWN;
        candidateStableCount = 0;
        initialRotationMatrix = rotationMatrix.clone();
        callback.onFaceChanged(newFace);
    }

    // Calculates parallax movement using angle differences from reference orientation
    private void calculateParallax() {
        float[] angleChange = new float[3];
        SensorManager.getAngleChange(angleChange, rotationMatrix, initialRotationMatrix);

        if (isValidAngles(angleChange)) {
            callback.onSensorChanged(angleChange);
        } else {
            Log.w(TAG, "Invalid angle changes detected, skipping parallax");
        }
    }

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

    /**
     * Sets the calibration mode for the rotation sensor
     * @param mode The calibration mode (CALIBRATION_DEFAULT, CALIBRATION_VERTICAL, or CALIBRATION_DYNAMIC)
     */
    public void setCalibrationMode(int mode) {
        if (mode != calibrationMode) {
            calibrationMode = mode;

            // Reset calibration state when mode changes
            initialRotationMatrix = null;
            currentPhoneFace = FACE_UNKNOWN;
            candidatePhoneFace = FACE_UNKNOWN;
            candidateStableCount = 0;
        }
    }

    /**
     * Gets the current calibration mode
     * @return The current calibration mode
     */
    public int getCalibrationMode() {
        return calibrationMode;
    }

    public interface Callback {
        void onSensorChanged(float[] angle);
        void onFaceChanged(int face);
    }
}
