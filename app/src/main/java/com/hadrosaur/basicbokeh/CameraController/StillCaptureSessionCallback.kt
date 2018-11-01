package com.hadrosaur.basicbokeh.CameraController

import android.graphics.Bitmap
import android.hardware.camera2.*
import android.os.Build
import android.view.Surface
import androidx.annotation.NonNull
import com.hadrosaur.basicbokeh.*
import com.hadrosaur.basicbokeh.MainActivity.Companion.dualCamLogicalId
import com.hadrosaur.basicbokeh.MainActivity.Companion.normalLensId
import com.hadrosaur.basicbokeh.MainActivity.Companion.singleLens
import com.hadrosaur.basicbokeh.MainActivity.Companion.twoLens
import com.hadrosaur.basicbokeh.MainActivity.Companion.wideAngleId

class StillCaptureSessionCallback(val activity: MainActivity, val params: CameraParams) : CameraCaptureSession.CaptureCallback() {

    override fun onCaptureSequenceAborted(session: CameraCaptureSession?, sequenceId: Int) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture captureCallback: Sequence aborted.")
        super.onCaptureSequenceAborted(session, sequenceId)
    }

    override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture captureCallback: Capture Failed. Failure: " + failure?.reason)

        //The session failed. Let's just try again (yay infinite loops)
        closeCamera(params, activity)
        camera2OpenCamera(activity, params)
        super.onCaptureFailed(session, request, failure)
    }

    override fun onCaptureStarted(session: CameraCaptureSession?, request: CaptureRequest?, timestamp: Long, frameNumber: Long) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture captureCallback: Capture Started.")
        super.onCaptureStarted(session, request, timestamp, frameNumber)
    }

    override fun onCaptureProgressed(session: CameraCaptureSession?, request: CaptureRequest?, partialResult: CaptureResult?) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture captureCallback: Capture progressed.")
        super.onCaptureProgressed(session, request, partialResult)
    }

    override fun onCaptureBufferLost(session: CameraCaptureSession?, request: CaptureRequest?, target: Surface?, frameNumber: Long) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture captureCallback: Buffer lost.")
        super.onCaptureBufferLost(session, request, target, frameNumber)
    }

    override fun onCaptureCompleted(@NonNull session: CameraCaptureSession,
                                    @NonNull request: CaptureRequest,
                                    @NonNull result: TotalCaptureResult) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("captureStillPicture onCaptureCompleted. Hooray!.")

        val mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE)
        val faces = result.get(CaptureResult.STATISTICS_FACES)

//        MainActivity.Logd("FACE-DETECT DEBUG: in onCaptureCompleted. CaptureResult.STATISTICS_FACE_DETECT_MODE is: " + mode)

        if (faces != null && mode != null) {
            MainActivity.Logd("faces : " + faces.size + " , mode : " + mode)
            for (face in faces) {
                val rect = face.bounds
                MainActivity.Logd("Bounds: bottom: " + rect.bottom + " left: " + rect.left + " right: " + rect.right + " top: " + rect.top)
            }
            MainActivity.Logd("Image size: " + params.maxSize.width + " x " + params.maxSize.height)
            if (faces.isNotEmpty()) {
                params.hasFace = true
                params.faceBounds = faces.first().bounds

                //TODO    This assumes we are taking max size stills. Need a Prefs setting.
                params.faceBounds.top -= (params.maxSize.height / 8)
                params.faceBounds.bottom += (params.maxSize.height / 8)
                params.faceBounds.right += (params.maxSize.width / 8)
                params.faceBounds.left -= (params.maxSize.width / 8)
            }
        }

        //It might be that we received this callback first and we're waiting for the image
        if (twoLens.isTwoLensShot) {
            when (params.id) {
                dualCamLogicalId-> {
                    twoLens.wideShotDone = true
                    twoLens.normalShotDone = true
                    twoLens.normalParams = MainActivity.cameraParams.get(normalLensId) ?: params
                    twoLens.wideParams = MainActivity.cameraParams.get(wideAngleId) ?: params
                }
                wideAngleId-> {
                    twoLens.wideShotDone = true
                    twoLens.wideParams = params
                }
                normalLensId-> {
                    twoLens.normalShotDone = true
                    twoLens.normalParams = params
                }
            }

            if (twoLens.wideShotDone && twoLens.normalShotDone
                    && null != twoLens.wideImage
                    && null != twoLens.normalImage) {

                val finalBitmap: Bitmap = DoBokeh(activity, twoLens)
                setCapturedPhoto(activity, params.capturedPhoto, finalBitmap)

                twoLens.normalImage?.close()
                twoLens.wideImage?.close()
            }
        } else {
            singleLens.shotDone = true
            //ImageReady might have been recevied first
            if (null != singleLens.image) {
                if (28 <= Build.VERSION.SDK_INT)
                    params.backgroundExecutor.execute(ImageSaver(activity, params, singleLens.image, params.capturedPhoto, params.isFront, params))
                else
                    params.backgroundHandler?.post(ImageSaver(activity, params, singleLens.image, params.capturedPhoto, params.isFront, params))
            }
        }


        MainActivity.Logd("captureStillPicture onCaptureCompleted. CaptureEnd.")
        createCameraPreviewSession(activity, params.device!!, params)

//        params.captureBuilder?.removeTarget(params.imageReader?.getSurface())
    }
}
