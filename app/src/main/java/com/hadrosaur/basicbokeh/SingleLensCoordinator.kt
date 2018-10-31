package com.hadrosaur.basicbokeh

import android.media.Image

class SingleLensCoordinator (var isSingleLensShot: Boolean = true, var shotDone: Boolean = false,
                          var image: Image? = null, var params: CameraParams = CameraParams()) {
    fun reset() {
        isSingleLensShot = false
        shotDone = false
        image = null
    }
}