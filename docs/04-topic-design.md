# 4. Topic design: Analyze Android Screenshot

Source: `power-platform/copilot-studio/topic-analyze-android-screenshot.yaml`

| Aspect | Design |
|---|---|
| Trigger | **It's redirected to** (`OnRedirect`). There are no trigger phrases, so user text cannot start the topic. |
| Inputs | 9 topic input variables (see below). *Should prompt user* is off for each one, and *Fill using* is **Values from other topics**. |
| Guard | A `ConditionGroup` returns `ERROR/MISSING_INPUT` if any input is blank. It never asks the user and never uses the model. |
| Processing | An **Action** node runs the agent flow **SCA Agent Analyze**, which calls **SCA Analyze Core**. |
| Output | Topic output `responseJson` (String). The same string is also sent as one message activity for channel callers. |
| Generative features | None. Classic orchestration, no knowledge, no generative answers. |

## Topic variables

| Variable | Type | Source |
|---|---|---|
| `Topic.requestId` | String | Input |
| `Topic.sessionId` | String | Input (added for duplicate detection per session, see ADR-002) |
| `Topic.screenshotBase64` | String | Input (the Copilot Studio `File` type isn't used; see note) |
| `Topic.mimeType` | String | Input |
| `Topic.screenshotWidth` | Number | Input |
| `Topic.screenshotHeight` | Number | Input |
| `Topic.screenshotHash` | String | Input |
| `Topic.foregroundPackage` | String | Input |
| `Topic.requestedAction` | String | Input |
| `Topic.responseJson` | String | Output, set by the flow |

**Why String rather than File for the screenshot.** The agent-flow trigger's documented input
types are text, number and boolean. A base64 string moves through topic → agent flow → child
flow → connector without any conversion step.

## MANUAL PORTAL STEPS

1. Build the flows first (doc 08), then in Copilot Studio open **Screen Close Assistant > Topics >
   + Add a topic > From blank**.
2. Click **More (…) > Open code editor**, paste the YAML, and replace `<SCA_AGENT_ANALYZE_FLOW_ID>`.
   Alternatively, build it in the canvas as described next.
3. In the canvas:
   - Set the trigger to **It's redirected to**.
   - Under **Details > Input**, create the 9 inputs, and under **Output** create `responseJson`.
   - Add a **Condition** node with the formula from the YAML.
   - Add **Add a tool > SCA Agent Analyze** and map each input to its topic variable.
   - Add **Send a message** with `{Topic.responseJson}`, then **End current topic**.
4. **Save**, then **Publish**.

## Limitation (Agents Client SDK)

The Android Agents Client SDK is in private preview and supports text and Adaptive Cards only.
It can't deliver the image into this topic, so the Android app uses the HTTP-trigger entry point
(ADR-001). The topic is ready for channels that support redirecting with inputs, and for when
the SDK adds attachments.
