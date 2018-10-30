package com.hadrosaur.basicbokeh

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity
import android.util.Log
import androidx.lifecycle.ViewModelProviders
import com.hadrosaur.basicbokeh.MainActivity.Companion.Logd

class SettingsActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
/*        Logd("HELLO: " + key)

        if (key == getString(R.string.settings_log_key)) {
            val camViewModel = ViewModelProviders.of(callingActivity as MainActivity).get(CamViewModel::class.java)
            if (null != sharedPreferences)
                camViewModel.getShouldOutputLog().value = PrefHelper.getLog(sharedPreferences)
            Logd("New value: " + camViewModel.getShouldOutputLog().value)
        }
        */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

}