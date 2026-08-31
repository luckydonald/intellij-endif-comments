# Fix Gradle Wrapper Validation failure in Release workflow

## Context
`ai/errors/1.txt` shows the `.github/workflows/release.yml` job failing at the
`gradle/actions/setup-gradle@v4` step with:

```
✗ Found unknown Gradle Wrapper JAR files:
  307eca533bfc5c42b23f61492eb1cf32a0424ea0e3796b8ac1ff70a9dbd63f35 gradle/wrapper/gradle-wrapper.jar
Error: At least one Gradle Wrapper Jar failed validation!
```

**Root cause (confirmed):** `.gitattributes` line 101 (`*.jar filter=lfs diff=lfs merge=lfs -text`)
puts `gradle/wrapper/gradle-wrapper.jar` under Git LFS. `release.yml`'s `actions/checkout@v4`
step does not enable LFS, so CI checks out the raw ~130-byte LFS *pointer* text file instead of
the real 47505-byte jar. The Gradle Wrapper Validation action then hashes that pointer text —
`307eca533bfc5c42b23f61492eb1cf32a0424ea0e3796b8ac1ff70a9dbd63f35` is exactly
`sha256(<pointer file contents>)`, confirmed locally with `git cat-file -p HEAD:gradle/wrapper/gradle-wrapper.jar | sha256sum`.

The actual jar content (working tree, smudged locally by git-lfs) is byte-identical to the
official Gradle 9.7.0 wrapper jar downloaded from `raw.githubusercontent.com/gradle/gradle/v9.7.0/gradle/wrapper/gradle-wrapper.jar`
(`sha256: 7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`) — so the jar itself
is fine; it's only mis-tracked in git.

Fix (per user preference): keep `gradle-wrapper.jar` tracked via LFS as-is, and instead make
`actions/checkout@v4` in `release.yml` actually fetch LFS content, so CI checks out the real jar
bytes instead of the pointer text.

## Changes

**`.github/workflows/release.yml`** — add `lfs: true` to the `actions/checkout@v4` step's `with:`:

```yaml
- uses: actions/checkout@v4
  with:
    lfs: true
```

No changes to `.gitattributes` or the jar's own bytes — only the checkout step.

## Verification

- After committing, inspect the workflow file to confirm `lfs: true` is present on the checkout
  step.
- Exercising the actual CI fix requires pushing a `v*` tag (the workflow's only trigger) or a
  manual `gh workflow run` — flag this to the user rather than doing it automatically, since it's
  a release-triggering action. Once it runs, the `gradle/actions/setup-gradle@v4` step should no
  longer report an "unknown Gradle Wrapper JAR" — the checked-out jar's checksum will be
  `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`, matching the official
  Gradle 9.7.0 wrapper jar.
