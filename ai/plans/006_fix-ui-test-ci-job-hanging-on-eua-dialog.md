# Fix ui-test CI job hanging on EUA dialog

## Context
`ai/errors/2.txt` is the log from a failed `.github/workflows/test.yml` `ui-test` run. The sandbox IDE
launched by `runIdeForUiTests` never opens the `robot-server` port (`8082`), so the workflow's 60×5s
poll loop times out and the step exits with code 1.

The log shows why: right after startup, the IDE tries to show a modal End User Agreement /
Data Sharing dialog (`com.intellij.ide.gdpr.Agreements.showEndUserAndDataSharingAgreements` →
`AgreementUiBuilder.build` → `DialogWrapper.showAndGet`). Under `xvfb-run` in CI there is nobody to
click "Accept", so the dialog just sits there blocking the UI thread — the `SEVERE #c.i.d.LoadingState`
error and the repeating `AIPromoWindowAdvisor - Verdict calculation took too long` warnings are
downstream symptoms of the EDT being stuck on that dialog, not the root cause themselves.

`build.gradle.kts` already disables the separate "data sharing consent" dialog via
`-Djb.consents.confirmation.enabled=false`, but that property does not cover the EUA/privacy-policy
dialog — a second, distinct dialog gated by the platform's stored privacy-policy version. The standard
workaround (used in JetBrains' own UI-test CI configs) is to also pass
`-Djb.privacy.policy.text=<!--999.999-->`: this overrides the privacy-policy resource text with a
version comment high enough that the platform treats it as already-accepted, so the EUA dialog never
gets scheduled to appear at all.

## Fix
In `build.gradle.kts`, in the `runIdeForUiTests` task's `jvmArgumentProviders` block (around line 100-107),
add the missing system property next to the existing `jb.consents.confirmation.enabled` one:

```kotlin
jvmArgumentProviders += CommandLineArgumentProvider {
    listOf(
        "-Drobot-server.port=8082",
        "-Djb.consents.confirmation.enabled=false",
        "-Djb.privacy.policy.text=<!--999.999-->",
        "-Didea.trust.all.projects=true",
        "-Dide.show.tips.on.startup.default.value=false",
    )
}
```

## Verification
- Not runnable locally in a sandboxed headless X server here, so this can't be fully reproduced in
  this session. Recommend pushing/rerunning the `ui-test` GitHub Actions job and confirming:
  - `runIde.log` no longer shows `Agreements.showEndUserAndDataSharingAgreements` / `DialogWrapper.show`
    in the stack, and no repeated `AIPromoWindowAdvisor` stalls.
  - The `curl -sf http://127.0.0.1:8082` check succeeds well before the 5-minute timeout.
  - The subsequent `./gradlew uiTest --info` step runs against the now-reachable robot-server.
