package tech.ignacio.glosso

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tech.ignacio.glosso.di.commonModule
import tech.ignacio.glosso.di.appModule

class GlossoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GlossoApp)
            modules(commonModule, appModule)
        }
    }
}
