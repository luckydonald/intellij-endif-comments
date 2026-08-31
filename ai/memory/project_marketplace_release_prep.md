---
name: project_marketplace_release_prep
description: Status of prepping intellij-endif-comments for its first JetBrains Marketplace release
metadata: 
  node_type: memory
  type: project
  originSessionId: 90fefb58-a728-4200-989f-960b10c0253e
  modified: 2026-08-31T11:40:03.787Z
---

As of 2026-08-21, the "Explicit Block Endings" plugin (`intellij-endif-comments`) had its Marketplace-release blockers closed in commit `52b358d` (plan `ai/plans/004_...`): GPLv3 `LICENSE` added, pink/violet `pluginIcon.svg`/`pluginIcon_dark.svg` added ([[user_visual_style_preference]]), `pluginVersion` pinned to `1.0.0`, `<change-notes>`/`CHANGELOG.md` added, the temporary startup-notification diagnostic (`StartupNotifier`, from plan 002) removed, and `publishPlugin` wired into `build.gradle.kts`/`.github/workflows/release.yml` behind a `JETBRAINS_MARKETPLACE_TOKEN` secret + `PUBLISH_TOKEN` env var.

**Why:** the plugin was functionally mature (case-insensitive detection, configurable styling, solid CI) but had never been tagged/published — README said "not yet published."

**How to apply:** what's still open is entirely on the user's side — create a JetBrains Marketplace vendor account, generate a publish token, add it as the `JETBRAINS_MARKETPLACE_TOKEN` GitHub repo secret, then commit/tag `v1.0.0` and push the tag to trigger the real publish. Don't push that tag proactively — it's an irreversible, externally-visible action the user must explicitly request.
