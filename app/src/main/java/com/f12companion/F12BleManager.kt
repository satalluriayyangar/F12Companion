package com.f12companion

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.f12companion.model.BleLogEntry
import com.f12companion.model.Direction
import com.f12companion.uitl.ByteUtil
import com.f12companion.weather.CEProtocolBEncoder
import com.f12companion.weather.WeatherForecast
import com.f12companion.weather.VFitWeatherEncoder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class F12BleManager(private val context: Context) {
    private val _state = MutableStateFlow<com.f12companion.model.BleState>(com.f12companion.model.BleState.Idle)
    val state = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val logs: Flow<List<BleLogEntry>> = _logs.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val SERVICE_UUID = UUID.fromString("0000F618-0000-1000-8000-00805F9B34FB")
    private val B001_UUID = UUID.fromString("0000B001-0000-1000-8000-00805F9B34FB")
    private val B002_UUID = UUID.fromString("0000B002-0000-1000-8000-00805F9B34FB")

    private var pendingGoldenCallback: ((Boolean, String) -> Unit)? = null
    private var currentWriteIndex = 0
    private var pendingFrames: List<ByteArray> = emptyList()
    private var frameWriteCallback: ((Boolean) -> Unit)? = null

    private var watchFaceUploadCallback: ((Boolean, String) -> Unit)? = null
    private var watchFaceChunks: List<ByteArray> = emptyList()
    private var watchFaceChunkIndex = 0
    private var watchFaceRetryCount = 0
    private var watchFaceFileBytes: ByteArray? = null
    private var watchFaceFilePos = 0
    private val WATCH_FACE_DEFAULT_LENGTH = 300

    var onCallControlReceived: ((Int) -> Unit)? = null
    var onWatchFaceInfoReceived: ((Int, Int, Int, Int, Int) -> Unit)? = null

    companion object {
        val GOLDEN_TX = byteArrayOf(
            0x00, 0x01, 0x00, 0x00, 0x03, 0x84.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00
        )
        val GOLDEN_RX = byteArrayOf(
            0x00, 0xFF.toByte(), 0x00, 0x19, 0x04, 0x00,
            0x00, 0x00, 0x01, 0x00, 0x02, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00
        )
        const val DATA_TYPE_WATCH_FACE_INFO = 132
        const val DATA_TYPE_WATCH_FACE_SYNC = 131
        const val DATA_TYPE_CALL_CONTROL = 15
        const val WATCH_FACE_ACK = -101
    }

    fun scan(): Flow<BluetoothDevice> = callbackFlow {
        _state.value = com.f12companion.model.BleState.Scanning
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.device)
            }
            override fun onScanFailed(errorCode: Int) {
                _state.value = com.f12companion.model.BleState.Error("Scan failed: $errorCode")
                close()
            }
        }

        scanner.startScan(listOf(filter), settings, callback)
        awaitClose {
            scanner.stopScan(callback)
            if (_state.value is com.f12companion.model.BleState.Scanning) {
                _state.value = com.f12companion.model.BleState.Idle
            }
        }
    }

    fun connect(device: BluetoothDevice): Flow<com.f12companion.model.BleState> = callbackFlow {
        _state.value = com.f12companion.model.BleState.Connecting(device.address)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothGatt = gatt
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    _state.value = com.f12companion.model.BleState.Disconnected(if (status == 19) "GATT_CONN_TIMEOUT" else "status=$status")
                    close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _state.value = com.f12companion.model.BleState.Error("Service discovery failed: $status")
                    gatt.disconnect()
                    close()
                    return
                }
                val service = gatt.getService(SERVICE_UUID)
                notifyCharacteristic = service?.getCharacteristic(B001_UUID)
                writeCharacteristic = service?.getCharacteristic(B002_UUID)

                if (notifyCharacteristic == null || writeCharacteristic == null) {
                    _state.value = com.f12companion.model.BleState.Error("Missing B001 or B002 characteristic")
                    gatt.disconnect()
                    close()
                    return
                }

                val notifyOk = gatt.setCharacteristicNotification(notifyCharacteristic, true)
                if (!notifyOk) {
                    _state.value = com.f12companion.model.BleState.Error("Failed to enable B001 notifications")
                    gatt.disconnect()
                    close()
                    return
                }

                _state.value = com.f12companion.model.BleState.Connected(device.address)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == B001_UUID) {
                    val bytes = characteristic.value ?: return
                    val hex = bytes.joinToString(" ") { "%02X".format(it) }
                    addLog(Direction.RX, hex)

                    pendingGoldenCallback?.let { cb ->
                        val match = bytes.contentEquals(GOLDEN_RX)
                        cb(match, if (match) "Golden RX matched" else "Unexpected RX: $hex")
                        pendingGoldenCallback = null
                    }

                    if (bytes.size >= 2) {
                        val dataType = bytes[5].toInt() and 0xFF
                        if (dataType == DATA_TYPE_CALL_CONTROL && bytes.size >= 7) {
                            val cmd = bytes[6].toInt() and 0xFF
                            onCallControlReceived?.invoke(cmd)
                        } else if (dataType == DATA_TYPE_WATCH_FACE_INFO && bytes.size >= 15) {
                            val index = bytes[6].toInt() and 0xFF
                            val cmd3Id = ByteUtil.byte2ToInt(byteArrayOf(bytes[7], bytes[8]))
                            val allLen = ByteUtil.byte4ToInt(byteArrayOf(bytes[9], bytes[10], bytes[11], bytes[12]))
                            val currentPos = ByteUtil.byte4ToInt(byteArrayOf(bytes[13], bytes[14], bytes[15], bytes[16]))
                            onWatchFaceInfoReceived?.invoke(index, cmd3Id, allLen, currentPos, dataType)
                        } else if (dataType == DATA_TYPE_WATCH_FACE_SYNC && bytes.size >= 7) {
                            val ack = bytes[6].toInt()
                            if (ack == WATCH_FACE_ACK) {
                                watchFaceRetryCount = 5
                                watchFaceFilePos += watchFaceChunks.getOrNull(watchFaceChunkIndex)?.size ?: 0
                                watchFaceChunkIndex++
                                writeNextWatchFaceChunk()
                            } else {
                                watchFaceRetryCount--
                                if (watchFaceRetryCount >= 0) {
                                    writeCurrentWatchFaceChunk()
                                } else {
                                    watchFaceUploadCallback?.invoke(false, "Watch face upload failed after retries")
                                    resetWatchFaceUpload()
                                }
                            }
                        }
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeNextFrame()
                } else {
                    Log.e("F12BleManager", "Write failed: $status")
                    frameWriteCallback?.invoke(false)
                    frameWriteCallback = null
                    pendingFrames = emptyList()
                }
            }
        }

        device.connectGatt(context, false, callback)
        awaitClose {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    fun sendGoldenPacket(): Boolean {
        val char = writeCharacteristic ?: return false
        char.value = GOLDEN_TX
        val ok = bluetoothGatt?.writeCharacteristic(char) ?: false
        if (ok) {
            val hex = GOLDEN_TX.joinToString(" ") { "%02X".format(it) }
            addLog(Direction.TX, hex)
        }
        return ok
    }

    fun sendGoldenPacketAndWait(timeoutMs: Long = 5000L, onResult: (Boolean, String) -> Unit) {
        if (!sendGoldenPacket()) {
            onResult(false, "Write failed")
            return
        }
        pendingGoldenCallback = onResult
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (pendingGoldenCallback != null) {
                pendingGoldenCallback = null
                onResult(false, "Timeout waiting for B001 response")
            }
        }, timeoutMs)
    }

    fun sendWeather(forecast: WeatherForecast, onResult: (Boolean, String) -> Unit) {
        val payload = VFitWeatherEncoder.encode(forecast)
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendCallAlarm(enabled: Boolean, onResult: (Boolean, String) -> Unit) {
        val payload = byteArrayOf(if (enabled) 1 else 0)
        val frames = encodeSimpleCommand(122, payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendMessageAlarm(enabled: Boolean, onResult: (Boolean, String) -> Unit) {
        val payload = byteArrayOf(if (enabled) 1 else 0)
        val frames = encodeSimpleCommand(123, payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendWatchFaceSetting(pushSettingValue: Int, onResult: (Boolean, String) -> Unit) {
        val payload = byteArrayOf(
            (pushSettingValue and 0xFF).toByte(),
            ((pushSettingValue shr 8) and 0xFF).toByte(),
            0,
            0
        )
        val frames = encodeSimpleCommand(124, payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendMessageNotice(name: String, content: String, messageType: Byte, onResult: (Boolean, String) -> Unit) {
        val absTime = com.f12companion.uitl.ByteUtil.intToByte4((System.currentTimeMillis() / 1000).toInt())
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val contentBytes = content.toByteArray(Charsets.UTF_8)

        val maxContentLen = 230
        val trimmedContent = if (contentBytes.size > maxContentLen) {
            String(contentBytes, 0, maxContentLen - 3, Charsets.UTF_8) + "..."
        } else {
            content
        }
        val finalContentBytes = trimmedContent.toByteArray(Charsets.UTF_8)

        val infoSize = 2 + nameBytes.size + 2 + finalContentBytes.size
        val payload = ByteArray(absTime.size + 2 + infoSize)
        System.arraycopy(absTime, 0, payload, 0, absTime.size)
        payload[absTime.size] = messageType
        payload[absTime.size + 1] = 2

        var offset = absTime.size + 2
        val nameAttr = byteArrayOf(0, nameBytes.size.toByte())
        System.arraycopy(nameAttr, 0, payload, offset, 2)
        offset += 2
        System.arraycopy(nameBytes, 0, payload, offset, nameBytes.size)
        offset += nameBytes.size

        val contentAttr = byteArrayOf(2, finalContentBytes.size.toByte())
        System.arraycopy(contentAttr, 0, payload, offset, 2)
        offset += 2
        System.arraycopy(finalContentBytes, 0, payload, offset, finalContentBytes.size)

        val frames = encodeSimpleCommand(107, payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendCallControl(state: Int, onResult: (Boolean, String) -> Unit) {
        val payload = byteArrayOf(state.toByte())
        val frames = encodeSimpleCommand(117, payload)
        sendFramesSequentially(frames, onResult)
    }

    fun sendWatchFaceInfoRequest(onResult: (Boolean, String) -> Unit) {
        val frames = encodeSimpleCommand(132, ByteArray(0))
        sendFramesSequentially(frames, onResult)
    }

    fun uploadWatchFace(file: File, onProgress: (Int) -> Unit, onResult: (Boolean, String) -> Unit) {
        if (!file.exists() || writeCharacteristic == null) {
            onResult(false, "File not found or not connected")
            return
        }
        val bytes = file.readBytes()
        if (bytes.isEmpty()) {
            onResult(false, "Empty file")
            return
        }

        watchFaceFileBytes = bytes
        watchFaceFilePos = 0
        watchFaceChunkIndex = 0
        watchFaceRetryCount = 5
        watchFaceUploadCallback = onResult

        val chunkSize = WATCH_FACE_DEFAULT_LENGTH
        watchFaceChunks = bytes.toList().chunked(chunkSize).map { it.toByteArray() }

        sendWatchFaceInfoRequest { success, message ->
            if (success) {
                Log.d("F12BleManager", "Watch face info requested, waiting for device response...")
            } else {
                onResult(false, "Failed to request watch face info: $message")
            }
        }
    }

    private fun writeNextWatchFaceChunk() {
        if (watchFaceChunkIndex >= watchFaceChunks.size) {
            val progress = 100
            watchFaceUploadCallback?.invoke(true, "Upload complete: ${watchFaceFileBytes?.size ?: 0} bytes")
            resetWatchFaceUpload()
            return
        }

        val chunk = watchFaceChunks[watchFaceChunkIndex]
        val header = ByteArray(9)
        header[0] = 1
        val fileLen = watchFaceFileBytes ?: return
        System.arraycopy(ByteUtil.intToByte4(fileLen.size), 0, header, 1, 4)
        System.arraycopy(ByteUtil.intToByte4(watchFaceFilePos), 0, header, 5, 4)

        val frame = ByteArray(9 + chunk.size)
        System.arraycopy(header, 0, frame, 0, 9)
        System.arraycopy(chunk, 0, frame, 9, chunk.size)

        val ceData = ByteArray(2 + frame.size)
        ceData[0] = 1
        ceData[1] = DATA_TYPE_WATCH_FACE_SYNC.toByte()
        System.arraycopy(frame, 0, ceData, 2, frame.size)

        val fullFrame = ByteArray(20)
        fullFrame[0] = 0
        fullFrame[1] = 1
        fullFrame[2] = 0
        fullFrame[3] = 0
        fullFrame[4] = 1
        fullFrame[5] = DATA_TYPE_WATCH_FACE_SYNC.toByte()
        fullFrame[6] = 0
        fullFrame[7] = 0
        fullFrame[8] = (ceData.size and 0xFF).toByte()
        fullFrame[9] = ((ceData.size shr 8) and 0xFF).toByte()
        System.arraycopy(ceData, 0, fullFrame, 10, minOf(ceData.size, 10))

        val progress = ((watchFaceFilePos.toFloat() / (watchFaceFileBytes?.size ?: 1)) * 100).toInt()
        watchFaceUploadCallback?.let { _ ->
            onWatchFaceProgress?.invoke(progress)
        }

        val char = writeCharacteristic ?: return
        char.value = fullFrame
        val ok = bluetoothGatt?.writeCharacteristic(char) ?: false
        if (ok) {
            val hex = fullFrame.joinToString(" ") { "%02X".format(it) }
            addLog(Direction.TX, hex)
        } else {
            watchFaceRetryCount--
            if (watchFaceRetryCount >= 0) {
                SystemClock.sleep(50)
                writeNextWatchFaceChunk()
            } else {
                watchFaceUploadCallback?.invoke(false, "Write failed")
                resetWatchFaceUpload()
            }
        }
    }

    private fun writeCurrentWatchFaceChunk() {
        writeNextWatchFaceChunk()
    }

    private fun resetWatchFaceUpload() {
        watchFaceFileBytes = null
        watchFaceFilePos = 0
        watchFaceChunkIndex = 0
        watchFaceChunks = emptyList()
        watchFaceRetryCount = 0
        watchFaceUploadCallback = null
    }

    var onWatchFaceProgress: ((Int) -> Unit)? = null

    private fun encodeSimpleCommand(dataType: Int, payload: ByteArray): List<ByteArray> {
        val ceData = com.f12companion.uitl.ByteUtil.int2bytes2(payload.size)
        val header = ByteArray(20)
        header[0] = 0
        header[1] = 1
        header[2] = 0
        header[3] = 0
        header[4] = 1
        header[5] = dataType.toByte()
        header[6] = 0
        header[7] = 0
        header[8] = ceData[0]
        header[9] = ceData[1]
        if (payload.size > 0) {
            System.arraycopy(payload, 0, header, 10, minOf(payload.size, 10))
        }
        return listOf(header)
    }

    private fun sendFramesSequentially(frames: List<ByteArray>, finalCallback: (Boolean, String) -> Unit) {
        if (frames.isEmpty() || writeCharacteristic == null) {
            finalCallback(false, "No frames or not connected")
            return
        }
        pendingFrames = frames
        currentWriteIndex = 0
        frameWriteCallback = { success ->
            if (!success) {
                finalCallback(false, "Write failed")
                frameWriteCallback = null
                pendingFrames = emptyList()
            } else if (currentWriteIndex >= pendingFrames.size) {
                finalCallback(true, "Sent ${frames.size} frames")
                frameWriteCallback = null
                pendingFrames = emptyList()
            }
        }
        writeNextFrame()
    }

    private fun writeNextFrame() {
        if (currentWriteIndex >= pendingFrames.size) {
            frameWriteCallback?.invoke(true)
            return
        }
        val frame = pendingFrames[currentWriteIndex]
        val char = writeCharacteristic ?: return
        char.value = frame
        val ok = bluetoothGatt?.writeCharacteristic(char) ?: false
        if (!ok) {
            frameWriteCallback?.invoke(false)
        } else {
            val hex = frame.joinToString(" ") { "%02X".format(it) }
            addLog(Direction.TX, hex)
            currentWriteIndex++
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _state.value = com.f12companion.model.BleState.Idle
    }

    private fun addLog(direction: Direction, hex: String) {
        _logs.value = _logs.value + BleLogEntry(direction, hex)
    }
}
