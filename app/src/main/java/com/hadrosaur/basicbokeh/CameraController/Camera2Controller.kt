package com.hadrosaur.basicbokeh

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import java.util.*


import android.hardware.camera2.CameraAccessException
import android.view.TextureView
import com.hadrosaur.basicbokeh.CameraController.CameraStateCallback
import com.hadrosaur.basicbokeh.CameraController.FocusCaptureSessionCallback
import com.hadrosaur.basicbokeh.CameraController.FocusCaptureSessionCallback.Companion.STATE_PICTURE_TAKEN
import com.hadrosaur.basicbokeh.CameraController.FocusCaptureSessionCallback.Companion.STATE_WAITING_PRECAPTURE
import com.hadrosaur.basicbokeh.CameraController.PreviewSessionStateCallback
import com.hadrosaur.basicbokeh.CameraController.StillCaptureSessionCallback
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import com.hadrosaur.basicbokeh.MainActivity.Companion.cameraParams

fun createCameraPreviewSession(activity: MainActivity, camera: CameraDevice, params: CameraParams) {
    Logd("In createCameraPreviewSession.")
    if (!params.isOpen) {
//        camera2Abort(activity, params, testConfig)
        return
    }

    try {
        val texture = params.previewTextureView?.surfaceTexture

        if (null == texture)
            return

        val surface = Surface(texture)

        if (null == surface)
            return

        params.previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        params.previewBuilder?.addTarget(surface)

        val imageSurface = params.imageReader?.surface
        if (null == imageSurface)
            return

        // Here, we create a CameraCaptureSession for camera preview.
        camera.createCaptureSession(Arrays.asList(surface, imageSurface),
                PreviewSessionStateCallback(activity, params), null)
    } catch (e: CameraAccessException) {
        e.printStackTrace()
    } catch (e: IllegalStateException) {
        Logd("createCameraPreviewSession IllegalStateException, aborting: " + e)
        //camera2Abort(activity, params, testConfig)
    }
}

fun camera2OpenCamera(activity: MainActivity, params: CameraParams?) {
    if (null == params)
        return

    val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
        //We might be running a new test so make sure our callbacks match the test config
        params.cameraCallback = CameraStateCallback(params, activity)
        params.captureCallback = FocusCaptureSessionCallback(activity, params)

        Logd("openCamera: " + params.id)
        manager.openCamera(params.id, params.cameraCallback, params.backgroundHandler)
    } catch (e: CameraAccessException) {
        Logd("openCamera CameraAccessException: " + params.id)
        e.printStackTrace()
    } catch (e: SecurityException) {
        Logd("openCamera SecurityException: " + params.id)
        e.printStackTrace()
    }
}


//Close the first open camera we find
fun closeACamera(activity: MainActivity) {
    var closedACamera = false
    Logd("In closeACamera, looking for open camera.")
    for (tempCameraParams: CameraParams in cameraParams.values) {
        if (tempCameraParams.isOpen) {
            Logd("In closeACamera, found open camera, closing: " + tempCameraParams.id)
            closedACamera = true
            closeCamera(tempCameraParams, activity)
            break
        }
    }

    // We couldn't find an open camera, let's close everything
    if (!closedACamera) {
        closeAllCameras(activity)
    }
}

fun closeAllCameras(activity: MainActivity) {
    Logd("Closing all cameras.")
    for (tempCameraParams: CameraParams in cameraParams.values) {
        closeCamera(tempCameraParams, activity)
    }
}

fun closeCamera(params: CameraParams?, activity: MainActivity) {
    if (null == params)
        return

    Logd("closeCamera: " + params.id)
    params.isOpen = false
    params.captureSession?.close()
    params.device?.close()
}

class SurfaceCallback(val activity: MainActivity, val params: CameraParams): SurfaceHolder.Callback {
    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
    }

}

class TextureListener(internal var params: CameraParams, internal val activity: MainActivity): TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {
//        Logd( "In surfaceTextureUpdated. Id: " + params.id)
//        openCamera(params, activity)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        Logd( "In surfaceTextureAvailable. Id: " + params.id)
        camera2OpenCamera(activity, params)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        Logd( "In surfaceTextureSizeChanged. Id: " + params.id)
        val info = params.characteristics
                ?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        //       val largestSize = Collections.max(Arrays.asList(*info?.getOutputSizes(ImageFormat.JPEG)),
        //               CompareSizesByArea())
//        val optimalSize = chooseBigEnoughSize(
//                info?.getOutputSizes(SurfaceHolder::class.java), width, height)
//        params.previewTextureView.setFixedSize(optimalSize.width,
//                optimalSize.height)
//        configureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) : Boolean {
        Logd( "In surfaceTextureDestroyed. Id: " + params.id)
        closeCamera(params, activity)
        return true
    }
}



fun takePicture(activity: MainActivity, params: CameraParams) {
    Logd("TakePicture: capture start.")

    if (!params.isOpen) {
        //camera2Abort(activity, params, testConfig)
        return
    }

    lockFocus(activity, params)
}

fun lockFocus(activity: MainActivity, params: CameraParams) {
    Logd("In lockFocus.")
    if (!params.isOpen) {
        //camera2Abort(activity, params, testConfig)
        return
    }

    try {
        val camera = params.captureSession?.getDevice()
        if (null != camera) {
            params.captureBuilder?.addTarget(params.imageReader?.surface)
            setAutoFlash(activity, camera, params.captureBuilder)

            //If this lens can focus, we need to start a focus search and wait for focus lock
            if (params.hasAF) {
                Logd("In lockFocus. About to request focus lock and call capture.")
                params.captureBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
//                params.captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                params.captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                params.state = STATE_PICTURE_TAKEN
                captureStillPicture(activity, params)

/*
                params.state = STATE_WAITING_LOCK
                params.captureSession?.capture(params.captureBuilder?.build(), params.captureCallback,
                        params.backgroundHandler)
*/
                //Otherwise, a fixed focus lens so we can go straight to taking the image
            } else {
                Logd("In lockFocus. Fixed focus lens about call captureStillPicture.")
                params.state = STATE_PICTURE_TAKEN
                captureStillPicture(activity, params)
            }
        }

    } catch (e: CameraAccessException) {
        e.printStackTrace()
    }
}

fun runPrecaptureSequence(activity: MainActivity, params: CameraParams) {
    if (!params.isOpen) {
        //camera2Abort(activity, params, testConfig)
        return
    }

    try {
        val camera = params.captureSession?.getDevice()

        if (null != camera) {
            setAutoFlash(activity, camera, params.captureBuilder)
            params.captureBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

            params.state = STATE_WAITING_PRECAPTURE
            params.captureSession?.capture(params.captureBuilder?.build(), params.captureCallback,
                    params.backgroundHandler)
        }
    } catch (e: CameraAccessException) {
        e.printStackTrace()
    }

}

fun captureStillPicture(activity: MainActivity, params: CameraParams) {
    if (!params.isOpen) {
        //camera2Abort(activity, params, testConfig)
        return
    }

    try {
        Logd("In captureStillPicture.")

        val camera = params.captureSession?.getDevice()

        if (null != camera) {
            params.captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            setAutoFlash(activity, camera, params.captureBuilder)
            params.captureBuilder?.addTarget(params.imageReader?.getSurface())

/*            if (params.hasAF) {
                    params.captureBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            }
*/
            //Let's add a sepia effect for fun
            //Only for 2-camera case and the background
/*            if (params.hasSepia)
                params.captureBuilder?.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
            // Or mono if we don't have sepia
            else if (params.hasMono)
                params.captureBuilder?.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO)
*/

            //Otherwise too dark
            params.captureBuilder?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 4);

            //This is REQUIRED for face detect - even though Pixel 3 doesn't have sepia
            params.captureBuilder?.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)

            // Request face detection
            if (CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF != params.bestFaceDetectionMode)
                params.captureBuilder?.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, params.bestFaceDetectionMode)

            // Orientation
            val rotation = activity.getWindowManager().getDefaultDisplay().getRotation()
            val capturedImageRotation = getOrientation(params, rotation)
            params.captureBuilder?.set(CaptureRequest.JPEG_ORIENTATION, capturedImageRotation)

            //Do the capture
            params.captureSession?.capture(params.captureBuilder?.build(), StillCaptureSessionCallback(activity, params),
                    params.backgroundHandler)
        }
    } catch (e: CameraAccessException) {
        e.printStackTrace()

    } catch (e: IllegalStateException) {
        Logd("captureStillPicture IllegalStateException, aborting: " + e)
        //camera2Abort(activity, params, testConfig)
    }
}

fun unlockFocus(activity: MainActivity, params: CameraParams) {
    Logd("In unlockFocus.")

    if (!params.isOpen) {
        //camera2Abort(activity, params, testConfig)
        return
    }

    try {
        if (null != params.device) {
                // Reset auto-focus
//                params.captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
//                        CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
//                params.captureSession?.capture(params.captureRequestBuilder?.build(), params.captureCallback,
//                        params.backgroundHandler)
//                createCameraPreviewSession(activity, camera, params, testConfig)
        }
    } catch (e: CameraAccessException) {
        e.printStackTrace()

    } catch (e: IllegalStateException) {
        Logd("unlockFocus IllegalStateException, aborting: " + e)
        // camera2Abort(activity, params, testConfig)
    }

}


internal fun shutterControl(activity: Activity, shutter: View, openShutter: Boolean) {
/*    activity.runOnUiThread {
        if (openShutter)
            shutter.visibility = View.INVISIBLE
        else
            shutter.visibility = View.VISIBLE
    }
*/
}

fun camera2Abort(activity: MainActivity, params: CameraParams) {
//    activity.stopBackgroundThread(params)
}