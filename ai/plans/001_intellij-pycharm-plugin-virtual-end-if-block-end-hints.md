# IntelliJ/PyCharm plugin: virtual `# end if` block-end hints

## Context

The `code-style` skill (`ai/skills/code-style/references/py.md`) mandates writing a literal `# end if` / `# end with` / ... comment after every indented Python block, so readers can tell which block a dedent closes without counting indentation. Typing and maintaining these by hand in every file is friction, and other developers who don't use this convention would find them confusing if they show up as real text. The goal is an IntelliJ Platform plugin that renders these markers as IDE-only virtual annotations — never written into the actual file — the same way parameter-name hints or inferred-type hints are virtual overlays PyCharm draws into the editor.

Rendering must look like the skill's real example: a full virtual **line**, indented to match the opening statement, appearing right after the block's last real line, not text appended to the end of an existing line. That style isn't supported by the modern "declarative" Inlay Hints API (inline / end-of-line hints only); it requires the lower-level block-inlay API (`InlayModel.addBlockElement` + `EditorCustomElementRenderer`).

## Skill doc gap to close first

`py.md`'s "Explicit block endings" list is missing two indent-causing Python constructs. Add them, following the existing "close a whole chain with one comment" rule used for `if`/`elif`/`else`:

- `try` / `except` / `else` / `finally` → `# end try` (one comment closing the whole chain).
- `match` / `case` → the last `case` block gets its own `# end case` (otherwise its dedent back to the `match` level looks unmarked), and the whole `match` statement additionally gets `# end match` right after it. Two stacked comments, unlike the single-comment `if`/`try` chains, because each `case` body is its own indent scope nested inside `match`'s scope — the same "two blocks end at the same source point" case as the skill's `with`-inside-`if` example.

Edit `ai/skills/code-style/references/py.md`: add these two rows to the bullet list (§ line 26-33) and extend the code example if useful. The plugin's keyword table must match this doc exactly, so do this edit before/alongside writing the provider.

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

## Out of scope for v1 (call out, don't build)

- A Settings > Editor > Inlay Hints toggle entry (nice-to-have, can follow once the core rendering works).
- Any real-text insertion/quick-fix that would write the literal comment into the file — explicitly not wanted per the request.

## Verification

- `./gradlew buildPlugin` succeeds.
- `./gradlew runIde` launches a sandbox PyCharm; open a Python file with nested `if` / `with` / `for` / `while` / `def` / `class` / `try` / `match` blocks (mirror the skill's example plus a `try`/`except` and a `match`/`case` with 2+ cases) and confirm:
  - Each virtual `# end …` line renders at the correct indentation and nesting order.
  - The `match` case specifically shows both `# end case` (last case only) and `# end match` stacked.
  - Hints disappear/update live as code is edited.
  - The file is never modified (diff/save it and confirm it's unchanged).
