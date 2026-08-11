package com.f12companion.uitl

object ByteUtil {
    fun intToByte4(i: Int): ByteArray {
        return byteArrayOf(
            (i and 0xFF).toByte(),
            ((i shr 8) and 0xFF).toByte(),
            ((i shr 16) and 0xFF).toByte(),
            ((i shr 24) and 0xFF).toByte()
        )
    }

    fun byte4ToInt(b: ByteArray): Int {
        require(b.size >= 4) { "Need at least 4 bytes" }
        return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
    }

    fun byte2ToInt(b: ByteArray): Int {
        require(b.size >= 2) { "Need at least 2 bytes" }
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    fun int2bytes2(c: Int): ByteArray {
        return byteArrayOf((c and 0xFF).toByte(), ((c shr 8) and 0xFF).toByte())
    }
}
