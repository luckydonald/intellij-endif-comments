# Polish & prep for first JetBrains Marketplace release

## Context

`intellij-endif-comments` ("Explicit Block Endings") is functionally mature — case-insensitive
detection, configurable inlay/warning style, solid unit + UI test coverage, CI running
`check`/`verifyPlugin`/`uiTest` on every push. It has never been tagged or published; README
explicitly says "Not yet published to the JetBrains Marketplace." The goal is to close the gaps
that block a first public Marketplace release, without touching the working feature set.

Blockers found (survey via Explore agent + direct file reads):
1. No `LICENSE` file — Marketplace requires a declared license.
2. No Marketplace publish automation — `release.yml` only attaches a ZIP to GitHub Releases; no
   `publishPlugin` task wiring, no token secret, no channel config.
3. No `<change-notes>` in `plugin.xml`, no `CHANGELOG.md`.
4. No plugin icon (`pluginIcon.svg`) — Marketplace strongly recommends one.
5. `pluginVersion` isn't set in `gradle.properties` (silently defaults to `0.1.0` in
   `build.gradle.kts`) — should be explicit for a real release.
6. The always-on "Explicit Block Endings loaded." startup balloon
   (`src/main/kotlin/de/luckydonald/endifcomments/startup/StartupNotifier.kt`) was a temporary
   diagnostic from plan `002` (confirming the plugin wasn't inert) — noisy for real end users, user
   confirmed: remove it.

User decisions from clarifying questions:
- License: open source, copyleft — derivatives/forks must stay open → **GPLv3** (not MIT: user
  wants downstream use to keep the code open, which permissive licenses don't enforce).
- Startup notification → **remove**.
- Icon → generate an SVG showing `# end` in a stylized way.

## Changes

### 1. Remove the startup notification
- Delete `src/main/kotlin/de/luckydonald/endifcomments/startup/StartupNotifier.kt` and the
  `startup/` package.
- Remove its two `plugin.xml` entries: the `<notificationGroup id="de.luckydonald.endifcomments.startup" .../>`
  and `<postStartupActivity implementation="...StartupNotifier"/>`.
- Update `src/uiTest/kotlin/de/luckydonald/endifcomments/EndCommentUiTest.kt`'s
  `all plugin xml extensions are actually registered` test: drop the `notificationGroupFound` and
  `postStartupActivityFound` checks/markers (their extension points no longer exist). Keep
  `inspectionFound`, `configurableFound`, `highlightingPassFound` — they still cover the
  "extension silently didn't register" regression this test exists for.

### 2. Add LICENSE
- Add `LICENSE` (GPLv3 full text), copyright holder "Lucky Lucy" (git user), year 2026.
- Reference it from `README.md` if the README has a license section already (check first; add one
  short line if not) and declare it in `plugin.xml`/Marketplace listing metadata as GPL-3.0.

### 3. Plugin icon / logo — finalized design

User picked: rounded-square deep-violet badge, `#` dimmed muted-violet, `end` in bright pink,
small corner-bracket accent, monospace. Confirmed SVG (40x40 viewBox):

```svg
<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
  <rect width="40" height="40" rx="8" fill="#241B34"/>
  <path d="M8 30 L8 34 L12 34" stroke="#5B4A7A" stroke-width="2" fill="none" stroke-linecap="round"/>
  <text x="20" y="24" font-family="JetBrains Mono, Fira Code, monospace" font-size="13" text-anchor="middle">
    <tspan fill="#8A76B0">#</tspan><tspan fill="#F057C4" dx="1">end</tspan>
  </text>
</svg>
```

- `src/main/resources/META-INF/pluginIcon.svg` — the SVG above.
- `src/main/resources/META-INF/pluginIcon_dark.svg` — same layout, badge fill lightened
  (e.g. `#3A2C52`) so it doesn't disappear against a dark Settings/Marketplace panel; text colors
  unchanged (already bright enough on both).
- Plain SVG shapes + `<text>`, no embedded font/raster, so it stays crisp at Marketplace thumbnail
  size and in the Settings plugin list (~16x16).

### 4. Release metadata
- `gradle.properties`: add `pluginVersion = 1.0.0` (first public release; explicit instead of the
  code fallback).
- `plugin.xml`: add a `<change-notes>` block summarizing the current feature set (inlay end-markers,
  redundant-comment inspection, case-insensitive detection, configurable style) as the "1.0.0"
  entry.
- Add `CHANGELOG.md` at repo root, seeded with the 1.0.0 entry (mirrors `<change-notes>`, git-friendly
  format for future entries).

### 5. Marketplace publish automation
- `build.gradle.kts`: add a `publishing { }` block under `intellijPlatform { }` reading the token
  from an env var (e.g. `PUBLISH_TOKEN`), matching current `org.jetbrains.intellij.platform` 2.18.1
  API (`intellijPlatform.publishing.token.set(...)`, optional `channels.set(listOf("default"))`).
- `.github/workflows/release.yml`: add a `./gradlew publishPlugin` step after `buildPlugin`, gated
  on a `JETBRAINS_MARKETPLACE_TOKEN` (or similarly named) repo secret being present, running only
  on tag push (same trigger it already has). Keep the existing GitHub Release ZIP-attach step.
- `DEVELOPER.md`: add a short "Releasing" section documenting: bump `pluginVersion`, update
  `CHANGELOG.md`/`<change-notes>`, tag `vX.Y.Z`, push tag → CI builds, verifies, and publishes.

### 6. README
- Remove/replace the "Not yet published to the JetBrains Marketplace" line once the above is in
  place — replace with a Marketplace badge/link placeholder (actual link only valid after first
  publish; use the plugin id-based Marketplace URL pattern JetBrains generates).

## Out of scope / requires you

- Actually creating the `JETBRAINS_MARKETPLACE_TOKEN` secret in GitHub repo settings and a
  Marketplace vendor account — external accounts I can't create for you.
- Pushing the `v1.0.0` tag that triggers the real publish — that's an irreversible, externally
  visible action (public Marketplace listing). I'll leave the repo ready but won't push a release
  tag without you explicitly asking for it.

## Verification

- `./gradlew check` — unit tests still pass after `StartupNotifier` removal.
- `./gradlew verifyPlugin` — plugin.xml still valid after removing the two extension entries.
- `./gradlew runIdeForUiTests &` then `./gradlew uiTest` — confirms the trimmed
  `EndCommentUiTest` still passes (extension registration + redundant-comment daemon flag) with no
  startup notification present.
- `./gradlew buildPlugin` — confirms the ZIP builds with the new icon/version/change-notes and
  passes `pluginVerifier`'s marketplace-readiness checks (license/icon presence).
