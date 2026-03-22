package me.shirobyte42.glosso

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import me.shirobyte42.glosso.di.commonModule
import me.shirobyte42.glosso.di.appModule

class GlossoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GlossoApp)
            modules(commonModule, appModule)
        }
    }
}
