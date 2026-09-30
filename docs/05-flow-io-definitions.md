# 5. Agent-flow and flow input/output definitions

## SCA Analyze Core (child flow, trigger *Manually trigger a flow*)

Add the inputs **in this order** so the designer creates the property keys shown. All
expressions in the definitions refer to these keys.

| # | Designer input type | Title | Generated key | Rules (enforced in `Compose_Input_Error`) |
|---|---|---|---|---|
| 1 | Text | requestId | `text` | required, 1–64 chars |
| 2 | Text | sessionId | `text_1` | required, 1–64 chars |
| 3 | Text | screenshotBase64 | `text_2` | required; decoded size ≤ `sca_MaxImageBytes` (hard cap 7.5 MB); magic bytes must match mimeType |
| 4 | Text | mimeType | `text_3` | `image/jpeg`, `image/png`, `image/webp` |
| 5 | Number | screenshotWidth | `number` | integer, 1–2576 |
| 6 | Number | screenshotHeight | `number_1` | integer, 1–2576 |
| 7 | Text | screenshotHash | `text_4` | exactly 16 characters (64-bit dHash, hex) |
| 8 | Text | foregroundPackage | `text_5` | must be in `sca_PackageAllowlist` |
| 9 | Text | requestedAction | `text_6` | must equal `FIND_AD_CLOSE` |
| 10 | Text | channel | `text_7` | `ANDROID_HTTP` or `COPILOT_TOPIC` (audit only) |

Output (**Respond to a PowerApp or flow**):

| Title | Key | Type | Meaning |
|---|---|---|---|
| responseJson | `responsejson` | Text | The final SCA response document, serialized |
| httpStatus | `httpstatus` | Number | 200 on success; 400 for input errors; 409 for duplicates or a request in progress; 500 for internal errors |

## SCA Android HTTP Entry (cloud flow)

- Trigger **When an HTTP request is received**, method `POST`.
- Request body: `power-platform/schemas/android-request.schema.json`.
- Response: `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`,
  `x-sca-request-id`, `x-sca-flow-run-id`. The body is the SCA response document; see
  `power-platform/schemas/android-response.schema.json`.

## SCA Agent Analyze (agent flow, plan: Copilot Studio)

- Trigger **When an agent calls the flow** with inputs 1–9 above (same titles, in the same
  order, so the keys match).
- **Respond to the agent** output: `responseJson` (Text).
- Asynchronous response: **Off**. It must respond within 100 s.

## Environment variables (solution ScreenCloseAssistant)

| Display name | Schema name | Type | Default |
|---|---|---|---|
| SCA Package Allowlist | `sca_PackageAllowlist` | Text (JSON array) | `["com.schoolproject.adtestbed"]` |
| SCA Max Image Bytes | `sca_MaxImageBytes` | Decimal number | `3000000` |
| SCA Min Confidence | `sca_MinConfidence` | Decimal number | `0.9`. The flow uses `max(value, 0.9)`, so the threshold can only be raised. |
| SCA Claude Model | `sca_ClaudeModel` | Text | `claude-opus-5-5` |
| SCA Claude Effort | `sca_ClaudeEffort` | Text | `low` |
| SCA Vision System Prompt | `sca_VisionSystemPrompt` | Text | contents of `power-platform/prompts/vision-system-prompt.txt` |
| SCA Claude Output Schema | `sca_ClaudeOutputSchema` | Text | contents of `power-platform/schemas/claude-output.schema.json` |
