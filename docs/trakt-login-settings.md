# Trakt Login and Settings Design

## Decision

The APK patch will add Trakt login/settings inside the patched TiviMate app.

## User experience

Settings row:

```text
Settings → Other or General → Trakt
```

Trakt screen states:

### Disconnected

```text
Trakt
Status: Not connected
[Connect Trakt]

Sync:
[x] Movies
[x] Episodes
[x] Playback progress / scrobble
[x] Watched history
```

### Waiting for device authorization

```text
Trakt
Go to: https://trakt.tv/activate
Code: ABCD1234
Waiting for authorization…
[Cancel]
```

### Connected

```text
Trakt
Status: Connected as <username if available>
Last sync: <timestamp or Never>
[Sync now]
[Disconnect]

Sync:
[x] Movies
[x] Episodes
[x] Playback progress / scrobble
[x] Watched history
```

### Error

```text
Trakt
Status: Error
<short user-facing error>
[Retry]
[Disconnect]
```

## Device OAuth implementation

Endpoint docs: `docs/trakt-api.md`.

Flow:

1. `Connect Trakt` calls `POST /oauth/device/code`.
2. Store transient `device_code`, `expires_at`, `interval`.
3. Display `user_code` and `verification_url`.
4. Poll `POST /oauth/device/token` every `interval` seconds.
5. On success, persist:
   - access token;
   - refresh token;
   - expiry time;
   - token type/scope.
6. Fetch account/settings/profile if needed to display username.
7. Queue worker can now process sync events.

## Storage

Preferred:

- AndroidX Security Crypto / EncryptedSharedPreferences if available and compatible.

Fallback:

- app-private SharedPreferences:

```text
com.tivimate.traktpatch.auth
```

Keys:

```text
trakt_access_token
trakt_refresh_token
trakt_expires_at_ms
trakt_user_slug
sync_movies_enabled
sync_episodes_enabled
sync_progress_enabled
sync_watched_enabled
```

Never store credentials in committed repo files.

## Activity/component plan

Injected activity:

```text
com.tivimate.traktpatch.settings.TraktSettingsActivity
```

Manifest addition:

```xml
<activity
    android:name="com.tivimate.traktpatch.settings.TraktSettingsActivity"
    android:exported="false"
    android:theme="@style/APKTOOL_DUPLICATE_style_0x7f14030f" />
```

Implementation style:

- Use plain Android Views where possible to avoid adding large dependencies.
- Reuse TiviMate theme if safe.
- Keep screen functional even if visual integration is basic.

## Settings-menu patch point

Unknown until UI class is mapped.

Discovery targets:

```text
ar.tvplayer.tv.settings.ui.*
SelectBackupActivity
RestoreBackupActivity
settings rows/resources around Back up data / Restore data
```

Fallback during early patching:

- add activity as exported development-only launcher alias behind patcher flag;
- remove launcher alias once native settings row is patched.

## Validation

Manual test checklist on emulator:

- Open Trakt settings screen.
- Connect flow displays code and URL.
- Polling stops on success/cancel/expiry.
- Token persists after app restart.
- Disconnect clears tokens and disables queue processing.
- UI gives visible loading/status feedback.
