I have everything needed. Here's the full breakdown.

## Comment style format — current hardcoded state

Only one style exists today: `# end <keyword>`. The `#` prefix, the literal word `end`, and the single space are hardcoded in exactly two places, both Python-specific:

- `src/main/kotlin/de/luckydonald/endifcomments/model/EndCommentScanner.kt:34` — generation: `markers += markerFor(element, "# end $keyword", document)`
- `src/main/kotlin/de/luckydonald/endifcomments/model/EndCommentScanner.kt:40` — generation for the match/case special case: `markers += markerFor(lastCase, "# end case", document)`
- `src/main/kotlin/de/luckydonald/endifcomments/model/EndCommentScanner.kt:20-21` — detection regex:
  ```kotlin
  val END_COMMENT_LINE_REGEX: Regex =
      Regex("""^#\s*end\s+(${ALL_END_KEYWORDS.joinToString("|")})\b.*$""")
  ```
  This regex accepts: `#` then any/no whitespace, literal `end`, required whitespace, one of the keywords from `ALL_END_KEYWORDS` (in `EndKeyword.kt:20`: `if, with, for, while, def, class, try, match, case`) as a word-boundary match, then anything else on the line (so `# end def foobar` also matches — the "wrong form" case covered by `wrongFormComment.py` test data and `testWrongFormEndCommentIsAlsoFlagged`). It is case-sensitive (`end` must be lowercase) and does not accept a colon variant (`# end: if`) since `\s+` requires whitespace, not `:` directly after `end`.

No other file constructs or matches this text independently — `EndCommentInlayRenderer.kt` just paints whatever `text: String` it's given, `EndCommentPass.kt` just passes `marker.text` through, and `RedundantEndCommentInspection.kt:29` reuses the same shared `END_COMMENT_LINE_REGEX` for flagging real comments. So there is exactly one source of truth for both generation and detection: `EndKeyword.kt` (list of keywords) + `EndCommentScanner.kt` (format string and regex).

The `#` prefix is baked in as a literal character, not derived from any language-comment-prefix API — there is no abstraction for "line comment prefix of this language." The whole plugin is Python-only: `EndKeyword.kt` imports only `com.jetbrains.python.psi.*` types (`PyIfStatement`, `PyWithStatement`, `PyForStatement`, `PyWhileStatement`, `PyFunction`, `PyClass`, `PyTryExceptStatement`, `PyMatchStatement`, `PyCaseClause`), and `EndCommentPassFactory.kt:16` gates the whole highlighting pass on `file.language.isKindOf(PythonLanguage.INSTANCE)`. `plugin.xml:13` also declares `<depends>com.intellij.modules.python</depends>`, and the inspection's `<localInspection language="Python" .../>` (plugin.xml:19-27) is Python-scoped too. So "style" here only ever means the `#`-based marker text format, not a different comment-prefix per language — there's no other language support to vary the prefix for.

## Settings state

`src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentSettingsState.kt` — a single-field `PersistentStateComponent`:
```kotlin
@Service
@State(name = "EndCommentSettings", storages = [Storage("endCommentSettings.xml")])
class EndCommentSettingsState : PersistentStateComponent<EndCommentSettingsState> {
    var isActive: Boolean = true
    override fun getState(): EndCommentSettingsState = this
    override fun loadState(state: EndCommentSettingsState) { XmlSerializerUtil.copyBean(state, this) }
    companion object {
        fun getInstance(): EndCommentSettingsState =
            ApplicationManager.getApplication().getService(EndCommentSettingsState::class.java)
    }
}
```
Application-level service (not project-level), persisted to `endCommentSettings.xml` in the IDE config dir, registered in `plugin.xml:29-30` via `<applicationService serviceImplementation=".../EndCommentSettingsState"/>`. Only one field, `isActive: Boolean`, defaulting to `true`. Adding a new setting (e.g. a style enum/string) is just adding another `var` here — `XmlSerializerUtil.copyBean` handles arbitrary bean properties automatically, no extra plumbing needed for persistence itself.

## Configurable UI

`src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentConfigurable.kt` uses the modern Kotlin UI DSL (`com.intellij.ui.dsl.builder.panel`/`bindSelected`), not old Swing forms. Pattern:
```kotlin
class EndCommentConfigurable : Configurable {
    private val state = EndCommentSettingsState.getInstance()
    private var pendingIsActive = state.isActive
    override fun getDisplayName(): String = "Explicit Block Endings"
    override fun createComponent(): JComponent = panel {
        row {
            checkBox("Active").bindSelected({ pendingIsActive }, { pendingIsActive = it })
        }
    }
    override fun isModified(): Boolean = pendingIsActive != state.isActive
    override fun apply() { state.isActive = pendingIsActive }
    override fun reset() { pendingIsActive = state.isActive }
}
```
It follows the classic "pending local copy vs. persisted state, diff for `isModified`, copy across in `apply`/`reset`" pattern — a new style setting would need its own `pendingXxx` var mirroring this same three-method (`isModified`/`apply`/`reset`) pattern, plus a new UI row (e.g. `comboBox(...)` or a segmented control bound via `bindItem`). Registered in `plugin.xml:32-34` as `<applicationConfigurable instance=".../EndCommentConfigurable" displayName="Explicit Block Endings"/>` with no `groupId`/`parentId`, so it lands under Settings > Other Settings (per the class's own doc comment).

## Where settings are read/injected

`EndCommentSettingsState.getInstance().isActive` is read directly (no dependency injection layer) in exactly two places, both as a gate before doing any work:
- `EndCommentPass.kt:31` — `doCollectInformation`: `computedMarkers = if (EndCommentSettingsState.getInstance().isActive) EndCommentScanner.visibleMarkers(...) else emptyList()`
- `RedundantEndCommentInspection.kt:25` — `buildVisitor`: `if (!EndCommentSettingsState.getInstance().isActive) return PsiElementVisitor.EMPTY_VISITOR`

Neither `EndCommentScanner` nor `EndKeyword` nor `EndCommentInlayRenderer` currently reads settings at all — `EndCommentScanner.collectMarkers`/`markerFor` hardcode the `"# end $keyword"` string directly, and `END_COMMENT_LINE_REGEX` is a top-level `val` computed once at class-load time from the static `ALL_END_KEYWORDS` list, not from settings. To add a style option, both the marker-generation format string (in `EndCommentScanner.markerFor`/`collectMarkers`) and the detection regex (`END_COMMENT_LINE_REGEX`) would need to become functions of the settings state (or of a style enum passed in) rather than a compile-time constant — since `visibleMarkers`/`collectMarkers`/`findRealEndComment` are called from both `EndCommentPass` (which already reads settings) and directly from tests (`EndCommentPluginTest.kt`) without a settings state being threaded through explicitly today.

## Test coverage relevant to style variants

`src/test/kotlin/de/luckydonald/endifcomments/EndCommentPluginTest.kt` and its `testData/*.py` fixtures (`nested.py`, `matchcase.py`, `realComment.py`, `wrongFormComment.py`) only ever exercise the canonical `# end <keyword>` format — no alternate styles (colon, different prefix, capitalization) are tested anywhere. `testWrongFormEndCommentIsAlsoFlagged`/`wrongFormComment.py` (`# end def foobar`) is the only "variant" tested, and it's about trailing extra text, not a different marker style. `EndCommentUiTest.kt` (uiTest) similarly only checks `# end def` end-to-end via a real running IDE, plus verifies all `plugin.xml` extension points actually register. `testActiveSettingPersists` in `EndCommentPluginTest.kt:56-68` is the only settings-related test, covering only `isActive` — no precedent yet for testing a style setting.