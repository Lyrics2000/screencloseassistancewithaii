# 10. Validation conditions

The flow checks every rule below, and `ResponseValidator.kt` on Android checks them again.
Whichever rule fails first produces the error code. The error code is always
`warnings[0]` of an `ERROR` response.

## A. Input validation (`Compose_Input_Error`, evaluated in this order)

| # | Rule | Error code | HTTP |
|---|---|---|---|
| 1 | `requestId` present | `MISSING_REQUEST_ID` | 400 |
| 2 | `requestId` ≤ 64 chars | `REQUEST_ID_TOO_LONG` | 400 |
| 3 | `sessionId` present | `MISSING_SESSION_ID` | 400 |
| 4 | `sessionId` ≤ 64 chars | `SESSION_ID_TOO_LONG` | 400 |
| 5 | `requestedAction == FIND_AD_CLOSE` | `UNSUPPORTED_ACTION` | 400 |
| 6 | mimeType is jpeg, png or webp | `UNSUPPORTED_MIME_TYPE` | 400 |
| 7 | image present | `MISSING_IMAGE` | 400 |
| 8 | `len(base64)·3/4 ≤ sca_MaxImageBytes` (≤ 7.5 MB hard cap) | `IMAGE_TOO_LARGE` | 400 |
| 9 | base64 magic bytes match mimeType (`/9j/`, `iVBORw0KGgo`, `UklGR`) | `IMAGE_MIME_MISMATCH` | 400 |
| 10 | width and height are integers > 0 | `INVALID_DIMENSIONS` | 400 |
| 11 | width and height ≤ 2576 (Claude's native long edge, so coordinates stay 1:1) | `DIMENSIONS_TOO_LARGE` | 400 |
| 12 | hash is 16 characters | `INVALID_SCREENSHOT_HASH` | 400 |
| 13 | foregroundPackage is in `sca_PackageAllowlist` | `PACKAGE_NOT_ALLOWED` | 400 |

## B. Idempotency and duplicates (Dataverse alternate keys)

| Situation | Result |
|---|---|
| Same `requestId` already COMPLETED | The stored response is replayed unchanged (HTTP 200) |
| Same `requestId` still PROCESSING | `ERROR / REQUEST_IN_PROGRESS` (409) |
| New `requestId`, but `(sessionId, screenshotHash)` exists | `ERROR / DUPLICATE_SCREENSHOT` (409) |
| Two concurrent inserts race | The unique index lets exactly one insert succeed; the loser goes through `Scope_Create_Conflict` |

## C. Model envelope (`Compose_Envelope_Error`)

| Rule | Error code |
|---|---|
| Connector call failed or timed out (60 s, 2 retries for 408/429/5xx) | `MODEL_CALL_FAILED_HTTP_<status>` |
| `stop_reason == end_turn` | `MODEL_STOP_<REASON>` (for example `MODEL_STOP_REFUSAL` or `MODEL_STOP_MAX_TOKENS`) |
| exactly one `text` content block | `MODEL_TEXT_BLOCK_COUNT` |
| text not empty | `MODEL_EMPTY_OUTPUT` |
| trimmed text starts with `{` and ends with `}`, so Markdown fences are rejected | `MODEL_OUTPUT_NOT_JSON` |
| Parse JSON with the schema succeeds | `MODEL_OUTPUT_SCHEMA_INVALID` |

## D. Semantic rules (`Compose_Checks`, `Compose_Validation_Error_Code`)

Let `w`, `h` be the supplied dimensions. Null coordinates are coalesced to `-1`, so they fail the
range checks and are never compared with null.

| Rule | Applies to | Error / outcome |
|---|---|---|
| action is CLOSE, WAIT, NO_ACTION or UNSAFE (after upper-casing) | all | `MODEL_ACTION_INVALID` |
| reason is not blank (it's trimmed and truncated to 300 characters) | all | `MODEL_REASON_MISSING` |
| label and all 6 coordinates are present | CLOSE | `TARGET_OUT_OF_BOUNDS` |
| `0 ≤ left < right ≤ w` and `0 ≤ top < bottom ≤ h` | CLOSE | `TARGET_OUT_OF_BOUNDS` |
| `left ≤ centerX ≤ right`, `top ≤ centerY ≤ bottom` | CLOSE | `TARGET_OUT_OF_BOUNDS` |
| `0 ≤ centerX < w`, `0 ≤ centerY < h` | CLOSE | `TARGET_OUT_OF_BOUNDS` |
| `confidence ≥ max(sca_MinConfidence, 0.90)` | CLOSE | Downgraded to `NO_ACTION` with warning `LOW_CONFIDENCE_CLOSE_SUPPRESSED` |
| `1 ≤ waitSeconds ≤ 60` | WAIT | `WAIT_SECONDS_INVALID` |
| target fields present on a non-CLOSE action | WAIT, NO_ACTION, UNSAFE | target forced to `null`, warning `TARGET_DISCARDED_FOR_NON_CLOSE` |

The flow computes `normalizedX = centerX / w` and `normalizedY = centerY / h`. The model never
supplies these values.

## E. Additional checks on Android (`ResponseValidator`)

- `requestId` and `screenshotHash` must echo the values that were sent (`RESPONSE_MISMATCH`).
- `|normalizedX − centerX/w| ≤ 0.01` and the same for Y (`NORMALIZED_MISMATCH`).
- `target == null` exactly when `action != CLOSE` (`TARGET_CONTRACT_VIOLATION`).
- An unparseable body, a missing field, or an unknown enum becomes `ERROR / RESPONSE_UNPARSEABLE`.
- Before tapping, the app checks that the foreground package still matches the screenshot's
  and that confirmation came within 15 s (`STALE_SCREEN` or `CONFIRMATION_EXPIRED`).
