I'm debugging why an IntelliJ/PyCharm plugin never loads after "Install from disk" (no settings page appears, no inspection listed, no effect in editor, no startup notification would show either).

The built plugin's `META-INF/plugin.xml` (produced by the IntelliJ Platform Gradle Plugin 2.18.1's `patchPluginXml` task) contains:

```xml
<idea-version since-build="243" until-build="" />
```

The `until-build=""` came from a Gradle property `pluginUntilBuild =` (declared but left empty in `gradle.properties`), which the build script passes as `providers.gradleProperty("pluginUntilBuild")` into `intellijPlatform.pluginConfiguration.ideaVersion.untilBuild`.

I need you to research (via WebSearch/WebFetch only, this is a research task, no code changes) and report back:

1. Does IntelliJ's plugin compatibility checker treat a literal empty `until-build=""` attribute as "no upper bound" (same as omitting the attribute), or does it parse it as an actual (invalid/zero) build number that would make EVERY IDE version fail the compatibility check, silently disabling the plugin after install?
2. What is the correct way to leave `untilBuild` unbounded in the IntelliJ Platform Gradle Plugin 2.x DSL (`intellijPlatform.pluginConfiguration.ideaVersion { sinceBuild = ...; untilBuild = ... }`) — should `untilBuild` be omitted entirely / set to `provider { null }` / not called at all, rather than passed an empty-string Gradle property?
3. Look at how `IdeVersion`/`untilBuild` parsing works in intellij-community's `PluginManagerCore`/`BuildNumber` classes if you can find source — specifically what happens when `until-build` is an empty string vs missing.

Report a concise verdict (is this the bug or not) and the exact fix (what to change in build.gradle.kts/gradle.properties) in under 300 words.