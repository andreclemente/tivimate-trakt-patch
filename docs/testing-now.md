# Testing the Morphe Scaffold

## What can be tested now

The current Morphe patch bundle is a scaffold. It can be built and listed by Morphe tooling, but it should not be expected to change TiviMate yet.

Validated artifact:

```text
morphe/patches/build/libs/patches-0.1.0.mpp
```

Current patch names:

```text
TiviMate Trakt settings/login
TiviMate Trakt runtime sync
```

Both are placeholders until runtime/static evidence identifies stable insertion points.

## Build the Morphe bundle

Requirements:

- Java 21+
- Android SDK for extension build
- GitHub Packages credentials for Morphe artifacts

```sh
cd morphe
./gradlew --no-daemon :patches:buildAndroid :extensions:trakt:assembleRelease
```

Expected artifact:

```text
patches/build/libs/patches-0.1.0.mpp
```

## List patches with Morphe CLI

After downloading/building Morphe CLI:

```sh
java -jar morphe-cli.jar list-patches --with-packages --with-versions --with-options morphe/patches/build/libs/patches-0.1.0.mpp
```

Expected: the two TiviMate Trakt scaffold patches appear.

## Do not expect functional APK changes yet

Patching TiviMate with this bundle now is only a Morphe pipeline smoke test. It will not add Trakt login or sync until the patch TODOs are replaced with real bytecode/resource edits.

## Useful current test

The useful test remains runtime evidence capture on Android TV / TV box:

```sh
adb connect <tv-ip>:5555
adb devices -l
frida -U -f ar.tvplayer.tv -l tools/frida/dump-sqlite-schema.js --no-pause | tee schema.log
frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause | tee sqlite-movie-playback.log
```

Send logs back after redacting secrets.
