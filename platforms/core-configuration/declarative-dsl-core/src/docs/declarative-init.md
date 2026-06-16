# Declarative init definitions. Design discovery for global/init Declarative files replacing init scripts

> Companion to `research-init-scripts.md`. That document catalogues *why* init scripts are used in practice; 
> this one explores *what would replace them* if Declarative Gradle becomes the only scripting language. 
> It is a **design discovery** — drafts, decisions reached in discussion, and questions left explicitly open — 
> not an implementation plan. Any naming is provisional.

## Context

Premise under exploration: **Declarative Gradle (DCL) becomes the only scripting language in Gradle.** 
Declarative files contain only high-level public models ("definitions") — no imperative code. 
All low-level / "apply action" code lives in plugins. 
Groovy/Kotlin `init.gradle(.kts)` scripts are disallowed; a *declarative* init alternative may be allowed, plus a small number of sanctioned non-declarative injection paths.

Grounded in the catalogue of real init-script use cases in `research-init-scripts.md`, which splits into: 
* configure (apply plugins, specify the plugin configuration), 
* introspect (IDEs/scanners, tool-owned), 
* instrument (profilers/CI, tool-owned). 

Only *configure* needs a declarative home; introspect/instrument stay imperative and move to plugins + tool channels.

This doc covers the **infrastructure** of declarative init/global files; the *Out of scope* section below lists what it deliberately excludes.

## Principles

* Declarative files hold only **definitions (data)** + **references to plugins (code)**. No apply-action code is ever inlined.
* An init/global file contributes two distinct kinds of thing:
    - **defaults** = data (definition values), keyed/applied as today;
    - **build logic** = code = *referenced* plugins whose apply actions the build loads and runs.

There are two ways a Declarative init definition can be connected to plugins or declarative ecosystems:
* It adds, or applies, an ecosystem or an imperative plugin. This is expressed with a `plugins { }` block similar to regular Declarative
  settings files. The init definition can also provide declarative defaults to the project types/features of the ecosystem it adds to the build.
* It can rely on a Declarative ecosystem. It can then reference the definitions of the project types/features of that ecosystem to provide declarative defaults.
  If the ecosystem is not present in the build, it cannot work. This connection is expressed with
  a new guard block per file (see §2). The block (`relyOnEcosystems { }`) may list **multiple ecosystems**; the file applies when *all* of them are present & in range. 

## 1. Declarative init file — how it references code

- Lives in a Gradle-User-Home location discovered like today's `init.d/` (`*.init.gradle.dcl`); also an ad-hoc CLI form (see §5).
- Same surface as settings: `pluginManagement { repositories { } }` → `plugins { id("…").version("…") }` → `defaults { }`.
- A file is **either unguarded** (unconditional) **or carries a single file-level ecosystem guard block** (conditional; the block may list one or more ecosystems) — see §2.
- **Application target.** Plugins referenced from the init file apply to the **`Gradle` instance** — the same target as today's init scripts (`Plugin<Gradle>`), **not** as settings plugins. Build-wide and forcing.
- **Declarative plugins can't target settings/init.** A declarative plugin always brings in an **ecosystem that operates at the project level**. So introducing an ecosystem via the `plugins {}` block still works: the init file is just the *global introduction point*, and the ecosystem's own project-level apply actions do the work across every build. (Authoring per-project convention config *in the init file* remains out of scope — that's what the ecosystem + defaults are for.)
- Optionally, **local/unpublished plugins** could be supported with a DSL `localPlugin(files("…"), "FQN")`-style reference, for enterprises that drop a JAR on every agent instead of publishing. 

```kotlin
// $GRADLE_USER_HOME/init.d/org-infra.init.gradle.dcl
pluginManagement { 
    repositories { 
        maven { 
            url = uri("https://repo.mycorp.example/plugins") 
        } 
    } 
}

plugins {
    id("com.mycorp.repo-policy").version("3.2")
    localPlugin(files("/opt/mycorp/gradle/buildscan.jar"), "com.mycorp.BuildScanPlugin")
}
```

## 2. Adding or relying on an ecosystem

A file relates to ecosystems in one of two ways.

**Adding an ecosystem** — introduce an ecosystem into every build the file attaches to, and/or contribute infra:
```kotlin
plugins { 
    id("org.jetbrains.kotlin").version("2.0") 
}

defaults {
    kotlinLibrary { explicitApi = true } 
}
```
- The Kotlin ecosystem is a **declarative plugin operating at the project level**; the init file's `plugins {}` is just the *global introduction point* (it does not make it a settings/init plugin).

A normal plugin is resolved for the init script and is not added to the target build's classpath, like it is with `initscript { }` dependencies.

However, ecosystems added by init definitions **need** to be visible in the build and to be referenced from the projects. 
So any ecosystem plugin added to the init script must appear in the build classpath. For that, all plugin requests collected from init definitions
are checked for the presence of Declarative ecosystem descriptors (declaring project types/features). If there is one, such a plugin request
is carried to the build's root plugins. If an ecosystem plugin is added with different versions, the version conflict is resolved for the build.

Given this, another option is to separate `plugins` and `ecosystems` in the DSL so it is more explicit which of them adds the plugin
classes to the target build:

```kotlin
plugins { 
    id("my.build.logic").version("0.1") 
}

ecosystems {
    id("org.jetbrains.kotlin").version("2.0")
}

defaults {
    kotlinLibrary { explicitApi = true } 
}
```

Open question: how should `pluginManagement` work? If an ecosystem is requested in an init definition, it should also
appear in the build. Should there be a way to affect how the build's `pluginManagement`? 
Should there be a separate `ecosystemsPluginManagement`?

**Relying on an ecosystem** (guarded init definition) — the whole file applies only where the listed ecosystems are already present. The guard is **one top-level `relyOnEcosystems { }` block** — a DCL container of elements:
```kotlin
pluginManagement { 
    /* ... */
}

// kmp-android-conventions.init.gradle.dcl
// single top-level guard block; this file applies only when BOTH ecosystems are present & in range
relyOnEcosystems {
    id("com.android") {
        versionAtLeast = "8.5"
        versionBelow = "10"           // optional upper bound
        onVersionOutOfRange = skipAndWarn   // skipSilently | skipAndWarn (default) | fail
    }
    id("org.jetbrains.kotlin") {
        versionAtLeast = "2.0"
    }
}

plugins  { 
    id("com.mycorp.kmp-android-extra").version("1.4") 
}            

defaults { 
    androidApplication { /* … */ } 
    kotlinLibrary { /* … */ } 
}
```

Given that `relyOnEcosystems` makes everything else in the file conditional, it should appear before `plugins` and `defaults`, but after `pluginManagement`.

- The guard is a **single top-level block**, not something that wraps `plugins {}`/`defaults {}` — those stay top-level (DCL forbids nesting them; plugin resolution is early, so a wrapping runtime gate would be incoherent).
- The ecosystems relied upon can be resolved for init definition analysis based on the `pluginManagement` spec, however, outside of tooling assistance, in an actual build,
  the defaults for those ecosystems are evaluated against the versions of the ecosystems resolved by the build (assuming the version check passed).
- **Multiple ecosystems, conju  nctive:** the file applies iff **every** listed ecosystem is present **and** in its range. For independent (OR-style) reactions, or to mix unconditional + guarded content, **split into separate files**. Ecosystem relations are never nested inside `defaults`/`plugins`.
- Each `id(...)` element configures its relation via **properties** (`versionAtLeast`, optional `versionBelow`, `onVersionOutOfRange`). The **consumer/build owns the version**; `versionAtLeast` is a **compatibility floor, not a pin**; `versionBelow` bounds validity (majors can break the schema contract); the relation holds when the version is in `[versionAtLeast, versionBelow)`.
- **Within the range the ecosystem schema is a contract** (additive-compatible) — referenced members are guaranteed present, so no missing-member handling is needed. Out-of-range is governed per element by `onVersionOutOfRange` (`skipSilently` / `skipAndWarn` / `fail`, default `skipAndWarn`).
- A guarded file carries **both defaults (data) and build logic (code)**; when the guard holds, its `plugins {}` is introduced with **forcing/provide** semantics, build-wide.
- The whole file is validated at authoring time against the listed ecosystems' **floor** schemas (+ base).

**Presence is at ecosystem granularity.** 

A guard activates when the **build uses the ecosystem** — i.e. the ecosystem is applied/present. 
Once present, its project types are available and defaults flow to them. 

**Detecting applied ecosystems (open question).** 

Deciding *which* ecosystems a build uses — needed for guard evaluation and for `defaults` targeting — can't be read from the requested `plugins {}`: an ecosystem plugin's 
ID may not be requested directly at all (it can be pulled in **transitively**, e.g. by another plugin that depends on it). A separate detection mechanism is needed — for instance, 
the presence of an **ecosystem marker class or resource on the classpath**. 
**Versioning is the harder part:** evaluating `versionAtLeast`/`versionBelow` needs the ecosystem's *version*, which a bare marker may not carry — options include inspecting the
**resolved dependency graph** to recover it, or **embedding the version in the marker** class/resource (each with trade-offs). Left **partially open** — no precise solution required yet.

Ecosystem discovery semantics:

Option 1: **single-pass**: 
1. resolve the build's ecosystem set = intrinsic declarations **+ forcing `plugins` from unguarded files** (not ecosystems introduced by guarded files); 
2. evaluate each guarded file's `relyOnEcosystems` block against that set (it holds only when *all* listed ecosystems are present & in range); 
3. for files whose guard holds, introduce their `plugins {}` (forcing, build-wide) and contribute their `defaults`.

Option 2: **reactive application**
1. Resolve and add all plugins in unguarded init files.
2. Maintain a queue of guarded init definitions that have satisfied ecosystem relations.
3. On each guarded init definition that has its requirements satisfied, add its plugins and add check if any new guarded init definition now has its 
   ecosystem relations satisfied; add it to the queue if so.
4. Similar to dependency resolution, adding more plugins to the graph can affect the versions of the plugins already in the graph.
Problem: ordering of the `defaults` blocks (see §3) — unlike distributed defaults, one cannot witness an init script to set the order.

**Open:** single-pass is the conservative default (guards key only on the build's own + provided ecosystems; no re-trigger); 
whether to allow **any cascading** (a guarded file's ecosystem triggering further guarded files) is open — 
likely **disallowed** to avoid the added complexity, but if a good way to support it is found, having it will reduce confusion.

## 3. Ordering of `defaults` across init definitions

Several init definitions can contribute `defaults` for the same project type or property. 
When they conflict, the result must be **deterministic** — there must be a clear order in which the defaults are layered, so the winning value is predictable.

**Why the regular-defaults ordering doesn't transfer.** 

Distributed, plugin-provided defaults order themselves by a **witness**: if one plugin is *aware of* another (it can reference or depend on it), 
its defaults are layered on top and win. The dependency edge between the plugins is the witness that fixes the order.

Init definitions have no such edges. They are independent files dropped into `init.d` (or injected ad hoc), with no dependency relationship between them to act as a witness — 
so there is **no implicit, reliable order** to derive conflict resolution from. 

**Open question — how to order conflicting init defaults.** 

One option is to keep the current implicit ordering of init scripts and order the defaults in init definitions according to that.

Another candidate is an **explicit priority** declared in the init definition (e.g. a top-level `priority = N`), making the order visible and stable in the file 
rather than derived from a missing witness or from discovery order.

Left open: whether explicit priority is the right model and what shape it takes 
(numeric value? named tiers? relative `before`/`after` another definition?), and how it interacts with the per-ecosystem guards and with `plugins {}` application order.

## 4. Supported Gradle versions

A single `init.d` definition is shared by every build on the machine, and those builds run different Gradle versions (each repo's wrapper). 
So one definition must be consumable across a range of Gradle versions.

The extra hazard over the ecosystem ranges (§2): the init-definition DSL itself evolves with Gradle, so a file using a newer construct 
would fail to even *parse* on an older Gradle. The compatibility declaration must therefore be readable before the rest of the file.

**Frozen compatibility preamble.** 

Reuse the §2 version-range vocabulary, targeting the Gradle runtime, in a small preamble parsed by **every** Gradle version with a **frozen** grammar:

```kotlin
relyOnGradle {
    versionAtLeast = "10"
    versionBelow = "12"           // optional upper bound
    onVersionOutOfRange = skipAndWarn   // skipSilently | skipAndWarn (default) | fail
}
```

**Deferred body parsing.** 

Gradle parses only the preamble first; if its own version is out of range it **skips the file without parsing the body**, 
so newer constructs below are harmless on older Gradle. Only when the version is in range is the body parsed and evaluated against that version's schema — 
the same "schema is a contract within the declared range" idea as §2, applied to the init DSL.

**Default `skipAndWarn`, diagnosable.** 

A silently skipped definition (e.g. "our defaults don't apply on the 8.x repos") is hard to debug at scale, 
which is why the default is `skipAndWarn` (each skip is surfaced); `skipSilently` opts out of the warning, `fail` hard-errors. 
At scale, a report of which definitions were skipped and why (Gradle-version vs ecosystem mismatch) is still useful.

For a genuinely *incompatible* grammar break the fallback is version-scoped discovery (separate files per Gradle era); prefer the single-file preamble.

## 5. CLI injection (ad-hoc / tool-facing)

The **ad-hoc, per-invocation** channel (the `-I` replacement) for IDEs/scanners/CI to inject without editing the project or writing a persistent file. 

Primary concept: inject an **ad-hoc declarative init file** (same artifact as the persistent `init.d` form — guarded or unguarded).

```bash
# PRIMARY — ad-hoc declarative init file: plugins{}, an optional file-level guard, defaults, localPlugin(...)
gradle build --init-definitions=/tmp/ci.init.gradle.dcl

# OPTIONAL SUGAR — apply one plugin to the Gradle instance by ID if published
gradle build --init-plugin=com.gradle.develocity:develocity-gradle-plugin:4.0
# …or a local/unpublished imperative Plugin<Gradle> by class (IDE/scanner model-builder escape hatch) or ID
gradle :help --init-plugin-classpath=/opt/idea/tooling.jar --init-apply=com.intellij.gradle.JetGradleModelPlugin
```

- Imperative plugins are applied to the `Gradle` instance (today's init-script target). 
- A declarative ecosystem so introduced takes effect on the projects;
- `--init-plugin*` are **forcing/provide** (unconditional); **conditional (rely)** logic lives inside an injected `--init-definitions` file via its file-level guard. 
  The IDE local-by-class case is also expressible there via `localPlugin(files(...), "FQN")`.
- Local forms **ungated** — trust anchor is the invoker (same as `-I` today). Repeatable; order = application order; composes with persistent `init.d` files.
- **Decided:** keep both — `--init-definitions` (primary; full declarative init file) **and** `--init-plugin*` sugar for the "apply one plugin" case.

## Out of scope

This doc is about the *infrastructure* of declarative init/global files. The following are out of scope — each is already handled by **imperative plugins**, so none requires new declarative *infrastructure*:

- **Injecting declarative `Settings` configuration via an init definition** – this can be supported by imperative plugins and is not required in the first step 
  but can be added later as a new Declarative sub-DSL in init definitions.
- **Model extraction (IDEs / dependency scanners).** Solved by imperative plugins — a tool applies its own (possibly local) model-builder plugin via the init file or the ad-hoc CLI channel (§5),
  and the existing Tooling-API model-request protocol is unchanged. **No new declarative API is required.** (A built-in standard resolved-model / dependency-graph would be an *optional ergonomic* 
  improvement — reduces per-tool duplication + internal-API coupling — not a necessity.)
- **Problem-specific global config that would need a *new declarative API*** — repository enforcement/override and credentials/secrets. These *could* get first-class declarative APIs, but 
  they're **optional**: an imperative init-plugin applied from a declarative init file already handles them. Explored separately in `declarative-init-tasks.md`.

## Open questions

1. **Behavior of `pluginManagement` in an init file** (§2). Should it somehow affect the build's plugin management so the requested ecosystems get resolved? 
2. **Ecosystem detection & versioning** (§2 "Detecting applied ecosystems"). Presence can't be read from requested `plugins {}` (ecosystems may arrive transitively); needs a classpath **marker class/resource**. Recovering the *version* (for `versionAtLeast`/`versionBelow`) is harder — dependency-graph inspection vs version embedded in the marker. Partially open.
3. **Ordering of `defaults` across init definitions** (§3). Conflicting defaults from different init definitions need a deterministic order. The **witness** mechanism used for distributed defaults (a plugin aware of another wins) has no analogue here — init definitions have no dependency edges to witness. Candidate: an **explicit `priority`** in the init definition. Open: its model & shape (numeric / named tiers / relative `before`/`after`), and interaction with the ecosystem guards and `plugins {}` application order.
4. **Apply-time / ecosystem discovery semantics** (§2). Option 1 **single-pass** (resolve the ecosystem set once, then evaluate guards) vs Option 2 **reactive application** (queue guarded definitions and re-check as plugins are added, dependency-resolution-style). Single-pass is the conservative default; whether to allow **cascading** (a guarded definition's ecosystem triggering further guarded definitions) is open — likely disallowed for simplicity.
5. **`plugins` vs `ecosystems` split** (§2). Keep a single `plugins { }` block (ecosystem plugins detected by their Declarative ecosystem descriptors and carried to the build classpath) vs introduce a separate **`ecosystems { }`** block to make explicit which requests add plugin classes to the target build.
6. **Local/unpublished plugin support** (§1). Whether to support a `localPlugin(files("…"), "FQN")`-style reference for plugins dropped on an agent rather than published.
7. **Supported Gradle versions** (§4). A shared `init.d` definition must work across the Gradle versions of different builds, but the init DSL evolves per version. Proposed: a frozen `relyOnGradle { }` preamble (version range + `onVersionOutOfRange`: `skipSilently` / `skipAndWarn` / `fail`, default `skipAndWarn`) that gates parsing of the body. Open: which grammar is frozen forever; the skipped-definition report; whether to gate on the raw Gradle version or a finer init-DSL level.
