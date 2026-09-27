# Eon

<p align="center">
  <img src="icon.svg" width="128" height="128" alt="Eon logo">
</p>

An Android app for reading aeon.co articles offline. No server required.

## Why

Aeon.co has great longform essays but their site is slow on mobile, their search is broken (client-side rendered, doesn't actually find anything), and they don't have a proper app. This fixes that.

## What it does

- **Feed** – Parses the Aeon RSS feed (`/essays/feed.rss`) to show recent articles
- **Search** – Searches a local index of ~1,200 essays built from Aeon's own feeds. No third-party engine, so no CAPTCHA and no IP blocks
- **Article view** – Fetches and renders article text, hero images, inline images, blockquotes, subheadings
- **Offline** – Saves articles locally. No internet? Still readable
- **Bookmarks** – Tag articles you want to come back to
- **Reading progress** – Remembers where you left off

## How it works

No companion server. Everything runs on the phone:

- **Feed**: Jsoup parses the RSS XML directly (uses `Parser.xmlParser()` – HTML parser breaks RSS `<link>` elements). The home feed merges `/essays/feed.rss` with the five section feeds so paging has somewhere to go, and each section's feed supplies the category that the combined feed omits
- **Search**: Aeon has no server-side search. Its `/search` page is client-side rendered and disallowed in `robots.txt`, there is no sitemap, and every third-party engine reachable without an API key is unusable from a phone — Mojeek serves a CAPTCHA, DuckDuckGo flips to `anomaly.js` after two queries, Brave rate-limits to one query per IP, and no public SearXNG instance answers. What Aeon *does* publish is a feed per section *and* a feed per subtopic (`/philosophy/ethics/feed.rss`), about 120 of them. The app reads all of them once, caches ~1,200 essays locally, and searches that. The five section feeds alone only ever carry the newest 20 essays each (66 articles), so this is an 18x larger corpus. It refreshes at most daily, and search also covers the full text of articles already opened, so it works offline.
  - Known limit: the index covers titles, summaries and authors, so a word that appears only inside an essay's text (`"hitler"`, say) won't match unless you've already opened that essay. Indexing full text for all ~1,200 would mean downloading hundreds of megabytes.
- **Article fetch**: Aeon runs on Vercel which blocks HTTP/2 with 429s. HTTP/1.1 works fine, so that's what article requests use
- **Parser**: Reads the byline from the `<aside>`, the section from the `section-*` class on `<main>`, and the publication date from the `font-mono` leaf element (not a document-wide regex, which used to match dates mentioned in the prose). Article chrome is dropped via the `print:hidden` class Aeon wraps it in, rather than by deleting DOM subtrees

## Build

```
./gradlew :app:assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Tech

Kotlin, Jetpack Compose, Hilt, Jsoup, OkHttp, Room.

## Downloads

APKs on the [releases page](https://github.com/Wsylq/aeon-reader-android/releases).

## TODO

- [x] Line spacing / margins / text alignment settings
- [x] Immersive full-screen mode (hide status bar + controls)
- [x] Reading time estimate on feed cards
- [x] Swipe-to-bookmark on feed
- [x] Pull-to-refresh on feed
- [ ] Category filter / sort on feed
- [x] Search bookmarked/cached articles
- [ ] Export articles as PDF / plain text
- [ ] OPML export
- [ ] Text-to-speech (read aloud)
- [x] App shortcuts (long-press launcher icon) -- 
  - [x] App isnt installed error
- [x] Reading themes (sepia, green-tinted paper)
- [x] Fix read time of articles on feed page
- [ ] Make an endpoint api like MAL for tracking and retrieving user's data of read articles and all.
- [x] Fix a major lag spike while opening and closing articles.
- [x] Release notes arent being styled/formatted and just display * * instead of bolding and all. 
