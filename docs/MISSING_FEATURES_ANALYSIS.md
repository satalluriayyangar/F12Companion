# Missing Features Analysis

Source: `original-vfit/vfit_src.zip`

| Feature | Source found | Exact implementation found | BLE required | Status |
|---|---|---|---|---|
| **Custom watch-face upload** | Yes | Yes | Yes | **Implementable** |
| **Voice reply** | No | No | N/A | **Not in source** |
| **WhatsApp reply** | No | No | N/A | **Not in source** |
| **Call reject** | Yes | Yes | Yes (watch→phone) | **Implementable** |

---

## 1. Custom Watch-Face Upload

### Source files
- `com/vfit/vm/V2ModifyClockdialVM.java` — `cBinFile()` generates `.bin`
- `com/vfit/view/activity/device/watch/V2ModifyClockDialActivity.java` — UI triggers `K6BlueTools.startFileDownload(json)`
- `ce/com/cenewbluesdk/fileTransmission/FileTransControl.java` — chunked BLE upload
- `ce/com/cenewbluesdk/ota/ota_modea/OtaK6Control.java` — shared file-transmission state machine
- `ce/com/cenewbluesdk/proxy/CEDevK6Proxy.java` — `startFileTrans()`, `dealFileInfo()`, `dealFileData()`

### File format (`.bin`)

**Custom face (10-byte header + pixel data):**
```
Offset  Size  Field
0       1     time_pos
1       1     time_up
2       1     time_down
3       2     color (BGR565, big-endian)
5       1     flag (0 = custom, 2 = custom modified)
6       2     width (big-endian)
8       2     height (big-endian)
10      w*h*2 RGB565 pixel data (big-endian per pixel)
```

**Default face (6-byte header, no pixels):**
```
Offset  Size  Field
0       1     time_pos
1       1     time_up
2       1     time_down
3       2     color (BGR565, big-endian)
5       1     flag (1 = default)
```

**Pixel format:** RGB565, 16 bits per pixel, stored big-endian.

**Color conversion (source: `V2ModifyClockdialVM.RGB888ToRGB565`):**
```java
int RGB888ToRGB565(int rgb8888) {
    return ((rgb8888 >> 19) & 31)
         | (((rgb8888 >> 3) & 31) << 11)
         | (((rgb8888 >> 10) & 63) << 5);
}
```

**BGR565 conversion (source: `V2ModifyClockdialVM.bgr565Value`):**
```java
int bgr565Value(int color) {
    return (((color & 255) & 248) << 8)
         | ((((color >> 8) & 255) & 252) << 3)
         | (((color >> 16) & 255) >> 3);
}
```

### Upload protocol

1. **Start upload:** `K6BlueTools.startFileDownload(json)` → Messenger message `what=870558583`, `arg1=pid`, `data=JSON string`
2. **JSON payload:**
   ```json
   {
     "cmd": 2,
     "index": 2,
     "filePath": "/path/to/file.bin",
     "imgPath": "/path/to/image.png",
     "faceType": 0,
     "time_pos": 0,
     "time_up": 0,
     "time_down": 0,
     "color": -1
   }
   ```
3. **ToMainThreadHandler** routes `what=870558583` → `CEDevK6Proxy.startFileTrans(json, isCompressed, isOta=false)`
4. **FileTransControl.sendFile()**:
   - Reads file bytes
   - Optionally gzip-compresses via `GZipUtils.zlib()` if `isComprTransmission && bytes.length > 40`
   - Calls `obtainFileSendStatus()` → sends `CEDevData(3, 132)` to request watch-face state
5. **Device responds** with `K6_DATA_TYPE_WATCH_FACE_INFO` (132) via B001:
   ```
   byte 0: index
   bytes 1..(1+sizeWithoutPic-1): K6_DATA_TYPE_WATCH_FACE_INFO_CMD2
     - byte 0: time_pos
     - byte 1: time_up
     - byte 2: time_down
     - bytes 3-4: color (BGR565 big-endian)
     - byte 5: picture
   next 2 bytes: cmd3Id (big-endian uint16)
   next 4 bytes: all_len (little-endian uint32)
   next 4 bytes: current_pos (little-endian uint32)
   ```
6. **FileTransControl.fileStateResult()** parses response, resets `fPos=0`, starts sending chunks via `sendWatchFileData()`
7. **Chunk format** (`sendWatchFileData`):
   - BLE frame: `CEDevData(cmd=1, dataType=131, priority=-1)`
   - Payload = 9-byte header + chunk data
   - Header:
     - byte 0: cmd (1)
     - bytes 1-4: file_len (little-endian uint32)
     - bytes 5-8: index (little-endian uint32)
   - Chunk size: `DEFAULT_LENGTH` from `CEBlueSharedPreference` (default 300 bytes)
8. **Device ACK:** Sends `dealFileData(payload)` where `payload[0] == -101` (0x9B) for success
9. **Retry:** Up to 5 retries (`otaSendFileMAXTime = 5`) on failure
10. **Completion:** When `fPos >= data.length`, state = `STATE_SENT` (6), then `sendOTAFinish()` sends `CEDevData(1, 118)` (device restart? No, `sendDeviceRestart()` is dataType 118, but `sendOTAFinish()` sends message 908292122 with progress 100)
11. **Progress:** `message.what = -325001435`, `arg1 = percentage`

### Missing/unknown bytes
- `K6_DATA_TYPE_FUNCTION_CONTROL.isHasDialCompress()` controls whether gzip is used. Exact JSON key/format for this control is unknown from the watch-face upload path alone.
- The watch face `cmd3Id` (also called `binId`) is a 16-bit identifier. Its exact generation on the server side is outside the app source.

---

## 2. Voice Reply

### Investigation result

**No voice reply protocol exists in the F12/VFit source.**

Searched for: `voice`, `AudioRecord`, `MediaRecorder`, `PCM`, `AAC`, `SBC`, `Bluetooth SCO`, `BLE audio`, `microphone`, `speech`, `recording`.

**Evidence:**
- `NtfCollector.java` uses `RemoteController` and `MediaSessionManager` exclusively for **music control** (play/pause/next/prev), not voice.
- `BlueToothService.java` handles `RCVD_MUSIC_CONTROL` (dataType commands like play/pause/volume), no audio data paths.
- `K6_DATA_TYPE_MUSIC_CONTROL` sends metadata strings to the watch, never audio bytes.
- No `AudioRecord`, `MediaRecorder`, or Bluetooth audio profile usage exists in the app.

**Conclusion:** The F12 watch does not support BLE voice reply. If the watch has a voice feature, it is handled entirely on-device and never transmitted via BLE to the phone.

---

## 3. WhatsApp Reply

### Investigation result

**No notification reply protocol exists in the F12/VFit source.**

Searched for: `RemoteInput`, `reply`, `PendingIntent`, `Notification.Action`, `MessagingStyle`, `quick reply`, `notification reply`.

**Evidence:**
- `NtfCollector.java` is a `NotificationListenerService` that **only pushes notifications TO the watch** via `sendMessage_notice()`.
- It parses `notification.tickerText`, `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_SUB_TEXT`, etc., and sends them to the watch.
- It **never** reads `Notification.Action` objects, never extracts `RemoteInput` `PendingIntent`s, and never sends data back to WhatsApp or any messaging app.
- `K6_MessageNoticeStruct` contains only: `abs_time`, `type`, and `Property` list (name + content). No reply fields.

**Conclusion:** WhatsApp notifications are display-only on the watch. There is no phone-side code to forward a reply from the watch back to WhatsApp. Implementing this would require inventing a protocol not present in the verified source.

---

## 4. Call Reject

### Source files
- `com/vfit/receiver/PhoneStatReceiver.java` — detects incoming call, pushes caller info to watch
- `com/k6_wrist_android/util/HangUpTelephonyUtil.java` — ends call on Android
- `com/k6_wrist_android/data/blue/blue_thread/BlueToothService.java` — handles `RCVD_PHONE_CONTROL` from watch

### Protocol

**Watch → Phone BLE command:**
- Data type: `DATA_TYPE_CALL_CONTROL_TO_APP` = 15
- Payload: 1 byte
  - `0` = idle/no call
  - `1` = answered/offhook
  - `2` = **hang up / reject**
  - `3` = silent / mute

**Phone-side handler (source: `BlueToothService.java` lines 521-539):**
```java
mK6AnalysiDevRcvDataManager.addBleDataResultListner(
    K6_Action.RCVD.RCVD_PHONE_CONTROL,
    new K6BleDataResult<Integer>() {
        public boolean bleDataResult(Integer in) {
            if (in.intValue() == 3) {
                ((AudioManager) App.getInstance().getSystemService("audio")).setRingerMode(0);
            } else if (in.intValue() == 2) {
                try {
                    HangUpTelephonyUtil.endCall1(App.getInstance());
                } catch (Exception e) { ... }
            }
        }
    }
);
```

**Call rejection implementation (source: `HangUpTelephonyUtil.java`):**
```java
public static void endCall1(Context paramContext) throws Exception {
    // Try modern API first
    if (Build.VERSION.SDK_INT >= 21) {
        TelecomManager telecomManager = (TelecomManager) paramContext.getSystemService("telecom");
        if (ActivityCompat.checkSelfPermission(paramContext, "android.permission.ANSWER_PHONE_CALLS") != 0) {
            return;
        }
        telecomManager.endCall();
    }
    // Fallback: reflection on TelephonyManager
    TelephonyManager telephonyManager = (TelephonyManager) paramContext.getSystemService("phone");
    Method declaredMethod = telephonyManager.getClass().getDeclaredMethod("getITelephony", new Class[0]);
    declaredMethod.setAccessible(true);
    Object telephony = declaredMethod.invoke(paramContext, new Object[0]);
    Method method = paramContext.getClass().getMethod("endCall", new Class[0]);
    method.setAccessible(true);
    method.invoke(paramContext, new Object[0]);
}
```

**Required permissions:**
- `android.permission.ANSWER_PHONE_CALLS` (API 21+)
- `android.permission.CALL_PHONE` (some devices)
- `android.permission.READ_PHONE_STATE` (for incoming call detection)

**Incoming call push to watch (source: `PhoneStatReceiver.java`):**
- Listens for `android.intent.action.PHONE_STATE`
- On `CALL_STATE_RINGING` (1): looks up contact name, sends `sendMessage_notice(time, contactName, "", TYPE_PHONE=1)`
- On `CALL_STATE_OFFHOOK` (2): sends `sendCall(1)` (answered)
- On `CALL_STATE_IDLE` (0): sends `sendCall(2)` (missed/ended)
