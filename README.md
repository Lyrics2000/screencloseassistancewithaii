# Screen Close Assistant (school project)

This is an Android app that takes a screenshot only when you ask for one and sends it to a
**Microsoft Copilot Studio / Power Automate** backend. The backend asks **Claude Opus 5.5**
(`claude-opus-5-5`) to find an advertisement's close button. The app draws the proposed target
on screen and taps it **only after you confirm**.

There is no custom application backend. The Anthropic API key lives only in a Power Platform
custom-connector connection.

```
Android (Kotlin, Compose, MVVM)
  → Entra ID token (MSAL)
  → Power Automate HTTP trigger ("Specific users in my tenant")
  → SCA Analyze Core child flow  ← also called by the Copilot Studio agent flow / topic
  → SCA Anthropic custom connector (AnalyzeScreenshot, x-api-key in the connection)
  → Claude Opus 5.5 vision + structured JSON output
  → layered validation (flow + device)
  → application/json back to Android → user confirmation → one AccessibilityService tap
```

## Deliverables

| # | Deliverable | Location |
|---|---|---|
| 1 | Architecture diagram | `docs/01-architecture.md` |
| 2 | Licenses and prerequisites | `docs/02-licenses-prerequisites.md` |
| 3 | Copilot Studio agent instructions | `docs/03-agent-instructions.md` |
| 4 | Agent topic design | `docs/04-topic-design.md`, `power-platform/copilot-studio/topic-analyze-android-screenshot.yaml` |
| 5 | Agent-flow input/output definitions | `docs/05-flow-io-definitions.md` |
| 6 | Anthropic custom connector (OpenAPI) | `docs/06-anthropic-custom-connector.md`, `power-platform/connector/*` |
| 7 | Secure connection references | `docs/07-connection-reference.md` |
| 8 | Flow expressions | `docs/08-flow-expressions.md`, `power-platform/flows/*.definition.json` |
| 9 | Parse JSON schema | `docs/09-parse-json-schema.md`, `power-platform/schemas/*` |
| 10 | Validation conditions | `docs/10-validation-conditions.md` |
| 11–20 | Android project, Gradle, manifest, service, capture, auth, client, overlay, accessibility, tests | `docs/11-20-android-project.md`, `android/` |
| 21 | Deployment | `docs/21-deployment.md` |
| — | Data-layer migration | `power-platform/dataverse/migrations/001_create_analysis_request.py` |
| — | Backend smoke tests | `power-platform/tests/smoke_test_flow.sh` |
| — | ADRs | `docs/adr/ADR-001` … `ADR-006` |

## What must be configured by hand in Microsoft portals

These steps can't be scripted from source files here. Each one is marked **MANUAL PORTAL STEP**
or **PORTAL** in the docs:

- the environment
- the environment variables
- the connections and connection references
- the three flows, including the trigger's *Who can trigger* setting and secure inputs/outputs
- the Copilot Studio agent settings, topic binding and publish
- the Entra app registration and consent
- Dataverse bulk-delete retention

## Quick start

See `docs/21-deployment.md`. In short:

```bash
python3 power-platform/dataverse/migrations/001_create_analysis_request.py   # data layer
# … portal steps 3–8 …
cd android && cp local.properties.example local.properties   # fill in IDs + flow URL
./gradlew :app:testDebugUnitTest :app:assembleDebug :testbed:assembleDebug
```

## Scope and responsible use

- The only supported action is `FIND_AD_CLOSE`. The app never clicks ads or calls-to-action.
  Each tap needs your explicit confirmation, and a session has at most 3 attempts.
- `foregroundPackage` must be on a test allowlist. By default that's the bundled **SCA Ad Test
  Bed** app, which shows clearly labelled fake ads.
- Automating taps inside third-party apps can break those apps' terms of service. Keep the
  allowlist limited to apps you own or are allowed to test.
- Google Play restricts AccessibilityService use. This project is meant for sideloaded,
  educational use.
