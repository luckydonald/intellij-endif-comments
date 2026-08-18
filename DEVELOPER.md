# Developer guide

Build/dev instructions for the *Explicit Block Endings* IntelliJ Platform plugin. For what the
plugin does and how to use it, see [`README.md`](README.md).

## Requirements

- JDK 21 (the IntelliJ Platform Gradle Plugin needs it for building against 2024.3+ target IDEs).
- No local Gradle install needed — use the checked-in wrapper (`./gradlew`), which downloads the
  right Gradle version on first run.
- The first build also downloads a PyCharm Community instance to build/test against (a few hundred
  MB) — that only happens once and is cached under `~/.cache` (Linux/macOS) or `%USERPROFILE%\.gradle`
  (Windows).

## Build

```bash
./gradlew buildPlugin
```

The installable plugin ZIP lands in `build/distributions/`.

## Run in a sandbox IDE (fastest loop while developing)

```bash
./gradlew runIde
```

This launches a disposable PyCharm instance with the plugin already installed — open any `.py`
file in it to see the virtual markers live, without touching your real PyCharm installation/settings.

## Load it into a real PyCharm

1. Build the ZIP: `./gradlew buildPlugin`.
2. In PyCharm: **Settings/Preferences > Plugins > ⚙️ > Install Plugin from Disk...**
3. Pick the ZIP from `build/distributions/`.
4. Restart PyCharm when prompted.

## Run tests

```bash
./gradlew check
```

Runs the unit tests (`src/test/kotlin`, fixtures under `src/test/testData`) via the IntelliJ
Platform Test Framework.

## Verify plugin compatibility

```bash
./gradlew verifyPlugin
```

Runs the IntelliJ Plugin Verifier against the target IDE versions declared in `gradle.properties`
(`platformVersion`, `pluginSinceBuild`/`pluginUntilBuild`) — catches API-compatibility issues before
they surface as a runtime error in an actual IDE.

## CI

- `.github/workflows/test.yml` runs `./gradlew check` + `./gradlew verifyPlugin` on every push and
  pull request.
- `.github/workflows/release.yml` runs `./gradlew buildPlugin` on pushed version tags (`v*`) and
  attaches the resulting ZIP to a GitHub Release.

## Project layout

- `src/main/kotlin/de/luckydonald/endifcomments/model/` — the keyword table and PSI scanner shared
  by both the inlay rendering and the inspection.
- `src/main/kotlin/de/luckydonald/endifcomments/inlay/` — the `TextEditorHighlightingPass` that
  computes and renders the virtual `# end ...` block inlays.
- `src/main/kotlin/de/luckydonald/endifcomments/inspection/` — the inspection + quick-fix that flags
  and removes redundant real `# end ...` comments.
- `src/main/kotlin/de/luckydonald/endifcomments/settings/` — the persisted "Active" setting and its
  Settings page.
- `src/main/resources/META-INF/plugin.xml` — extension point registrations.
