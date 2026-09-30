# ADR-002: Dataverse alternate keys for idempotency and duplicate-hash rejection

- Status: Accepted
- Context: Flows are stateless and can run concurrently. Android retries on timeouts. The
  requirement says to reject duplicate `screenshotHash` values within a session. It doesn't
  define a session identifier.
- Decision: Add a required `sessionId` input (a UUID the app generates for each user-started
  session). The table `sca_analysisrequest` has unique alternate keys on `sca_requestid` and on
  `(sca_sessionid, sca_screenshothash)`. The flow inserts first and treats an insert failure as
  the duplicate signal. A completed `requestId` replays its stored response.
- Why not a flow variable, SharePoint or Redis: flow variables don't persist between runs.
  SharePoint has no unique compound index. Redis would add a backend, which the requirement
  forbids. Dataverse ships with Copilot Studio environments.
- Consequences: Retries are safe (at-least-once delivery, exactly-once analysis for each
  requestId). Rows hold metadata only, never images. Add a bulk-delete job (doc 21) to clear rows
  older than 7 days.
