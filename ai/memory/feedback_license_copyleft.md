---
name: feedback_license_copyleft
description: "User wants copyleft (GPL-family) licenses, not permissive ones, when open-sourcing a project"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 90fefb58-a728-4200-989f-960b10c0253e
  modified: 2026-08-31T11:39:50.298Z
---

When asked to pick a license and the user says something like "keep it open when doing things with it," they mean copyleft, not merely "open source." Default to GPLv3 (or similar), not MIT/Apache/BSD.

**Why:** on `intellij-endif-comments`, I first defaulted to MIT for "open source, permissive" during a Marketplace-release plan. User corrected: "No licence must be to keep it open when doing things with it" — they want downstream forks/derivatives to stay open, which permissive licenses don't enforce. Switched to GPLv3.

**How to apply:** for any future licensing decision for this user, propose GPL-family licenses first; only use MIT/permissive if they explicitly ask for permissive/no-copyleft.
