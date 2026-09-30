# ADR-004: System CA validation without certificate pinning

- Status: Accepted
- Context: The requirement is "certificate-validating HTTPS". Microsoft rotates the
  `*.flow.microsoft.com`, `*.powerplatform.com` and `login.microsoftonline.com` certificates and
  intermediate CAs without notice.
- Decision: Use OkHttp's default hostname verification and the platform trust store.
  `network_security_config.xml` blocks cleartext and ignores user-installed CAs in release builds.
  User CAs are allowed in debug builds only, for proxy debugging. There is no custom
  `TrustManager` and no `HostnameVerifier` override.
- Rejected: Pinning Microsoft leaf or intermediate keys. It risks outages whenever Microsoft
  rotates certificates, and the bearer token plus Entra audience checks already guard against a
  malicious endpoint.
