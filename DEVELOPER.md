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

## Run the automated UI smoke test

Unit tests exercise the plugin's logic directly, but can't catch a mis-registered `plugin.xml`
extension (a wrong extension-point name, a typo'd attribute, ...) — the platform silently ignores
those instead of failing the build. This is exactly what happened once: `<extensions
defaultExtensionPoint="com.intellij">` used a non-existent attribute name (it's
`defaultExtensionNs`), so every single extension in the block silently failed to register while the
plugin still showed up as "enabled" in Settings > Plugins. The UI test drives a real running IDE via
[`intellij-ui-test-robot`](https://github.com/JetBrains/intellij-ui-test-robot) and asserts, via
`ExtensionPointName` lookups run inside the IDE, that each of our extensions is actually registered
— catching that whole class of "installed and enabled, but has zero effect" bug going forward.

```bash
./gradlew runIdeForUiTests &
./gradlew uiTest
```

The first command launches a sandboxed IDE with the plugin and the `robot-server` companion plugin
installed, listening on `localhost:8082`; the second drives it and asserts against it. Test code
lives in `src/uiTest/kotlin`.

## Verify plugin compatibility

```bash
./gradlew verifyPlugin
```

Runs the IntelliJ Plugin Verifier against the target IDE versions declared in `gradle.properties`
(`platformVersion`, `pluginSinceBuild`/`pluginUntilBuild`) — catches API-compatibility issues before
they surface as a runtime error in an actual IDE.

## CI

- `.github/workflows/test.yml` runs `./gradlew check` + `./gradlew verifyPlugin` on every push and
  pull request, plus a separate `ui-test` job that launches `runIdeForUiTests` under `xvfb` and
  runs `uiTest` against it.
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
- `src/main/kotlin/de/luckydonald/endifcomments/startup/` — startup notification confirming the
  plugin loaded; added while diagnosing a "plugin installed but has no effect" bug (see below), kept
  as a quick visual "yes it's active" signal.
- `src/main/resources/META-INF/plugin.xml` — extension point registrations.
- `src/uiTest/kotlin/` — the `intellij-ui-test-robot` UI smoke test (see above).
