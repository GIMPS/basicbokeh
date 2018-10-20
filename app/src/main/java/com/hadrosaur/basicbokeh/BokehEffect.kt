package com.hadrosaur.basicbokeh

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.calib3d.StereoBM
import org.opencv.calib3d.StereoSGBM
import org.opencv.core.CvType.*
import org.opencv.core.Mat
import org.opencv.core.Scalar
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

    /* REF Code:
    numberOfDisparities = numberOfDisparities > 0 ? numberOfDisparities : ((img_size.width/8) + 15) & -16;

    bm->setROI1(roi1);
    bm->setROI2(roi2);
    bm->setPreFilterCap(31);
    bm->setBlockSize(SADWindowSize > 0 ? SADWindowSize : 9);
    bm->setMinDisparity(0);
    bm->setNumDisparities(numberOfDisparities);
    bm->setTextureThreshold(10);
    bm->setUniquenessRatio(15);
    bm->setSpeckleWindowSize(100);
    bm->setSpeckleRange(32);
    bm->setDisp12MaxDiff(1);

    sgbm->setPreFilterCap(63);
    int sgbmWinSize = SADWindowSize > 0 ? SADWindowSize : 3;
    sgbm->setBlockSize(sgbmWinSize);

    int cn = img1.channels();

    sgbm->setP1(8*cn*sgbmWinSize*sgbmWinSize);
    sgbm->setP2(32*cn*sgbmWinSize*sgbmWinSize);
    sgbm->setMinDisparity(0);
    sgbm->setNumDisparities(numberOfDisparities);
    sgbm->setUniquenessRatio(10);
    sgbm->setSpeckleWindowSize(100);
    sgbm->setSpeckleRange(32);
    sgbm->setDisp12MaxDiff(1);
    if(alg==STEREO_HH)
        sgbm->setMode(StereoSGBM::MODE_HH);
    else if(alg==STEREO_SGBM)
        sgbm->setMode(StereoSGBM::MODE_SGBM);
    else if(alg==STEREO_3WAY)
        sgbm->setMode(StereoSGBM::MODE_SGBM_3WAY);

     */

    //Get camera matricies
    //If we are < 28, just use the images, even though depth map will be wonkified
    if (Build.VERSION.SDK_INT >= 28) {

        val camMatrixNormal: Mat = Mat(3, 3, CV_64F, cameraMatrixFromCalibration(twoLens.normalParams.intrinsicCalibration))
        val camMatrixWide: Mat = Mat(3, 3, CV_64F, cameraMatrixFromCalibration(twoLens.wideParams.intrinsicCalibration))

        val distCoeffNormal: Mat = Mat(5, 1, CV_64F, Scalar(floatArraytoDoubleArray(twoLens.normalParams.lensDistortion)))
        val distCoeffWide: Mat = Mat(5, 1, CV_64F, Scalar(floatArraytoDoubleArray(twoLens.wideParams.lensDistortion)))

        val poseRotationNormal: Mat = Mat(3, 3, CV_64F, rotationMatrixFromQuaternion(twoLens.normalParams.poseRotation))
        val poseRotationWide: Mat = Mat(3, 3, CV_64F, rotationMatrixFromQuaternion(twoLens.wideParams.poseRotation))
        val poseTranslationNormal: Mat = Mat(3, 1, CV_64F, Scalar(floatArraytoDoubleArray(twoLens.normalParams.poseTranslation)))
        val poseTranslationWide: Mat = Mat(3, 1, CV_64F, Scalar(floatArraytoDoubleArray(twoLens.wideParams.poseTranslation)))


        //TODO PLUG IN!!
/*
        //Stereo rectify
        Mat R, T, R1, P1, R2, P2;
        fs["R"] >> R;
        fs["T"] >> T;
        stereoRectify( M1, D1, M2, D2, img_size, R, T, R1, R2, P1, P2, Q, CALIB_ZERO_DISPARITY, -1, img_size, &roi1, &roi2 );

        Mat map11, map12, map21, map22;
        initUndistortRectifyMap(M1, D1, R1, P1, img_size, CV_16SC2, map11, map12);
        initUndistortRectifyMap(M2, D2, R2, P2, img_size, CV_16SC2, map21, map22);

        Mat img1r, img2r;
        remap(img1, img1r, map11, map12, INTER_LINEAR);
        remap(img2, img2r, map21, map22, INTER_LINEAR);
*/
    }






    //Adapted from: https://docs.opencv.org/3.1.0/dd/d53/tutorial_py_depthmap.html
    val sgbmWinSize = 15
    val sgbmBlockSize = sgbmWinSize
    val sgbmMinDisparity = 0
    val sgbmNumDisparities = 16
    val sgbmP1 = 8 * finalNormalMat.channels() * sgbmWinSize * sgbmWinSize
    val sgbmP2 = 32 * finalNormalMat.channels() * sgbmWinSize * sgbmWinSize


    val stereoBM: StereoSGBM = StereoSGBM.create(sgbmMinDisparity, sgbmNumDisparities, sgbmBlockSize, sgbmP1, sgbmP2)
//    stereoBM.speckleWindowSize = 20
//    stereoBM.speckleRange = 1
    stereoBM.setPreFilterCap(63)
    stereoBM.mode = StereoSGBM.MODE_HH4
    stereoBM.compute(finalNormalMat, finalWideMat, disparityMat)
    val disparityBitmap: Bitmap = Bitmap.createBitmap(disparityMat.cols(), disparityMat.rows(), Bitmap.Config.ARGB_8888)

    val disparityMatConverted: Mat = Mat(disparityMat.rows(), disparityMat.cols(), CV_8UC1)
    disparityMat.convertTo(disparityMatConverted, CV_8UC1, 255 / (sgbmNumDisparities * 16.0));
//    Imgproc.cvtColor(disparityMat, disparityMatConverted, CV_8UC1)

    Logd("Disparity Cols: " + disparityMatConverted.cols() + " Rows: " + disparityMatConverted.rows() + " Width: " + disparityBitmap.width + " Height: " + disparityBitmap.height)
    Logd("Source type: " + disparityMatConverted.type() + " is it: " + CV_8UC1 + " or " + CV_8UC3 + " or " + CV_8UC4)

    Utils.matToBitmap(disparityMatConverted, disparityBitmap)
    val rotatedDisparityBitmap: Bitmap = rotateBitmap(disparityBitmap, -90f)

    return horizontalFlip(rotatedDisparityBitmap)
}

fun floatArraytoDoubleArray(fArray: FloatArray) : DoubleArray {
    val dArray: DoubleArray = DoubleArray(fArray.size)
    for ((index, float) in fArray.withIndex())
        dArray.set(index, float.toDouble())

    return dArray
}

//From https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_POSE_ROTATION
//For (x,y,x,w)
//R = [ 1 - 2y^2 - 2z^2,       2xy - 2zw,       2xz + 2yw,
//2xy + 2zw, 1 - 2x^2 - 2z^2,       2yz - 2xw,
//2xz - 2yw,       2yz + 2xw, 1 - 2x^2 - 2y^2 ]
fun rotationMatrixFromQuaternion(quatFloat: FloatArray) : Scalar {
    val quat: DoubleArray = floatArraytoDoubleArray(quatFloat)
    val rotationMatrix: DoubleArray = DoubleArray(9)

    val x: Int = 0
    val y: Int = 1
    val z: Int = 2
    val w: Int = 3

    //Row 1
    rotationMatrix[0] = 1 - (2 * quat[y] * quat[y]) - (2 * quat[z] * quat[z])
    rotationMatrix[1] = (2 * quat[x] * quat[y]) - (2 * quat[z] * quat[w])
    rotationMatrix[2] = (2 * quat[x] * quat[z]) + (2 * quat[y] * quat[w])

    //Row 2
    rotationMatrix[3] = (2 * quat[x] * quat[y]) + (2 * quat[z] * quat[w])
    rotationMatrix[4] = 1 - (2 * quat[x] * quat[x]) - (2 * quat[z] * quat[z])
    rotationMatrix[5] = (2 * quat[y] * quat[z]) - (2 * quat[x] * quat[w])

    //Row 3
    rotationMatrix[6] = (2 * quat[x] * quat[z]) - (2 * quat[y] * quat[w])
    rotationMatrix[7] = (2 * quat[y] * quat[z]) + (2 * quat[x] * quat[w])
    rotationMatrix[8] = 1 - (2 * quat[x] * quat[x]) - (2 * quat[y] * quat[y])

    return Scalar(rotationMatrix)
}

//https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INTRINSIC_CALIBRATION
//[f_x, f_y, c_x, c_y, s]
//K = [ f_x,   s, c_x,
//0, f_y, c_y,
//0    0,   1 ]
 fun cameraMatrixFromCalibration(calibrationFloat: FloatArray) : Scalar {
    val cal: DoubleArray = floatArraytoDoubleArray(calibrationFloat)
    val cameraMatrix: DoubleArray = DoubleArray(9)

    val f_x: Int = 0
    val f_y: Int = 1
    val c_x: Int = 2
    val c_y: Int = 3
    val s: Int = 4

    //Row 1
    cameraMatrix[0] = cal[f_x]
    cameraMatrix[1] = cal[s]
    cameraMatrix[2] = cal[c_x]

    //Row 2
    cameraMatrix[3] = 0.0
    cameraMatrix[4] = cal[f_y]
    cameraMatrix[5] = cal[c_y]

    //Row 3
    cameraMatrix[6] = 0.0
    cameraMatrix[7] = 0.0
    cameraMatrix[8] = 1.0

    return Scalar(cameraMatrix)
}
