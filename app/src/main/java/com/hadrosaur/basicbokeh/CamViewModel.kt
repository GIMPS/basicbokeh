package com.hadrosaur.basicbokeh

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CamViewModel : ViewModel() {
    private var cameraParams: HashMap<String, CameraParams> = HashMap<String, CameraParams>()
    private val shouldOutputLog: MutableLiveData<Boolean> = MutableLiveData()

    fun getCameraParams(): HashMap<String, CameraParams> {
        return cameraParams
    }

    fun getShouldOutputLog(): MutableLiveData<Boolean> {
        if (shouldOutputLog.value == null)
            shouldOutputLog.value = true
        return shouldOutputLog
    }
}