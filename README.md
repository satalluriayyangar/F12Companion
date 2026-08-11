# F12 Companion — Android Weather Companion

## Original VFit source

The repository includes the original source archive:

```text
original-vfit/vfit_src.zip
```

This is the authoritative reference to inspect before implementing any
unverified F12/VFit protocol detail. It is a JADX/decompiled source archive and
may not be a directly buildable Android project.

Search it for:

```text
B001
B002
0000B001
0000B002
F618
CEProtocolB
weather
69
weather code
watch face
upload
```

Prefer source-derived behavior and the successful BLE capture over guesses.
Keep the archive READ-ONLY.

## Goal
Build a minimal Android companion for the F12 smartwatch:
- connect over BLE
- subscribe to B001 notifications
- write weather to B002
- fetch phone 3-day weather
- encode using the VFit weather structure
- log all TX/RX HEX

Custom watch faces are out of scope.

## BLE UUIDs

Service:
`0000F618-0000-1000-8000-00805F9B34FB`

B001 — notify/receive:
`0000B001-0000-1000-8000-00805F9B34FB`

B002 — write/transmit:
`0000B002-0000-1000-8000-00805F9B34FB`

Never put these UUIDs inside the payload. Select the characteristic in nRF Connect/app and enter only protocol bytes.

## Known VFit weather application structure

Weather data type:
`0x69`

Command:
`0x01`

Weather application payload is 19 bytes:

```text
TT TT TT TT
W1 L1 H1 00 00
W2 L2 H2 00 00
W3 L3 H3 00 00
```

Where:
- `TT TT TT TT` = Unix timestamp, little-endian
- `Wn` = VFit weather/condition code
- `Ln` = minimum temperature byte
- `Hn` = maximum temperature byte
- `00 00` = reserved/PM field observed in the VFit weather structure

Do NOT invent the condition-code mapping or temperature encoding. Verify these against the original VFit source/capture before production.

## Known weather transport header

The VFit/CEProtocolB weather header is:

```text
00 01 01 00 01 69 00 00 13 00
```

`13 00` is the 19-byte payload length.

The first frame is 20 bytes:

```text
00 01 01 00 01 69 00 00 13 00
TT TT TT TT
W1 L1 H1 00 00
```

A continuation frame was reconstructed as:

```text
01
W2 L2 H2 00 00
W3 L3 H3 00 00
```

### Protocol warning
The continuation/chunk framing above MUST be verified against the successful VFit BLE capture before being treated as production-safe. Do not guess missing protocol bytes.

## Golden BLE transport test

Previously observed working diagnostic exchange:

B002 TX:
```text
00 01 00 00 03 84 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

B001 RX:
```text
00 FF 00 19 04 00 00 00 01 00 02 00 00 00 00 00 00 00 00
```

This is a transport test, NOT a weather packet.

## Required implementation stages

### Stage 1 — BLE diagnostics
Implement:
- F618 discovery
- B001 notification subscription
- B002 write
- scan/connect/reconnect
- TX/RX HEX logger

Acceptance test:
F12 discovered -> connected -> B001 subscribed -> golden B002 packet sent -> expected B001 response observed.

### Stage 2 — Weather encoder
Create pure Kotlin `VFitWeatherEncoder`.
Output exactly 19 bytes.
Unit-test timestamp endianness, 3 daily records, and reserved bytes.

### Stage 3 — CEProtocolB
Create pure Kotlin encoder:
`encodeWeather(payload19): List<ByteArray>`
Unit-test exact output.

### Stage 4 — Weather provider
Use an interface:
```kotlin
interface WeatherProvider {
    suspend fun forecast(latitude: Double, longitude: Double): WeatherForecast
}
```
Provider can be Open-Meteo or another suitable source. Keep provider separate from protocol code.

### Stage 5 — Automatic sync
Implement:
- Sync Now
- reconnect sync
- periodic refresh
- cached last successful forecast
- TX/RX log
- errors
- don't retransmit unchanged weather

## Android build requirements
- Kotlin
- Android Gradle Plugin 8.x
- compileSdk 35
- minSdk 26
- targetSdk 35
- JDK 17
- Kotlin JVM target 17

Android 12+ BLE permissions:
`BLUETOOTH_SCAN`
`BLUETOOTH_CONNECT`

For ongoing synchronization, evaluate a foreground service using the `connectedDevice` service type under the current Android rules.

## Rules
1. Never write to B001.
2. B002 is the write characteristic.
3. B001 is the notification characteristic.
4. Never include UUIDs in payloads.
5. Do not send the bare 19-byte weather structure until a capture proves that is accepted.
6. Require an acknowledgement/response before declaring success.
7. Preserve the golden exchange as a regression test.
8. If source and capture disagree, stop and flag it instead of guessing.
