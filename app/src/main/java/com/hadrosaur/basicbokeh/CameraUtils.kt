package com.hadrosaur.basicbokeh

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import com.hadrosaur.basicbokeh.MainActivity.Companion.LOG_TAG
import com.hadrosaur.basicbokeh.MainActivity.Companion.ORIENTATIONS

fun smallestFocalLength(focalLengths: FloatArray) : Float = focalLengths.min()
        ?: MainActivity.INVALID_FOCAL_LENGTH

fun largestAperture(apertures: FloatArray) : Float = apertures.max()
        ?: MainActivity.NO_APERTURE

fun focalLengthMinDeltaFromNormal(focalLengths: FloatArray) : Float
        = focalLengths.minBy { Math.abs(it - MainActivity.NORMAL_FOCAL_LENGTH) } ?: Float.MAX_VALUE

fun setAutoFlash(activity: Activity, camera: CameraDevice, requestBuilder: CaptureRequest.Builder?) {
    val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    try {
        val characteristics = manager.getCameraCharacteristics(camera.id)
        // Check if the flash is supported.
        val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        val isFlashSupported = available ?: false

        if (isFlashSupported) {
            requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    } catch (e: Exception) {
        //Do nothing
    }
}

fun getOrientation(params: CameraParams, rotation: Int): Int {
    // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
    // We have to take that into account and rotate JPEG properly.
    // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
    // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.

    Log.d(LOG_TAG, "Orientation: sensor: " + params.characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
            + " and current rotation: " + ORIENTATIONS.get(rotation))
    val sensorRotation: Int = params.characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    return (ORIENTATIONS.get(rotation) + sensorRotation + 270) % 360
}

