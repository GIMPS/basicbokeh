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
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 1
    private val REQUEST_FILE_WRITE_PERMISSION = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camViewModel = ViewModelProviders.of(this).get(CamViewModel::class.java)
        cameraParams = camViewModel.getCameraParams()

        //Load OpenCV for Bokeh effects
        if (!OpenCVLoader.initDebug()) {
            Logd("OpenCV failed to load!")
        } else {
            Logd("OpenCV loaded successfully!")
        }


        if (checkCameraPermissions())
            initializeCameras(this)

        buttonTakePhoto.setOnClickListener {
            if (wideAngleId == normalLensId) {
                twoLens.isTwoLensShot = false
                MainActivity.cameraParams.get(wideAngleId).let {
                    if (it?.isOpen == true) {
                        MainActivity.Logd("In onClick. Taking Photo on wide-angle camera: " + wideAngleId)
                        takePicture(this, it)
                    }
                }
            } else {
                twoLens.reset()
                twoLens.isTwoLensShot = true
                MainActivity.cameraParams.get(wideAngleId).let {
                    if (it?.isOpen == true) {
                        MainActivity.Logd("In onClick. Taking Photo on wide-angle camera: " + wideAngleId)
                        takePicture(this, it)
                    }
                }
                MainActivity.cameraParams.get(normalLensId).let {
                    if (it?.isOpen == true) {
                        MainActivity.Logd("In onClick. Taking Photo on normal-lens camera: " + normalLensId)
                        takePicture(this, it)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //We now have permission, restart the app
                    val intent = getIntent()
                    finish()
                    startActivity(intent)
                } else {
                }
                return
            }
            REQUEST_FILE_WRITE_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //We now have permission, restart the app
                    val intent = getIntent()
                    finish()
                    startActivity(intent)
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
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                !== PackageManager.PERMISSION_GRANTED) {
            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_FILE_WRITE_PERMISSION)
            return false

        }

        return true
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
        params.backgroundThread?.quitSafely()
        try {
            params.backgroundThread?.join()
            params.backgroundThread = null
            params.backgroundHandler = null
        } catch (e: InterruptedException) {
            Logd( "Interrupted while shutting background thread down: " + e.message)
        }
    }

    override fun onResume() {
        super.onResume()
        Logd( "In onResume")

        for (tempCameraParams in cameraParams) {
            startBackgroundThread(tempCameraParams.value)

            if (tempCameraParams.value.previewTextureView?.isAvailable == true) {
                camera2OpenCamera(this, tempCameraParams.value)
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
        const val NO_APERTURE: Float = 0f
        const val FIXED_FOCUS_DISTANCE: Float = 0f
        val INVALID_FOCAL_LENGTH: Float = Float.MAX_VALUE
        var NUM_CAMERAS = 0
        var logicalCamId = ""
        var wideAngleId = ""
        var normalLensId = ""

        lateinit var camViewModel:CamViewModel
        lateinit var cameraParams: HashMap<String, CameraParams>
        val twoLens: TwoLensCoordinator = TwoLensCoordinator()

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

        fun Logd(message: String) {
            if (camViewModel.getShouldOutputLog().value ?: true)
                Log.d(LOG_TAG, message)
        }

    }
}
