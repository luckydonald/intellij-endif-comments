# IntelliJ/PyCharm plugin: virtual `# end if` block-end hints

## Context

The `code-style` skill (`ai/skills/code-style/references/py.md`) mandates writing a literal `# end if` / `# end with` / ... comment after every indented Python block, so readers can tell which block a dedent closes without counting indentation. Typing and maintaining these by hand in every file is friction, and other developers who don't use this convention would find them confusing if they show up as real text. The goal is an IntelliJ Platform plugin that renders these markers as IDE-only virtual annotations — never written into the actual file — the same way parameter-name hints or inferred-type hints are virtual overlays PyCharm draws into the editor.

Rendering must look like the skill's real example: a full virtual **line**, indented to match the opening statement, appearing right after the block's last real line, not text appended to the end of an existing line. That style isn't supported by the modern "declarative" Inlay Hints API (inline / end-of-line hints only); it requires the lower-level block-inlay API (`InlayModel.addBlockElement` + `EditorCustomElementRenderer`).

## Skill doc gap to close first

`py.md`'s "Explicit block endings" list is missing two indent-causing Python constructs. Add them, following the existing "close a whole chain with one comment" rule used for `if`/`elif`/`else`:

- `try` / `except` / `else` / `finally` → `# end try` (one comment closing the whole chain).
- `match` / `case` → the last `case` block gets its own `# end case` (otherwise its dedent back to the `match` level looks unmarked), and the whole `match` statement additionally gets `# end match` right after it. Two stacked comments, unlike the single-comment `if`/`try` chains, because each `case` body is its own indent scope nested inside `match`'s scope — the same "two blocks end at the same source point" case as the skill's `with`-inside-`if` example.

Edit `ai/skills/code-style/references/py.md`: add these two rows to the bullet list (§ line 26-33) and extend the code example if useful. The plugin's keyword table must match this doc exactly, so do this edit before/alongside writing the provider.

Also add an explicit bad-example next to the existing "Use only the block type in the comment. Do not repeat a function or class name." rule (§ line 33), e.g. showing `# end def foobar` and `# end class SomeClass` as wrong, `# end def` / `# end class` as right. This documents the exact wrong-form the plugin's new inspection (below) should flag and offer to fix.

## Project scaffold

New Gradle/Kotlin IntelliJ Platform plugin at the repo root (or a subdirectory, e.g. `plugin/`, if we don't want Gradle files at repo root next to the `ai/` tooling — confirm during setup):

- Base on the `JetBrains/intellij-platform-plugin-template` layout: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `src/main/kotlin`, `src/main/resources/META-INF/plugin.xml`.
- Use IntelliJ Platform Gradle Plugin 2.x (`intellijPlatform { }` DSL).
- `build.gradle.kts` dependencies block:
  ```kotlin
  dependencies {
    intellijPlatform {
      pycharm("<version>")       // or intellijIdeaCommunity + bundledPlugin, either works since PythonCore is bundled in CE/Professional/Ultimate-with-Python-plugin alike
      bundledPlugin("PythonCore")
    }
  }
  ```
  Only depend on `PythonCore` (not `Pythonid`) — it's the plugin actually bundled inside PyCharm CE, PyCharm Professional, and IntelliJ Ultimate-with-Python-plugin, so this covers all three without needing Professional-only APIs.
- `plugin.xml`: `<depends>com.intellij.modules.python</depends>`.

## Core implementation

1. **Keyword table** — a single lookup covering the 8 constructs from the (updated) skill doc: `PyIfStatement → "if"`, `PyWithStatement → "with"`, `PyForStatement → "for"`, `PyWhileStatement → "while"`, `PyFunction → "def"`, `PyClass → "class"`, `PyTryExceptStatement → "try"`, `PyMatchStatement → "match"`, `PyCaseClause → "case"` (exact PSI class names to be confirmed against the bundled `PythonCore` sources when writing the code — Python plugin PSI interfaces live in package `com.jetbrains.python.psi`).
   - For `if`/`elif`/else and `try`/`except`/`else`/`finally`, anchor the hint to the end of the *whole* statement (covers all branches) — one `# end if` / `# end try`, not one per branch.
   - For `match`, additionally emit `# end case` for the **last** `case` clause only (its own block ends there), stacked with `# end match` right underneath it — see placement rule below.

2. **Renderer** — an `EditorCustomElementRenderer` implementation that paints one line of text (e.g. `# end if`) using the editor's comment/inlay-hint color scheme attributes, left-padded to the same column as the opening statement's indentation.

3. **Placement** — for each matched statement, compute the offset just after its last line and call `editor.inlayModel.addBlockElement(offset, relatesToPrecedingText = true, showAbove = false, priority, renderer)`. When several blocks end on the same source line (e.g. the skill's own example: `with` and `if` both close after `await connection.process()`), stack multiple block elements in nesting order (innermost first) so the rendering matches the skill's example exactly.

4. **Recomputation** — implement the scan as a `TextEditorHighlightingPass` (via a `TextEditorHighlightingPassFactory`/`TextEditorHighlightingPassFactoryRegistrar`) so the platform automatically reruns it on every document/PSI change, the same mechanism the daemon uses for other inline annotations. On each run, diff the newly computed set of (offset, text) against the inlays already present in that range and only add/dispose the difference, to avoid flicker.

   Reference implementation for this exact pass-factory + diffing pattern (register pass, collect in `doCollectInformation`, diff old vs new by sorted offset comparison in `doApplyInformationToEditor`, dispose stale, add missing): the rainbow-brackets plugin's `RainbowIndentsPassFactory.kt` / `RainbowIndentsPass.kt` (`~/git/izhangzhihao/intellij-rainbow-brackets/src/main/kotlin/.../lite/indents/`). Note it uses `MarkupModel.addRangeHighlighter` + `CustomHighlighterRenderer` to paint *inside* existing lines (indent guides) — that part doesn't apply here since we need real block inlays (`InlayModel.addBlockElement` + `EditorCustomElementRenderer`) that reserve extra vertical space for a whole new virtual line. Only the pass-registration and old/new diffing structure carries over.

5. Register the pass/provider only for Python files (`PythonFileType.INSTANCE` / `PythonLanguage.INSTANCE`).

6. **Real-comment detection** — before adding a virtual hint for a matched statement, check whether a real trailing comment already occupies that spot in the source (a `PsiComment` on the line right after the block, at the same indentation, matching `# end <keyword>` — allowing the wrong-form variants like `# end def foobar` too). If found:
   - Skip adding the virtual block inlay for that statement (the real comment already does the job; showing both would double up).
   - Feed that comment into the inspection below instead of (or in addition to) the inlay logic.

## New inspection: flag real `# end …` comments for removal

Since the plugin now renders these virtual, a real `# end if` (or any real `# end <keyword>[ ...]`) typed into the source is redundant and should be flagged, not silently left alone:

- Implement a `LocalInspectionTool` (registered via `com.intellij.localInspection` in `plugin.xml`) that visits `PsiComment`s in Python files, matches them against the same keyword table used for the virtual hints (including the wrong-form variants like `# end def foobar`), and reports a warning when a comment sits exactly where a block-end marker belongs.
- Provide a quick-fix (`LocalQuickFix`) that deletes the comment (and, if the comment was on its own line with nothing else, the whole line including its trailing newline) via a `WriteCommandAction`, so `# end if` never needs to be hand-maintained once the plugin covers that block.
- This inspection is the one place in this plugin allowed to modify real file text — it deletes a redundant marker, it never inserts one. That's consistent with the "never write the marker into the file" goal: the plugin only removes stray real markers and replaces them with the virtual overlay.
- "Fix all problems of this type in file" is IntelliJ's own built-in grouping, not something to implement by hand: as long as the `LocalQuickFix` returns a consistent, non-parameterized `getFamilyName()`, the platform automatically offers "Fix all '<inspection name>' problems in file" from the Alt+Enter menu, and lets multiple matches be selected and applied together from the Problems tool window / batch inspection results. Just verify this shows up once the inspection exists — no extra plumbing needed.

## Settings: Active toggle

In scope for v1: a settings page under **Settings > Other Settings** with a single "Active" checkbox that enables/disables the whole plugin (both the virtual hints and the inspection).

- A lightweight application-level `PersistentStateComponent` service (e.g. `EndCommentSettingsState`) holding `var isActive: Boolean = true`, persisted to its own XML file via `@State`/`@Storage`.
- A `Configurable` (registered via `<applicationConfigurable>` in `plugin.xml`, no `groupId`/`parentId` pointing at a known top-level group) with a single checkbox bound to that service's `isActive`. Leaving `groupId` unset is what makes the IDE place it under the synthetic "Other Settings" bucket — confirm this placement when wiring it up.
- Both the `TextEditorHighlightingPass` (step 4 above) and the `LocalInspectionTool` (previous section) must check `isActive` early and no-op (no inlays added, no problems reported) when it's off.

## Out of scope for v1 (call out, don't build)

- Any settings beyond the single "Active" checkbox (e.g. per-keyword toggles, color customization) — can follow later.
- Any code path that *inserts* the literal `# end …` comment into the file — explicitly not wanted. The new inspection above only *removes* redundant real comments; it must never write one.

## Automated tests

Use the IntelliJ Platform Test Framework the plugin template already wires up (JUnit4 + `BasePlatformTestCase`, `src/test/kotlin` + `src/test/testData`):

- One test fixture per keyword/scenario: `.py` source under `src/test/testData/` for plain `if`/`with`/`for`/`while`/`def`/`class`/`try`/`match` blocks, the skill's nested `with`-inside-`if` stacking example, a `match` with 2+ `case`s, and a file containing a pre-existing real `# end if` and a wrong-form `# end def foobar`.
- For each fixture, `myFixture.configureByFile(...)` then assert on `myFixture.editor.inlayModel.getBlockElementsInRange(0, document.textLength)`: the right count, text, indentation column, and stacking order of hints — this is the same fixture/editor access other platform inlay tests use, no UI test framework needed since block inlays are inspectable directly from the model.
- A test asserting the file's text is byte-identical before/after the pass runs (covers the "never writes to the file" requirement).
- A test driving the `LocalInspectionTool` via `myFixture.enableInspections(...)` + `myFixture.checkHighlighting()` / `myFixture.getAllQuickFixes()` for the real-comment fixture, applying the quick-fix and asserting the resulting text.
- Run via `./gradlew check` (also what CI below calls).

## CI pipeline

Reuse the GitHub Actions workflows already scaffolded by `intellij-platform-plugin-template` (it ships `build.yml`/`release.yml` by default) rather than writing new ones from scratch:

- **Test workflow** (`build.yml`-equivalent), triggered on every push/PR: run `./gradlew check` (compiles + unit tests from above) and `./gradlew verifyPlugin` (IntelliJ Plugin Verifier, catches API-compatibility issues against the target IDE versions declared in `build.gradle.kts`).
- **Build workflow** (`release.yml`-equivalent), triggered on pushed version tags (e.g. `v*`): run `./gradlew buildPlugin` and upload the resulting ZIP from `build/distributions/` as a workflow/release artifact (a GitHub Release attached to that tag). Skip wiring up the template's Marketplace-publish step unless/until there's an actual public release to ship — tag-triggered builds just need to produce a downloadable artifact for now.

## Docs: root `README.md` (user-facing) + root `DEVELOPER.md` (build/dev instructions)

**`README.md`** — user-facing, describes the plugin rather than how to hack on it (audience is a Python dev deciding whether to install it and how to use it, not a total beginner, but not a build-tooling doc either):

- What it does: shows the `# end if`/`# end with`/... block-end markers from the `code-style` skill's convention as virtual, IDE-only annotations — never written into the file — so nested Python blocks stay readable without hand-maintaining real comments.
- What it looks like (a short before/after or screenshot-style example, reusing the skill's nested `if`/`with` sample).
- What happens if you already have a real `# end if` comment: it's flagged as redundant with a quick-fix to remove it, since the plugin now shows it virtually.
- Where the settings live: **Settings > Other Settings** → the plugin's page, with the "Active" checkbox to turn it on/off.
- Installation: link to the JetBrains Marketplace listing once published; until then, point to `DEVELOPER.md` for building from source.
- A line at the bottom linking to `DEVELOPER.md` for building/testing/contributing.

**`DEVELOPER.md`** — the build/compile/load-into-PyCharm/test instructions from the previous section (build, run in sandbox IDE, install from disk into a real PyCharm, run tests, one-time setup). Referenced from `README.md`, not duplicated there.

## Verification

- `./gradlew buildPlugin` succeeds.
- `./gradlew check` passes (unit tests above, including the inlay-content, no-file-mutation, and inspection/quick-fix tests).
- `./gradlew runIde` launches a sandbox PyCharm; open a Python file with nested `if` / `with` / `for` / `while` / `def` / `class` / `try` / `match` blocks (mirror the skill's example plus a `try`/`except` and a `match`/`case` with 2+ cases) and confirm:
  - Each virtual `# end …` line renders at the correct indentation and nesting order.
  - The `match` case specifically shows both `# end case` (last case only) and `# end match` stacked.
  - Hints disappear/update live as code is edited.
  - The file is never modified by the hint rendering itself (diff/save it and confirm it's unchanged).
- Type a real `# end if` (and a wrong-form `# end def foobar`) into a test file and confirm: the virtual inlay for that block is suppressed, the inspection reports a warning on the real comment, and its quick-fix removes the comment (and its now-empty line) without touching anything else in the file.
- With several flagged comments in one file, confirm Alt+Enter on any one offers "Fix all '<inspection name>' problems in file", and that it removes all of them in one step.
- Open **Settings > Other Settings**, confirm the plugin's page appears there with the "Active" checkbox, and that unchecking it removes all virtual hints and inspection warnings from open Python files (and re-checking restores them).
