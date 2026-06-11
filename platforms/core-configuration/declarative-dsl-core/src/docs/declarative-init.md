# Declarative-only Gradle — design discovery: global/init files & global code injection

> Companion to `research-init-scripts.md`. That document catalogues *why* init scripts are used in practice; this one explores *what would replace them* if Declarative Gradle becomes the only scripting language. It is a **design discovery** — drafts, decisions reached in discussion, and questions left explicitly open — not an implementation plan.

## Context

Premise under exploration: **Declarative Gradle (DCL) becomes the only scripting language in Gradle.** Declarative files contain only high-level public models ("definitions") — no imperative code. All low-level / "apply action" code lives in plugins. Groovy/Kotlin `init.gradle(.kts)` scripts are disallowed; a *declarative* init alternative may be allowed, plus a small number of sanctioned non-declarative injection paths.

Grounded in the catalogue of real init-script use cases in `research-init-scripts.md`, which splits into: **configure** (declarable → DSL), **introspect** (IDEs/scanners → tool-owned), **instrument** (profilers/CI → tool-owned). Only *configure* needs a declarative home; introspect/instrument stay imperative and move to plugins + tool channels.

## Principles

- Declarative files hold only **definitions (data)** + **references to plugins (code)**. No apply-action code is ever inlined.
- An init/global file contributes two distinct kinds of thing:
    - **defaults** = data (definition values), keyed/applied as today;
    - **build logic** = code = *referenced* plugins whose apply actions the build loads and runs.

## 1. Declarative init file — how it references code

- Lives in a Gradle-User-Home location discovered like today's `init.d/` (`*.init.gradle.dcl`); also an ad-hoc CLI form (see §3).
- Same surface as settings: `pluginManagement { repositories { } }` → `plugins { id("…").version("…") }` → `defaults { }`.
- **Application target.** Plugins referenced from the init file apply to the **`Gradle` instance** — the same target as today's init scripts (`Plugin<Gradle>`), **not** as settings plugins. Build-wide and forcing.
- **Declarative plugins can't target settings/init.** A declarative plugin always brings in an **ecosystem that operates at the project level**. So introducing an ecosystem via the `plugins {}` block still works: the init file is just the *global introduction point*, and the ecosystem's own project-level apply actions do the work across every build. (Authoring per-project convention config *in the init file* remains out of scope — that's what the ecosystem + defaults are for.)
- **Local/unpublished plugins are allowed in the persistent file** (tier relaxed): a `localPlugin(files("…"), "FQN")`-style reference, for enterprises that drop a JAR on every agent instead of publishing. (Trust/governance — see §4 in the open questions.)

```kotlin
// $GRADLE_USER_HOME/init.d/org-conventions.init.gradle.dcl
pluginManagement { repositories { maven { url = uri("https://repo.mycorp.example/plugins") } } }

plugins {
    id("com.mycorp.repo-policy").version("3.2")
    localPlugin(files("/opt/mycorp/gradle/buildscan.jar"), "com.mycorp.BuildScanPlugin")
}
```

## 2. Ecosystem relations — provide vs rely

An init file relates to an ecosystem in one of two stances. Both bring the ecosystem's **schema into scope** for authoring/validation. An "ecosystem" is identified by its `@RegistersProjectFeatures` plugin, resolvable to a declarative schema *without* applying it.

**Provide (forcing)** — introduce the ecosystem into every build the file attaches to:
```kotlin
plugins { id("org.jetbrains.kotlin").version("2.0") }   // introduces Kotlin (a project-level ecosystem) into every build
defaults { kotlinLibrary { explicitApi = true } }        // unconditional — ecosystem is provided
```
- The Kotlin ecosystem is a **declarative plugin operating at the project level**; the init file's `plugins {}` is just the *global introduction point* (it does not make it a settings/init plugin).
- Version is a normal **version request** → **standard plugin version conflict resolution** decides the effective version. No special drift handling (same as any plugin version conflict today).

**Rely (conditional)** — apply only where the ecosystem is already present:
```kotlin
onEcosystem("com.android").atLeast("8.5").below("10") {  // inert unless Android present & in range
    onVersionOutOfRange = skip                           // skip (default) | warn | fail
    plugins { id("com.mycorp.android-extra").version("1.4") }   // conditional build logic
    defaults { androidApplication { /* … */ } }                 // + defaults
}
```
- The **consumer/build owns the version**; `atLeast(…)` is a **compatibility floor, not a pin**; optional `below(…)` bounds validity (majors can break the schema contract). Applied iff the ecosystem is present **and** its version is in `[floor, upper)`.
- **Within the range the ecosystem schema is a contract** (additive-compatible) — referenced members are guaranteed present, so no missing-member handling is needed. Out-of-range is governed by `onVersionOutOfRange` (skip/warn/fail, default skip).
- Carries **both defaults (data) and conditional build logic (code)** — when the gate holds, the contained `plugins {}` is introduced with **forcing/provide** semantics, build-wide.
- Schema validated at authoring time against the **floor** version (minimum guaranteed surface).

**Presence is at ecosystem granularity.** A rely relation activates when the **build uses the ecosystem** — i.e. the ecosystem is applied/present. Once present, its project types are available and defaults flow to them. It is **not** gated on a project declaring a specific project type.

**Apply-time semantics.** Phased, **single-pass** (proposed): (1) resolve the build's ecosystem set = intrinsic declarations **+ forcing `provide`s** (not rely-introduced ecosystems); (2) evaluate rely gates against that set; (3) for passing gates, introduce the gated `plugins {}` (forcing, build-wide) and contribute the gated `defaults` to the ecosystem's project types. A gated-introduced plugin that the build also declares → standard version conflict resolution. **Open:** single-pass is the conservative default (gates key only on the build's own + provided ecosystems; no re-trigger); whether to allow **any cascading** (fixpoint, where a rely-introduced ecosystem triggers further rely blocks) is open — likely **disallowed** to avoid the added complexity.

## 3. CLI injection (ad-hoc / tool-facing)

The **ad-hoc, per-invocation** channel (the `-I` replacement) for IDEs/scanners/CI to inject without editing the project or writing a persistent file. Primary concept: inject an **ad-hoc declarative init file** (same artifact as the persistent `init.d` form).

```bash
# PRIMARY — ad-hoc declarative init file: plugins{}, provide/rely relations, defaults, repositoryPolicy, localPlugin(...)
gradle build --init-definitions=/tmp/ci.init.gradle.dcl

# OPTIONAL SUGAR — apply one plugin to the Gradle instance: published…
gradle build --init-plugin=com.gradle.develocity:develocity-gradle-plugin:4.0
# …or a local/unpublished imperative Plugin<Gradle> by class (IDE/scanner model-builder escape hatch)
gradle :help --init-plugin-classpath=/opt/idea/tooling.jar --init-apply=com.intellij.gradle.JetGradleModelPlugin
```

- Everything applies at the **`Gradle` instance** (today's init-script target). A declarative ecosystem so introduced takes **project-level** effect (per §1); an imperative `Plugin<Gradle>` (IDE model-builder, build-scan) runs at the Gradle instance.
- `--init-plugin*` are **forcing/provide**; **conditional (rely)** logic lives inside an injected `--init-definitions` file. The IDE local-by-class case is also expressible there via `localPlugin(files(...), "FQN")`.
- Local forms **ungated** — trust anchor is the invoker (same as `-I` today). Repeatable; order = application order; composes with persistent `init.d` files.
- **Decided:** keep both — `--init-definitions` (primary; full declarative init file) **and** `--init-plugin*` sugar for the "apply one plugin" case.

## 4. Model extraction (IDEs/scanners) — plugin-only, no new API

Model builders are plugin code (allowed); tools apply their own (possibly local) model-builder plugin via the global file or the ad-hoc CLI channel; the existing Tooling-API model-request protocol is unchanged. **No new Gradle model-extraction API is required.** A built-in standard resolved-model/dependency-graph would be an *optional ergonomic* improvement (reduces per-tool duplication + internal-API coupling), not a necessity.

## 5. Repository enforcement / override (proposed)

Core move: **separate declaration from governance.** Builds/defaults *declare* repositories (data); a **policy** owned by the global/enterprise file declares *how resolution may use* them. Engine evaluates `declarations + policy → effective resolution` — no user code mutates a list, so the anti-declarative problem dissolves.

```kotlin
repositoryPolicy {                       // global/init file; also legal in settings.gradle.dcl
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

## 6. Credentials & secrets (OPEN design problem — no concrete solution yet)

How declarative files specify/reference credentials and secrets is deliberately left open. Constraints any solution should honor:
- Secrets are **never inlined** in declarative files; values are machine/user-scoped and provisioned out of band.
- Must cover today's init-script credential use cases: enterprise repo creds (Spring `repo.spring.io`, Artifactory), machine-specific secrets, and the gradle-credentials-plugin "decrypt stored credential" case (imperative work → in a plugin).
- Should integrate with Gradle's existing lazy/identified credentials, config-cache safety, and log/build-scan **redaction**.
- Sub-questions left open: reference-by-identity vs identity+source; the resolution-source chain; general secret providers beyond repository credentials; relationship to existing `<id>Username`/`<id>Password`.

## Open questions

1. **Version range & out-of-range policy.** A rely relation declares `atLeast(floor)` + optional `below(upper)` (bounds against majors breaking the schema contract). When the present version is outside `[floor, upper)` the relation is inert, governed by **`onVersionOutOfRange`** (skip / warn / fail, default **skip**). Remaining openness: exact encoding (property vs verbs).
2. **Above-floor compatibility — resolved.** Within the declared range the ecosystem schema is a **contract** (additive-compatible), so referenced members are guaranteed present — **no `onMissingMember` policy**. Crossing a major is handled by bounding with `below(...)` → out-of-range (Q#1). A contract violation is an ecosystem bug, surfaced as an ordinary schema error, not a policy knob.
3. **Repo-enforcement / override policy** — drafted in §5 (declaration-vs-governance + `repositoryPolicy`). Remaining: the architectural fork (precedence axis vs dedicated concept) + the sub-questions listed there.
4. **Local build logic in the persistent file — resolved.** Two forms: (a) **preferred** — a local file/maven repo declared in `pluginManagement.repositories`, plugins referenced uniformly via `id().version()` (reuses resolution + verification + §5 policy); (b) **escape hatch** — `localPlugin(files("…"), "FQN")` for a bare unpublished JAR applied by class. **Both allowed ungated** — the persistent init file's trusted location (agent provisioning) is the trust anchor. Governance: the file is an **inspectable/diffable/auditable manifest** (the real win; code still runs with full privilege), integrity via **dependency verification** (recommended), source control via §5 `repositoryPolicy.appliesTo(plugins)`.
5. **Credentials / secrets — OPEN design problem** (§6): no concrete solution. Constraints only: never inline; machine-scoped; cover today's init-script cases; integrate with lazy credentials + redaction.
6. **Conditional build logic in rely blocks — drafted** (§2 "Apply-time semantics"): phased single-pass; gates key on the build's own + force-provided ecosystems; passing gate introduces the gated `plugins {}` (forcing, build-wide) + defaults; conflicts via standard resolution. **Open:** single-pass is the conservative default; whether to allow **any cascading** (fixpoint) is open — likely disallowed for simplicity.
7. **Naming** — `onEcosystem` / `provide` vs `rely` / `atLeast` / `below` / `onVersionOutOfRange` / `localPlugin` / `repositoryPolicy` / `--init-definitions`.
8. **CLI injection — resolved in §3.** Keep **both**: `--init-definitions=<file.dcl>` (primary, ad-hoc declarative init file) **and** `--init-plugin` / `--init-plugin-classpath`+`--init-apply` sugar. All apply at the `Gradle` instance; ungated; composes with `init.d`.

## Status

Design discovery — drafts and decisions for discussion, not an implementation plan. **Settled:** §1 (init file / code references), §2 (provide/rely, presence granularity, version handling), §3 (CLI), §4 (model extraction). **Open:** §5 repo-enforcement anchor; §6 credentials (whole problem); cascading in §2; out-of-range encoding (Q#1); naming (Q#7).
