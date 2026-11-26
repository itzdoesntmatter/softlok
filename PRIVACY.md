# Privacy

Softlok stores everything on the device.

- No account or sign-in
- No analytics, crash reporting, advertising, or telemetry
- No `INTERNET` permission
- No data sold or shared

## What is stored

- Package names and labels of apps you choose
- Daily usage totals for those apps
- Your rules
- A salted hash of the override code, never the code itself
- Recovery deadline and failed-attempt counters

The accessibility service reads only the package name of the app in the foreground. Window content, text, and keystrokes are not accessed.

## Retention and deletion

Usage rows are kept only as long as needed for day-boundary handling. Clearing Android app data or uninstalling Softlok deletes all stored data.

## Android backup

Backups are disabled for Softlok so rules and security state do not leave the device through Google backup or device transfer.
