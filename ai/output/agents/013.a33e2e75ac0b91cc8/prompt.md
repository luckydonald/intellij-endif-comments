In this IntelliJ plugin repo (/home/user/git/luckydonald/intellij-endif-comments), I need to understand how "# end ..." style comments are detected and generated, and how settings currently work, in order to plan adding a settings option to change the style of the detected/inserted end comments.

Please read and summarize these files in full:
- src/main/kotlin/de/luckydonald/endifcomments/model/EndKeyword.kt
- src/main/kotlin/de/luckydonald/endifcomments/model/EndMarker.kt
- src/main/kotlin/de/luckydonald/endifcomments/model/EndCommentScanner.kt
- src/main/kotlin/de/luckydonald/endifcomments/inlay/EndCommentInlayRenderer.kt
- src/main/kotlin/de/luckydonald/endifcomments/inlay/EndCommentPass.kt
- src/main/kotlin/de/luckydonald/endifcomments/inlay/EndCommentPassFactory.kt
- src/main/kotlin/de/luckydonald/endifcomments/inspection/RedundantEndCommentInspection.kt
- src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentConfigurable.kt
- src/main/kotlin/de/luckydonald/endifcomments/settings/EndCommentSettingsState.kt
- src/main/kotlin/de/luckydonald/endifcomments/startup/StartupNotifier.kt

Also search the codebase for:
1. All places where a literal string like "# end" or "end " comment prefix/format is hardcoded (e.g. comment prefix characters like `#`, `//`, format strings for constructing the fake inlay end-comment text, e.g. "end <keyword>" vs "end: <keyword>" vs other variants).
2. Any existing regex used to detect end-comments (in EndCommentScanner or elsewhere), noting what styles/formats it currently accepts (e.g. "# end if", "#end if", "# end: if", capitalization, colon usage, etc).
3. Any existing tests related to this (search src/test and src/uiTest) that show example input/output of comment styles, to understand what variants matter.
4. The plugin.xml to see how settings/configurable are registered.

Report back:
- A full breakdown of the current comment style format(s) supported/generated, with exact code locations (file:line) and code snippets of the format strings/regexes.
- How EndCommentSettingsState is structured currently (what fields it has, persistence mechanism, e.g. PersistentStateComponent).
- How EndCommentConfigurable builds its UI (what UI framework: Swing forms / Kotlin UI DSL) and what settings it currently exposes.
- Where the settings state is read/injected into the scanner, renderer, and inspection (to know how a new "style" setting would need to be threaded through).
- Any language-specific comment prefix handling (Python "#" vs other languages "//" etc) since this affects what "style" means.

This is a read-only investigation — do not write or edit any files.