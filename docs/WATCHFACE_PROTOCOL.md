# Watch Face Protocol

Source: `original-vfit/vfit_src.zip`

## File Format

Extension: `.bin`

### Custom Watch Face (10-byte header + pixel data)

| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | time_pos | Time display position (0=above, 1=below) |
| 1 | 1 | time_up | Element above time (0=none, 1=date, 2=sleep, 4=heart, 8=steps) |
| 2 | 1 | time_down | Element below time (same values as time_up) |
| 3 | 2 | color | Text color in BGR565, big-endian |
| 5 | 1 | flag | 0 = custom with image, 2 = modified custom |
| 6 | 2 | width | Image width in pixels, big-endian |
| 8 | 2 | height | Image height in pixels, big-endian |
| 10 | w*h*2 | pixels | RGB565 pixel data, big-endian per pixel |

### Default Watch Face (6-byte header)

| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | time_pos | Time display position |
| 1 | 1 | time_up | Element above time |
| 2 | 1 | time_down | Element below time |
| 3 | 2 | color | Text color in BGR565, big-endian |
| 5 | 1 | flag | 1 = default (no pixel data follows) |

## Color Conversions

### BGR565 (source: V2ModifyClockdialVM.bgr565Value)

Input: Android ARGB int (`0xAARRGGBB`)

```
BGR565 = ((B & 0xF8) << 8) | ((G & 0xFC) << 3) | (R >> 3)
```

Stored as 2 bytes big-endian:
- byte 0 = (BGR565 >> 8) & 0xFF
- byte 1 = BGR565 & 0xFF

### RGB888 to RGB565 (source: V2ModifyClockdialVM.RGB888ToRGB565)

Input: ARGB int from `Bitmap.getPixels()`

```
RGB565 = ((rgb >> 19) & 0x1F)
       | (((rgb >> 3) & 0x1F) << 11)
       | (((rgb >> 10) & 0x3F) << 5)
```

Equivalent to:
- Red: bits 19-23 (top 5 of 8) → bits 11-15
- Green: bits 10-15 (lower 6 of 8) → bits 5-10
- Blue: bits 3-7 (top 5 of 8) → bits 0-4

Wait — this maps to **BGR565** bit layout, not standard RGB565. The VFit source treats the 16-bit value as `BBBBB GGGGGG RRRRR` when rendered.

## BLE Upload Protocol

### Commands

| Direction | Cmd | DataType | Purpose |
|-----------|-----|----------|---------|
| Phone→Watch | 3 | 132 | Request watch-face info |
| Watch→Phone | 1 | 132 | Watch-face info response |
| Phone→Watch | 1 | 131 | File data chunk |
| Watch→Phone | 1 | 131 | ACK (-101 = success) |

### Sequence

1. Phone sends `CEDevData(3, 132)` to request upload state
2. Watch responds with `K6_DATA_TYPE_WATCH_FACE_INFO` on B001
3. Phone sends file chunks via `sendWatchFileData()`:
   - Payload = 9-byte header + chunk data
   - Header: `[cmd(1), file_len(4 LE), index(4 LE)]`
   - Chunk size: `DEFAULT_LENGTH` (default 300 bytes)
4. Watch ACKs each chunk with `dealFileData([-101])`
5. On failure: retry up to 5 times
6. On completion: `sendOTAFinish()` (message 908292122 with progress 100)

### JSON Start Message

Sent via Messenger (`what=870558583`):

```json
{
  "cmd": 2,
  "index": 2,
  "filePath": "/absolute/path/to/file.bin",
  "imgPath": "/absolute/path/to/source/image.png",
  "faceType": 0,
  "time_pos": 0,
  "time_up": 0,
  "time_down": 0,
  "color": -1
}
```

Fields:
- `cmd`: command type (2 for file download/upload)
- `index`: watch face index
- `filePath`: absolute path to `.bin` file
- `imgPath`: absolute path to source image (for preview)
- `faceType`: 0 = circle, 1 = square
- `time_pos`: 0 = above, 1 = below
- `time_up`: bitmask for above-time elements
- `time_down`: bitmask for below-time elements
- `color`: ARGB text color

## Compression

Optional gzip compression controlled by `K6_DATA_TYPE_FUNCTION_CONTROL.isHasDialCompress()`.

When enabled and file > 40 bytes:
1. Raw file is compressed with zlib (`deflaterZip`)
2. 20-byte wrapper is prepended:
   - bytes 0-3: compressed length + 20 (little-endian)
   - bytes 4-5: CRC16 of compressed data
   - bytes 6-7: marker `0xFEFE`
   - bytes 8-10: type info (varies)
   - byte 11: subtype

For simplicity, the F12 Companion implementation sends uncompressed data by default.

## Progress

Progress updates via Messenger:
- `what = -325001435`
- `arg1 = percentage` (0-100)

## States

| State | Value | Description |
|-------|-------|-------------|
| STATE_NONE | 0 | Idle |
| STATE_NEED_UPDATE | 1 | Update available |
| STATE_DOWNLOADING | 2 | Downloading |
| STATE_READY_OK | 3 | Ready to send |
| STATE_HAS_DEV_STATE | 4 | Device state received |
| STATE_SENDING | 5 | Sending file |
| STATE_SENT | 6 | File sent completely |
| STATE_FINISH | 7 | Finished |
| STATE_CRC_OK | 7 | CRC valid (OTA only) |
