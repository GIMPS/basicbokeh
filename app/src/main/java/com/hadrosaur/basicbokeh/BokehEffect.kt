package com.hadrosaur.basicbokeh

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d.CALIB_ZERO_DISPARITY
import org.opencv.calib3d.StereoSGBM
import org.opencv.core.CvType.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d.stereoRectify
import org.opencv.calib3d.StereoBM
import java.nio.ByteBuffer
import org.opencv.calib3d.StereoSGBM.MODE_HH4
import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.imgproc.Imgproc.*
import org.opencv.ximgproc.DisparityFilter
import org.opencv.ximgproc.DisparityWLSFilter


fun DoBokeh(activity: MainActivity, twoLens: TwoLensCoordinator) : Bitmap {
    //We need both shots to be done and both images in order to proceed
    if (!twoLens.normalShotDone || !twoLens.wideShotDone || (null == twoLens.normalImage)
        || (null == twoLens.wideImage))
        return Bitmap.createBitmap(100, 100,  Bitmap.Config.ARGB_8888) //Return empty bitmap

    Logd("Normal image timestamp: " + twoLens.normalImage?.timestamp)
    Logd("Wide image timestamp: " + twoLens.wideImage?.timestamp)

    val wideBuffer: ByteBuffer? = twoLens.wideImage!!.planes[0].buffer
    val wideBytes = ByteArray(wideBuffer!!.remaining())
    wideBuffer.get(wideBytes)

    val normalBuffer: ByteBuffer? = twoLens.normalImage!!.planes[0].buffer
    val normalBytes = ByteArray(normalBuffer!!.remaining())
    normalBuffer.get(normalBytes)

    if (PrefHelper.getCalibrationMode(activity)) {
        //Calibration images. Save plain photos with timestamp
        WriteFile(activity, normalBytes, "NormalCalibration", true)
        WriteFile(activity, wideBytes, "WideCalibration", true)
    }

    val wideMat: Mat = Mat(twoLens.wideImage!!.height, twoLens.wideImage!!.width, CV_8UC1)
    val tempWideBitmap = BitmapFactory.decodeByteArray(wideBytes, 0, wideBytes.size, null)
    Utils.bitmapToMat(tempWideBitmap, wideMat)

    val normalMat: Mat = Mat(twoLens.normalImage!!.height, twoLens.normalImage!!.width, CV_8UC1)
    val tempNormalBitmap = BitmapFactory.decodeByteArray(normalBytes, 0, normalBytes.size, null)
    Utils.bitmapToMat(tempNormalBitmap, normalMat)

    if (PrefHelper.getIntermediate(activity)) {
        activity.runOnUiThread {
            activity.imageIntermediate1.setImageBitmap(horizontalFlip(rotateBitmap(tempNormalBitmap, -90f)))
            activity.imageIntermediate2.setImageBitmap(horizontalFlip(rotateBitmap(tempWideBitmap, -90f)))
        }
    }

    if (PrefHelper.getCalibrationMode(activity)) {
        return tempNormalBitmap
    }

//    var resizedNormalMat: Mat = Mat(croppedWideMat.rows(), croppedWideMat.cols(), CV_8UC1)
//    Imgproc.resize(normalMat, resizedNormalMat, resizedNormalMat.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)

    //    val disparityMat: Mat = Mat(twoLens.normalImage!!.width, twoLens.normalImage!!.height, CV_8UC1)

//    val normalMat: Mat = Utils.loadResource(activity, R.drawable.tsukuba_r, CV_8UC1)
//    val resizedCroppedWideMat: Mat = Utils.loadResource(activity, R.drawable.tsukuba_l, CV_8UC1)


//    Logd("Sanity check, disparity Cols: " + disparityMat.cols() + " Rows: " + disparityMat.rows() + " Normal cols: " + resizedNormalMat.cols() + " Normal rows: " + resizedNormalMat.rows()
//            + " Wide cols: " + croppedWideMat.cols() + " Wide rows: " + croppedWideMat.rows())


    //Just use raw images
//    croppedWideMat = wideMat
//    resizedNormalMat = normalMat

    Logd( "Now saving photos to disk.")
    val controlBitmap: Bitmap = Bitmap.createBitmap(wideMat.cols(), wideMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(wideMat, controlBitmap)
    val controlNormalBitmap: Bitmap = Bitmap.createBitmap(normalMat.cols(), normalMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(normalMat, controlNormalBitmap)

    WriteFile(activity, controlBitmap,"WideShot")
    WriteFile(activity, controlNormalBitmap, "NormalShot")

    //Convert the Mats to 1-channel greyscale so we can compute depth maps
    var finalNormalMat: Mat = Mat(normalMat.rows(), normalMat.cols(), CV_8UC1)
    Imgproc.cvtColor(normalMat, finalNormalMat, Imgproc.COLOR_BGR2GRAY)
//    var finalNormalMat: Mat = Mat(normalMat.rows(), normalMat.cols(), CV_8UC1)
//    Imgproc.cvtColor(normalMat, finalNormalMat, Imgproc.COLOR_BGR2GRAY)

    var finalWideMat: Mat = Mat(wideMat.rows(), wideMat.cols(), CV_8UC1)
    Imgproc.cvtColor(wideMat, finalWideMat, Imgproc.COLOR_BGR2GRAY)
//    var finalWideMat: Mat = Mat(wideMat.rows(), wideMat.cols(), CV_8UC1)
//    Imgproc.cvtColor(wideMat, finalWideMat, Imgproc.COLOR_BGR2GRAY)

    //Get camera matricies
    //If we are >= 28, rectify images to get a good depth map.
    if (Build.VERSION.SDK_INT >= 28) {

        val camMatrixNormal: Mat = Mat(3, 3, CV_64FC1)
        setMat(camMatrixNormal, 3, 3, cameraMatrixFromCalibration(twoLens.normalParams.intrinsicCalibration))
        val camMatrixWide: Mat = Mat(3, 3, CV_64FC1)
        setMat(camMatrixWide, 3, 3, cameraMatrixFromCalibration(twoLens.wideParams.intrinsicCalibration))

        var distCoeffNormal: Mat = Mat(5, 1, CV_64FC1)
        setMat(distCoeffNormal, 5, 1, cameraDistortionFromCalibration(twoLens.normalParams.lensDistortion))
        var distCoeffWide: Mat = Mat(5, 1, CV_64FC1)
        setMat(distCoeffWide, 5, 1, cameraDistortionFromCalibration(twoLens.wideParams.lensDistortion))

        /*
        Logd("Cam MAtrix K1 Check: "
                + camMatrixNormal[0, 0].get(0) + ", "
                + camMatrixNormal[0, 1].get(0) + ", "
                + camMatrixNormal[0, 2].get(0) + ", "
                + camMatrixNormal[1, 0].get(0) + ", "
                + camMatrixNormal[1, 1].get(0) + ", "
                + camMatrixNormal[1, 2].get(0) + ", "
                + camMatrixNormal[2, 0].get(0) + ", "
                + camMatrixNormal[2, 1].get(0) + ", "
                + camMatrixNormal[2, 2].get(0)
        )

        Logd("Cam MAtrix K2 Check: "
                + camMatrixWide[0, 0].get(0) + ", "
                + camMatrixWide[0, 1].get(0) + ", "
                + camMatrixWide[0, 2].get(0) + ", "
                + camMatrixWide[1, 0].get(0) + ", "
                + camMatrixWide[1, 1].get(0) + ", "
                + camMatrixWide[1, 2].get(0) + ", "
                + camMatrixWide[2, 0].get(0) + ", "
                + camMatrixWide[2, 1].get(0) + ", "
                + camMatrixWide[2, 2].get(0)
        )
*/
        /*
        distort = np.array(props_physical[i]['android.lens.distortion'])
            assert len(distort) == 5, 'distortion has wrong # of params.'
            cv2_distort = np.array([distort[0], distort[1],
                                    distort[3], distort[4],
                                    distort[2]])
         */


        var poseRotationNormal: Mat = Mat(3, 3, CV_64FC1)
        setMat(poseRotationNormal, 3, 3, rotationMatrixFromQuaternion(twoLens.normalParams.poseRotation))
        var poseRotationWide: Mat = Mat(3, 3, CV_64FC1)
        setMat(poseRotationWide, 3, 3, rotationMatrixFromQuaternion(twoLens.wideParams.poseRotation))

        var poseTranslationNormal: Mat = Mat(3, 1, CV_64FC1)
        setMat(poseTranslationNormal, 3, 1, floatArraytoDoubleArray(twoLens.normalParams.poseTranslation))
        var poseTranslationWide: Mat = Mat(3, 1, CV_64FC1)
        setMat(poseTranslationWide, 3, 1, floatArraytoDoubleArray(twoLens.wideParams.poseTranslation))

        var combinedR: Mat = Mat(3, 3, CV_64FC1)
        var combinedT: Mat = Mat(3, 1, CV_64FC1)

        val combinedT1: Mat = Mat()
        val combinedT2: Mat = Mat()

//        multiply(poseTranslationNormal, poseRotationNormal, combinedT, -1.0)
//        multiply(poseRotationNormal, poseTranslationNormal, combinedT)
//        multiply(poseRotationWide, poseTranslationWide, combinedT2, -1.0)
//        t[i] = -1.0 * np.dot(r[i], t[i])

        //To get T1 -> T2 we need to translate using -1 * innerproduct(R1 * T1) for each row. So:
        // T[0] = -1 * innerProduct(row0(R1) * T1)
        // T[1] = -1 * innerProduct(row1(R1) * T1)
        // T[2] = -1 * innerProduct(row2(R1) * T1)
        combinedT.put(0,0, -1.0 * poseRotationNormal.colRange(0, 1).dot(poseTranslationNormal))
        combinedT.put(1,0, -1.0 * poseRotationNormal.colRange(1, 2).dot(poseTranslationNormal))
        combinedT.put(2,0, -1.0 * poseRotationNormal.colRange(2, 3).dot(poseTranslationNormal))

        //To get our combined R, inverse poseRotationWide and multiply
        Core.gemm(poseRotationWide.inv(DECOMP_SVD), poseRotationNormal, 1.0, Mat(), 0.0, combinedR)
//        Core.gemm(poseRotationNormal.inv(DECOMP_SVD), poseRotationWide, 1.0, Mat(), 0.0, combinedR)
//        combinedR = poseRotationNormal

        //To get our combined T, we take the difference
//        subtract(poseTranslationWide, poseTranslationNormal, combinedT)

        //Note: Wide is the reference cam (poseTranslationWide = [0,0,0] (TODO: check this for other devices)

        // NOTE For future if implementing for back cams
        //    if props['android.lens.facing']:
        //        print 'lens facing BACK'
        //        chart_distance *= -1  # API spec defines +z i pointing out from screen


        Logd("Final Combined Rotation Matrix: "
                + combinedR[0, 0].get(0) + ", "
                + combinedR[0, 1].get(0) + ", "
                + combinedR[0, 2].get(0) + ", "
                + combinedR[1, 0].get(0) + ", "
                + combinedR[1, 1].get(0) + ", "
                + combinedR[1, 2].get(0) + ", "
                + combinedR[2, 0].get(0) + ", "
                + combinedR[2, 1].get(0) + ", "
                + combinedR[2, 2].get(0)
        )

        Logd("Final Combined Translation Matrix: "
                + combinedT[0, 0].get(0) + ", "
                + combinedT[1, 0].get(0) + ", "
                + combinedT[2, 0].get(0)
        )



        //Hardcode calibrated camera values from manual calibration
//        var camMatrixNormal: Mat = Mat(3, 3, CV_64F)
//        camMatrixNormal.put(0, 0, 2.6289061692874407e+03, 0.0, 1.2651595182693038e+03, 0.0, 2.6345299439106893e+03, 1.6621994904402075e+03, 0.0, 0.0, 1.0)

//        var camMatrixWide: Mat = Mat(3, 3, CV_64F)
//        camMatrixWide.put(0, 0, 1.7820701618366402e+03, 0.0, 1.2551415015648533e+03, 0.0, 1.7877533505136726e+03, 1.6546480789366969e+03, 0.0, 0.0, 1.0)

//        val distCoeffNormal: Mat = Mat(5, 1, CV_64F)
//        distCoeffNormal.put(0, 0, 1.5221251808808173e-01, -4.4303863722184700e-01, 1.8538025531416699e-03, 3.3818864760858879e-03, 2.7401402708450756e-01)

//        val distCoeffWide: Mat = Mat(5, 1, CV_64F)
//        distCoeffWide.put(0, 0,1.7938446872417149e-02, -9.9556265387957060e-02, 3.3623700882571417e-04, 3.2871964141030957e-04, 6.9618584226421878e-02)

//        val combinedR: Mat = Mat(3, 3, CV_64F)
//        combinedR.put(0, 0, 9.9999036019893961e-01, 4.3651297163037981e-03, 4.7450158558713646e-04, -4.3680753488045121e-03, 9.9997001162287436e-01, 6.3949802732688014e-03, -4.4657243762857884e-04, -6.3969912856101565e-03, 9.9997943932640421e-01)

//        val combinedT: Mat = Mat(3, 1, CV_64F)
//        combinedT.put(0, 0, -9.6433495690526512e-03, -2.1189241527520949e-04, -1.3812421365998870e-03)

        //Stereo rectify
        var R1: Mat = Mat(3, 3, CV_64FC1)
        var R2: Mat = Mat(3, 3, CV_64FC1)
        var P1: Mat = Mat(3, 4, CV_64FC1)
        var P2: Mat = Mat(3, 4, CV_64FC1)
        var Q: Mat = Mat(4, 4, CV_64FC1)
//        val R1: Mat = Mat()
//        val R2: Mat = Mat()
//        val P1: Mat = Mat()
//        val P2: Mat = Mat()
//        val Q: Mat = Mat()

        val roi1: Rect = Rect()
        val roi2: Rect = Rect()



        stereoRectify(camMatrixNormal, distCoeffNormal, camMatrixWide, distCoeffWide,
                finalNormalMat.size(), combinedR, combinedT, R1, R2, P1, P2, Q,
                CALIB_ZERO_DISPARITY, 0.0, Size(), roi1, roi2)
//        CALIB_ZERO_DISPARITY, 0.96, Size(), null, null)

/*        Logd("R1: " + R1[0,0].get(0) + ", " + R1[0,1].get(0) + ", " + R1[0,2].get(0) + ", " + R1[1,0].get(0) + ", " + R1[1,1].get(0) + ", " + R1[1,2].get(0) + ", " + R1[2,0].get(0) + ", " + R1[2,1].get(0) + ", " + R1[2,2].get(0))
        Logd("R2: " + R2[0,0].get(0) + ", " + R2[0,1].get(0) + ", " + R2[0,2].get(0) + ", " + R2[1,0].get(0) + ", " + R2[1,1].get(0) + ", " + R2[1,2].get(0) + ", " + R2[2,0].get(0) + ", " + R2[2,1].get(0) + ", " + R2[2,2].get(0))
        Logd("P1: " + P1[0,0].get(0) + ", " + P1[0,1].get(0) + ", " + P1[0,2].get(0) + ", " + P1[0,3].get(0) + ", " + P1[1,0].get(0) + ", " + P1[1,1].get(0) + ", " + P1[1,2].get(0) + ", " + P1[1,3].get(0) + ", "
                + P1[2,0].get(0) + ", " + P1[2,1].get(0) + ", " + P1[2,2].get(0) + ", " + P1[2,3].get(0))
        Logd("P2: " + P2[0,0].get(0) + ", " + P2[0,1].get(0) + ", " + P2[0,2].get(0) + ", " + P2[0,3].get(0) + ", " + P2[1,0].get(0) + ", " + P2[1,1].get(0) + ", " + P2[1,2].get(0) + ", " + P2[1,3].get(0) + ", "
                + P2[2,0].get(0) + ", " + P2[2,1].get(0) + ", " + P2[2,2].get(0) + ", " + P2[2,3].get(0))
*/
/*
        //Hardcode R P and Q values from manual calibration
        R1.put(0, 0, 9.8949548377632979e-01, 2.5158349059318304e-02,
                1.4235780645562190e-01, -2.5875921632922544e-02,
                9.9966006827129561e-01, 3.1913294213162476e-03,
                -1.4222912594083417e-01, -6.8414454933153814e-03,
                9.8981011833465737e-01 )

        R2.put(0, 0, 9.8966331369869143e-01, 2.1745779134861743e-02,
                1.4175206033332605e-01, -2.1510512033114850e-02,
                9.9976352655227796e-01, -3.1919974664517837e-03,
                -1.4178795220680185e-01, 1.0984339044766995e-04,
                9.8989704744656715e-01)
*/
/*        P1.put(0, 0,1.5760697336640203e+03, 0.0, 8.3778312683105469e+02, 0.0, 0.0,
                1.5760697336640203e+03, 1.6607865600585938e+03, 0.0, 0.0, 0.0, 1.0, 0.0)
        P2.put (0, 0, 1.5760697336640203e+03, 0.0, 8.3778312683105469e+02,
                -1.5357335344809142e+01, 0.0, 1.5760697336640203e+03,
                1.6607865600585938e+03, 0.0, 0.0, 0.0, 1.0, 0.0)
//        Q.put(0, 0, 1.0, 0.0, 0.0, -4.0483023452758789e+02, 0.0, 1.0, 0.0,
//                -1.6956139907836914e+03, 0.0, 0.0, 0.0, 2.2048689061465034e+03, 0.0,
//                0.0, 4.2089805994572998e+01, 0.0)

        Logd("R1: " + R1[0,0].get(0) + ", " + R1[0,1].get(0) + ", " + R1[0,2].get(0) + ", " + R1[1,0].get(0) + ", " + R1[1,1].get(0) + ", " + R1[1,2].get(0) + ", " + R1[2,0].get(0) + ", " + R1[2,1].get(0) + ", " + R1[2,2].get(0))
        Logd("R2: " + R2[0,0].get(0) + ", " + R2[0,1].get(0) + ", " + R2[0,2].get(0) + ", " + R2[1,0].get(0) + ", " + R2[1,1].get(0) + ", " + R2[1,2].get(0) + ", " + R2[2,0].get(0) + ", " + R2[2,1].get(0) + ", " + R2[2,2].get(0))
        Logd("P1: " + P1[0,0].get(0) + ", " + P1[0,1].get(0) + ", " + P1[0,2].get(0) + ", " + P1[0,3].get(0) + ", " + P1[1,0].get(0) + ", " + P1[1,1].get(0) + ", " + P1[1,2].get(0) + ", " + P1[1,3].get(0) + ", "
                + P1[2,0].get(0) + ", " + P1[2,1].get(0) + ", " + P1[2,2].get(0) + ", " + P1[2,3].get(0))
        Logd("P2: " + P2[0,0].get(0) + ", " + P2[0,1].get(0) + ", " + P2[0,2].get(0) + ", " + P2[0,3].get(0) + ", " + P2[1,0].get(0) + ", " + P2[1,1].get(0) + ", " + P2[1,2].get(0) + ", " + P2[1,3].get(0) + ", "
                + P2[2,0].get(0) + ", " + P2[2,1].get(0) + ", " + P2[2,2].get(0) + ", " + P2[2,3].get(0))


*/

        val mapNormal1: Mat = Mat()
        val mapNormal2: Mat = Mat()
        val mapWide1: Mat = Mat()
        val mapWide2: Mat = Mat()

        initUndistortRectifyMap(camMatrixNormal, distCoeffNormal, R1, P1, finalNormalMat.size(), CV_32F, mapNormal1, mapNormal2);
        initUndistortRectifyMap(camMatrixWide, distCoeffWide, R2, P2, finalWideMat.size(), CV_32F, mapWide1, mapWide2);

        var rectifiedNormalMat: Mat = Mat()
        var rectifiedWideMat: Mat = Mat()

        remap(finalNormalMat, rectifiedNormalMat, mapNormal1, mapNormal2, INTER_LINEAR);
        remap(finalWideMat, rectifiedWideMat, mapWide1, mapWide2, INTER_LINEAR);

        //Individually rectify
//        undistort(finalNormalMat, rectifiedNormalMat, camMatrixNormal, distCoeffNormal)
//        undistort(finalWideMat, rectifiedWideMat, camMatrixWide, distCoeffWide)

        //Crop the wide angle shot so that has the same frame of view as the normal shot
//    val scaleFactor: Float = twoLens.wideParams.smallestFocalLength / twoLens.normalParams.smallestFocalLength
//        val scaleFactor = 0.813f
//        val scaleFactor = 0.84f
//        rectifiedWideMat = cropMat(rectifiedWideMat, scaleFactor)
//        Imgproc.resize(rectifiedNormalMat, rectifiedNormalMat, rectifiedWideMat.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)


        Logd( "Now saving rectified photos to disk.")
        val rectifiedNormalBitmap: Bitmap = Bitmap.createBitmap(rectifiedNormalMat.cols(), rectifiedNormalMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rectifiedNormalMat, rectifiedNormalBitmap)
        val rectifiedWideBitmap: Bitmap = Bitmap.createBitmap(rectifiedWideMat.cols(), rectifiedWideMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rectifiedWideMat, rectifiedWideBitmap)

        WriteFile(activity, rectifiedNormalBitmap, "RectifiedNormalShot")
        WriteFile(activity, rectifiedWideBitmap,"RectifiedWideShot")

        if (PrefHelper.getIntermediate(activity)) {
            activity.runOnUiThread {
                activity.imageIntermediate3.setImageBitmap(horizontalFlip(rotateBitmap(rectifiedNormalBitmap,-90f)))
                activity.imageIntermediate4.setImageBitmap(horizontalFlip(rotateBitmap(rectifiedWideBitmap, -90f)))
            }
        }


        finalNormalMat = rectifiedNormalMat
        finalWideMat = rectifiedWideMat
    }

    val sgbmWinSize = 19
    val sgbmBlockSize = sgbmWinSize
    val sgbmMinDisparity = 0
    val sgbmNumDisparities = 16
    val sgbmP1 = 1600
    val sgbmP2 = 6000
    val sgbmDispMaxDiff = -1
    val sgbmPreFilterCap = 44
    val sgbmUniquenessRatio = 0
    val sgbmSpeckleSize = 212
    val sgbmSpeckleRange = 2
//    val sgbmMode = StereoSGBM.MODE_HH4
    val sgbmMode = StereoSGBM.MODE_SGBM

    val disparityMat: Mat = Mat(normalMat.rows(), normalMat.cols(), CV_8UC1)

    val stereoBM: StereoSGBM = StereoSGBM.create(sgbmMinDisparity, sgbmNumDisparities, sgbmBlockSize,
            sgbmP1, sgbmP2, sgbmDispMaxDiff, sgbmPreFilterCap, sgbmUniquenessRatio, sgbmSpeckleSize,
            sgbmSpeckleRange, sgbmMode)
//    val stereoBM: StereoBM = StereoBM.create()
    stereoBM.compute(finalNormalMat, finalWideMat, disparityMat)

    val disparityBitmap: Bitmap = Bitmap.createBitmap(disparityMat.cols(), disparityMat.rows(), Bitmap.Config.ARGB_8888)


//    Utils.matToBitmap(disparityMat, disparityBitmap)
    var rotatedDisparityBitmap: Bitmap = rotateBitmap(disparityBitmap, -90f)
//    WriteFile(activity, disparityBitmap, "DisparityMap")


    val disparityMatConverted: Mat = Mat(disparityMat.rows(), disparityMat.cols(), CV_8UC1)
    disparityMat.convertTo(disparityMatConverted, CV_8UC1, 255 / (sgbmNumDisparities * 16.0));
//    Imgproc.cvtColor(disparityMat, disparityMatConverted, CV_8UC1)


//    Logd("Disparity Cols: " + disparityMatConverted.cols() + " Rows: " + disparityMatConverted.rows() + " Width: " + disparityBitmap.width + " Height: " + disparityBitmap.height)
//    Logd("Source type: " + disparityMatConverted.type() + " is it: " + CV_8UC1 + " or " + CV_8UC3 + " or " + CV_8UC4)

    Utils.matToBitmap(disparityMatConverted, disparityBitmap)
    rotatedDisparityBitmap = rotateBitmap(disparityBitmap, -90f)
    WriteFile(activity, rotatedDisparityBitmap, "DisparityMapNormalized")

    return horizontalFlip(rotatedDisparityBitmap)
}

fun floatArraytoDoubleArray(fArray: FloatArray) : DoubleArray {
    val dArray: DoubleArray = DoubleArray(fArray.size)
//    Logd("floatArraytoDouble: START")

    var output = ""
    for ((index, float) in fArray.withIndex()) {
        dArray.set(index, float.toDouble())
        output += "" + float.toDouble() + ", "
    }
//    Logd(output)
//    Logd("floatArraytoDouble: END")
    return dArray
}

//From https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_POSE_ROTATION
//For (x,y,x,w)
//R = [ 1 - 2y^2 - 2z^2,       2xy - 2zw,       2xz + 2yw,
//2xy + 2zw, 1 - 2x^2 - 2z^2,       2yz - 2xw,
//2xz - 2yw,       2yz + 2xw, 1 - 2x^2 - 2y^2 ]
fun rotationMatrixFromQuaternion(quatFloat: FloatArray) : DoubleArray {
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

    //Print
    Logd("Final Rotation Matrix: "
            + rotationMatrix[0] + ", "
            + rotationMatrix[1] + ", "
            + rotationMatrix[2] + ", "
            + rotationMatrix[3] + ", "
            + rotationMatrix[4] + ", "
            + rotationMatrix[5] + ", "
            + rotationMatrix[6] + ", "
            + rotationMatrix[7] + ", "
            + rotationMatrix[8]
    )


    return rotationMatrix
}

//https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INTRINSIC_CALIBRATION
//[f_x, f_y, c_x, c_y, s]
//K = [ f_x,   s, c_x,
//0, f_y, c_y,
//0    0,   1 ]
 fun cameraMatrixFromCalibration(calibrationFloat: FloatArray) : DoubleArray {
    val cal: DoubleArray = floatArraytoDoubleArray(calibrationFloat)
    val cameraMatrix: DoubleArray = DoubleArray(9)

    val f_x: Int = 0
    val f_y: Int = 1
    val c_x: Int = 2
    val c_y: Int = 3
    val s: Int = 4

    //NOTE: 0,2 and 1,2 are swapped going from google values to opencv.

    //Row 1
    cameraMatrix[0] = cal[f_x]
    cameraMatrix[1] = cal[s]
    cameraMatrix[2] = cal[c_x]
//    cameraMatrix[2] = cal[c_y]

    //Row 2
    cameraMatrix[3] = 0.0
    cameraMatrix[4] = cal[f_y]
    cameraMatrix[5] = cal[c_y]
//    cameraMatrix[5] = cal[c_x]

    //Row 3
    cameraMatrix[6] = 0.0
    cameraMatrix[7] = 0.0
    cameraMatrix[8] = 1.0

    //Print
    Logd("Final Cam Matrix: "
            + cameraMatrix[0] + ", "
            + cameraMatrix[1] + ", "
            + cameraMatrix[2] + ", "
            + cameraMatrix[3] + ", "
            + cameraMatrix[4] + ", "
            + cameraMatrix[5] + ", "
            + cameraMatrix[6] + ", "
            + cameraMatrix[7] + ", "
            + cameraMatrix[8]
    )

//    printArray(cameraMatrix)

    return cameraMatrix
}

//The android intrinsic values are swizzled from what OpenCV needs. Output indexs should be: 0,1,3,4,2
fun cameraDistortionFromCalibration(calibrationFloat: FloatArray) : DoubleArray {
    val cal: DoubleArray = floatArraytoDoubleArray(calibrationFloat)
    val cameraDistortion: DoubleArray = DoubleArray(5)

    cameraDistortion[0] = cal[0]
    cameraDistortion[1] = cal[1]
    cameraDistortion[2] = cal[3]
    cameraDistortion[3] = cal[4]
    cameraDistortion[4] = cal[2]

    //Print
    Logd("Final Distortion Matrix: "
            + cameraDistortion[0] + ", "
            + cameraDistortion[1] + ", "
            + cameraDistortion[2] + ", "
            + cameraDistortion[3] + ", "
            + cameraDistortion[4]
    )


    return cameraDistortion
}

fun setMat(mat: Mat, rows: Int, cols: Int, vals: DoubleArray) {
    mat.put(0,0, *vals)
/*
    for (row in 0..rows-1) {
        for (col in 0..cols-1) {
            //For some reason, Mat allocation fails for the 5th element sometimes...
            var temp: DoubleArray? = mat[row, col]
            if (null == temp) {
                Logd("Weird, at " + row + "and " + col + " and array is null.")
                continue
            }
            Logd("Checking mat at r: " + row + " and col: " + col + " : " + mat.get(row, col)[0])
        }
    }
*/
}

fun printArray(doubleArray: DoubleArray) {
    Logd("Checking double array Start")
    for (double in doubleArray)
        Logd("element: " + double)
    Logd("Checking double array End")
}
