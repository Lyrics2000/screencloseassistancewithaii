# ADR-006: 64-bit difference hash (dHash) for screenshot identity

- Status: Accepted
- Context: The app needs a perceptual hash to detect "the screen didn't change" after a tap, and
  a deterministic key for server-side de-duplication.
- Decision: dHash. Downscale to 9×8 luminance with box averaging, and set bit = left > right, which
  gives 64 bits (16 hex characters). It's cheap (under 5 ms), stable across JPEG re-encoding, and
  simple to unit test on raw pixel arrays. On the device, a Hamming distance of 4 or less from any
  frame already analysed in the session counts as a duplicate, and the session stops with
  `SCREEN_UNCHANGED`. The server compares hashes exactly.
- Rejected: pHash, which needs a DCT and is more code for no benefit here. Cryptographic hashes
  aren't perceptual, because any pixel change (a clock tick) alters them.
