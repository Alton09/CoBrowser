package com.example.cobrowser

import android.app.Application
import com.example.cobrowser.twilio.TwilioManager
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class CoBrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            modules(
                module {
                    factory { TwilioManager() }
                }
            )
        }
    }
}