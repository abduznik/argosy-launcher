package com.nendo.argosy.libchdr

import java.io.Closeable

class ChdReader private constructor(private var handle: Long) : Closeable {

    companion object {
        init {
            System.loadLibrary("chdr-jni")
        }

        fun open(path: String): ChdReader? {
            val handle = nativeOpen(path)
            return if (handle != 0L) ChdReader(handle) else null
        }

        @JvmStatic
        private external fun nativeOpen(path: String): Long

        @JvmStatic
        private external fun nativeReadSector(handle: Long, lba: Int): ByteArray?

        @JvmStatic
        private external fun nativeGetTotalFrames(handle: Long): Int

        @JvmStatic
        private external fun nativeClose(handle: Long)
    }

    val totalFrames: Int
        get() {
            check(handle != 0L) { "ChdReader is closed" }
            return nativeGetTotalFrames(handle)
        }

    fun readSector(lba: Int): ByteArray? {
        check(handle != 0L) { "ChdReader is closed" }
        return nativeReadSector(handle, lba)
    }

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0
        }
    }
}
