## Verdict

**This is not the bug.** A literal `until-build=""` is parsed and treated identically to omitting the attribute — it does **not** make every IDE version fail compatibility.

## Evidence from intellij-community source (current master)

1. **XML parsing nullifies empty attributes.** In `platform/pluginSystem/parser/impl/.../XmlReader.kt`:
   ```kotlin
   PluginXmlConst.IDEA_VERSION_UNTIL_ATTR -> builder.untilBuild = getNullifiedAttributeValue(reader, i)
   ```
   and in `XmlReadUtils.kt`:
   ```kotlin
   fun getNullifiedAttributeValue(reader, i) = reader.getAttributeValue(i).trim().takeIf { !it.isEmpty() }
   ```
   An empty (or whitespace-only) string becomes `null` at parse time — same as if `until-build` weren't present.

2. **Even if it weren't nullified, `BuildNumber.fromString("")` returns `null`** (`BuildNumber.java`: `if (version.isEmpty()) return null;`).

3. **The compatibility check only fails on a non-null, too-low build number** (`PluginCompatibilityUtils.checkBuildNumberCompatibility`):
   ```kotlin
   val untilBuild = descriptor.getUntilBuild()
   if (untilBuild != null) {
       val untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null)
       if (untilBuildNumber != null && untilBuildNumber < ideBuildNumber) { ... violation ... }
   }
   ```
   Since `untilBuild` is `null` after parsing, this block is skipped entirely — no violation, no silent disable.

## Note on the Marketplace verifier (unrelated)

`JetBrains/intellij-platform-plugin-template#480` shows the *Marketplace "Verify Plugin" upload job* rejecting an empty `until-build` as malformed, wanting `*` or a real build number instead. That only affects marketplace validation/publishing, not local IDE loading of a sideloaded plugin — irrelevant to "Install from disk" failing.

## Recommended fix anyway (best practice, IJ Platform Gradle Plugin 2.x)

Leave `untilBuild` truly unset rather than an empty Gradle property:
```kotlin
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }   // don't call untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
}
```
And delete the empty `pluginUntilBuild =` line from `gradle.properties` (or give it a real value/`*`). This avoids ambiguity and marketplace-verifier complaints, but won't fix your load failure — look elsewhere (idea.log for plugin load errors, `since-build="243"` vs actual IDE build, plugin id/dependency errors, or a build/packaging issue).