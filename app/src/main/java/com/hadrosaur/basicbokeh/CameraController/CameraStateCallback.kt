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

import android.hardware.camera2.CameraDevice
import androidx.annotation.NonNull
import com.hadrosaur.basicbokeh.*

class CameraStateCallback(internal var params: CameraParams, internal var activity: MainActivity) : CameraDevice.StateCallback() {
    override fun onClosed(camera: CameraDevice?) {
        MainActivity.Logd("In CameraStateCallback onClosed. Camera: " + params.id + " is closed.")
        super.onClosed(camera)
    }

    override fun onOpened(@NonNull cameraDevice: CameraDevice) {
        MainActivity.Logd("In CameraStateCallback onOpened: " + cameraDevice.id)
        params.isOpen = true
        params.device = cameraDevice

        createCameraPreviewSession(activity, cameraDevice, params)
    }

    override fun onDisconnected(@NonNull cameraDevice: CameraDevice) {
        MainActivity.Logd("In CameraStateCallback onDisconnected: " + params.id)
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }

//        closeCamera(params, activity)
    }

    override fun onError(@NonNull cameraDevice: CameraDevice, error: Int) {
        MainActivity.Logd("In CameraStateCallback onError: " + cameraDevice.id + " and error: " + error)
        if (!params.isOpen) {
            //camera2Abort(activity, params, testConfig)
            return
        }


        if (CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE == error) {
            //Let's try to close an open camera and re-open this one
            MainActivity.Logd("In CameraStateCallback too many cameras open, closing one...")
            closeACamera(activity)
            camera2OpenCamera(activity, params)
        } else if (CameraDevice.StateCallback.ERROR_CAMERA_DEVICE == error){
            MainActivity.Logd("Fatal camera error, close device.")
            closeCamera(params, activity)
//            camera2OpenCamera(activity, params)
        } else if (CameraDevice.StateCallback.ERROR_CAMERA_IN_USE == error){
            MainActivity.Logd("This camera is already open... doing nothing")
        } else {
            closeCamera(params, activity)
        }
    }
}
