# 7. Secure connection references

The Anthropic API key exists in exactly one place: the **connection** of the SCA custom
connector. Flows reach it only through a **solution-aware connection reference**, so the key
never appears in a flow definition, a solution export, an environment variable, a prompt, a
request body, or the APK.

## MANUAL PORTAL STEPS

1. make.powerapps.com > **Solutions > ScreenCloseAssistant > New > More > Connection reference**.
   - Display name `SCA Anthropic Claude Connection`, schema name `sca_AnthropicClaudeConnection`.
   - Connector: **SCA Anthropic Claude Vision**.
   - Connection: **+ New connection**, then paste the Anthropic API key and click **Create**.
2. Repeat for Dataverse: `SCA Dataverse Connection` / `sca_DataverseConnection` / connector
   **Microsoft Dataverse**.
3. Open each flow and confirm that every action uses these connection references. Flows created
   inside the solution use connection references automatically.
4. **Connection ownership**: create the connections as a dedicated service account (for example
   `sca-svc@<tenant>`) rather than your personal account. Share the connection with **nobody**.
   Flows use it through the reference, and flow co-owners can't read the key.
5. **Hide the key from run history**: in the core flow, open *Analyze_Screenshot > Settings* and
   turn on **Secure inputs** and **Secure outputs**. Do the same for `Compose_Image`,
   `Compose_Claude_Request`, the child-flow trigger and the HTTP trigger. The API key header is
   never shown in run history, and securing the inputs also hides the base64 screenshot.
6. **DLP**: in the Power Platform admin center, create a data policy for the environment. Put
   **SCA Anthropic Claude Vision**, **Microsoft Dataverse**, **HTTP with Microsoft Entra ID**
   (if used) and **Request** in the *Business* group. Block *HTTP* (the generic action) to stop
   anyone calling Anthropic with a raw key.
7. **Rotation**: create a new key in the Anthropic Console, edit the connection, update the key,
   run `tests/smoke_test_flow.sh`, then revoke the old key.

## Deploying to another environment

When the solution is imported, Power Platform asks you to choose or create connections for
`sca_AnthropicClaudeConnection` and `sca_DataverseConnection`. The key is entered there, in the
target environment, and never travels in the solution file.
