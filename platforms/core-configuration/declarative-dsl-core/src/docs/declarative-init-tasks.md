# Declarative-only Gradle — task-specific global-config APIs (optional)

> Companion to `declarative-init.md` (which covers the *infrastructure* of declarative init/global files) and `research-init-scripts.md` (the use-case catalogue). This doc collects **specific global-config tasks** that init scripts handle today — repository enforcement, credentials — and explores *optional* first-class declarative APIs for them.
>
> **Why these are optional.** Each task can already be handled by an **imperative init-plugin applied from a declarative init file** (see `declarative-init.md` §1 — plugins referenced from the init file run at the `Gradle` instance, can use any existing Gradle API, and need no new declarative surface). So a declarative API here is a *convenience / inspectability / governance* improvement, **not** a requirement for the declarative-only model to work. The baseline is always "ship an init-plugin"; the declarative APIs below are the optional alternative that keeps these tasks expressible as inspectable data rather than opaque code.

## 1. Repository enforcement / override (optional declarative API)

**Baseline (no new API).** An imperative init-plugin applied from the init file can already enforce repositories using existing Gradle APIs (`dependencyResolutionManagement.repositoriesMode`, content filtering / `exclusiveContent`) plus custom logic for mirroring/redirect. The declarative `repositoryPolicy` below is the *optional* first-class alternative that keeps it inspectable and declarative.

Core move: **separate declaration from governance.** Builds/defaults *declare* repositories (data); a **policy** owned by the global/enterprise file declares *how resolution may use* them. Engine evaluates `declarations + policy → effective resolution` — no user code mutates a list, so the anti-declarative problem dissolves.

```kotlin
repositoryPolicy {                       // top-level in a global/init file; also legal in settings.gradle.dcl
    appliesTo(projects, plugins)         // governs project repos AND plugin/buildscript classpath (default both)
    enforce { maven { url = uri("https://nexus.mycorp.example/repository/maven-public") } }  // sole authoritative source
    allowOnly { host("nexus.mycorp.example"); host("repo.gradle.org") }                       // restrict to allow-list
    mirror { from(anyMavenRepository); to(uri("https://artifactory.example/remote-repos")) }  // rewrite declared → mirror (NEW)
    onViolation = fail                   // ignore | warn | fail
}
```

- `enforce` / `allowOnly` largely **surface existing Gradle** (`dependencyResolutionManagement.repositoriesMode` PREFER_SETTINGS / FAIL_ON_PROJECT_REPOS, content filtering / `exclusiveContent`) at global scope with enforce authority, **extended to the plugin classpath**. `mirror` (URL rewrite) is the **one genuinely new** capability (community `repository-mirrors` fills this gap today).
- `default` vs `enforce` reuses the defaults **precedence axis**; `onViolation` reuses the author-controlled skip/warn/fail pattern; matchers are a **closed vocabulary** (`host`/`urlPrefix`/type/`any`) — no arbitrary code.
- Sub-questions: mirror granularity (per-host vs per-group/artifact routing); merge semantics across multiple policy sources (enforce-wins? allow-lists intersect?); exact relationship to existing `repositoriesMode`/`exclusiveContent` (surface vs extend).
- **Architectural fork (OPEN — needs a decision):** how to anchor — (a) **dedicated `repositoryPolicy` concept** (separate governance; needed anyway for `mirror`/`allowOnly`); (b) **reuse the defaults precedence axis** (enforced repos = the `enforce` end of default-vs-enforce); (c) **hybrid** — precedence axis governs repo *declarations* (additive vs authoritative), a dedicated `repositoryPolicy` carries the genuinely-new rules (`mirror`/`allowOnly`).

## 2. Credentials & secrets (optional declarative API — OPEN design problem)

**Baseline (no new API).** Credential wiring is handled by an imperative init-plugin today (e.g. the gradle-credentials-plugin pattern: decrypt/resolve out of band, set credentials on repositories). A declarative reference mechanism would be the optional enhancement — but its shape is an open design problem.

How declarative files specify/reference credentials and secrets is deliberately left open. Constraints any solution should honor:
- Secrets are **never inlined** in declarative files; values are machine/user-scoped and provisioned out of band.
- Must cover today's init-script credential use cases: enterprise repo creds (Spring `repo.spring.io`, Artifactory), machine-specific secrets, and the gradle-credentials-plugin "decrypt stored credential" case (imperative work → in a plugin).
- Should integrate with Gradle's existing lazy/identified credentials, config-cache safety, and log/build-scan **redaction**.
- Sub-questions left open: reference-by-identity vs identity+source; the resolution-source chain; general secret providers beyond repository credentials; relationship to existing `<id>Username`/`<id>Password`.

## Open questions

1. **Repository enforcement** — the architectural fork above (dedicated `repositoryPolicy` vs reuse the defaults precedence axis vs hybrid) + the listed sub-questions. Or skip the declarative API entirely and rely on an imperative init-plugin.
2. **Credentials / secrets** — whole problem open (no concrete solution). Constraints only: never inline; machine-scoped; cover today's init-script cases; integrate with lazy credentials + redaction. Baseline remains an init-plugin.

## Status

Optional, task-specific declarative APIs — explicitly *not* required for declarative-only Gradle (an imperative init-plugin applied from a declarative init file covers each). Both entries remain open: the repository-policy anchor and the credentials mechanism. Infrastructure is in `declarative-init.md`.
