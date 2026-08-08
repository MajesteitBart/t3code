# Native Android persistence contract

The foundation deliberately separates protected access credentials from ordinary environment state.

## Credentials

- `AndroidKeystoreCredentialRepository` encrypts a versioned `direct-bearer` envelope with AES-256-GCM.
- The non-exportable encryption key lives in Android Keystore. The environment ID is authenticated as AES-GCM additional data, so ciphertext cannot be moved between environment records.
- App-private preferences contain only the envelope version, initialization vector, and ciphertext. Room never receives an access credential.
- Unknown envelope versions or credential kinds, missing key material, invalidated keys, and corrupt ciphertext fail closed as re-pair-required credential failures. Foundation does not persist DPoP fields, pairing codes, or WebSocket tickets.
- The application backup rules exclude preferences and databases from cloud backup and device transfer.

The implementation follows the Android Keystore and cryptography guidance for app-scoped, non-exportable keys and AES/GCM/NoPadding:

- <https://developer.android.com/privacy-and-security/keystore>
- <https://developer.android.com/privacy-and-security/cryptography>

## Room catalog

Room schema version 2 owns only non-secret data:

- `environments`: identity, label, HTTP/WebSocket bases, and the canonical descriptor JSON;
- `application_state`: one atomic active-environment selection;
- `last_known_shell`: one canonical last-known shell snapshot per environment, deleted with its environment.

`MIGRATION_1_2` adds last-known shell state without destructive fallback. Room schema JSON is checked in under `schemas/`, and device-backed tests open a real version-1 database through the migration before exercising the new table.

## Cross-repository ordering

Keystore and Room cannot share a transaction. `EnvironmentPersistenceCoordinator` therefore enforces explicit compensation:

1. Save the credential, then save/activate the catalog row. If Room fails, restore the previous credential or remove the newly written credential.
2. Remove the catalog row and update selection, then delete the credential. If credential deletion fails, restore the exact environment, selection, and last-known snapshot.
3. If compensation also fails, `PersistenceTransactionFailure` retains the initiating failure and every rollback failure while exposing only a safe summary message.

Compensation runs in a non-cancellable context so a partially completed cross-repository operation is repaired before cancellation escapes. Genuine coroutine cancellation is then rethrown unchanged (with compensation failures suppressed) rather than being converted into a persistence failure.

Run pure and device-backed evidence from `apps/kotlin-android`:

```powershell
./gradlew.bat foundationPersistence
```
