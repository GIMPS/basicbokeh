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

package com.hadrosaur.basicbokeh

import android.media.Image

class TwoLensCoordinator (var isTwoLensShot: Boolean = false, var wideShotDone: Boolean = false, var normalShotDone: Boolean = false,
                          var wideImage: Image? = null, var normalImage: Image? = null, var wideParams: CameraParams = CameraParams(),
                          var normalParams: CameraParams = CameraParams()) {
    fun reset() {
        isTwoLensShot = false
        wideShotDone = false
        normalShotDone = false
        wideImage = null
        normalImage = null
    }
}