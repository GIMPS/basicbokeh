/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hadrosaur.basicbokeh.CameraController

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.annotation.NonNull
import com.hadrosaur.basicbokeh.*
import com.hadrosaur.basicbokeh.CameraController.FocusCaptureSessionCallback.Companion.STATE_PREVIEW

class PreviewSessionStateCallback(val activity: MainActivity, val params: CameraParams) : CameraCaptureSession.StateCallback() {
    override fun onActive(session: CameraCaptureSession?) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        //        takePicture(activity, params)
        super.onActive(session)
    }

    override fun onReady(session: CameraCaptureSession?) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        //This may be the initial start or we may have cleared an in-progress capture and the pipeline is now clear
        MainActivity.Logd("In onReady.")

        super.onReady(session)
    }

    override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("In onConfigured: CaptureSession configured!")
        // When the session is ready, we start displaying the preview.
        try {

            params.previewBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
//            setAutoFlash(activity, cameraCaptureSession.device, params.previewBuilder)
            params.captureSession = cameraCaptureSession
            params.state = STATE_PREVIEW

            // Finally, we start displaying the camera preview.
            if (28 <= Build.VERSION.SDK_INT)
                params.captureSession?.setSingleRepeatingRequest(params.previewBuilder?.build(), params.backgroundExecutor,
                    params.captureCallback)
            else
                params.captureSession?.setRepeatingRequest(params.previewBuilder?.build(),
                        params.captureCallback, params.backgroundHandler)

        } catch (e: CameraAccessException) {
            MainActivity.Logd("Create Preview Session error: " + params.id)
            e.printStackTrace()

        } catch (e: IllegalStateException) {
            MainActivity.Logd("createCameraPreviewSession onConfigured IllegalStateException, aborting: " + e)
            // camera2Abort(activity, params, testConfig)
        }
    }

    override fun onConfigureFailed(
            @NonNull cameraCaptureSession: CameraCaptureSession) {
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

        MainActivity.Logd("Camera preview initialization failed.")
        MainActivity.Logd("Trying again")
        createCameraPreviewSession(activity, cameraCaptureSession.device, params)
        //TODO: fix endless loop potential.
    }
}
