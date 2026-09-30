# ADR-003: Structured outputs plus layered validation

- Status: Accepted
- Context: The requirements say model output must never be trusted to be well-formed, and that
  Markdown-wrapped or invalid output must become ERROR.
- Decision: The Claude request uses `output_config.format` (json_schema, GA on Opus 5.5), then
  the envelope check, then Parse JSON with bounds, then semantic checks in the flow, then
  `ResponseValidator` on the device, then human confirmation before any tap. Values the model
  doesn't supply (normalized coordinates) are computed, never requested. The Copilot Studio
  general-purpose model is never used to fill in values.
- Consequences: Every layer can reject independently. A bug or a prompt-injection success in one
  layer can't cause an unconfirmed tap.
