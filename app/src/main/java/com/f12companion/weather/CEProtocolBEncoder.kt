package com.f12companion.weather

object CEProtocolBEncoder {
    private const val SL_DATA_L = 20
    private const val HEADER_SIZE = 10
    private const val PAYLOAD_PER_CONTINUATION = 19

    fun encode(data: ByteArray, cmd: Int, dataType: Int, devType: Int = 1, n: Int = 0): List<ByteArray> {
        val length = data.size
        val totalPage = getPageNumber(length)
        val frames = mutableListOf<ByteArray>()

        val header = ByteArray(SL_DATA_L)
        header[0] = 0
        header[1] = (devType and 0xFF).toByte()
        header[2] = (totalPage and 0xFF).toByte()
        header[3] = (n and 0xFF).toByte()
        header[4] = (cmd and 0xFF).toByte()
        header[5] = (dataType and 0xFF).toByte()
        header[6] = 0 // CRC low
        header[7] = 0 // CRC high
        header[8] = (length and 0xFF).toByte()
        header[9] = ((length shr 8) and 0xFF).toByte()

        if (length <= HEADER_SIZE) {
            System.arraycopy(data, 0, header, HEADER_SIZE, length)
            frames.add(header)
        } else {
            val firstPayloadSize = SL_DATA_L - HEADER_SIZE
            System.arraycopy(data, 0, header, HEADER_SIZE, firstPayloadSize)
            frames.add(header)

            val remaining = ByteArray(length - firstPayloadSize)
            System.arraycopy(data, firstPayloadSize, remaining, 0, remaining.size)

            var remainingIndex = 0
            var pageIndex = 1
            while (remainingIndex < remaining.size) {
                val frame = ByteArray(SL_DATA_L)
                frame[0] = (pageIndex and 0xFF).toByte()
                val chunkSize = minOf(PAYLOAD_PER_CONTINUATION, remaining.size - remainingIndex)
                System.arraycopy(remaining, remainingIndex, frame, 1, chunkSize)
                frames.add(frame)
                remainingIndex += chunkSize
                pageIndex++
            }
        }

        return frames
    }

    fun encodeWeather(payload19: ByteArray, devType: Int = 1, n: Int = 0): List<ByteArray> {
        return encode(payload19, cmd = 1, dataType = 105, devType = devType, n = n)
    }

    private fun getPageNumber(bytesLength: Int): Int {
        if (bytesLength <= HEADER_SIZE) return 0
        val remaining = bytesLength - HEADER_SIZE
        var pages = remaining / PAYLOAD_PER_CONTINUATION
        if (remaining % PAYLOAD_PER_CONTINUATION > 0) pages++
        return minOf(pages, 255)
    }
}
