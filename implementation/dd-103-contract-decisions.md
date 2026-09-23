# DD-103 Contract Decisions

## Token trust contract

- Ticket, Knowledge, and AI Orchestrator are OAuth2 Resource Servers for user-facing APIs.
- Only RS256 access tokens are accepted.
- `JWT_ISSUER`, `JWT_AUDIENCE`, and `JWKS_URI` are required deployment contracts with local
  defaults pointing to Identity on port 8081.
- Signature, expiry/not-before timestamps, issuer, and audience are validated before claims are
  mapped to an application principal.
- `sub` and `tid` are mandatory UUID strings. `roles` is a non-empty array containing only
  `CUSTOMER`, `AGENT`, or `ADMIN`.

## Principal and authorization

- Each bounded context owns an `application.AuthenticatedPrincipal` containing `userId`,
  `tenantId`, and an immutable role set.
- JWT roles map to Spring authorities with the `ROLE_` prefix for method-level authorization.
- Application and persistence code derive tenant exclusively from the verified principal. Request
  bodies, query parameters, and arbitrary headers are never trusted as tenant identity.
- `/actuator/health/**` and `/actuator/info` are public; other user-facing routes require a valid
  access token.

## Service-to-service boundary

- User access tokens are denied on `/internal/**`.
- Workload credentials and internal scopes/audiences are a separate implementation concern. Until
  that mechanism exists, internal endpoints fail closed rather than accepting a user token.

## Key rotation

- Resource services resolve keys by JWT `kid` from Identity JWKS.
- Identity publishes the current and previous public key during the overlap window, allowing
  already-issued tokens to remain valid until their normal expiry.
- Private signing keys remain only in Identity and are never copied into resource services.
