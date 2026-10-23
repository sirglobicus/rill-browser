package com.rillbrowser.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = Store(this)
        blocker = AdBlocker(this).also { it.reload(store.adsEnabled, store.trackersEnabled) }
    }

    companion object {
        lateinit var blocker: AdBlocker
            private set
    }
}
