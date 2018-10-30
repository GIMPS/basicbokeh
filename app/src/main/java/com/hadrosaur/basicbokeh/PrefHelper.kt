package com.hadrosaur.basicbokeh

import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd
import java.util.*

class PrefHelper {
    companion object {
        fun getLog(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_log_key), true)
        }

        fun getLog(sharedPref: SharedPreferences): Boolean {
            //Hardcoded key so we don't have to pass the context around so much
            return sharedPref.getBoolean("settings_log", true)
        }

        fun getGrabCut(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_grabcut_key), false)
        }

        fun getDualCam(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_dualcam_key), true)
        }

        fun setDualCam(activity: MainActivity, dualCam: Boolean) {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            sharedPref.edit().putBoolean(activity.getString(R.string.settings_dualcam_key), dualCam).apply()
        }

        fun getIntermediate(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_intermediate_key), false)
        }

        fun setIntermediates(activity: MainActivity, showIntermediates: Boolean) {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            sharedPref.edit().putBoolean(activity.getString(R.string.settings_intermediate_key), showIntermediates).apply()
        }

        fun getManualCalibration(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_manual_calibration_key), false)
        }

        fun getCalibrationMode(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_calibration_mode_key), false)
        }

        fun getSepia(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_bokeh_sepia_key), true)
        }

        fun getBlur(activity: MainActivity): Boolean {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getBoolean(activity.getString(R.string.settings_bokeh_blur_key), true)
        }

        fun getWindowSize(activity: MainActivity): Int {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getString(activity.getString(R.string.settings_sgbm_windowsize_key), "19").toInt()
        }

        fun getNumDisparities(activity: MainActivity): Int {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getString(activity.getString(R.string.settings_sgbm_numdisparities_key), "32").toInt()
        }

        fun getQuality(activity: MainActivity): Byte {
            var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            return sharedPref.getString(activity.getString(R.string.settings_quality_key), "90").toByte()
        }
    }
}