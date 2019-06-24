package com.example.cobrowser

import android.app.Application
import timber.log.Timber

class CoBrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }


    }
}