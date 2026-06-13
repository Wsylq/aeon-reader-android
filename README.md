# Eon

<p align="center">
  <img src="icon.svg" width="128" height="128" alt="Eon logo">
</p>

An Android app for reading aeon.co articles offline. No server required.

## Why

Aeon.co has great longform essays but their site is slow on mobile, their search is broken (client-side rendered, doesn't actually find anything), and they don't have a proper app. This fixes that.

## What it does

- **Feed** – Parses the Aeon RSS feed (`/essays/feed.rss`) to show recent articles
- **Search** – Searches the full Aeon archive via Mojeek (site:aeon.co + your query). Works from any IP, no CAPTCHA
- **Article view** – Fetches and renders article text, hero images, inline images, blockquotes, subheadings
- **Offline** – Saves articles locally. No internet? Still readable
- **Bookmarks** – Tag articles you want to come back to
- **Reading progress** – Remembers where you left off

## How it works

No companion server. Everything runs on the phone:

- **Feed**: Jsoup parses the RSS XML directly (uses `Parser.xmlParser()` – HTML parser breaks RSS `<link>` elements)
- **Search**: Mojeek returns clean HTML, no JS required. Just a `GET` with a query
- **Article fetch**: Aeon runs on Vercel which blocks HTTP/2 with 429s. HTTP/1.1 works fine, so that's what article requests use
- **Parser**: Finds the content div inside `<main>` (the one with the most text), extracts paragraphs, headings, blockquotes, and images. Filters out nav, related articles, social buttons by checking each element's parent chain instead of deleting DOM subtrees

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
- [ ] Search bookmarked/cached articles
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
