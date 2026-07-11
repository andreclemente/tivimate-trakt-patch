# Decompile status
- Apktool: success at research/apktool/Tivi8KPro/
- JADX full: failed/killed by OOM; partial output exists at research/jadx/Tivi8KPro/; targeted single-class output at research/jadx-single/

## Key Room table list from TvPlayerDatabase_Impl
    public final C1498 mo1381() {
        LinkedHashMap r0 = new LinkedHashMap();
        r0.put("channels_fts", "channels");
        r0.put("movies_fts", "movies");
        r0.put("series_fts", "series");
        return new C1498(this, r0, new LinkedHashMap(), new String[]{"playlists", "channels", "channel_groups", "channel_group_links", "channels_fts", "tvg_sources", "tvg_channels", "channel_group_options", "channel_manual_positions", "channel_tvg_bindings", "playlist_tvg_source_assignments", "last_played_positions", "recordings", "reminders", "history_programs", "my_programs", "movie_categories", "movies", "movies_fts", "series_categories", "series", "series_fts", "episode_last_played_positions", "search_queries", "dummy"});
    }
