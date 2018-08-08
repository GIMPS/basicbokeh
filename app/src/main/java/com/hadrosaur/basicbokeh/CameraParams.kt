package com.hadrosaur.basicbokeh

import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import android.widget.ImageView
import com.hadrosaur.basicbokeh.MainActivity.Companion.NO_APERTURE

class CameraParams {
    internal var id: String? = null
    internal var state: Int = STATE_UNINITIALIZED
    internal var isFront: Boolean = false
    internal var hasFlash: Boolean = false
    internal var hasMulti: Boolean = false
    internal var hasManualControl: Boolean = false
    internal var isOpen: Boolean = false
    internal var characteristics: CameraCharacteristics? = null

    internal var backgroundThread: HandlerThread? = null
    internal var backgroundHandler: Handler? = null

    internal var shutter: ImageView? = null
    internal var capturedPhoto: ImageView? = null
    internal var imageReader: ImageReader? = null
    internal var previewTextureView: TextureView? = null

    internal var physicalCameras: Set<String> = HashSet<String>()
    internal var focalLengths: FloatArray = FloatArray(0)
    internal var apertures: FloatArray = FloatArray(0)
    internal var smallestFocalLength: Float = MainActivity.INVALID_FOCAL_LENGTH
    internal var minDeltaFromNormal: Float = MainActivity.INVALID_FOCAL_LENGTH
    internal var minFocusDistance: Float = MainActivity.FIXED_FOCUS_DISTANCE
    internal var largestAperture: Float = NO_APERTURE
    internal var effects: IntArray = IntArray(0)
    internal var hasSepia: Boolean = false
    internal var hasMono: Boolean = false

    internal var previewBuilder: CaptureRequest.Builder? = null
    internal var captureBuilder: CaptureRequest.Builder? = null

    internal var captureSession: CameraCaptureSession? = null
    internal var cameraCallback: CameraStateCallback? = null
    internal var textureListener: TextureListener? = null
    internal var captureCallback: CaptureSessionCallback? = null
    internal var imageAvailableListener: ImageAvailableListener? = null

    internal var hasFace: Boolean = false
    internal var faceBounds: Rect = Rect(0,0,0,0)
}