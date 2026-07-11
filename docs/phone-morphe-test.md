# Phone test via Morphe Manager

Goal: make testing simple from the phone:

```text
add source in Morphe → choose TiviMate patches → patch APK → install
```

## Add the patch source

Open this on the phone with Morphe Manager installed:

```text
https://morphe.software/add-source?github=andreclemente/tivimate-trakt-patch&name=TiviMate%20Trakt
```

Morphe should ask to add the `TiviMate Trakt` patch source.

Equivalent manual path:

```text
Morphe → sources / patch sources → add source → GitHub URL
https://github.com/andreclemente/tivimate-trakt-patch
```

Morphe transforms that GitHub URL to:

```text
https://raw.githubusercontent.com/andreclemente/tivimate-trakt-patch/main/patches-bundle.json
```

## Patch on phone

1. Open Morphe.
2. Add/enable the `TiviMate Trakt` source.
3. Pick your clean TiviMate APK.
4. Select:
   - `TiviMate Trakt settings/login`
   - `TiviMate Trakt runtime sync`
5. Patch.
6. Install the output APK.

## Expected result right now

This is currently a Morphe source/pipeline smoke test only.

Expected:

- source adds successfully;
- patches appear in Morphe;
- patch process can run;
- output APK can install/launch if Morphe accepts the input APK/version.

Not expected yet:

- Trakt settings screen;
- Trakt login;
- Trakt sync.

Those require real TiviMate hook implementation after runtime evidence is collected.
