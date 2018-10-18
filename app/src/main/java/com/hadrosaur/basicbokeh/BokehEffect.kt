package com.hadrosaur.basicbokeh

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import org.opencv.android.Utils
import org.opencv.calib3d.StereoBM
import org.opencv.calib3d.StereoSGBM
import org.opencv.core.CvType.*
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

fun DoBokeh(activity: MainActivity, twoLens: TwoLensCoordinator) : Bitmap {
    //We need both shots to be done and both images in order to proceed
    if (!twoLens.normalShotDone || !twoLens.wideShotDone || (null == twoLens.normalImage)
        || (null == twoLens.wideImage))
        return Bitmap.createBitmap(100, 100,  Bitmap.Config.ARGB_8888) //Return empty bitmap

    Logd("WIDE IMAGE: " + twoLens.wideImage?.width + "x" + twoLens.wideImage?.height)
    Logd("NORMAL IMAGE: " + twoLens.normalImage?.width + "x" + twoLens.normalImage?.height)

    val wideBuffer: ByteBuffer? = twoLens.wideImage!!.planes[0].buffer
    val wideBytes = ByteArray(wideBuffer!!.remaining())
    wideBuffer.get(wideBytes)

    val normalBuffer: ByteBuffer? = twoLens.normalImage!!.planes[0].buffer
    val normalBytes = ByteArray(normalBuffer!!.remaining())
    normalBuffer.get(normalBytes)

    val wideMat: Mat = Mat(twoLens.wideImage!!.height, twoLens.wideImage!!.width, CV_8UC1)
    val tempWideBitmap = BitmapFactory.decodeByteArray(wideBytes, 0, wideBytes.size, null)
    Utils.bitmapToMat(tempWideBitmap, wideMat)

    //Crop the wide angle shot so that has the same frame of view as the normal shot, ignoring distortion
//    val scaleFactor: Float = twoLens.wideParams.smallestFocalLength / twoLens.normalParams.smallestFocalLength
    val scaleFactor = 0.84f
    val croppedWideMat = cropMat(wideMat, scaleFactor)

    val normalMat: Mat = Mat(twoLens.normalImage!!.height, twoLens.normalImage!!.width, CV_8UC1)
    val tempNormalBitmap = BitmapFactory.decodeByteArray(normalBytes, 0, normalBytes.size, null)
    Utils.bitmapToMat(tempNormalBitmap, normalMat)

    val resizedNormalMat: Mat = Mat(croppedWideMat.rows(), croppedWideMat.cols(), CV_8UC1)
    Imgproc.resize(normalMat, resizedNormalMat, resizedNormalMat.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)

    //    val disparityMat: Mat = Mat(twoLens.normalImage!!.width, twoLens.normalImage!!.height, CV_8UC1)

//    val normalMat: Mat = Utils.loadResource(activity, R.drawable.tsukuba_r, CV_8UC1)
//    val resizedCroppedWideMat: Mat = Utils.loadResource(activity, R.drawable.tsukuba_l, CV_8UC1)

    val disparityMat: Mat = Mat(resizedNormalMat.rows(), resizedNormalMat.cols(), CV_8UC1)

    Logd("Sanity check, disparity Cols: " + disparityMat.cols() + " Rows: " + disparityMat.rows() + " Normal cols: " + resizedNormalMat.cols() + " Normal rows: " + resizedNormalMat.rows()
            + " Wide cols: " + croppedWideMat.cols() + " Wide rows: " + croppedWideMat.rows())


    Logd( "Now saving photos to disk.")
    val controlBitmap: Bitmap = Bitmap.createBitmap(croppedWideMat.cols(), croppedWideMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(croppedWideMat, controlBitmap)
    val controlNormalBitmap: Bitmap = Bitmap.createBitmap(resizedNormalMat.cols(), resizedNormalMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(resizedNormalMat, controlNormalBitmap)

    WriteFile(activity, controlBitmap,"WideShot")
    WriteFile(activity, controlNormalBitmap, "NormalShot")


    //Convert the Mats to 1-channel greyscale so we can compute depth maps
    val finalNormalMat: Mat = Mat(resizedNormalMat.rows(), resizedNormalMat.cols(), CV_8UC1)
    Imgproc.cvtColor(resizedNormalMat, finalNormalMat, Imgproc.COLOR_BGR2GRAY)

    val finalWideMat: Mat = Mat(croppedWideMat.rows(), croppedWideMat.cols(), CV_8UC1)
    Imgproc.cvtColor(croppedWideMat, finalWideMat, Imgproc.COLOR_BGR2GRAY)

    Logd("Type, normal: " + finalNormalMat.type() + " and wide: " + finalWideMat.type() + "ref: " + CV_8UC1 + " or " + CV_8UC3 + " or " + CV_8UC4)



    //Adapted from: https://docs.opencv.org/3.1.0/dd/d53/tutorial_py_depthmap.html
    val stereoBM: StereoSGBM = StereoSGBM.create(0, 16, 15, 1800, 7200)
//    stereoBM.speckleWindowSize = 20
//    stereoBM.speckleRange = 1
    stereoBM.mode = StereoSGBM.MODE_HH4
    stereoBM.compute(finalNormalMat, finalWideMat, disparityMat)
    val disparityBitmap: Bitmap = Bitmap.createBitmap(disparityMat.cols(), disparityMat.rows(), Bitmap.Config.ARGB_8888)

    val disparityMatConverted: Mat = Mat(disparityMat.rows(), disparityMat.cols(), CV_8UC1)
    disparityMat.convertTo(disparityMatConverted, CV_8UC1);
//    Imgproc.cvtColor(disparityMat, disparityMatConverted, CV_8UC1)

    Logd("Disparity Cols: " + disparityMatConverted.cols() + " Rows: " + disparityMatConverted.rows() + " Width: " + disparityBitmap.width + " Height: " + disparityBitmap.height)
    Logd("Source type: " + disparityMatConverted.type() + " is it: " + CV_8UC1 + " or " + CV_8UC3 + " or " + CV_8UC4)

    Utils.matToBitmap(disparityMatConverted, disparityBitmap)
    val rotatedDisparityBitmap: Bitmap = rotateBitmap(disparityBitmap, -90f)

    return horizontalFlip(rotatedDisparityBitmap)
}
