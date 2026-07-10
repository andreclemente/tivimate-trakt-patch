# VOD model findings

Status: Initial provider/catalog model candidates identified from DEX class names and constructors.

## Xtream Codes candidates

Evidence: `research/findings/candidate-methods-fields.txt`

```text
Lar/tvplayer/core/data/api/xtreamcodes/MovieData;
Lar/tvplayer/core/data/api/xtreamcodes/MovieDataJsonAdapter;
Lar/tvplayer/core/data/api/xtreamcodes/Episode;
Lar/tvplayer/core/data/api/xtreamcodes/EpisodeInfo;
Lar/tvplayer/core/data/api/xtreamcodes/EpisodesMap;
Lar/tvplayer/core/data/api/xtreamcodes/SeriesCategory;
Lar/tvplayer/core/data/api/xtreamcodes/SeriesCategoryJsonAdapter;
```

## Stalker candidates

```text
Lar/tvplayer/core/data/api/stalker/VodCategory;
Lar/tvplayer/core/data/api/stalker/VodGenre;
Lar/tvplayer/core/data/api/stalker/VodItemsResponse;
Lar/tvplayer/core/data/api/stalker/Episode;
Lar/tvplayer/core/data/api/stalker/FullEpisode;
Lar/tvplayer/core/data/api/stalker/Season;
Lar/tvplayer/core/data/api/stalker/SeasonsResponse;
```

## Current conclusion

These are provider/API catalog models, not yet local watched/progress storage models. They are useful for content identity and Trakt mapping later, but the current milestone still requires finding local persistent state.

## Next tests

- Inspect Moshi `JsonAdapter` field-name options to map constructor fields to provider JSON keys.
- Runtime-log model values when opening movie/series/season/episode details.
- Compare provider IDs with local database records once database schema is recovered.
