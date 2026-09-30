# ADR-005: Manual dependency injection (AppContainer) instead of Hilt

- Status: Accepted
- Context: A foreground service, an AccessibilityService, an overlay and an Activity have to
  share one `SessionController`. Hilt would add annotation processing (KSP/KAPT), build
  complexity and more for a student to learn.
- Decision: The application holds a single `AppContainer`, created in `Application.onCreate`.
  Every collaborator is behind an interface (`ScreenshotSource`, `AnalysisTransport`,
  `GestureDispatcher`, `TokenProvider`, `Clock`), so unit tests pass fakes without a DI framework.
