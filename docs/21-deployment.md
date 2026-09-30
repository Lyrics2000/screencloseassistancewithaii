# 21. Deployment instructions

Do the steps in order. Anything marked **PORTAL** is a manual step in a Microsoft web portal.
Commands are for macOS (zsh). Replace everything in `<ANGLE_BRACKETS>`.

## Step 0: Tools

```bash
brew install azure-cli jq python
# Android Studio Ladybug+ from https://developer.android.com/studio (it bundles JDK 17)
az version && python3 --version && jq --version
```

## Step 1: Power Platform environment and solution (PORTAL)

1. Go to https://admin.powerplatform.microsoft.com, then **Environments > New**. Choose type
   **Developer** (or Sandbox), turn **Add Dataverse** on, and create it.
2. Copy the **Environment URL** (`https://<org>.crm.dynamics.com`) and the **Environment ID**.

## Step 2: Dataverse migration (creates the publisher, the solution, the table and its keys)

```bash
cd "/Users/lyrics/Desktop/personal projects/projectforads/power-platform/dataverse/migrations"
az login --tenant <TENANT_ID>
export SCA_DATAVERSE_URL="https://<org>.crm.dynamics.com"
python3 001_create_analysis_request.py
python3 001_create_analysis_request.py --status   # both keys must show "Active"
```

**Retention (PORTAL):** make.powerapps.com > **Settings > Advanced settings > Data management >
Bulk record deletion > New**. Choose table *SCA Analysis Requests*, with the condition *Created On*
**Older Than X Days** = 7, recurring daily.

## Step 3: Environment variables (PORTAL)

In **Solutions > ScreenCloseAssistant > New > More > Environment variable**, create the seven
variables listed in doc 05.

- For `sca_VisionSystemPrompt`, paste the contents of
  `power-platform/prompts/vision-system-prompt.txt`.
- For `sca_ClaudeOutputSchema`, paste `power-platform/schemas/claude-output.schema.json`.
- `sca_PackageAllowlist` must be a JSON array, for example `["com.schoolproject.adtestbed"]`.

## Step 4: Anthropic key and custom connector

1. In the Anthropic Console (https://console.anthropic.com), go to **API keys > Create key**.
   Name it `sca-power-platform` and set a workspace spend limit. Keep the key only until step 5.
2. Import the connector (doc 06, option A or B).

## Step 5: Connection references (PORTAL)

Follow doc 07 to create `sca_AnthropicClaudeConnection`, entering the key in the connection, and
`sca_DataverseConnection`. Then clear the key from your clipboard.

## Step 6: Flows (PORTAL)

Follow doc 08:

1. **SCA Analyze Core** (child flow).
2. **SCA Android HTTP Entry**. Its trigger is *When an HTTP request is received*, with **Who can
   trigger the flow = Specific users in my tenant** and your test accounts listed. Save the flow,
   then copy the **HTTP URL**. It must **not** contain `sig=`.
3. **SCA Agent Analyze** (agent flow in Copilot Studio).
4. Turn on **Secure inputs/outputs** wherever doc 08 says so.

## Step 7: Copilot Studio agent (PORTAL)

Follow docs 03 and 04: create the agent, paste the instructions, add the topic, then **Publish**.

## Step 8: Entra app registration for Android (PORTAL, entra.microsoft.com)

1. **App registrations > New registration**. Name it `Screen Close Assistant Android`,
   choose **Accounts in this organizational directory only**, and click **Register**.
   Copy the **Application (client) ID** and the **Directory (tenant) ID**.
2. Get the signature hash of your debug keystore:

   ```bash
   keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android \
     | openssl sha1 -binary | openssl base64
   ```

3. **Authentication > Add a platform > Android**. Package name `com.schoolproject.screenclose`,
   plus the signature hash from the previous step. Then **Configure**.
4. **API permissions > Add a permission > APIs my organization uses**. Search for
   **Power Automate** (it may be listed as *Microsoft Flow Service*), then choose **Delegated >
   User**. Click **Grant admin consent** if user consent is disabled in your tenant.
5. Don't create any client secret or certificate. This is a public client.

## Step 9: Configure and build the Android app

```bash
cd "/Users/lyrics/Desktop/personal projects/projectforads/android"
cp local.properties.example local.properties
# edit local.properties: sdk.dir, sca.entra.clientId, sca.entra.tenantId,
#                        sca.entra.signatureHash, sca.flow.endpointUrl
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --version
./gradlew :app:testDebugUnitTest            # unit + MockWebServer integration tests
./gradlew :app:assembleDebug :testbed:assembleDebug
```

The build refuses to start (`SCA-BUILD-CONFIG`) until these values are valid. That's a
deliberate secret and misconfiguration guard. To run only the unit tests, the placeholder values
in `local.properties.example` pass validation.

Environment variables override `local.properties` in CI: `SCA_ENTRA_CLIENT_ID`,
`SCA_ENTRA_TENANT_ID`, `SCA_ENTRA_SIGNATURE_HASH`, `SCA_FLOW_ENDPOINT_URL`.

## Step 10: Install and run on a device

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r testbed/build/outputs/apk/debug/testbed-debug.apk
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device must be unlocked)
adb logcat -s SCA                          # structured, redacted logs
```

On the phone:

1. Open **Screen Close Assistant** and sign in.
2. Allow notifications.
3. Allow **Display over other apps**.
4. Turn on the accessibility service **Screen Close Assistant (confirmed taps)**.
5. **Start capture** and accept the screen-capture prompt.
6. Open **SCA Ad Test Bed** and pick a scenario.
7. On the floating panel, press **Find close button**. Check the green box, then press **Tap it**.

## Step 11: Backend smoke test

```bash
cd "/Users/lyrics/Desktop/personal projects/projectforads/power-platform/tests"
chmod +x smoke_test_flow.sh
export SCA_FLOW_URL='<HTTP URL from step 6>'
export SCA_TEST_IMAGE=./sample.jpg    # e.g. a screenshot of the test bed: adb exec-out screencap -p > s.png, then convert to JPEG <= 2576 px
az login --tenant <TENANT_ID>
./smoke_test_flow.sh
```

## Observability checklist

| Layer | Where to look |
|---|---|
| Android | `adb logcat -s SCA`: events `analysis_result`, `flow_http_response`, `metric`; the diagnostics card in the app |
| Correlation | `requestId` = `x-ms-client-request-id`, and the trace id in `traceparent`; the flow run id is returned in `x-sca-flow-run-id` |
| Flows | Power Automate run history. Inputs and outputs containing the image or key are secured. |
| Data | The Dataverse table *SCA Analysis Requests* records status, action, confidence, error code, latencies, token usage and flow run id |
| Agent | Copilot Studio **Analytics**, plus optional Application Insights (doc 03) |
| Anthropic | Console **Usage** and **Logs** for the `sca-power-platform` key |

## Rollback

- Turn off the HTTP flow: **Turn off** in Power Automate. Android then gets `ERROR` documents.
- Revoke the Anthropic key in the Console.
- `python3 001_create_analysis_request.py --down` deletes the table and all audit rows.
