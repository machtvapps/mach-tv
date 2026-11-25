package io.github.machtvapps.machtv.core

import android.content.Context

internal object NativeEngine {
    init {
        System.loadLibrary("machtv_engine")
    }

    @JvmStatic external fun start(context: Context, configDir: String, cacheDir: String): String

    @JvmStatic external fun stop()
}
