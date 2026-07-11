# Morphe Build Smoke Test

## Result

The Morphe-native patch scaffold compiles locally.

Command run:

```sh
cd morphe
GRADLE_USER_HOME=/tmp/tivimate-morphe-gradle-home ./gradlew --no-daemon :patches:compileKotlin :patches:stub:build
```

Result:

```text
BUILD SUCCESSFUL
3 actionable tasks: 3 executed
```

## Notes

- This validates the Morphe Gradle plugin and Kotlin patch stubs.
- It does **not** build a complete `.mpp` bundle yet.
- It does **not** modify TiviMate yet.
- Real bytecode/resource changes still require evidence-backed fingerprints:
  - settings/menu insertion point;
  - playback/progress/watched hook point;
  - media metadata extraction path.

## Credentials

Morphe artifacts resolve from GitHub Packages. Local builds require credentials with package-read access via:

```properties
gpr.user=<github-user>
gpr.key=<github-token>
```

or `GITHUB_ACTOR` / `GITHUB_TOKEN`.
