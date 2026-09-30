# ADR-001: The Android image path uses an Entra-protected HTTP trigger, not the Agents Client SDK

- Status: Accepted (2026-09-29)
- Context: The requirement prefers the Microsoft Agents Client SDK for Android. Microsoft Learn
  (updated 2026-08-19) and the `microsoft/AgentsClientSDK.Android` README say the SDK is in
  **private preview**, is shipped as a JAR from GitHub Releases (not Maven), and "supports
  text-based communication and adaptive cards rendering". It has no attachment or image API.
- Decision: Android sends the screenshot to **SCA Android HTTP Entry**. That flow uses the
  *When an HTTP request is received* trigger with **Specific users in my tenant**. It calls the
  shared child flow **SCA Analyze Core**, which the Copilot Studio agent flow also calls. On the
  device, the transport sits behind `AnalysisTransport`, so an SDK transport can be added once
  the SDK supports attachments.
- Consequences: There's one source of validation logic for both entry points. There's no SAS
  `sig=` URL in the APK, and the Gradle build fails if one is configured. The app needs a Power
  Automate delegated permission and consent in Entra.
- Rejected: Sending base64 as SDK text messages. That exceeds activity size limits, isn't a
  supported pattern, and puts image bytes into conversation transcripts.
