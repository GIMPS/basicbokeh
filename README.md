# Stereo Camera Material Image Collection Tool

This tool is developed from goolge open soure project.
Only compatible with Huawei Mate 20 Series


# BasicBokeh

## This is not an officially supported Google product

This app is an alpha quality project to explore generating depth maps using phones with dual cameras. This project was demoed at the [Google Android Developer Summit 2018](https://www.youtube.com/watch?v=u38wOv2a_dA)

Do not use any of this code in production.

This requires SDK 28 and the following CameraCharacteristics:
 - LENS_DISTORTION
 - LENS_INTRINSIC_CALIBRATION
 - LENS_POSE_ROTATION
 - LENS_POSE_TRANSLATION

This project was developed for and tested on the Pixel 3 phone. Other devices may not work out of 
the box.

## Operation

**Single cam** - This uses the built-in FaceDetect algorithm to crop out the head of a person in the photo for the foreground. Bokeh effects are applied to the background, and the head is feathered in as the foreground. The widest angle camera is used for this mode.

Optional: GrabCut mode will attempt to improve foreground detection by selecting a rectangle from the head to the bottom of the photo and try to detect the foreground.

**Dual cam** - Simultaneous captures from two physical cameras are taken. They are undistorted/rectified using OpenCV and a depth map is generated. The foreground is cropped from the image, Bokeh effects applied to the background, and the foreground feathered in.

Note: FaceDetect is used to “protect” the face from being excluded

## Settings
- **Dual-cam**: use single cam or dual cam mode
- **Show steps**: show the intermediate bokeh creation steps
- **Output log:** output debugging log to logcat. Default: on
- **Use GrabCut:** in single cam mode, use GrabCut to attempt to do better than just a square using the [GrabCut](https://docs.opencv.org/trunk/d8/d83/tutorial_py_grabcut.html) algorithm.
- **Save intermediate steps:** save the intermediate processing steps to DCIM/BasicBokeh directory
- **JPG Quality:** jpeg quality for capture request
- **Manual calibration:** override calibration values from API (currently not implemented)
- **Calibration mode:** capture a series of 30 photos from both cameras with timestamps for use in calibrating cameras
- **Sepia/Mono:** select either Sepia or Monochrome effect for Bokeh effects
- **Blur:** blur background in Bokeh effects
- **Foreground cutoff:** cutoff value for foreground/background in depth map. Lower values mean more is included in foreground.
- **SGBM settings:** depth map creation settings. Corresponds to [StereoSGBM](https://docs.opencv.org/trunk/d2/d85/classcv_1_1StereoSGBM.html) documentation
- **WLS Filter settings:** WLS filter settings. Corresponds to [DisparityWLSFiter](https://docs.opencv.org/master/d9/d51/classcv_1_1ximgproc_1_1DisparityWLSFilter.html) settings

## Images
Images are stored under /DCIM/BasicBokeh 

Currently, saved images are not rotated correctly to save on image processing time

## Next Steps
 - Test on other multi-camera devices
 - Ensure multi-camera works on back cameras
 - Fix rotation and large screen support
 - Optimize speed
 - Optimized memory usage (currently ~1gb)
 - Improve depth map tuning, possibly add post-capture tuning
 - Add different tuning cases like “bright light”, “dim light” that are automatically detected
 - Add multiple-head support for single-cam mode
 - Look at ML Kit for face detection
 - Add capacity to input manual calibration numbers
 - Add GrabCut to dual cam mode.

## Notes
 - Optimized for Pixel 3 in portrait mode (may not run in current state on other phones)
 - For multiple tests, I recommend changing your CPU governor to performance
 - Not saving images increases run time significantly
 - Image size is currently max, no optimization have been done


## LICENSE

***

Copyright 2018 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


