# Security policy

## Reporting

Report vulnerabilities privately through GitHub Security Advisories on this repository. Do not open a public issue for a bypass or a code-handling flaw.

Include:

- Android version and device model
- Steps to reproduce
- Whether the issue needs a rooted or device-owner device
- The impact on limits, the override code, or stored data

## Scope

In scope:

- Override-code disclosure or verification bypass without the code
- Recovery bypass that skips the 48-hour wait
- Reading app usage or rules from another app
- A crash or data loss in time accounting

Out of scope (documented in [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md)):

- Uninstalling, clearing data, force-stopping, or revoking permissions
- Changing the system clock
- Rooted or device-owner devices

## Supported versions

Fixes land on `main` and in the latest tagged release. Older releases are not maintained.
