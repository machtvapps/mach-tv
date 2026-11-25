package io.github.machtvapps.machtv.core

import android.content.Context
import java.io.File

object Engine {
    fun start(context: Context): String {
        val app = context.applicationContext
        return NativeEngine.start(
            app,
            File(app.filesDir, "engine").absolutePath,
            File(app.cacheDir, "engine").absolutePath,
        )
    }

    fun stop() {
        NativeEngine.stop()
    }
}
