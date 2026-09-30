# 6. Anthropic custom connector (OpenAPI)

Files:

- `power-platform/connector/apiDefinition.swagger.json`: OpenAPI 2.0. Power Platform custom
  connectors only accept OpenAPI 2.0.
- `power-platform/connector/apiProperties.json`: declares the `api_key` connection parameter as
  a `securestring`.

| Setting | Value |
|---|---|
| Host / path | `https://api.anthropic.com` `POST /v1/messages` |
| Operation ID | `AnalyzeScreenshot` |
| Auth | API key, header `x-api-key`. The value is stored in the **connection**, not in the connector definition. |
| `anthropic-version` | `2023-06-01`, internal header parameter with a fixed default |
| `content-type` | `application/json`, internal header parameter |
| Model | `claude-opus-5-5` (from the Anthropic models overview; no dated snapshot is listed for Opus 5.5) |
| Structured output | `output_config.format = {type: json_schema, schema: …}`. This is GA, so no beta header is needed. |
| Effort | `output_config.effort = low`, which keeps latency well under the 100 s agent-flow limit |

Opus 5.5 request notes, taken from Anthropic's *What's new in Claude Opus 5.5*:

- `thinking` is omitted. Opus 5.5 can't disable thinking, so `thinking: {type: "disabled"}`
  returns HTTP 400.
- The response can begin with `thinking` blocks, so the flow picks the text block by `type`,
  not by position.
- Prefill isn't used, because it's incompatible with JSON structured outputs.

## MANUAL PORTAL STEPS: option A (portal)

1. Go to make.powerapps.com, then **Solutions > ScreenCloseAssistant > New > Automation > Custom connector**.
2. Choose **Import an OpenAPI file** and select `apiDefinition.swagger.json`. Name it
   `SCA Anthropic Claude Vision`.
3. **Security** tab: confirm the authentication type is **API Key**, parameter label
   `Anthropic API key`, parameter name `x-api-key`, location **Header**.
4. **Create connector**. **Don't test it with a real screenshot from the portal.** The Test tab
   stores the request in the browser session.

## Option B (CLI)

```bash
pip3 install paconn
paconn login
paconn create --api-def power-platform/connector/apiDefinition.swagger.json \
              --api-prop power-platform/connector/apiProperties.json \
              --environment <ENVIRONMENT_ID>
```

Then open the connector and use **Add to solution** to add it to **ScreenCloseAssistant**.
