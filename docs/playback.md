# Playback findings

Status: Initial static playback library evidence only. App-level playback callbacks are not yet identified.

## Confirmed library evidence

Evidence: `research/findings/dex-class-search.txt`

Confirmed Media3/ExoPlayer classes include:

```text
Landroidx/media3/exoplayer/ExoPlayer;
Landroidx/media3/common/PlaybackException;
Landroidx/media3/exoplayer/ExoPlaybackException;
Landroidx/media3/exoplayer/source/BehindLiveWindowException;
Landroidx/media3/exoplayer/hls/HlsMediaSource$Factory;
Landroidx/media3/exoplayer/dash/DashMediaSource$Factory;
Landroidx/media3/exoplayer/rtsp/RtspMediaSource$Factory;
```

Native playback/media libraries include:

```text
libffmpegJNI.so
libavutil.so
librtmp-jni.so
```

## Not yet confirmed

Unknown:
- TiviMate class that creates/owns `ExoPlayer`.
- TiviMate class that receives player events.
- Start/pause/resume/stop/completion callback path.
- Where current media item metadata is stored during playback.
- How live TV is distinguished from VOD playback at runtime.

## Next tests

- JADX call search for `androidx.media3.exoplayer.ExoPlayer`, `androidx.media3.common.Player$Listener`, `getCurrentPosition`, `getDuration`, `onPlaybackStateChanged`, `onIsPlayingChanged`.
- Frida hook Media3 `Player` methods and app-level listeners on a device.
