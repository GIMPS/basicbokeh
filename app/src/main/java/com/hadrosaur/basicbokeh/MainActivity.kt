package com.hadrosaur.basicbokeh

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.*
import android.preference.PreferenceManager
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
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
        sharedPrefs =  PreferenceManager.getDefaultSharedPreferences(this)

        toggleRotationLock(true)

        //Load OpenCV for Bokeh effects
        if (!OpenCVLoader.initDebug()) {
            Logd("OpenCV failed to load!")
        } else {
            Logd("OpenCV loaded successfully!")
        }

        if (checkCameraPermissions())
            initializeCameras(this)

        //TODO: can this logic be simplified?  4 cases: dual cam, dual cam calibration, dual cam but single shot, single cam/single shot
        buttonTakePhoto.setOnClickListener {
            buttonTakePhoto.visibility = View.GONE
            progress_take_photo.visibility = View.VISIBLE

            if (PrefHelper.getIntermediate(this)) {
                imageIntermediate1.setImageResource(android.R.color.transparent);
                imageIntermediate2.setImageResource(android.R.color.transparent);
                imageIntermediate3.setImageResource(android.R.color.transparent);
                imageIntermediate4.setImageResource(android.R.color.transparent);
                imagePhoto.setImageResource(android.R.color.transparent);
            }

            if (!(camViewModel.getDoDualCamShot().value ?: false)
                || wideAngleId == normalLensId) {
                twoLens.reset()
                twoLens.isTwoLensShot = false
                singleLens.reset()
                singleLens.isSingleLensShot = true

                if (!dualCamLogicalId.equals("") && cameraParams.get(dualCamLogicalId)?.isOpen == true) {
                    MainActivity.Logd("In onClick. Two cameras but taking single photo.")
                    val logicalParams = cameraParams.get(dualCamLogicalId)
                    if (null != logicalParams)
                        takePicture(this, logicalParams)

                } else {
                    MainActivity.cameraParams.get(wideAngleId).let {
                        if (it?.isOpen == true) {
                            MainActivity.Logd("In onClick. Only one camera, taking Photo on wide-angle camera: " + wideAngleId)
                            twoLens.reset()
                            twoLens.isTwoLensShot = false
                            singleLens.reset()
                            singleLens.isSingleLensShot = true
                            if (null != it)
                                takePicture(this, it)
                        }
                    }
                }

            } else {
                if (PrefHelper.getCalibrationMode(this)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    var counter = 0
                    val NUM_PHOTOS: Long = 30
                    val DELAY: Long = 5
                    val timer = object : CountDownTimer(NUM_PHOTOS * DELAY * 1000, DELAY * 1000) {
                        override fun onFinish() {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            text_calibration_counter.setText("")
                        }
                        override fun onTick(millisUntilFinished: Long) {
                            counter++
                            text_calibration_counter.setText("" + counter + "/" + NUM_PHOTOS)
                            singleLens.reset()
                            singleLens.isSingleLensShot = false
                            twoLens.reset()
                            twoLens.isTwoLensShot = true
                            MainActivity.cameraParams.get(dualCamLogicalId).let {
                                if (it?.isOpen == true) {
                                    MainActivity.Logd("In onClick. Taking Dual Cam Photo on logical camera: " + dualCamLogicalId)
                                    takePicture(this@MainActivity, it)
                                }
                            }
                        }
                   }.start()
                } else {
                    singleLens.reset()
                    singleLens.isSingleLensShot = false
                    twoLens.reset()
                    twoLens.isTwoLensShot = true
                    MainActivity.cameraParams.get(dualCamLogicalId).let {
                        if (it?.isOpen == true) {
                            MainActivity.Logd("In onClick. Taking Dual Cam Photo on logical camera: " + dualCamLogicalId)
                            takePicture(this@MainActivity, it)
                        }
                    }
                }
            }
        }

        //Set up mode switch
        switch_mode.setOnCheckedChangeListener { switch, isChecked ->
            camViewModel.getDoDualCamShot().value = isChecked
            PrefHelper.setDualCam(this, isChecked)
        }

        switch_mode.isChecked = PrefHelper.getDualCam(this)

        val modeToggleObserver = object : Observer<Boolean> {
            override fun onChanged(t: Boolean?) {
                switch_mode.isChecked = t ?: false
            }
        }
        camViewModel.getDoDualCamShot().observe(this, modeToggleObserver)

        //Set up show intermediates switch
        switch_intermediate.setOnCheckedChangeListener { switch, isChecked ->
            camViewModel.getShowIntermediate().value = isChecked
            PrefHelper.setIntermediates(this, isChecked)
            toggleIntermediateImages(isChecked)
        }

        toggleIntermediateImages(PrefHelper.getIntermediate(this))
        switch_intermediate.isChecked = PrefHelper.getIntermediate(this)

        val intermediateToggleObserver = object : Observer<Boolean> {
            override fun onChanged(t: Boolean?) {
                switch_intermediate.isChecked = t ?: true
            }
        }
        camViewModel.getShowIntermediate().observe(this, intermediateToggleObserver)

        /*
        val image = BitmapFactory.decodeResource(getResources(), R.drawable.ambush);
//        val mask = BitmapFactory.decodeResource(getResources(), R.drawable.ambush_filter_trans);
        val mask = BitmapFactory.decodeResource(getResources(), R.drawable.ambush_filter);

        val normalizedMask = hardNormalizeDepthMap(this, mask)
        WriteFile(this, normalizedMask, "NormalizedMask")

        val nicelyMasked = applyMask(this, image, normalizedMask)
        WriteFile(this, nicelyMasked, "NicelyMasked")

        var background = sepiaFilter(this, image)
        background = gaussianBlur(this, background, BLUR_SCALE_FACTOR)
        background = gaussianBlur(this, background, BLUR_SCALE_FACTOR)
        WriteFile(this, background, "Background")

        val finalImage = pasteBitmap(this, background, nicelyMasked, Rect(0, 0, background.width, background.height))
        WriteFile(this, finalImage, "FinalImage")
        imagePhoto.setImageBitmap(finalImage)
*/
    }//onCreate

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                val settingsIntent = Intent(this, SettingsActivity::class.java)
                startActivity(settingsIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
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
//            params.backgroundThread = null
//            params.backgroundHandler = null
        } catch (e: InterruptedException) {
            Logd( "Interrupted while shutting background thread down: " + e.message)
        }
    }

    override fun onResume() {
        super.onResume()
        Logd( "In onResume")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        for (tempCameraParams in cameraParams) {
            //In 28+ we use Executors so don't need the background thread
            if (28 > Build.VERSION.SDK_INT)
                startBackgroundThread(tempCameraParams.value)
/*
            if (tempCameraParams.value.previewTextureView?.isAvailable == true) {
                camera2OpenCamera(this, tempCameraParams.value)
            } else {
                tempCameraParams.value.previewTextureView?.surfaceTextureListener =
                        TextureListener(tempCameraParams.value, this)
            }
*/
        }
    }

    override fun onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        for (tempCameraParams in cameraParams) {
//            closeCamera(tempCameraParams.value, this)

            //In 28+ we use Executors so don't need the background thread
            if (28 > Build.VERSION.SDK_INT)
                stopBackgroundThread(tempCameraParams.value)
        }
        super.onPause()
    }


    fun toggleRotationLock(lockRotation: Boolean = true) {
        //Lock the screen orientation during a test so the camera doesn't get re-initialized mid-capture
        if (lockRotation) {
            val currentOrientation = resources.configuration.orientation
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
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
        var dualCamLogicalId = ""
        var logicalCamId = ""
        var wideAngleId = ""
        var normalLensId = ""

        lateinit var camViewModel:CamViewModel
        lateinit var cameraParams: HashMap<String, CameraParams>
        lateinit var sharedPrefs: SharedPreferences

        val twoLens: TwoLensCoordinator = TwoLensCoordinator()
        val singleLens: SingleLensCoordinator = SingleLensCoordinator()

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
            if (PrefHelper.getLog(sharedPrefs))
                Log.d(LOG_TAG, message)
//            if (camViewModel.getShouldOutputLog().value ?: true)
        }

    }

    fun toggleIntermediateImages(showIntermediate: Boolean) {
        if (showIntermediate) {
            imageIntermediate1.visibility = View.VISIBLE
            imageIntermediate2.visibility = View.VISIBLE
            imageIntermediate3.visibility = View.VISIBLE
            imageIntermediate4.visibility = View.VISIBLE
        } else {
            imageIntermediate1.visibility = View.GONE
            imageIntermediate2.visibility = View.GONE
            imageIntermediate3.visibility = View.GONE
            imageIntermediate4.visibility = View.GONE
        }
    }
}
