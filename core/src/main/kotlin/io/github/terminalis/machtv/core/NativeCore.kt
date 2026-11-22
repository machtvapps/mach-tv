package io.github.terminalis.machtv.core

internal object NativeCore {
    init {
        System.loadLibrary("machtv_core")
    }

    @JvmStatic external fun initialize(dataDir: String)

    @JvmStatic external fun dispatch(actionJson: String, field: String?)

    @JvmStatic external fun getState(field: String): String

    @JvmStatic external fun getBoardDelta(field: String, since: Long): String

    @JvmStatic external fun nextEvent(timeoutMs: Long): String?

    @JvmStatic external fun shutdown()

    @JvmStatic external fun exportBackup(): String

    @JvmStatic external fun importBackup(json: String): String
}
