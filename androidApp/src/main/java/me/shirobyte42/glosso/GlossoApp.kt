package me.shirobyte42.glosso

import android.app.Application
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import me.shirobyte42.glosso.di.commonModule
import me.shirobyte42.glosso.di.appModule
import java.io.File
import java.security.MessageDigest

class GlossoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // --- CRITICAL: PRE-INITIALIZATION INTEGRITY CHECK ---
        // We MUST verify files before Room or ONNX attempts to open them.
        performIntegrityCheck()
        
        startKoin {
            androidContext(this@GlossoApp)
            modules(commonModule, appModule)
        }
    }

    private fun performIntegrityCheck() {
        val ONNX_HASH = "a792590100c9ca5ea4e8f04a71de884f9fe9fb9cdaa17a528acb369f56028a07"
        val PHONE_HASH = "883efb706711ccbf72df2522c765f3012f170cd787bca519a1b4f68bf9aa0f47"
        
        // 1. Verify Model Assets
        verifyAndCleanup(File(filesDir, "allosaurus_eng2102.onnx"), ONNX_HASH)
        verifyAndCleanup(File(filesDir, "phone_eng.txt"), PHONE_HASH)
        
        // 2. Verify Level Databases
        // For levels, we don't have hardcoded hashes here, but we can verify minimum sizes
        // to prevent Room from opening tiny partial files (usually LFS pointers or interrupted downloads).
        for (i in 0..5) {
            val dbFile = getDatabasePath("sentences_$i.db")
            if (dbFile.exists() && dbFile.length() < 1024 * 1024) { // Less than 1MB is definitely invalid
                Log.w("GlossoApp", "Deleting invalid DB: ${dbFile.name}")
                dbFile.delete()
                // Also delete Room journals if they exist
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-wal").delete()
            }
        }
    }

    private fun verifyAndCleanup(file: File, expectedHash: String) {
        if (!file.exists()) return
        
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            
            if (hash != expectedHash) {
                Log.e("GlossoApp", "CORRUPTION DETECTED: Deleting ${file.name}")
                file.delete()
            }
        } catch (e: Exception) {
            file.delete()
        }
    }
}
