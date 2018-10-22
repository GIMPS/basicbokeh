package com.hadrosaur.basicbokeh

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.hadrosaur.basicbokeh.MainActivity.Companion.LOG_TAG
import com.hadrosaur.basicbokeh.MainActivity.Companion.ORIENTATIONS
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import android.hardware.camera2.CameraCharacteristics
import com.hadrosaur.basicbokeh.CameraController.CameraStateCallback
import com.hadrosaur.basicbokeh.CameraController.FocusCaptureSessionCallback
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import kotlin.collections.ArrayList


fun initializeCameras(activity: MainActivity) {
    val manager = activity.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
    try {
        MainActivity.NUM_CAMERAS = manager.cameraIdList.size

        for (cameraId in manager.cameraIdList) {
            val tempCameraParams = CameraParams().apply {

                val cameraChars = manager.getCameraCharacteristics(cameraId)
                val cameraCapabilities = cameraChars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                for (capability in cameraCapabilities) {
                    when (capability) {
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> hasMulti = true
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> hasManualControl = true
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> hasDepth = true
                    }
                }

                MainActivity.Logd("Camera " + cameraId + " of " + MainActivity.NUM_CAMERAS)

                id = cameraId
                isOpen = false
                hasFlash = cameraChars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                isFront = CameraCharacteristics.LENS_FACING_FRONT == cameraChars.get(CameraCharacteristics.LENS_FACING)
                characteristics = cameraChars
                focalLengths = cameraChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                smallestFocalLength = smallestFocalLength(focalLengths)
                minDeltaFromNormal = focalLengthMinDeltaFromNormal(focalLengths)

                apertures = cameraChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                largestAperture = largestAperture(apertures)
                minFocusDistance = cameraChars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

                //Bokeh calculations
                if (Build.VERSION.SDK_INT >= 28) {
                    lensDistortion = cameraChars.get(CameraCharacteristics.LENS_DISTORTION)
                    intrinsicCalibration = cameraChars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
                    poseRotation = cameraChars.get(CameraCharacteristics.LENS_POSE_ROTATION)
                    poseTranslation = cameraChars.get(CameraCharacteristics.LENS_POSE_TRANSLATION)
                }

                for (focalLength in focalLengths) {
                    MainActivity.Logd("In " + id + " found focalLength: " + focalLength)
                }
                MainActivity.Logd("Smallest smallestFocalLength: " + smallestFocalLength)
                MainActivity.Logd("minFocusDistance: " + minFocusDistance)

                for (aperture in apertures) {
                    MainActivity.Logd("In " + id + " found aperture: " + aperture)
                }
                MainActivity.Logd("Largest aperture: " + largestAperture)

                if (hasManualControl) {
                    MainActivity.Logd("Has Manual, minFocusDistance: " + minFocusDistance)
                }

                effects = cameraChars.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                hasSepia = effects.contains(CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                hasMono = effects.contains(CameraMetadata.CONTROL_EFFECT_MODE_MONO)

                hasAF = minFocusDistance != MainActivity.FIXED_FOCUS_DISTANCE //If camera is fixed focus, no AF

                if (hasSepia)
                    MainActivity.Logd("WE HAVE Sepia!")
                if (hasMono)
                    MainActivity.Logd("WE HAVE Mono!")

                //Facical detection
                val faceDetectModes: IntArray = cameraChars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                for (mode in faceDetectModes) {
                    Logd("This cam has face detect mode: " + mode)
                    bestFaceDetectionMode = mode //assume array is assorted ascending
                }

                if (hasDepth)
                    Logd("This camera has depth output!")

                capturedPhoto = activity.imagePhoto

                cameraCallback = CameraStateCallback(this, activity)
                captureCallback = FocusCaptureSessionCallback(activity, this)
//                    textureListener = TextureListener(this,this@MainActivity)

                imageAvailableListener = ImageAvailableListener(activity, this)

                if (Build.VERSION.SDK_INT >= 28) {
                    physicalCameras = cameraChars.physicalCameraIds
                }

                //Get image capture sizes
                val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (map != null) {
                    maxSize = Collections.max(Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                            CompareSizesByArea())
                    minSize = Collections.min(Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                            CompareSizesByArea())

                    setupImageReader(activity, this)
                } //if map != null
            }
            MainActivity.cameraParams.put(cameraId, tempCameraParams)
        } //for all camera devices

        //Default to using the first camera for everything
        if (!MainActivity.cameraParams.keys.isEmpty()) {
            MainActivity.logicalCamId = MainActivity.cameraParams.keys.first()
            MainActivity.wideAngleId = MainActivity.logicalCamId
            MainActivity.normalLensId = MainActivity.logicalCamId
        }

        //Next, if we have a front-facing camera, use the first one
        for (tempCameraParams in MainActivity.cameraParams) {
            if (tempCameraParams.value.isFront) {
                MainActivity.wideAngleId = tempCameraParams.value.id ?: MainActivity.cameraParams.keys.first()
                MainActivity.normalLensId = MainActivity.wideAngleId
            }
        }

        //Determine the first multi-camera logical camera (front or back)
        //Then choose the shortest focal for the wide-angle background camera
        //And closest to 50mm for the "normal lens"
        for (tempCameraParams in MainActivity.cameraParams) {
            if (tempCameraParams.value.hasMulti) {
                MainActivity.logicalCamId = tempCameraParams.key
                if(!tempCameraParams.value.physicalCameras.isEmpty()) {
                    //Determine the widest angle lens
                    MainActivity.wideAngleId = tempCameraParams.value.physicalCameras.first()
                    for (physicalCamera in tempCameraParams.value.physicalCameras) {
                        val tempLens: Float = MainActivity.cameraParams.get(physicalCamera)?.smallestFocalLength ?: MainActivity.INVALID_FOCAL_LENGTH
                        val minLens: Float = MainActivity.cameraParams.get(MainActivity.wideAngleId)?.smallestFocalLength ?: MainActivity.INVALID_FOCAL_LENGTH
                        if (tempLens < minLens)
                            MainActivity.wideAngleId = physicalCamera
                    }

                    //Determine the closest to "normal" that is not the wide angle lens
                    MainActivity.normalLensId = tempCameraParams.value.physicalCameras.first()
                    for (physicalCamera in tempCameraParams.value.physicalCameras) {
                        if (physicalCamera.equals(MainActivity.wideAngleId))
                            continue

                        val tempLens: Float = MainActivity.cameraParams.get(physicalCamera)?.minDeltaFromNormal ?: MainActivity.INVALID_FOCAL_LENGTH
                        val normalLens: Float = MainActivity.cameraParams.get(MainActivity.normalLensId)?.minDeltaFromNormal ?: MainActivity.INVALID_FOCAL_LENGTH
                        if (tempLens < normalLens)
                            MainActivity.normalLensId = physicalCamera
                    }
                }

                MainActivity.Logd("Found a multi: " + MainActivity.logicalCamId + " with wideAngle: " + MainActivity.wideAngleId + "(" + MainActivity.cameraParams.get(MainActivity.wideAngleId)?.smallestFocalLength
                        + ") and normal: " + MainActivity.normalLensId + " (" + MainActivity.cameraParams.get(MainActivity.normalLensId)?.minDeltaFromNormal + ")")
                break //Use the first multi-camera
            }
        }

        MainActivity.Logd("Setting logical: " + MainActivity.logicalCamId + " with wideAngle: " + MainActivity.wideAngleId + "(" + MainActivity.cameraParams.get(MainActivity.wideAngleId)?.smallestFocalLength
                + ") and normal: " + MainActivity.normalLensId + " (" + MainActivity.cameraParams.get(MainActivity.normalLensId)?.minDeltaFromNormal + ")")

        MainActivity.cameraParams.get(MainActivity.wideAngleId)?.previewTextureView  = activity.texture_background

        // If no multi-camera, only open one stream
        if (MainActivity.wideAngleId != MainActivity.normalLensId) {
            MainActivity.cameraParams.get(MainActivity.normalLensId)?.previewTextureView  = activity.texture_foreground
        }

        //TODO: DYnamically blur preview, doing something like this: https://stackoverflow.com/questions/34972250/android-dynamically-blur-surface-with-video
    } catch (accessError: CameraAccessException) {
        accessError.printStackTrace()
    }
}


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

fun setupImageReader(activity: MainActivity, params: CameraParams) {
    with (params) {
        params.imageReader?.close()
        imageReader = ImageReader.newInstance(maxSize.width, maxSize.height,
                ImageFormat.JPEG, /*maxImages*/10)
        imageReader?.setOnImageAvailableListener(
                imageAvailableListener, backgroundHandler)
    }
}

