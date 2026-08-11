# Kilo Code implementation brief

Read `README.md` first.

Build an Android Kotlin app named F12 Companion. Its purpose is automatic 3-day
weather synchronization to an F12 smartwatch over BLE.

Start with BLE diagnostics only. Reproduce the golden B002/B001 exchange before
implementing weather.

UUIDs:
- Service: 0000F618-0000-1000-8000-00805F9B34FB
- B001 notify: 0000B001-0000-1000-8000-00805F9B34FB
- B002 write: 0000B002-0000-1000-8000-00805F9B34FB

Golden TX on B002:
00 01 00 00 03 84 00 00 00 00 00 00 00 00 00 00 00 00 00 00

Expected B001 RX:
00 FF 00 19 04 00 00 00 01 00 02 00 00 00 00 00 00 00 00

Only after that passes, implement the 19-byte VFit weather structure and then
CEProtocolB framing. Do not guess condition-code mappings, temperature encoding,
or unverified continuation framing. Keep protocol encoders pure and unit-tested.


## Mandatory original-source inspection

Before implementing CEProtocolB, weather encoding, or any watch-face protocol,
inspect:

`original-vfit/vfit_src.zip`

Resolve from the original VFit code wherever possible:
- exact B001/B002 handling
- CEProtocolB frame/chunk construction
- weather command/data type
- weather condition-code mapping
- temperature encoding
- acknowledgement parsing
- custom watch-face upload protocol

If source and capture disagree, document the discrepancy and do not guess.
