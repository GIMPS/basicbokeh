package com.hadrosaur.basicbokeh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        camViewModel = ViewModelProviders.of(this).get(CamViewModel::class.java)
        cameraParams = camViewModel.getCameraParams()

        if (checkCameraPermissions())
            initializeCameras()
    }

    fun initializeCameras() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            NUM_CAMERAS = manager.cameraIdList.size

            for (cameraId in manager.cameraIdList) {
                val tempCameraParams = CameraParams().apply {

                    val cameraChars = manager.getCameraCharacteristics(cameraId)
                    val front_facing = if (CameraCharacteristics.LENS_FACING_FRONT == cameraChars.get(CameraCharacteristics.LENS_FACING)) "Front facing" else "World facing"
                    val has_flash = if (cameraChars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) "Has flash" else "No flash"
                    var has_multi = false
                    var has_multi_string = "No"

                    val multi_camera = cameraChars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    for (i in multi_camera.indices) {
                        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA == multi_camera[i]) {
                            has_multi = true
                            has_multi_string = "Yes"
                        }
                    }

                    Log.d(LOG_TAG, "Camera " + cameraId + " of " + NUM_CAMERAS)

                    id = cameraId
                    hasMulti = has_multi
                    isOpen = false
                    hasFlash = cameraChars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    isFront = CameraCharacteristics.LENS_FACING_FRONT == cameraChars.get(CameraCharacteristics.LENS_FACING)
                    characteristics = cameraChars
                    focalLengths = cameraChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    smallestFocalLength = smallestFocalLength(focalLengths)
                    minDeltaFromNormal = focalLengthMinDeltaFromNormal(focalLengths)

                    effects = cameraChars.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                    hasSepia = effects.contains(CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                    hasMono = effects.contains(CameraMetadata.CONTROL_EFFECT_MODE_MONO)

                    if (hasSepia)
                        Log.d(LOG_TAG, "WE HAVE Sepia!")
                    if (hasMono)
                        Log.d(LOG_TAG, "WE HAVE Mono!")

                    capturedPhoto = imagePhoto

                    cameraCallback = CameraStateCallback(this, this@MainActivity)
                    captureCallback = CaptureSessionCallback(this@MainActivity, this)
//                    textureListener = TextureListener(this,this@MainActivity)

                    if (Build.VERSION.SDK_INT >= 28) {
                        physicalCameras = cameraChars.physicalCameraIds
                    }

                    val map = cameraChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (map != null) {
                        // For still image captures, we use the largest available size.
                        val largest = Collections.max(
                                Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                                CompareSizesByArea())

                        Log.d(LOG_TAG, "Map is not null, setting image reader listener")
                        imageReader = ImageReader.newInstance(largest.width, largest.height,
                                ImageFormat.JPEG, /*maxImages*/3)
                                imageReader?.setOnImageAvailableListener(
                                        imageAvailableListener, backgroundHandler)
                    } //if map != null
                }
                cameraParams.put(cameraId, tempCameraParams)
            } //for all camera devices

            //Default to using the first camera for everything
            if (!cameraParams.keys.isEmpty()) {
                logicalCamId = cameraParams.keys.first()
                wideAngleId = logicalCamId
                normalLensId = logicalCamId
            }

            //Determine the first multi-camera logical camera
            //Then choose the shortest focal for the wide-angle background camera
            //And closest to 50mm for the "normal lens"
            for (tempCameraParams in cameraParams) {
                if (tempCameraParams.value.hasMulti) {
                    logicalCamId = tempCameraParams.key
                    if(!tempCameraParams.value.physicalCameras.isEmpty()) {
                        //Determine the widest angle lens
                        wideAngleId = tempCameraParams.value.physicalCameras.first()
                        for (physicalCamera in tempCameraParams.value.physicalCameras) {
                            val tempLens: Float = cameraParams.get(physicalCamera)?.smallestFocalLength ?: MainActivity.INVALID_FOCAL_LENGTH
                            val minLens: Float = cameraParams.get(wideAngleId)?.smallestFocalLength ?: MainActivity.INVALID_FOCAL_LENGTH
                            if (tempLens < minLens)
                                wideAngleId = physicalCamera
                        }

                        //Determine the closest to "normal" that is not the wide angle lens
                        normalLensId = tempCameraParams.value.physicalCameras.first()
                        for (physicalCamera in tempCameraParams.value.physicalCameras) {
                            if (physicalCamera.equals(wideAngleId))
                                continue

                            val tempLens: Float = cameraParams.get(physicalCamera)?.minDeltaFromNormal ?: MainActivity.INVALID_FOCAL_LENGTH
                            val normalLens: Float = cameraParams.get(normalLensId)?.minDeltaFromNormal ?: MainActivity.INVALID_FOCAL_LENGTH
                            if (tempLens < normalLens)
                                normalLensId = physicalCamera
                        }
                    }

                    Log.d(LOG_TAG,"Found a multi: " + logicalCamId + " with wideAngle: " + wideAngleId + "(" + cameraParams.get(wideAngleId)?.smallestFocalLength
                        + ") and normal: " + normalLensId + " (" + cameraParams.get(normalLensId)?.minDeltaFromNormal + ")")
                    break //Use the first multi-camera
                }
            }

            Log.d(LOG_TAG,"Setting logical: " + logicalCamId + " with wideAngle: " + wideAngleId + "(" + cameraParams.get(wideAngleId)?.smallestFocalLength
                    + ") and normal: " + normalLensId + " (" + cameraParams.get(normalLensId)?.minDeltaFromNormal + ")")

            cameraParams.get(wideAngleId)?.previewTextureView  = texture_background

            // If no multi-camera, only open one stream
            if (wideAngleId != normalLensId) {
//                cameraParams.get(normalLensId)?.previewTextureView  = texture_foreground
            }

            //TODO: DYnamically blur preview, doing something like this: https://stackoverflow.com/questions/34972250/android-dynamically-blur-surface-with-video
        } catch (accessError: CameraAccessException) {
            accessError.printStackTrace()
        }

        buttonTakePhoto.setOnClickListener {
            cameraParams.get(wideAngleId).let {
                if (it?.isOpen == true) {
                    Log.d(LOG_TAG, "In onClick. Taking Photo on camera: " + wideAngleId)
                    takePicture(this, it)
                }
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int,
                                   permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeCameras()
                } else {
                }
                return
            }
        }
    }

    fun checkCameraPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                !== PackageManager.PERMISSION_GRANTED) {

            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION)
            return false
        } else {
            return true
        }
    }

    private fun startBackgroundThread(params: CameraParams) {
        if (params.backgroundThread == null) {
            params.backgroundThread = HandlerThread(LOG_TAG).apply {
                this.start()
                params.backgroundHandler = Handler(this.getLooper())
            }
        }
    }


    private fun stopBackgroundThread(params: CameraParams) {
//        params.backgroundThread?.quitSafely()
        params.backgroundThread?.quit()
        params.backgroundThread = null
        params.backgroundHandler = null
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_TAG, "In onResume")

        for (tempCameraParams in cameraParams) {
            startBackgroundThread(tempCameraParams.value)

            if (tempCameraParams.value.previewTextureView?.isAvailable == true) {
                openCamera(tempCameraParams.value, this)
            } else {
                tempCameraParams.value.previewTextureView?.surfaceTextureListener =
                        TextureListener(tempCameraParams.value, this)
            }
        }
    }

    override fun onPause() {
        for (tempCameraParams in cameraParams) {
            closeCamera(tempCameraParams.value, this)
            stopBackgroundThread(tempCameraParams.value)
        }
        super.onPause()
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        const val NORMAL_FOCAL_LENGTH: Float = 50f
        const val GAUSSIAN_BLUR_RADIUS: Float = 25f
        const val BLUR_SCALE_FACTOR: Float = 0.25f
        val INVALID_FOCAL_LENGTH: Float = Float.MAX_VALUE
        var NUM_CAMERAS = 0
        var logicalCamId = ""
        var wideAngleId = ""
        var normalLensId = ""

        lateinit var camViewModel:CamViewModel
        lateinit var cameraParams: HashMap<String, CameraParams>

        val ORIENTATIONS = SparseIntArray()

        const val SAVE_FILE = "saved_photo.jpg"

        val LOG_TAG = "BasicBokeh"

        init {
            System.loadLibrary("native-lib")
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }
}
