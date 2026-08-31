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

Fix: stop tracking `gradle/wrapper/gradle-wrapper.jar` via LFS and store it as a normal git
blob, which is the standard/expected setup for Gradle wrapper jars (small, trusted, needed
verbatim by the wrapper-validation action and by `gradlew` bootstrap itself — no LFS smudge
step exists at that point in CI anyway).

## Changes

1. **`.gitattributes`** — add an override line after the existing `*.jar filter=lfs ...` rule
   (line 101) that un-LFSes this specific path (later patterns win in gitattributes):
   ```
   gradle/wrapper/gradle-wrapper.jar filter= diff= merge= text=
   ```
   Empty values explicitly clear the inherited LFS attributes for this one file, leaving all
   other `*.jar` files under LFS as before.

2. **Re-track the file as a plain blob:**
   ```
   git rm --cached gradle/wrapper/gradle-wrapper.jar
   git add gradle/wrapper/gradle-wrapper.jar
   ```
   With the attribute override now in place, `git add` will store the literal jar bytes instead
   of an LFS pointer.

3. Commit both changes together.

No changes needed to `release.yml` or the jar's own bytes — only how git tracks the file.

## Verification

- `git cat-file -p HEAD:gradle/wrapper/gradle-wrapper.jar | sha256sum` (after committing) should
  print `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`, not the pointer-file
  checksum.
- `git cat-file -s HEAD:gradle/wrapper/gradle-wrapper.jar` should report `47505` bytes, not ~130.
- `git check-attr -a gradle/wrapper/gradle-wrapper.jar` should no longer list `filter: lfs` /
  `diff: lfs` / `merge: lfs`.
- Optionally, actually exercising the CI fix requires pushing a `v*` tag (the workflow's only
  trigger) or a manual `gh workflow run` — flag this to the user rather than doing it
  automatically, since it's a release-triggering action.
