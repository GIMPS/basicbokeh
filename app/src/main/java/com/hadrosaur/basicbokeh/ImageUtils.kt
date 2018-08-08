package com.hadrosaur.basicbokeh

import android.app.Activity
import android.content.res.Resources
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.support.v8.renderscript.*
import android.util.Log
import android.widget.ImageView
import com.hadrosaur.basicbokeh.MainActivity.Companion.GAUSSIAN_BLUR_RADIUS
import com.hadrosaur.basicbokeh.MainActivity.Companion.LOG_TAG
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.provider.MediaStore
import android.opengl.ETC1.getWidth
import android.opengl.ETC1.getHeight




class ImageAvailableListener(private val activity: Activity, internal var params: CameraParams) : ImageReader.OnImageAvailableListener {

    override fun onImageAvailable(reader: ImageReader) {
        // Orientation
        val rotation = activity.getWindowManager().getDefaultDisplay().getRotation()
        val capturedImageRotation = getOrientation(params, rotation)

        Log.d(MainActivity.LOG_TAG, "ImageReader. Image is available, about to post.")
        params.backgroundHandler?.post(ImageSaver(activity, reader.acquireNextImage(), params.capturedPhoto, capturedImageRotation, params.isFront, params))
        Log.d(MainActivity.LOG_TAG, "ImageReader. Post has been set.")
    }
}

class ImageSaver internal constructor(private val activity: Activity, private val image: Image?, private val imageView: ImageView?, private val rotation: Int, private val flip: Boolean, private val cameraParams: CameraParams) : Runnable {

    override fun run() {
        Log.d(LOG_TAG, "ImageSaver. ImageSaver is running.")

        if (null == image)
            return

        Log.d(LOG_TAG, "ImageSaver. Image is not null.")

        val file = File(Environment.getExternalStorageDirectory(), MainActivity.SAVE_FILE)

        Log.d(LOG_TAG, "ImageSaver. Got file handle.")


        val buffer = image.planes[0].buffer

        Log.d(LOG_TAG, "ImageSaver. Got image buffer planes")

        val bytes = ByteArray(buffer.remaining())

        Log.d(LOG_TAG, "ImageSaver. image got the buffer remaining")

        buffer.get(bytes)

        Log.d(LOG_TAG, "Got the photo, converted to bytes. About to set image view.")

        //Set the image view to be the saved image
        val imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        val boxedBitmap = drawBox(activity, cameraParams, imageBitmap)
        val rotatedImageBitmap = rotateBitmap(boxedBitmap, rotation.toFloat())
        val scaledBitmap = scaleBitmap(activity, rotatedImageBitmap, MainActivity.BLUR_SCALE_FACTOR)
        val sepiaBitmap = sepiaFilter(activity, scaledBitmap)
        val blurredImageBitmap = gaussianBlur(activity, sepiaBitmap, MainActivity.GAUSSIAN_BLUR_RADIUS)

        val finalBitmap = blurredImageBitmap

        //If front facing camera, flip the bitmap
//        if (flip)
            val finalBitmap2 = horizontalFlip(activity, finalBitmap)

        setCapturedPhoto(activity, imageView, finalBitmap2)

        Log.d(LOG_TAG, "Now saving photo to disk.")

        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file)
            output.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            image.close()
            if (null != output) {
                try {
                    output.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    fun rotateBitmap(original: Bitmap, degrees: Float): Bitmap {
        /*        int width = original.getWidth();
        int height = original.getHeight();

        Matrix matrix = new Matrix();
        matrix.preRotate(degrees);

        Bitmap rotatedBitmap = Bitmap.createBitmap(original, 0, 0, width, height, matrix, true);
        Canvas canvas = new Canvas(rotatedBitmap);
        canvas.drawBitmap(original, 5.0f, 0.0f, null);

        return rotatedBitmap;
        */
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

     fun setCapturedPhoto(activity: Activity, imageView: ImageView?, bitmap: Bitmap) {
        activity.runOnUiThread { imageView?.setImageBitmap(bitmap) }
    }
}

fun scaleBitmap(activity: Activity, bitmap: Bitmap, scaleFactor: Float): Bitmap {
    val scaledWidth = Math.round(bitmap.width * scaleFactor)
    val scaledHeight = Math.round(bitmap.height * scaleFactor)

    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

fun gaussianBlur(activity: Activity, bitmap: Bitmap, blurRadius: Float): Bitmap {
    val rs = RenderScript.create(activity)
    val allocation = Allocation.createFromBitmap(rs, bitmap)
    val allocationType = allocation.type
    val blurredAllocation = Allocation.createTyped(rs, allocationType)
    val blurredBitmap = bitmap

    val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    script.setRadius(blurRadius)
    script.setInput(allocation)

    //Do the blur
    script.forEach(blurredAllocation)
    blurredAllocation.copyTo(blurredBitmap)

    allocation.destroy()
    blurredAllocation.destroy()
    script.destroy()
    allocationType.destroy()
    rs.destroy()
    return blurredBitmap
}

fun sepiaFilter(activity: Activity, bitmap: Bitmap): Bitmap {
    val rs = RenderScript.create(activity)
    val allocation = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
            Allocation.USAGE_SCRIPT)
    val allocationType = allocation.type
    val sepiaAllocation = Allocation.createTyped(rs, allocationType)
    val sepiaBitmap = bitmap
    val script = ScriptC_sepia(rs)

    script.set_allocationIn(allocation)
    script.set_allocationOut(sepiaAllocation);
    script.set_script(script);
    script.invoke_filter();
    sepiaAllocation.copyTo(sepiaBitmap);

    return sepiaBitmap
}

fun drawBox(activity: Activity, cameraParams: CameraParams, bitmap: Bitmap): Bitmap {
    val bitmapBoxed = bitmap.copy(Bitmap.Config.ARGB_8888, true);
    val canvas = Canvas(bitmapBoxed)
    val paint = Paint()
    paint.setColor(Color.GREEN)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    canvas.drawRect(cameraParams.faceBounds, paint)
    return bitmapBoxed
}

fun horizontalFlip(activity: Activity, bitmap: Bitmap) : Bitmap {
    val matrix = Matrix()
    matrix.preScale(-1.0f, 1.0f)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
