# 2. Licenses and prerequisites

Microsoft licensing changes often. Check each item on the linked Microsoft pages before you
buy anything. For a school project, the free developer and trial options at the end of this page
are usually enough.

## Microsoft

| Need | Why | Options |
|---|---|---|
| Microsoft Entra ID tenant | Sign-in for the Android app and for the HTTP trigger | Your school or work tenant, or a free Microsoft 365 Developer Program tenant if you qualify |
| Power Platform environment **with Dataverse** | Solution, connection references, environment variables, the `sca_analysisrequest` table | Developer environment (Power Apps Developer Plan, free), or a trial or production environment |
| Copilot Studio | The agent, the topic and the agent flow | Copilot Studio license or capacity (tenant license, capacity pack, or pay-as-you-go), or a Copilot Studio trial. Agent flow actions consume Copilot Studio capacity. Test runs in the designer are free. |
| Power Automate **Premium** for the owner of the HTTP flow and the core flow | *When an HTTP request is received*, custom connectors and Dataverse are premium | Power Automate Premium (per user), or a Developer Plan environment where premium connectors are allowed for development |
| Rights to create a custom connector | Anthropic connector | Environment Maker role |
| System Customizer or System Administrator in the environment | Run the Dataverse migration and import the solution | Assigned by the environment admin |
| Entra permission to register applications | Android public-client app registration | Application Developer role, or have an admin do it |
| Tenant admin consent (only if user consent is blocked) | Delegated `Power Automate` permission for the Android app | Global Admin or Privileged Role Admin |

## Anthropic

- An Anthropic Console account with billing turned on and a **dedicated API key** for this
  project. Set a monthly spend limit on the workspace.
- Model: `claude-opus-5-5` (Claude Opus 5.5). This ID comes from the Anthropic models overview
  page. `anthropic-version: 2023-06-01` is the current API version.

## Developer machine (macOS)

| Tool | Version | Install |
|---|---|---|
| Android Studio | Ladybug 2024.2.1 or newer | https://developer.android.com/studio |
| JDK | 17 (bundled with Android Studio as the JBR) | Bundled |
| Android SDK | Platform 35, Build-Tools 35.0.0 | Android Studio > SDK Manager |
| Azure CLI | 2.50+ | `brew install azure-cli` |
| Power Platform CLI (optional) | latest | `brew install --cask powershell`, then `dotnet tool install --global Microsoft.PowerApps.CLI.Tool` |
| paconn (optional, connector upload) | latest | `pip3 install paconn` |
| jq, python3 | any recent | `brew install jq python` |

## Android device

- Android 11 (API 30) or newer, with USB debugging turned on.
- You grant these at runtime, each time you need them: screen-capture consent, the overlay
  permission (*Display over other apps*), the notification permission (Android 13+), and the
  **Screen Close Assistant** accessibility service.
