# Make end-comment detection case-insensitive + configurable visual style

## Context

The plugin currently has exactly one hardcoded format for `# end <keyword>` markers, used both to detect
real hand-written markers (`END_COMMENT_LINE_REGEX` in `EndCommentScanner.kt`) and to render/flag them
(`EndCommentInlayRenderer` for the fake virtual inlay, `RedundantEndCommentInspection` for flagging real
ones as redundant). Two things need to change:

1. **Detection must become case-insensitive.** Today `END_COMMENT_LINE_REGEX` only matches lowercase
   `end` and lowercase keywords, so `# EnD clASS foBaR 'n stuff` is not recognized as a real marker. The
   textual format itself (`# end <keyword>`) stays fixed — this is purely a matching-strictness fix, not
   a new user-facing option.
2. **Visual presentation must become configurable**, in two independent places:
   - The fake-inserted virtual inlay (`EndCommentInlayRenderer`) — currently always plain, comment-colored
     text with no italic/bold/strikethrough/underline and no way to pick a custom color.
   - The redundant-comment inspection warning on real hand-written markers
     (`RedundantEndCommentInspection`) — currently hardcoded to `ProblemHighlightType.LIKE_DEPRECATED`
     (strikethrough).

Both need to move from hardcoded to reading a new `EndCommentSettingsState`, exposed through
`EndCommentConfigurable`, following the existing pending-var / `isModified` / `apply` / `reset` pattern
already used for `isActive`.

## 1. Case-insensitive detection

In `src/main/kotlin/de/luckydonald/endifcomments/model/EndCommentScanner.kt:20-21`, add
`RegexOption.IGNORE_CASE` to `END_COMMENT_LINE_REGEX`:

```kotlin
val END_COMMENT_LINE_REGEX: Regex =
    Regex("""^#\s*end\s+(${ALL_END_KEYWORDS.joinToString("|")})\b.*$""", RegexOption.IGNORE_CASE)
```

This regex is the single source of truth used by both `EndCommentScanner.findRealEndComment` and
`RedundantEndCommentInspection`, so this one change covers both call sites. Generation
(`EndCommentScanner.collectMarkers`, `markerFor`) keeps emitting lowercase `# end <keyword>` — only
matching becomes lenient.

Add a test fixture (e.g. `testData/mixedCaseComment.py`) with a comment like `# EnD clASS foBaR 'n stuff`
and a corresponding test in `EndCommentPluginTest.kt` asserting it is recognized as a real marker (i.e.
`findRealEndComment` finds it / the virtual inlay is suppressed for that block), mirroring the existing
`testWrongFormEndCommentIsAlsoFlagged` test.

## 2. New settings fields

In `src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentSettingsState.kt`, add fields
alongside the existing `isActive: Boolean`:

```kotlin
var inlayItalic: Boolean = false
var inlayBold: Boolean = false
var inlayStrikethrough: Boolean = false
var inlayUnderline: Boolean = false
var inlayColorSource: String = "THEME"       // "THEME" | "CUSTOM"
var inlayCustomColorRgb: Int = 0x808080
var redundantHighlightType: String = "LIKE_DEPRECATED"
```

`XmlSerializerUtil.copyBean` already handles arbitrary bean properties, so persistence needs no extra
plumbing beyond adding these `var`s. `redundantHighlightType` stores the name of one of a fixed preset
list of `com.intellij.codeInspection.ProblemHighlightType` values (see section 4).

## 3. Configurable UI

In `src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentConfigurable.kt`, extend the existing
pending-var pattern: one `pendingXxx` var per new field, mirrored in `isModified()`, `apply()`, `reset()`.
Add UI rows (Kotlin UI DSL) grouped logically, e.g.:

```kotlin
group("Virtual End-Comment Style") {
    row { checkBox("Italic").bindSelected(...) }
    row { checkBox("Bold").bindSelected(...) }
    row { checkBox("Strikethrough").bindSelected(...) }
    row { checkBox("Underline").bindSelected(...) }
    row {
        comboBox(listOf("Theme line-comment color", "Custom color")).bindItem(...)
        cell(ColorPanel()).bindColor(...) // only enabled/visible when "Custom color" selected, via visibleIf/enabledIf
    }
}
group("Redundant Comment Warning Style") {
    row {
        comboBox(listOf("Strikethrough", "Grayed out", "Warning underline", "Weak warning")).bindItem(...)
    }
}
```

### Live previews

Add a small preview under each group so the user sees the effect without opening a real editor:

- **Inlay preview**: a `JLabel`-based custom `JComponent` (reuse the same painting logic as
  `EndCommentInlayRenderer.paint`, factored into a shared helper so there's one source of truth for
  "how do we paint a styled end-comment string") showing a fixed sample string, e.g. `# end if`, styled
  live with the current *pending* (not-yet-applied) checkbox/combo values. Wrap it in a `row { cell(...) }`
  placed directly below the style controls, and repaint it from each control's `onChanged`/listener so it
  updates as the user toggles options, before hitting Apply/OK.
- **Redundant-warning preview**: IntelliJ doesn't expose `ProblemHighlightType` rendering outside a real
  editor easily, so approximate it: a small non-editable `EditorTextField`/`Editor` snippet (or, simpler,
  a `JLabel` with matching Swing text attributes — strikethrough/underline/grayed-out font style can all
  be approximated with `Font`/`AttributedString` the same way as the inlay preview) showing e.g.
  `# end class` styled per the selected preset. Exact rendering doesn't need pixel-parity with the real
  daemon-driven inspection highlight — it's a settings-panel preview, so an `AttributedString`-based
  `JLabel` (using `TextAttribute.STRIKETHROUGH`/`UNDERLINE`/reduced alpha for "grayed out"/wavy-underline
  approximation for warnings) is sufficient and keeps this self-contained in Swing without spinning up a
  real `Editor`/inspection pipeline inside the settings dialog.

Both previews live entirely in `EndCommentConfigurable` (or a small private helper class next to it) and
read from the panel's pending/local state, not from the persisted `EndCommentSettingsState` — so they
reflect unsaved edits immediately, consistent with how `panel { }` bindings normally behave before
`apply()` is called.

Map the combo box's human labels to `ProblemHighlightType` enum names / the `inlayColorSource` string
values internally (a small `Map` or `enum class` local to the configurable, not persisted directly as
display text).

## 4. Wire style into rendering

**`EndCommentInlayRenderer`** (`src/main/kotlin/de/luckydonald/endifcomments/inlay/EndCommentInlayRenderer.kt`):
add constructor parameters for the style (italic, bold, strikethrough, underline, resolved `Color?` for
custom override). In `EndCommentPass.doCollectInformation` (or `doApplyInformationToEditor`, where the
renderer is constructed at line 54), read `EndCommentSettingsState.getInstance()` once and pass the
resolved style down — do not re-read settings inside `paint()` on every repaint.

In `paint()`:
- Font: `scheme.getFont(EditorFontType.PLAIN)`, then `.deriveFont(style)` where `style` combines
  `Font.ITALIC`/`Font.BOLD` bit flags based on the booleans.
- Color: if `inlayColorSource == "CUSTOM"`, use `Color(inlayCustomColorRgb)`; else keep current behavior
  (`DefaultLanguageHighlighterColors.LINE_COMMENT` foreground).
- Strikethrough/underline: Swing's `drawString` doesn't do these natively — after drawing the string,
  compute its width via `g.fontMetrics.stringWidth(text)` and draw a horizontal line at the appropriate
  y-offset (mid-height for strikethrough, just below baseline for underline), same color as the text.

**`RedundantEndCommentInspection`** (`src/main/kotlin/de/luckydonald/endifcomments/inspection/RedundantEndCommentInspection.kt:37`):
replace the hardcoded `ProblemHighlightType.LIKE_DEPRECATED` with a lookup from
`EndCommentSettingsState.getInstance().redundantHighlightType`, mapped via a small
`when`/`ProblemHighlightType.valueOf(...)` with a safe fallback to `LIKE_DEPRECATED` if the stored value
is somehow invalid (e.g. from an older settings file).

## Files touched

- `model/EndCommentScanner.kt` — regex `IGNORE_CASE` fix
- `settings/EndCommentSettingsState.kt` — new fields
- `settings/EndCommentConfigurable.kt` — new UI rows + pending vars + live previews for both style groups
- `inlay/EndCommentInlayRenderer.kt` — style-aware painting, with shared paint logic factored out so the
  settings-panel inlay preview can reuse it
- `inlay/EndCommentPass.kt` — resolve settings into style when constructing the renderer
- `inspection/RedundantEndCommentInspection.kt` — settings-driven `ProblemHighlightType`
- `src/test/kotlin/.../EndCommentPluginTest.kt` + new `testData/mixedCaseComment.py` — case-insensitivity test
- Optionally extend `testActiveSettingPersists`-style test coverage to the new settings fields

## Verification

- `./gradlew test` — existing + new unit tests (case-insensitive detection, settings persistence round-trip).
- Manually run the plugin in a sandbox IDE (`./gradlew runIde`), open a `.py` file, toggle each new style
  option in Settings > Other Settings > Explicit Block Endings, and confirm:
  - The virtual inlay updates its italic/bold/strikethrough/underline/color live.
  - A real `# end if` (or `# EnD If`) comment is now recognized case-insensitively and the redundant-comment
    inspection warning renders with the selected highlight style.
