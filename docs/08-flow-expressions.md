# 8. Power Automate / agent-flow expressions

This page is generated from `power-platform/flows/*.definition.json`, which is the source of truth.
Build each flow in the designer by adding the actions in the order listed. Use the **exact action names**, because expressions refer to them. Type each `@…` value into the expression editor (**fx**), leaving out the leading `@`.
`@{…}` is string interpolation. In a text field, type it as a dynamic expression.

## MANUAL PORTAL STEPS (all flows)

1. make.powerapps.com > **Solutions > ScreenCloseAssistant**. Create all three flows **inside the solution**, so they are solution-aware and use connection references.
2. Create **SCA Analyze Core** first: **New > Automation > Cloud flow > Instant**, trigger *Manually trigger a flow*, with the inputs listed in doc 05.
3. Create **SCA Android HTTP Entry**: **New > Automation > Cloud flow > Automated/Instant**, trigger *When an HTTP request is received*. Set **Who can trigger the flow** to **Specific users in my tenant**.
4. In Copilot Studio open **Screen Close Assistant > Flows > + New agent flow**, trigger *When an agent calls the flow*, and add *Run a Child Flow* and *Respond to the agent*. After saving, go to **Settings > Solution: ScreenCloseAssistant**.
5. In the core flow, open **Details > Edit > Run only users** and set the child-flow connections to **Use this connection (sca_…)**, not *Provided by run-only user*. This is required for *Run a Child Flow*.
6. Turn on **Secure inputs/outputs** wherever the definition shows `secureData`.


## Flow: SCA Analyze Core

Source: `power-platform/flows/SCA-Analyze-Core.definition.json`

### Trigger

```json
{
  "type": "Request",
  "kind": "Button",
  "inputs": {
    "schema": {
      "type": "object",
      "properties": {
        "text": {
          "title": "requestId",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_1": {
          "title": "sessionId",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_2": {
          "title": "screenshotBase64",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_3": {
          "title": "mimeType",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "number": {
          "title": "screenshotWidth",
          "type": "number",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "NUMBER"
        },
        "number_1": {
          "title": "screenshotHeight",
          "type": "number",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "NUMBER"
        },
        "text_4": {
          "title": "screenshotHash",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_5": {
          "title": "foregroundPackage",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_6": {
          "title": "requestedAction",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_7": {
          "title": "channel",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        }
      },
      "required": [
        "text",
        "text_1",
        "text_2",
        "text_3",
        "number",
        "number_1",
        "text_4",
        "text_5",
        "text_6",
        "text_7"
      ]
    }
  },
  "runtimeConfiguration": {
    "secureData": {
      "properties": [
        "outputs"
      ]
    }
  }
}
```

### Initialize_varResponse

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: (first in its container)
- Inputs:

```json
{
  "variables": [
    {
      "name": "varResponse",
      "type": "object",
      "value": {}
    }
  ]
}
```

### Initialize_varHttpStatus

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varResponse is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varHttpStatus",
      "type": "integer",
      "value": 200
    }
  ]
}
```

### Initialize_varStartTicks

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varHttpStatus is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varStartTicks",
      "type": "integer",
      "value": "@ticks(utcNow())"
    }
  ]
}
```

### Initialize_varRowId

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varStartTicks is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varRowId",
      "type": "string",
      "value": ""
    }
  ]
}
```

### Initialize_varModelLatencyMs

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varRowId is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varModelLatencyMs",
      "type": "integer",
      "value": 0
    }
  ]
}
```

### Initialize_varInputTokens

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varModelLatencyMs is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varInputTokens",
      "type": "integer",
      "value": 0
    }
  ]
}
```

### Initialize_varOutputTokens

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varInputTokens is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varOutputTokens",
      "type": "integer",
      "value": 0
    }
  ]
}
```

### Initialize_varStopReason

- Type: `InitializeVariable`
- Container: SCA Analyze Core
- Run after: Initialize_varOutputTokens is Succeeded
- Inputs:

```json
{
  "variables": [
    {
      "name": "varStopReason",
      "type": "string",
      "value": ""
    }
  ]
}
```

### Compose_Inputs

- Type: `Compose`
- Container: SCA Analyze Core
- Run after: Initialize_varStopReason is Succeeded
- Inputs:

```json
{
  "requestId": "@{trim(coalesce(triggerBody()?['text'], ''))}",
  "sessionId": "@{trim(coalesce(triggerBody()?['text_1'], ''))}",
  "mimeType": "@{toLower(trim(coalesce(triggerBody()?['text_3'], '')))}",
  "screenshotWidth": "@{coalesce(triggerBody()?['number'], '')}",
  "screenshotHeight": "@{coalesce(triggerBody()?['number_1'], '')}",
  "screenshotHash": "@{toLower(trim(coalesce(triggerBody()?['text_4'], '')))}",
  "foregroundPackage": "@{trim(coalesce(triggerBody()?['text_5'], ''))}",
  "requestedAction": "@{trim(coalesce(triggerBody()?['text_6'], ''))}",
  "channel": "@{coalesce(triggerBody()?['text_7'], 'UNKNOWN')}"
}
```

### Compose_Image

- Type: `Compose`
- Container: SCA Analyze Core
- Run after: Compose_Inputs is Succeeded
- Settings > **Secure inputs/outputs: On**
- Inputs:

```json
"@{trim(coalesce(triggerBody()?['text_2'], ''))}"
```

### Compose_Config

- Type: `Compose`
- Container: SCA Analyze Core
- Run after: Compose_Image is Succeeded
- Inputs:

```json
{
  "allowlist": "@json(parameters('SCA Package Allowlist (sca_PackageAllowlist)'))",
  "maxImageBytes": "@min(parameters('SCA Max Image Bytes (sca_MaxImageBytes)'), 7500000)",
  "minConfidence": "@max(parameters('SCA Min Confidence (sca_MinConfidence)'), 0.9)",
  "model": "@{parameters('SCA Claude Model (sca_ClaudeModel)')}",
  "effort": "@{parameters('SCA Claude Effort (sca_ClaudeEffort)')}",
  "maxModelEdge": 2576
}
```

### Scope_Try

- Type: `Scope`
- Container: SCA Analyze Core
- Run after: Compose_Config is Succeeded

#### Compose_Input_Error

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try
- Run after: (first in its container)
- Inputs:

```json
"@if(empty(outputs('Compose_Inputs')?['requestId']), 'MISSING_REQUEST_ID', if(greater(length(outputs('Compose_Inputs')?['requestId']), 64), 'REQUEST_ID_TOO_LONG', if(empty(outputs('Compose_Inputs')?['sessionId']), 'MISSING_SESSION_ID', if(greater(length(outputs('Compose_Inputs')?['sessionId']), 64), 'SESSION_ID_TOO_LONG', if(not(equals(outputs('Compose_Inputs')?['requestedAction'], 'FIND_AD_CLOSE')), 'UNSUPPORTED_ACTION', if(not(contains(createArray('image/jpeg', 'image/png', 'image/webp'), outputs('Compose_Inputs')?['mimeType'])), 'UNSUPPORTED_MIME_TYPE', if(empty(outputs('Compose_Image')), 'MISSING_IMAGE', if(greater(div(mul(length(outputs('Compose_Image')), 3), 4), outputs('Compose_Config')?['maxImageBytes']), 'IMAGE_TOO_LARGE', if(not(startsWith(outputs('Compose_Image'), if(equals(outputs('Compose_Inputs')?['mimeType'], 'image/jpeg'), '/9j/', if(equals(outputs('Compose_Inputs')?['mimeType'], 'image/png'), 'iVBORw0KGgo', 'UklGR')))), 'IMAGE_MIME_MISMATCH', if(or(not(isInt(outputs('Compose_Inputs')?['screenshotWidth'])), not(isInt(outputs('Compose_Inputs')?['screenshotHeight']))), 'INVALID_DIMENSIONS', if(or(lessOrEquals(int(outputs('Compose_Inputs')?['screenshotWidth']), 0), lessOrEquals(int(outputs('Compose_Inputs')?['screenshotHeight']), 0)), 'INVALID_DIMENSIONS', if(or(greater(int(outputs('Compose_Inputs')?['screenshotWidth']), outputs('Compose_Config')?['maxModelEdge']), greater(int(outputs('Compose_Inputs')?['screenshotHeight']), outputs('Compose_Config')?['maxModelEdge'])), 'DIMENSIONS_TOO_LARGE', if(not(equals(length(outputs('Compose_Inputs')?['screenshotHash']), 16)), 'INVALID_SCREENSHOT_HASH', if(not(contains(outputs('Compose_Config')?['allowlist'], outputs('Compose_Inputs')?['foregroundPackage'])), 'PACKAGE_NOT_ALLOWED', 'OK'))))))))))))))"
```

#### Condition_Input_Valid

- Type: `If`
- Container: SCA Analyze Core > Scope_Try
- Run after: Compose_Input_Error is Succeeded
- Condition expression:

```json
{
  "and": [
    {
      "equals": [
        "@outputs('Compose_Input_Error')",
        "OK"
      ]
    }
  ]
}
```

##### List_Existing_Request

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes)
- Run after: (first in its container)
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_commondataserviceforapps",
    "operationId": "ListRecords",
    "apiId": "/providers/Microsoft.PowerApps/apis/shared_commondataserviceforapps"
  },
  "parameters": {
    "entityName": "sca_analysisrequests",
    "$select": "sca_analysisrequestid,sca_status,sca_responsejson",
    "$filter": "sca_requestid eq '@{replace(outputs('Compose_Inputs')?['requestId'], '''', '''''')}'",
    "$top": 1
  },
  "authentication": "@parameters('$authentication')"
}
```

##### Condition_Request_Seen

- Type: `If`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes)
- Run after: List_Existing_Request is Succeeded
- Condition expression:

```json
{
  "and": [
    {
      "greater": [
        "@length(outputs('List_Existing_Request')?['body/value'])",
        0
      ]
    }
  ]
}
```

###### Condition_Previous_Completed

- Type: `If`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If yes)
- Run after: (first in its container)
- Condition expression:

```json
{
  "and": [
    {
      "equals": [
        "@first(outputs('List_Existing_Request')?['body/value'])?['sca_status']",
        "COMPLETED"
      ]
    },
    {
      "not": {
        "equals": [
          "@coalesce(first(outputs('List_Existing_Request')?['body/value'])?['sca_responsejson'], '')",
          ""
        ]
      }
    }
  ]
}
```

###### Set_Response_Replay

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If yes) > Condition_Previous_Completed (If yes)
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varResponse",
  "value": "@json(first(outputs('List_Existing_Request')?['body/value'])?['sca_responsejson'])"
}
```

###### Set_Response_In_Progress

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If yes) > Condition_Previous_Completed (If no)
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "A request with this requestId is already being processed.",
    "warnings": [
      "REQUEST_IN_PROGRESS"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

###### Set_Status_409

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If yes) > Condition_Previous_Completed (If no)
- Run after: Set_Response_In_Progress is Succeeded
- Inputs:

```json
{
  "name": "varHttpStatus",
  "value": 409
}
```

###### Create_Request_Row

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: (first in its container)
- Settings > Retry policy: `{"type": "none"}`
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_commondataserviceforapps",
    "operationId": "CreateRecord",
    "apiId": "/providers/Microsoft.PowerApps/apis/shared_commondataserviceforapps"
  },
  "parameters": {
    "entityName": "sca_analysisrequests",
    "item/sca_name": "@outputs('Compose_Inputs')?['requestId']",
    "item/sca_requestid": "@outputs('Compose_Inputs')?['requestId']",
    "item/sca_sessionid": "@outputs('Compose_Inputs')?['sessionId']",
    "item/sca_screenshothash": "@outputs('Compose_Inputs')?['screenshotHash']",
    "item/sca_foregroundpackage": "@outputs('Compose_Inputs')?['foregroundPackage']",
    "item/sca_channel": "@outputs('Compose_Inputs')?['channel']",
    "item/sca_status": "PROCESSING",
    "item/sca_flowrunid": "@workflow()?['run']?['name']",
    "item/sca_modelid": "@outputs('Compose_Config')?['model']"
  },
  "authentication": "@parameters('$authentication')"
}
```

###### Scope_Create_Conflict

- Type: `Scope`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Create_Request_Row is Failed

###### List_Request_After_Conflict

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Create_Conflict
- Run after: (first in its container)
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_commondataserviceforapps",
    "operationId": "ListRecords",
    "apiId": "/providers/Microsoft.PowerApps/apis/shared_commondataserviceforapps"
  },
  "parameters": {
    "entityName": "sca_analysisrequests",
    "$select": "sca_analysisrequestid",
    "$filter": "sca_requestid eq '@{replace(outputs('Compose_Inputs')?['requestId'], '''', '''''')}'",
    "$top": 1
  },
  "authentication": "@parameters('$authentication')"
}
```

###### Set_Response_Conflict

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Create_Conflict
- Run after: List_Request_After_Conflict is Succeeded
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "@{if(greater(length(outputs('List_Request_After_Conflict')?['body/value']), 0), 'A request with this requestId is already being processed.', 'This screenshot hash was already analysed in this session.')}",
    "warnings": [
      "@{if(greater(length(outputs('List_Request_After_Conflict')?['body/value']), 0), 'REQUEST_IN_PROGRESS', 'DUPLICATE_SCREENSHOT')}"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

###### Set_Status_409_Conflict

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Create_Conflict
- Run after: Set_Response_Conflict is Succeeded
- Inputs:

```json
{
  "name": "varHttpStatus",
  "value": 409
}
```

###### Set_varRowId

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Create_Request_Row is Succeeded
- Inputs:

```json
{
  "name": "varRowId",
  "value": "@{outputs('Create_Request_Row')?['body/sca_analysisrequestid']}"
}
```

###### Compose_Claude_Request

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Set_varRowId is Succeeded
- Settings > **Secure inputs/outputs: On**
- Inputs:

```json
{
  "model": "@{outputs('Compose_Config')?['model']}",
  "max_tokens": 8000,
  "system": "@{parameters('SCA Vision System Prompt (sca_VisionSystemPrompt)')}",
  "metadata": {
    "user_id": "@{outputs('Compose_Inputs')?['sessionId']}"
  },
  "output_config": {
    "effort": "@{outputs('Compose_Config')?['effort']}",
    "format": {
      "type": "json_schema",
      "schema": "@json(parameters('SCA Claude Output Schema (sca_ClaudeOutputSchema)'))"
    }
  },
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "image",
          "source": {
            "type": "base64",
            "media_type": "@{outputs('Compose_Inputs')?['mimeType']}",
            "data": "@{outputs('Compose_Image')}"
          }
        },
        {
          "type": "text",
          "text": "Screenshot dimensions: @{int(outputs('Compose_Inputs')?['screenshotWidth'])}x@{int(outputs('Compose_Inputs')?['screenshotHeight'])} pixels (width x height). requestedAction: FIND_AD_CLOSE. Return only the JSON object."
        }
      ]
    }
  ]
}
```

###### Set_varModelStart

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Compose_Claude_Request is Succeeded
- Inputs:

```json
{
  "name": "varModelLatencyMs",
  "value": "@div(sub(ticks(utcNow()), variables('varStartTicks')), 10000)"
}
```

###### Analyze_Screenshot

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Set_varModelStart is Succeeded
- Settings > Timeout: `PT60S`
- Settings > Retry policy: `{"type": "exponential", "count": 2, "interval": "PT2S", "minimumInterval": "PT2S", "maximumInterval": "PT8S"}`
- Settings > **Secure inputs/outputs: On**
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_scaanthropic",
    "operationId": "AnalyzeScreenshot",
    "apiId": "/providers/Microsoft.PowerApps/apis/<ANTHROPIC_CONNECTOR_API_NAME>"
  },
  "parameters": {
    "anthropic-version": "2023-06-01",
    "content-type": "application/json",
    "body": "@outputs('Compose_Claude_Request')"
  },
  "authentication": "@parameters('$authentication')"
}
```

###### Set_Response_Model_Call_Failed

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Analyze_Screenshot is Failed/TimedOut
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "The vision model call failed or timed out.",
    "warnings": [
      "@{concat('MODEL_CALL_FAILED_HTTP_', coalesce(string(outputs('Analyze_Screenshot')?['statusCode']), 'TIMEOUT'))}"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

###### Scope_Interpret

- Type: `Scope`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Analyze_Screenshot is Succeeded

###### Set_varModelLatency

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varModelLatencyMs",
  "value": "@sub(div(sub(ticks(utcNow()), variables('varStartTicks')), 10000), variables('varModelLatencyMs'))"
}
```

###### Set_varInputTokens

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Set_varModelLatency is Succeeded
- Inputs:

```json
{
  "name": "varInputTokens",
  "value": "@int(coalesce(outputs('Analyze_Screenshot')?['body/usage/input_tokens'], 0))"
}
```

###### Set_varOutputTokens

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Set_varInputTokens is Succeeded
- Inputs:

```json
{
  "name": "varOutputTokens",
  "value": "@int(coalesce(outputs('Analyze_Screenshot')?['body/usage/output_tokens'], 0))"
}
```

###### Set_varStopReason

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Set_varOutputTokens is Succeeded
- Inputs:

```json
{
  "name": "varStopReason",
  "value": "@{coalesce(outputs('Analyze_Screenshot')?['body/stop_reason'], 'unknown')}"
}
```

###### Filter_Text_Blocks

- Type: `Query`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Set_varStopReason is Succeeded
- Inputs:

```json
{
  "from": "@coalesce(outputs('Analyze_Screenshot')?['body/content'], createArray())",
  "where": "@equals(item()?['type'], 'text')"
}
```

###### Compose_Raw_Text

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Filter_Text_Blocks is Succeeded
- Inputs:

```json
"@trim(if(equals(length(body('Filter_Text_Blocks')), 1), coalesce(first(body('Filter_Text_Blocks'))?['text'], ''), ''))"
```

###### Compose_Envelope_Error

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Compose_Raw_Text is Succeeded
- Inputs:

```json
"@if(not(equals(variables('varStopReason'), 'end_turn')), concat('MODEL_STOP_', toUpper(variables('varStopReason'))), if(not(equals(length(body('Filter_Text_Blocks')), 1)), 'MODEL_TEXT_BLOCK_COUNT', if(empty(outputs('Compose_Raw_Text')), 'MODEL_EMPTY_OUTPUT', if(or(not(startsWith(outputs('Compose_Raw_Text'), '{')), not(endsWith(outputs('Compose_Raw_Text'), '}'))), 'MODEL_OUTPUT_NOT_JSON', 'OK'))))"
```

###### Condition_Envelope_OK

- Type: `If`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret
- Run after: Compose_Envelope_Error is Succeeded
- Condition expression:

```json
{
  "and": [
    {
      "equals": [
        "@outputs('Compose_Envelope_Error')",
        "OK"
      ]
    }
  ]
}
```

###### Parse_Claude_JSON

- Type: `ParseJson`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: (first in its container)
- Inputs:

```json
{
  "content": "@outputs('Compose_Raw_Text')",
  "schema": {
    "type": "object",
    "properties": {
      "action": {
        "type": "string",
        "enum": [
          "CLOSE",
          "WAIT",
          "NO_ACTION",
          "UNSAFE",
          "close",
          "wait",
          "no_action",
          "unsafe"
        ]
      },
      "confidence": {
        "type": "number",
        "minimum": 0,
        "maximum": 1
      },
      "label": {
        "type": [
          "string",
          "null"
        ],
        "maxLength": 200
      },
      "center_x": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "center_y": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "left": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "top": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "right": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "bottom": {
        "type": [
          "integer",
          "null"
        ],
        "minimum": 0
      },
      "wait_seconds": {
        "type": "integer",
        "minimum": 0,
        "maximum": 60
      },
      "reason": {
        "type": "string",
        "minLength": 1,
        "maxLength": 1000
      },
      "observations": {
        "type": "array",
        "maxItems": 10,
        "items": {
          "type": "string",
          "maxLength": 300
        }
      }
    },
    "required": [
      "action",
      "confidence",
      "label",
      "center_x",
      "center_y",
      "left",
      "top",
      "right",
      "bottom",
      "wait_seconds",
      "reason",
      "observations"
    ],
    "additionalProperties": false
  }
}
```

###### Set_Response_Schema_Invalid

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Parse_Claude_JSON is Failed
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "The vision model output failed schema validation.",
    "warnings": [
      "MODEL_OUTPUT_SCHEMA_INVALID"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

###### Compose_Model

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Parse_Claude_JSON is Succeeded
- Inputs:

```json
{
  "action": "@{toUpper(body('Parse_Claude_JSON')?['action'])}",
  "confidence": "@float(string(body('Parse_Claude_JSON')?['confidence']))",
  "label": "@{trim(coalesce(body('Parse_Claude_JSON')?['label'], ''))}",
  "cx": "@coalesce(body('Parse_Claude_JSON')?['center_x'], -1)",
  "cy": "@coalesce(body('Parse_Claude_JSON')?['center_y'], -1)",
  "left": "@coalesce(body('Parse_Claude_JSON')?['left'], -1)",
  "top": "@coalesce(body('Parse_Claude_JSON')?['top'], -1)",
  "right": "@coalesce(body('Parse_Claude_JSON')?['right'], -1)",
  "bottom": "@coalesce(body('Parse_Claude_JSON')?['bottom'], -1)",
  "hasFullTarget": "@and(not(equals(body('Parse_Claude_JSON')?['center_x'], null)), not(equals(body('Parse_Claude_JSON')?['center_y'], null)), not(equals(body('Parse_Claude_JSON')?['left'], null)), not(equals(body('Parse_Claude_JSON')?['top'], null)), not(equals(body('Parse_Claude_JSON')?['right'], null)), not(equals(body('Parse_Claude_JSON')?['bottom'], null)), not(equals(body('Parse_Claude_JSON')?['label'], null)))",
  "hasAnyTarget": "@or(not(equals(body('Parse_Claude_JSON')?['center_x'], null)), not(equals(body('Parse_Claude_JSON')?['center_y'], null)), not(equals(body('Parse_Claude_JSON')?['left'], null)), not(equals(body('Parse_Claude_JSON')?['top'], null)), not(equals(body('Parse_Claude_JSON')?['right'], null)), not(equals(body('Parse_Claude_JSON')?['bottom'], null)), not(equals(body('Parse_Claude_JSON')?['label'], null)))",
  "waitSeconds": "@body('Parse_Claude_JSON')?['wait_seconds']",
  "reason": "@{substring(trim(body('Parse_Claude_JSON')?['reason']), 0, min(300, length(trim(body('Parse_Claude_JSON')?['reason']))))}",
  "w": "@int(outputs('Compose_Inputs')?['screenshotWidth'])",
  "h": "@int(outputs('Compose_Inputs')?['screenshotHeight'])"
}
```

###### Compose_Checks

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Model is Succeeded
- Inputs:

```json
{
  "reasonValid": "@not(empty(outputs('Compose_Model')?['reason']))",
  "geometryValid": "@and(outputs('Compose_Model')?['hasFullTarget'], not(empty(outputs('Compose_Model')?['label'])), greaterOrEquals(outputs('Compose_Model')?['left'], 0), greaterOrEquals(outputs('Compose_Model')?['top'], 0), less(outputs('Compose_Model')?['left'], outputs('Compose_Model')?['right']), less(outputs('Compose_Model')?['top'], outputs('Compose_Model')?['bottom']), lessOrEquals(outputs('Compose_Model')?['right'], outputs('Compose_Model')?['w']), lessOrEquals(outputs('Compose_Model')?['bottom'], outputs('Compose_Model')?['h']), greaterOrEquals(outputs('Compose_Model')?['cx'], outputs('Compose_Model')?['left']), lessOrEquals(outputs('Compose_Model')?['cx'], outputs('Compose_Model')?['right']), greaterOrEquals(outputs('Compose_Model')?['cy'], outputs('Compose_Model')?['top']), lessOrEquals(outputs('Compose_Model')?['cy'], outputs('Compose_Model')?['bottom']), less(outputs('Compose_Model')?['cx'], outputs('Compose_Model')?['w']), less(outputs('Compose_Model')?['cy'], outputs('Compose_Model')?['h']))",
  "confidenceOk": "@greaterOrEquals(outputs('Compose_Model')?['confidence'], outputs('Compose_Config')?['minConfidence'])",
  "waitValid": "@and(greaterOrEquals(outputs('Compose_Model')?['waitSeconds'], 1), lessOrEquals(outputs('Compose_Model')?['waitSeconds'], 60))",
  "knownAction": "@contains(createArray('CLOSE', 'WAIT', 'NO_ACTION', 'UNSAFE'), outputs('Compose_Model')?['action'])"
}
```

###### Compose_Resp_Close

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Checks is Succeeded
- Inputs:

```json
{
  "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
  "action": "CLOSE",
  "confidence": "@outputs('Compose_Model')?['confidence']",
  "target": {
    "label": "@{substring(outputs('Compose_Model')?['label'], 0, min(100, length(outputs('Compose_Model')?['label'])))}",
    "centerX": "@outputs('Compose_Model')?['cx']",
    "centerY": "@outputs('Compose_Model')?['cy']",
    "normalizedX": "@div(float(outputs('Compose_Model')?['cx']), float(outputs('Compose_Model')?['w']))",
    "normalizedY": "@div(float(outputs('Compose_Model')?['cy']), float(outputs('Compose_Model')?['h']))",
    "left": "@outputs('Compose_Model')?['left']",
    "top": "@outputs('Compose_Model')?['top']",
    "right": "@outputs('Compose_Model')?['right']",
    "bottom": "@outputs('Compose_Model')?['bottom']"
  },
  "waitSeconds": 0,
  "reason": "@{outputs('Compose_Model')?['reason']}",
  "warnings": [],
  "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
}
```

###### Compose_Resp_Wait

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Resp_Close is Succeeded
- Inputs:

```json
{
  "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
  "action": "WAIT",
  "confidence": "@outputs('Compose_Model')?['confidence']",
  "target": null,
  "waitSeconds": "@outputs('Compose_Model')?['waitSeconds']",
  "reason": "@{outputs('Compose_Model')?['reason']}",
  "warnings": "@if(outputs('Compose_Model')?['hasAnyTarget'], createArray('TARGET_DISCARDED_FOR_NON_CLOSE'), createArray())",
  "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
}
```

###### Compose_Resp_Passthrough

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Resp_Wait is Succeeded
- Inputs:

```json
{
  "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
  "action": "@{outputs('Compose_Model')?['action']}",
  "confidence": "@outputs('Compose_Model')?['confidence']",
  "target": null,
  "waitSeconds": 0,
  "reason": "@{outputs('Compose_Model')?['reason']}",
  "warnings": "@if(outputs('Compose_Model')?['hasAnyTarget'], createArray('TARGET_DISCARDED_FOR_NON_CLOSE'), createArray())",
  "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
}
```

###### Compose_Resp_Low_Confidence

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Resp_Passthrough is Succeeded
- Inputs:

```json
{
  "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
  "action": "NO_ACTION",
  "confidence": "@outputs('Compose_Model')?['confidence']",
  "target": null,
  "waitSeconds": 0,
  "reason": "@{concat('Close control confidence below threshold. ', outputs('Compose_Model')?['reason'])}",
  "warnings": [
    "LOW_CONFIDENCE_CLOSE_SUPPRESSED"
  ],
  "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
}
```

###### Compose_Validation_Error_Code

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Resp_Low_Confidence is Succeeded
- Inputs:

```json
"@if(not(outputs('Compose_Checks')?['knownAction']), 'MODEL_ACTION_INVALID', if(not(outputs('Compose_Checks')?['reasonValid']), 'MODEL_REASON_MISSING', if(and(equals(outputs('Compose_Model')?['action'], 'CLOSE'), not(outputs('Compose_Checks')?['geometryValid'])), 'TARGET_OUT_OF_BOUNDS', if(and(equals(outputs('Compose_Model')?['action'], 'WAIT'), not(outputs('Compose_Checks')?['waitValid'])), 'WAIT_SECONDS_INVALID', 'OK'))))"
```

###### Compose_Resp_Invalid

- Type: `Compose`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Validation_Error_Code is Succeeded
- Inputs:

```json
{
  "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
  "action": "ERROR",
  "confidence": 0,
  "target": null,
  "waitSeconds": 0,
  "reason": "The vision model output failed semantic validation.",
  "warnings": [
    "@{outputs('Compose_Validation_Error_Code')}"
  ],
  "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
}
```

###### Set_Response_Final

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If yes)
- Run after: Compose_Resp_Invalid is Succeeded
- Inputs:

```json
{
  "name": "varResponse",
  "value": "@if(not(equals(outputs('Compose_Validation_Error_Code'), 'OK')), outputs('Compose_Resp_Invalid'), if(equals(outputs('Compose_Model')?['action'], 'CLOSE'), if(outputs('Compose_Checks')?['confidenceOk'], outputs('Compose_Resp_Close'), outputs('Compose_Resp_Low_Confidence')), if(equals(outputs('Compose_Model')?['action'], 'WAIT'), outputs('Compose_Resp_Wait'), outputs('Compose_Resp_Passthrough'))))"
}
```

###### Set_Response_Envelope_Invalid

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no) > Scope_Interpret > Condition_Envelope_OK (If no)
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "The vision model output was not a single JSON object.",
    "warnings": [
      "@{outputs('Compose_Envelope_Error')}"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

###### Update_Request_Row

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If yes) > Condition_Request_Seen (If no)
- Run after: Scope_Interpret is Succeeded/Failed/Skipped/TimedOut, Set_Response_Model_Call_Failed is Succeeded/Skipped
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_commondataserviceforapps",
    "operationId": "UpdateRecord",
    "apiId": "/providers/Microsoft.PowerApps/apis/shared_commondataserviceforapps"
  },
  "parameters": {
    "entityName": "sca_analysisrequests",
    "recordId": "@variables('varRowId')",
    "item/sca_status": "@{if(empty(variables('varResponse')), 'FAILED', 'COMPLETED')}",
    "item/sca_action": "@{coalesce(variables('varResponse')?['action'], 'ERROR')}",
    "item/sca_confidence": "@coalesce(variables('varResponse')?['confidence'], 0)",
    "item/sca_errorcode": "@{if(equals(variables('varResponse')?['action'], 'ERROR'), first(variables('varResponse')?['warnings']), '')}",
    "item/sca_responsejson": "@{string(variables('varResponse'))}",
    "item/sca_latencyms": "@div(sub(ticks(utcNow()), variables('varStartTicks')), 10000)",
    "item/sca_modellatencyms": "@variables('varModelLatencyMs')",
    "item/sca_inputtokens": "@variables('varInputTokens')",
    "item/sca_outputtokens": "@variables('varOutputTokens')",
    "item/sca_stopreason": "@{variables('varStopReason')}"
  },
  "authentication": "@parameters('$authentication')"
}
```

##### Set_Response_Input_Invalid

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If no)
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "Request rejected by input validation.",
    "warnings": [
      "@{outputs('Compose_Input_Error')}"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

##### Set_Status_400

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Try > Condition_Input_Valid (If no)
- Run after: Set_Response_Input_Invalid is Succeeded
- Inputs:

```json
{
  "name": "varHttpStatus",
  "value": 400
}
```

### Scope_Catch

- Type: `Scope`
- Container: SCA Analyze Core
- Run after: Scope_Try is Failed/TimedOut

#### Condition_No_Response_Yet

- Type: `If`
- Container: SCA Analyze Core > Scope_Catch
- Run after: (first in its container)
- Condition expression:

```json
{
  "and": [
    {
      "equals": [
        "@empty(variables('varResponse'))",
        true
      ]
    }
  ]
}
```

##### Set_Response_Internal_Error

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Catch > Condition_No_Response_Yet (If yes)
- Run after: (first in its container)
- Inputs:

```json
{
  "name": "varResponse",
  "value": {
    "requestId": "@{outputs('Compose_Inputs')?['requestId']}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "@{concat('Internal flow error. Trace id: ', workflow()?['run']?['name'])}",
    "warnings": [
      "INTERNAL_FLOW_ERROR"
    ],
    "screenshotHash": "@{outputs('Compose_Inputs')?['screenshotHash']}"
  }
}
```

##### Set_Status_500

- Type: `SetVariable`
- Container: SCA Analyze Core > Scope_Catch > Condition_No_Response_Yet (If yes)
- Run after: Set_Response_Internal_Error is Succeeded
- Inputs:

```json
{
  "name": "varHttpStatus",
  "value": 500
}
```

#### Condition_Row_Created

- Type: `If`
- Container: SCA Analyze Core > Scope_Catch
- Run after: Condition_No_Response_Yet is Succeeded
- Condition expression:

```json
{
  "and": [
    {
      "not": {
        "equals": [
          "@variables('varRowId')",
          ""
        ]
      }
    }
  ]
}
```

##### Mark_Row_Failed

- Type: `OpenApiConnection`
- Container: SCA Analyze Core > Scope_Catch > Condition_Row_Created (If yes)
- Run after: (first in its container)
- Inputs:

```json
{
  "host": {
    "connectionName": "shared_commondataserviceforapps",
    "operationId": "UpdateRecord",
    "apiId": "/providers/Microsoft.PowerApps/apis/shared_commondataserviceforapps"
  },
  "parameters": {
    "entityName": "sca_analysisrequests",
    "recordId": "@variables('varRowId')",
    "item/sca_status": "FAILED",
    "item/sca_action": "ERROR",
    "item/sca_errorcode": "@{first(variables('varResponse')?['warnings'])}",
    "item/sca_responsejson": "@{string(variables('varResponse'))}",
    "item/sca_latencyms": "@div(sub(ticks(utcNow()), variables('varStartTicks')), 10000)"
  },
  "authentication": "@parameters('$authentication')"
}
```

### Respond_To_Caller

- Type: `Response` / kind `PowerApp`
- Container: SCA Analyze Core
- Run after: Scope_Catch is Succeeded/Skipped/Failed/TimedOut
- Inputs:

```json
{
  "statusCode": 200,
  "body": {
    "responsejson": "@{string(variables('varResponse'))}",
    "httpstatus": "@variables('varHttpStatus')"
  },
  "schema": {
    "type": "object",
    "properties": {
      "responsejson": {
        "title": "responseJson",
        "type": "string",
        "x-ms-dynamically-added": true
      },
      "httpstatus": {
        "title": "httpStatus",
        "type": "number",
        "x-ms-dynamically-added": true
      }
    }
  }
}
```


## Flow: SCA Android HTTP Entry

Source: `power-platform/flows/SCA-Android-HTTP-Entry.definition.json`

### Trigger

```json
{
  "type": "Request",
  "kind": "Http",
  "inputs": {
    "method": "POST",
    "schema": {
      "type": "object",
      "properties": {
        "requestId": {
          "type": "string"
        },
        "sessionId": {
          "type": "string"
        },
        "screenshotBase64": {
          "type": "string"
        },
        "mimeType": {
          "type": "string"
        },
        "screenshotWidth": {
          "type": "integer"
        },
        "screenshotHeight": {
          "type": "integer"
        },
        "screenshotHash": {
          "type": "string"
        },
        "foregroundPackage": {
          "type": "string"
        },
        "requestedAction": {
          "type": "string"
        }
      }
    }
  },
  "runtimeConfiguration": {
    "secureData": {
      "properties": [
        "inputs",
        "outputs"
      ]
    }
  }
}
```

### Run_Core

- Type: `Workflow`
- Container: SCA Android HTTP Entry
- Run after: (first in its container)
- Settings > Timeout: `PT100S`
- Settings > **Secure inputs/outputs: On**
- Inputs:

```json
{
  "host": {
    "workflowReferenceName": "<SCA_ANALYZE_CORE_WORKFLOW_ID>"
  },
  "body": {
    "text": "@{coalesce(triggerBody()?['requestId'], '')}",
    "text_1": "@{coalesce(triggerBody()?['sessionId'], '')}",
    "text_2": "@{coalesce(triggerBody()?['screenshotBase64'], '')}",
    "text_3": "@{coalesce(triggerBody()?['mimeType'], '')}",
    "number": "@coalesce(triggerBody()?['screenshotWidth'], 0)",
    "number_1": "@coalesce(triggerBody()?['screenshotHeight'], 0)",
    "text_4": "@{coalesce(triggerBody()?['screenshotHash'], '')}",
    "text_5": "@{coalesce(triggerBody()?['foregroundPackage'], '')}",
    "text_6": "@{coalesce(triggerBody()?['requestedAction'], '')}",
    "text_7": "ANDROID_HTTP"
  }
}
```

### Response_Success

- Type: `Response` / kind `Http`
- Container: SCA Android HTTP Entry
- Run after: Run_Core is Succeeded
- Inputs:

```json
{
  "statusCode": "@int(body('Run_Core')?['httpstatus'])",
  "headers": {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "x-sca-request-id": "@{coalesce(triggerBody()?['requestId'], '')}",
    "x-sca-flow-run-id": "@{workflow()?['run']?['name']}"
  },
  "body": "@json(body('Run_Core')?['responsejson'])"
}
```

### Response_Core_Failed

- Type: `Response` / kind `Http`
- Container: SCA Android HTTP Entry
- Run after: Run_Core is Failed/TimedOut/Skipped
- Inputs:

```json
{
  "statusCode": 502,
  "headers": {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "x-sca-request-id": "@{coalesce(triggerBody()?['requestId'], '')}",
    "x-sca-flow-run-id": "@{workflow()?['run']?['name']}"
  },
  "body": {
    "requestId": "@{coalesce(triggerBody()?['requestId'], '')}",
    "action": "ERROR",
    "confidence": 0,
    "target": null,
    "waitSeconds": 0,
    "reason": "@{concat('Core analysis flow failed. Trace id: ', workflow()?['run']?['name'])}",
    "warnings": [
      "CORE_INVOCATION_FAILED"
    ],
    "screenshotHash": "@{coalesce(triggerBody()?['screenshotHash'], '')}"
  }
}
```


## Flow: SCA Agent Analyze

Source: `power-platform/flows/SCA-Agent-Analyze.definition.json`

### Trigger

```json
{
  "type": "Request",
  "kind": "Skills",
  "inputs": {
    "schema": {
      "type": "object",
      "properties": {
        "text": {
          "title": "requestId",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_1": {
          "title": "sessionId",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_2": {
          "title": "screenshotBase64",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_3": {
          "title": "mimeType",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "number": {
          "title": "screenshotWidth",
          "type": "number",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "NUMBER"
        },
        "number_1": {
          "title": "screenshotHeight",
          "type": "number",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "NUMBER"
        },
        "text_4": {
          "title": "screenshotHash",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_5": {
          "title": "foregroundPackage",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        },
        "text_6": {
          "title": "requestedAction",
          "type": "string",
          "x-ms-dynamically-added": true,
          "x-ms-content-hint": "TEXT"
        }
      },
      "required": [
        "text",
        "text_1",
        "text_2",
        "text_3",
        "number",
        "number_1",
        "text_4",
        "text_5",
        "text_6"
      ]
    }
  },
  "runtimeConfiguration": {
    "secureData": {
      "properties": [
        "outputs"
      ]
    }
  }
}
```

### Run_Core

- Type: `Workflow`
- Container: SCA Agent Analyze
- Run after: (first in its container)
- Settings > Timeout: `PT90S`
- Settings > **Secure inputs/outputs: On**
- Inputs:

```json
{
  "host": {
    "workflowReferenceName": "<SCA_ANALYZE_CORE_WORKFLOW_ID>"
  },
  "body": {
    "text": "@{triggerBody()?['text']}",
    "text_1": "@{triggerBody()?['text_1']}",
    "text_2": "@{triggerBody()?['text_2']}",
    "text_3": "@{triggerBody()?['text_3']}",
    "number": "@triggerBody()?['number']",
    "number_1": "@triggerBody()?['number_1']",
    "text_4": "@{triggerBody()?['text_4']}",
    "text_5": "@{triggerBody()?['text_5']}",
    "text_6": "@{triggerBody()?['text_6']}",
    "text_7": "COPILOT_TOPIC"
  }
}
```

### Respond_to_the_agent

- Type: `Response` / kind `Skills`
- Container: SCA Agent Analyze
- Run after: Run_Core is Succeeded
- Inputs:

```json
{
  "statusCode": 200,
  "body": {
    "responsejson": "@{body('Run_Core')?['responsejson']}"
  },
  "schema": {
    "type": "object",
    "properties": {
      "responsejson": {
        "title": "responseJson",
        "type": "string",
        "x-ms-dynamically-added": true
      }
    }
  }
}
```

### Respond_to_the_agent_on_failure

- Type: `Response` / kind `Skills`
- Container: SCA Agent Analyze
- Run after: Run_Core is Failed/TimedOut/Skipped
- Inputs:

```json
{
  "statusCode": 200,
  "body": {
    "responsejson": "@{string(json(concat('{\"requestId\":\"', replace(replace(coalesce(triggerBody()?['text'], ''), '\"', ''), '\\', ''), '\",\"action\":\"ERROR\",\"confidence\":0,\"target\":null,\"waitSeconds\":0,\"reason\":\"Core analysis flow failed.\",\"warnings\":[\"CORE_INVOCATION_FAILED\"],\"screenshotHash\":\"', replace(replace(coalesce(triggerBody()?['text_4'], ''), '\"', ''), '\\', ''), '\"}')))}"
  },
  "schema": {
    "type": "object",
    "properties": {
      "responsejson": {
        "title": "responseJson",
        "type": "string",
        "x-ms-dynamically-added": true
      }
    }
  }
}
```

