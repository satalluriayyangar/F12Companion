# Test vectors

## Golden transport
B002 TX:
`00 01 00 00 03 84 00 00 00 00 00 00 00 00 00 00 00 00 00 00`

B001 RX:
`00 FF 00 19 04 00 00 00 01 00 02 00 00 00 00 00 00 00 00`

## Weather structure
`TT TT TT TT W1 L1 H1 00 00 W2 L2 H2 00 00 W3 L3 H3 00 00`

## Weather header
`00 01 01 00 01 69 00 00 13 00`

The header and structure are documented from the VFit path. Continuation framing,
condition codes, and temperature encoding must be verified against the original
source/capture before release.
