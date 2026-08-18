# Debug: `intellij-endif-comments` shows no effect in a real IDE

## Context

The plugin (virtual `# end if` block-end hints, its inspection, and its Settings page) was built, unit-tested, and packaged successfully, and a `<textEditorHighlightingPassFactory>` → `<highlightingPassFactory>` extension-point-name typo was already found and fixed (that typo alone would explain the inlay not rendering, but not the missing Settings page or missing inspection). After reinstalling the fixed build, the user still sees nothing at all:

- No **Settings > Other Settings > Explicit Block Endings** page.
- No entry under **Editor > Inspections** (or Intentions) for "Redundant explicit block-ending comment".
- No virtual `# end ...` hints in a `.py` file, and no warning on a real, hand-typed `# end def`.
- Settings > Plugins does show "Explicit Block Endings" listed and enabled, version `0.1.0` — so the plugin *is* installed and the IDE isn't outright rejecting it, but that only means the XML parsed and its `<depends>` were satisfied, not that every extension inside it actually registered or that the plugin's classes loaded without error.

Given none of the three features show any effect at all, the likely cause is something that keeps the whole plugin from properly initializing, or a plugin.xml issue that's still wrong in a way that doesn't block "enabled" status. The user explicitly asked for a startup notification as an unambiguous "did this plugin actually initialize" signal, which doubles as the fastest way to disambiguate "plugin never really loads" from "plugin loads, but each individual feature is still misregistered."

## Suspected causes to fix

1. **`until-build=""` — confirmed NOT the bug, fix anyway as cleanup.** Researched against intellij-community source: XML parser calls `getNullifiedAttributeValue`, which turns an empty/whitespace `until-build` into `null` at parse time — identical to omitting the attribute. `BuildNumber.fromString("")` also returns `null`, and the compatibility checker only rejects a plugin when `untilBuild` is non-null AND below the IDE's build number. So this can't be why the plugin shows zero effect. (It only trips the separate JetBrains Marketplace upload verifier, irrelevant to local "install from disk".) Still worth fixing for hygiene: in `build.gradle.kts`, set `untilBuild = provider { null }` instead of feeding it the blank `pluginUntilBuild` Gradle property, and delete the empty `pluginUntilBuild =` line from `gradle.properties`.

2. **Check `idea.log` for a plugin load/init exception.** Since `until-build` is ruled out and Settings > Plugins shows it "enabled" with no warning, but literally none of the three features work, the next most likely explanation is an exception during plugin class-loading or extension instantiation (e.g. missing Kotlin runtime dependency, wrong Kotlin stdlib version bundled, a class-not-found on one of `EndCommentPassFactory`/`RedundantEndCommentInspection`/`EndCommentSettingsState`/`EndCommentConfigurable`). Ask the user to open **Help > Show Log in Files/Explorer** (or check `idea.log` directly) and search for `de.luckydonald.endifcomments` or `PluginException` around the time the IDE started, before assuming everything is fine at the XML level.

3. **Add a startup notification** so "did the plugin actually load and run its code" becomes directly observable, independent of the Settings page / inspection / inlay separately working or not:
   - A `ProjectActivity` (`com.intellij.openapi.startup.ProjectActivity`, registered via `<postStartupActivity implementation="..."/>` in `plugin.xml`) that shows a one-time balloon notification ("Explicit Block Endings loaded") via `NotificationGroupManager`/`Notification`, on project open.
   - Register a `<notificationGroup id="..." displayType="BALLOON"/>` for it (required since newer platform versions no longer auto-register ad-hoc notification groups).
   - This is a temporary diagnostic — note in the plan (and mention to the user) that once the real issue is confirmed fixed, this notification should be removed again; don't leave permanent noisy startup UI in the shipped plugin.

4. **Re-audit every remaining `plugin.xml` extension tag** the same way the `highlightingPassFactory` typo was found — confirm `localInspection`, `applicationService`, `applicationConfigurable` are exactly right (spot-checked already against real JetBrains examples and they match), and double check the `applicationConfigurable`'s missing `groupId`/`parentId` really does land it under "Other Settings" rather than simply not showing anywhere obvious — this was an assumption in the original plan, not something actually verified against real IDE behavior.

5. **Add automated IDE UI test (`intellij-ui-test-robot`, remote-robot).** Per `ai/references/https/github.com/JetBrains/intellij-ui-test-robot/.../README.md`, this replaces "user manually checks Settings/Inspections/editor" with a scripted, repeatable check — also doubles as the long-term regression test the debug session lacked.
   - Add a `uiTest` source set / module with `testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")` and `testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")`.
   - Configure `runIdeForUiTests { systemProperty "robot-server.port", "8082" }` and `downloadRobotServerPlugin { version = "0.11.23" }` in `build.gradle.kts`, with the useful launch properties from the README (`jb.consents.confirmation.enabled=false`, `idea.trust.all.projects=true`, `ide.show.tips.on.startup.default.value=false`) to keep the run headless-friendly/non-interactive.
   - Write one UI test that: launches with our plugin installed, opens a `.py` file with an `if`/`def` block, asserts the virtual `# end ...` inlay hint text is present via `findAllText()`/`hasText`, opens Settings and asserts the "Explicit Block Endings" page (`applicationConfigurable`) is findable by XPath, and asserts the "Redundant explicit block-ending comment" inspection is registered/listed.
   - Add a `./gradlew runIdeForUiTests & ./gradlew uiTest` (or equivalent Gradle task wiring) step to `DEVELOPER.md`, and add it as a CI job on push/PR alongside the existing `check`/`buildPlugin` jobs — this is exactly the kind of "installed plugin has zero effect" regression this whole debug session was triggered by, so it should run automatically going forward rather than relying on manual user testing.
   - Use this same UI test locally, right after the fixes above, as the decisive check instead of (or in addition to) asking the user to manually click through Settings — it can assert the inlay/inspection/settings page all in one run.

## Verification

- Before rebuilding: ask user check `idea.log` now (current install) for `de.luckydonald.endifcomments` / `PluginException` — cheap, might already show root cause without a rebuild cycle.
- Rebuild (`./gradlew buildPlugin`), reinstall from disk, restart the IDE.
- Confirm the "Explicit Block Endings loaded" notification appears on project open — if it doesn't, the problem is upstream of all three features (plugin isn't initializing at all; check `idea.log` for a startup exception mentioning our plugin id) and the `until-build` fix should be re-checked first.
- Once the notification confirms real initialization, check the three original symptoms again: Settings > Other Settings, Editor > Inspections listing, and virtual hints in a `.py` file with an `if`/`def`/etc. block, plus the warning on a real `# end def`.
- Once confirmed working, remove the temporary startup notification in a follow-up commit.
