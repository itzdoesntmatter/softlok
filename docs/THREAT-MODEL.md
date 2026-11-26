# Threat model

Overplay is a self-control tool. It is designed to make impulsive bypassing inconvenient, not to resist a determined owner of the device.

## Assets

- The override code
- Per-app rules and daily usage
- Recovery state

## Adversaries

| Adversary | Capability | Result |
| --- | --- | --- |
| The device owner in a hurry | Close the block, open settings | Must enter the code to add time or weaken a rule |
| The device owner with patience | Uninstall, clear data, revoke permissions, force stop | Protection ends; data is lost |
| Another app | Read Overplay data | Blocked by Android app sandboxing |
| A network attacker | Intercept data | Nothing to intercept; Overplay has no network permission |
| Someone with physical access | Use the device, change the clock | Can bypass unless the owner also controls the device's security |

## Mitigations

- The code is generated with `SecureRandom` and stored as a salted PBKDF2-HMAC-SHA256 hash.
- Five wrong attempts trigger a 30-second lockout.
- Adding time, raising a limit, removing an app, or changing the code requires the current code.
- Recovery waits 48 hours. Protection stays active during the wait, and the deadline is persisted.
- The code is shown once and must be typed back before it is saved.
- Database backups are disabled; rules and usage do not leave the device.
- Overplay has no `INTERNET` permission.

## Known bypasses

- Uninstalling the app or clearing its data resets everything.
- Disabling the accessibility service stops enforcement.
- Force-stopping the app stops enforcement until Android restarts the service.
- Changing the system clock can shorten the recovery wait or affect day boundaries.
- A rooted or device-owner-controlled device can bypass any app-level restriction.

## Non-goals

- Preventing uninstall or permission changes
- Parental controls or enterprise device management
- Blocking websites, domains, or traffic
- Cloud sync, accounts, or remote reporting
- Hiding the fact that the app is installed

A future Device Owner mode could add tamper resistance, but it changes the product into device management and is out of scope for the first release.
