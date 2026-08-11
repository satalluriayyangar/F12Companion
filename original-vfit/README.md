# Original VFit source

`vfit_src.zip` is the original VFit source/decompiled-source archive previously
supplied for the F12/VFit protocol work.

Treat it as READ-ONLY reference material. It is a JADX/decompiled source archive
and is not necessarily a directly buildable Android Studio project.

Use it to locate and verify:
- BLE characteristics and UUID handling
- CEProtocolB implementation
- weather encoding and condition-code mapping
- temperature encoding
- acknowledgement handling
- custom watch-face upload protocol

Do not guess a protocol field if it can be resolved from this source.
Implement the new F12 Companion app separately and copy only the minimum
source-derived protocol logic needed.
