# 9. Parse JSON schemas

| Where it's used | File |
|---|---|
| Sent to Claude as `output_config.format.schema` (stored in env var `sca_ClaudeOutputSchema`) | `power-platform/schemas/claude-output.schema.json` |
| Power Automate **Parse_Claude_JSON** action → *Schema* box | `power-platform/schemas/parse-json-claude.schema.json` |
| HTTP trigger → *Request Body JSON Schema* | `power-platform/schemas/android-request.schema.json` |
| Contract returned to Android (and used by `ResponseValidator`) | `power-platform/schemas/android-response.schema.json` |

## Why there are two Claude schemas

Structured outputs don't support `minimum`, `maximum`, `minLength`, `maxLength` or `maxItems`,
and the SDKs strip them. The schema sent to Claude therefore contains only types, `enum`,
`required` and `additionalProperties: false`. It uses 7 nullable unions, which is under the
documented limit of 16.

The **Parse JSON** schema adds the numeric and length bounds and also accepts lower-case enum
values, because Anthropic doesn't guarantee enum capitalization. `Compose_Model` then
upper-cases the action.

Parse JSON **fails** when a type is wrong, a required field is missing, there's an extra
property, or a value is out of range. The flow catches that failure (`Set_Response_Schema_Invalid`
runs after `Parse_Claude_JSON` has **failed**) and returns
`ERROR / MODEL_OUTPUT_SCHEMA_INVALID`. Markdown fences are caught one step earlier by
`Compose_Envelope_Error`, which returns `MODEL_OUTPUT_NOT_JSON`.

## Parse_Claude_JSON schema (paste as is)

```json
{
  "type": "object",
  "properties": {
    "action": { "type": "string", "enum": ["CLOSE", "WAIT", "NO_ACTION", "UNSAFE", "close", "wait", "no_action", "unsafe"] },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
    "label": { "type": ["string", "null"], "maxLength": 200 },
    "center_x": { "type": ["integer", "null"], "minimum": 0 },
    "center_y": { "type": ["integer", "null"], "minimum": 0 },
    "left": { "type": ["integer", "null"], "minimum": 0 },
    "top": { "type": ["integer", "null"], "minimum": 0 },
    "right": { "type": ["integer", "null"], "minimum": 0 },
    "bottom": { "type": ["integer", "null"], "minimum": 0 },
    "wait_seconds": { "type": "integer", "minimum": 0, "maximum": 60 },
    "reason": { "type": "string", "minLength": 1, "maxLength": 1000 },
    "observations": { "type": "array", "maxItems": 10, "items": { "type": "string", "maxLength": 300 } }
  },
  "required": ["action", "confidence", "label", "center_x", "center_y", "left", "top", "right", "bottom", "wait_seconds", "reason", "observations"],
  "additionalProperties": false
}
```
