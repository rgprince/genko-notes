package com.rgprince.genkonotes

import android.app.Application
import com.tencent.mmkv.MMKV

class NijiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }
}
