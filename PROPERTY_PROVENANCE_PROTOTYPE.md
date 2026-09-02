# Property Mutation Provenance — prototype

This branch is a prototype for the mutation-provenance model specified in
`PROPERTY_PROVENANCE.md` of the *gradle-provider-api-semantics* repository. It implements
the smallest useful slice of that specification: **contributor identity for ordinary
properties, surfaced in error messages.**

It exists to test one load-bearing claim of the specification — that Gradle's existing
user-code application context is enough to attribute a property mutation to a stable
contributor, and that the capture point can be a single choke point in `AbstractProperty`.

Everything is behind an internal flag, **off by default**:

```
-Dorg.gradle.internal.property-provenance=true
```

With the flag off, nothing is captured and every message is byte-for-byte what it was
before.

## What it does

A plugin configures a task property; the build script tries to change it after it has been
finalized:

```
* What went wrong:
Execution failed for task ':show'.
> The value for task ':show' property 'prop' is final and cannot be changed any further. It was last set by plugin 'com.example.feature'.
```

A property is queried and has no value:

```
* What went wrong:
Execution failed for task ':show'.
> Cannot query the value of task ':show' property 'other' because it has no value available.
  This property was last set by plugin 'com.example.feature'.
```

When a property was configured more than once, the whole chain is reported in order:

```
* What went wrong:
Execution failed for task ':show'.
> The value for task ':show' property 'prop' is final and cannot be changed any further.
  It was configured by, in order:
    1. given its convention by plugin 'com.example.feature'
    2. set by plugin 'com.example.feature'
    3. set by build file 'build.gradle'
```

With `-Dorg.gradle.internal.property-provenance.locations=true`, each entry carries its call
site:

```
  1. set by plugin 'com.example.feature' at FeaturePlugin.groovy:11
  2. set by build file 'build.gradle' at build.gradle:7
```

The contributor is named the same way whether it is a plugin ID, a plugin class, a build
script or a script plugin:

```
It was last set by plugin 'com.example.feature'.
It was last set by plugin class 'FeaturePlugin'.
It was last set by build file 'build.gradle'.
It was last set by script 'other.gradle'.
```

Attribution survives deferred configuration: a property set inside a `tasks.register { }`
or `configureEach { }` callback registered by a plugin is attributed to that plugin, not
to whoever happened to trigger the callback.

## Design

Provenance is captured where the property is mutated, from the user code that is running.

```
UserCodeApplicationContext.current()          existing; already restored across
        |                                     Gradle-managed callbacks by
        v                                     Application.reapplyLater(...)
ProjectBackedPropertyHost.currentMutation(kind)
        |
        v
MutationOriginRegistry.recordFor(source, kind)      interned
        |
        v
AbstractProperty.setSupplier / setConvention / ...  one reference write
```

| Type | Role |
|---|---|
| `ContributorKey` | Stable identity: `Plugin(id)`, `PluginClass(className)`, `BuildAuthor`, `ScriptPlugin(uri)`, `Unknown`. Not a runtime application id and not a source location. |
| `MutationOrigin` | A contributor plus how to name it to a user, taken from `UserCodeSource`. |
| `MutationKind` | What happened: `SET_SOURCE`, `SET_CONVENTION`, `UNSET`, `ADD`, `PUT`, … |
| `MutationRecord` | `(origin, kind)`, interned per `(user code source, kind)` pair. |
| `MutationOriginRegistry` | Build-tree scoped interning table and the on/off switch. |

Interning is what makes this affordable: with provenance on, a mutation costs one
ThreadLocal read, two map lookups and a reference write, with **no allocation**. With it
off, a mutation costs one boolean field read. A property carries two references — the last
mutation of its explicit value and the last mutation of its convention — plus that boolean.

### Where the seams are

- **`PropertyHost.tracksMutationProvenance()`** — asked **once, when the property is
  created**, and cached on the property. This is not just an optimization; see
  "What this turned up" below.
- **`PropertyHost.currentMutation(MutationKind)`** — a `default` method returning `null`.
  The host is already handed to every property at construction, so no property
  constructor or factory had to change, and `PropertyHost.NO_OP` keeps working.
- **`ValueState.host()`** — lets `AbstractProperty` reach its host without a third field.
  Returns `null` once finalized, which is exactly when mutation is rejected anyway.
- **`AbstractMinimalProvider.describeProvenance(TreeFormatter)`** — a no-op hook that
  `AbstractProperty` overrides, so the missing-value message can carry provenance without
  providers knowing anything about mutation.
- **`UserCodeSource.Script.isTopLevelScript()`** — new. The specification predicted this
  gap: without it a build script and an applied script plugin are indistinguishable, so
  `BuildAuthor` cannot be told apart from `ScriptPlugin`. The flag was already a parameter
  of `ScriptPluginFactorySelector.create(...)` and was simply being dropped.

### What was reused rather than rebuilt

- `UserCodeApplicationContext` / `UserCodeSource` for the contributor.
- `Application.reapplyLater(...)`, already applied at callback registration by
  `DefaultCollectionCallbackActionDecorator` and `DefaultListenerBuildOperationDecorator`.
  **No new callback decoration was needed.**
- `UserCodeSourceCodec` for configuration cache serialization of the new field.
- The wording of `TaskProvenanceUtil` ("plugin 'x'", "build file 'build.gradle'"), so
  property and task provenance read the same way.

## What this turned up

Five things worth feeding back into the specification:

1. **No new callback decoration is needed** (spec §6). Attribution already survives
   `tasks.register { }`, `tasks.named { }` and `withType(...).configureEach { }`, because
   `DefaultCollectionCallbackActionDecorator` already wraps those actions in
   `Application.reapplyLater(...)` at registration. The specification's "centralized
   registration-time callback decorator" largely exists; the work is auditing its coverage,
   not building it.
2. **Nested plugin application already nests correctly.** A plugin that applies another
   plugin gets attribution restored when the inner application returns, with no extra work —
   `DefaultUserCodeApplicationContext` saves and restores in a `finally`.
3. **The build-script/script-plugin gap is real and cheap to close** (spec §4). The
   `topLevelScript` flag needed to tell `BuildAuthor` from `ScriptPlugin` is already a
   parameter of `ScriptPluginFactorySelector.create(...)` and was simply not being
   propagated into `UserCodeSource.Script`.
4. **Capture must be gated at property construction, not per mutation.** The first version
   asked the host for provenance on every mutation. That is behaviour-neutral, but it broke
   **188 existing unit tests** that assert no interaction with a property's collaborators
   (`0 * _`) while mutating it. Asking the host once, when the property is created, moves
   that interaction outside those assertions and cuts the disabled-path cost to a field
   read. Any production implementation will hit the same wall, so this belongs in the
   specification as an implementation constraint.
5. **Silent mis-attribution is the real failure mode, not missing attribution** (spec §9).
   Where propagation does not reach — user code a plugin stores itself, or a mutation
   performed inside a `Provider` transform — the mutation is not left unattributed. It is
   attributed to whoever happened to run it, which is a plausible and wrong answer. Only a
   mutation with no user code context at all reports `Unknown`. This is the concrete reason
   collaborative mode has to be fail-closed rather than trusting the recorded contributor.
6. **`PropertyHost` is the right injection point.** It is already threaded into every
   property constructor, so provenance needed no change to any property constructor,
   `PropertyFactory`, or `ObjectFactory` signature. The only awkwardness is that some tests
   construct properties with a null host, which the constructor now tolerates.

## Callback coverage

Attribution rides on `UserCodeApplicationContext.Application.reapplyLater(...)`, which
Gradle applies where it stores user code. So coverage is a property of the **registration
boundary**, not of the kind of user code: a Groovy closure, a Java `Action` and a Kotlin
lambda registered through the same API all behave identically. There is no separate
"closure handling" to get right.

Verified by `PropertyProvenanceCallbackCoverageIntegrationTest`:

| Registration point | Attributed to |
|---|---|
| Direct mutation during plugin application | the plugin |
| `tasks.register(name, type) { }` | the plugin |
| `tasks.named(name) { }` / `.configure { }` | the plugin |
| `tasks.withType(T).configureEach { }` | the plugin |
| `project.afterEvaluate { }` | the plugin |
| `gradle.projectsEvaluated { }` | the plugin |
| `pluginManager.withPlugin(id) { }` | the plugin |
| `gradle.taskGraph.whenReady { }` | the plugin |
| `configurations.configureEach { }` | the plugin |
| A plugin's own thread or executor | `Unknown` |
| User code a plugin stores itself and runs later | **whoever ran it** |
| A mutation performed inside a `Provider` transform | **whoever evaluated it** |

The last two are wrong answers rather than absent ones — see finding 5 above.

Not probed, and expected to need work: tooling model builders, worker actions, build
services, flow actions, and `beforeProject`/`afterProject` from init scripts.

## The mutation chain

`MutationTrace` is an append-only list of records on the property, allocated only when the
host tracks provenance. Mutations are kept **even once superseded** by a replacing `set`:
for diagnostics, "the plugin set it and then the build script overwrote it" is the
interesting fact, and the specification (§7) explicitly permits keeping it.

The trace is bounded at 32 records so a property mutated in a loop cannot grow without
limit; anything beyond that is counted and reported as "and N later mutation(s) not
retained". A single mutation still renders as one sentence rather than a list of one.

This also replaced the two separate fields the first version kept: `getExplicitMutation()`
and `getConventionMutation()` are now derived by scanning the trace backwards, which only
happens when a message is actually rendered.

## Call sites

Locations are a second, narrower switch
(`-Dorg.gradle.internal.property-provenance.locations=true`) because they cost a stack walk
per mutation and defeat record interning — a location is per call site, not per
contributor, so a located record has to be allocated. They are capped at 2000 captures per
build, mirroring the cap `DefaultProblemDiagnosticsFactory` puts on its own stack captures.

The walk itself is **not** `BoundedCallerStackCapturer.captureCallerStack()`, which is tuned
for problem reporting: it stops at the first Gradle frame below a user frame. A Groovy
property assignment (`prop = "x"`) puts a generated, line-less accessor frame for the
decorated task directly above Gradle's dynamic dispatch, so that walk terminates before
reaching the script and yields no location at all — measured, not assumed. Property
mutation needs a walk that *steps over* synthetic user frames and keeps going, which is
added alongside it as `BoundedCallerStackCapturer.captureCallSite()`.

With that, all three DSL shapes resolve:

| Mutation | Location |
|---|---|
| `prop = "x"` (Groovy assignment) | `build.gradle:4` |
| `prop.set("x")` (explicit call) | `build.gradle:5` |
| inside a plugin | `FeaturePlugin.groovy:11` |

Note this is a raw stack frame, not a `Location` resolved through `RegisteredScripts`:
`DefaultProblemLocationAnalyzer.tryGetLocation` returns a location **only** for registered
scripts, so reusing it would have covered build scripts and dropped plugin call sites.

## Deliberately not implemented

This is the ordinary-diagnostics slice. Each of the following is a documented seam in the
specification, not an oversight:

- **Declarative DSL and project-feature attribution** (spec §5). Project feature apply
  actions (`DefaultProjectFeatureApplicator`) currently run with no user-code application
  scope at all, so they would need one before explicit attribution can be layered on.
- **Precise source file and line** (spec §4). `ProblemDiagnosticsFactory` and
  `BoundedCallerStackCapturer` already do budgeted stack-walking to a `Location`; that is
  the thing to reuse, rather than new bytecode instrumentation.
- **`ApplicationId` on the record** (spec §3). Dropped so records can be interned.
- **Configuration cache persistence of property provenance** (spec §10). `PropertyCodec`
  collapses a property to its provider, so the mutation record does not survive a cache
  round trip. `UserCodeSource` itself already round trips.
- **Per-contribution provenance for collections.** Only the *last* explicit mutation is
  kept, so a list built by five plugins names one of them. This is the natural next slice,
  and collaborative mode needs it anyway.
- **Collaborative fail-closed rejection** (spec §8, §9). Unattributed mutations are
  recorded as `Unknown` and simply omitted from messages.
- **`ConfigurableFileCollection`.** It uses `ValueState` directly rather than extending
  `AbstractProperty`, so it records nothing. `DirectoryProperty` and `RegularFileProperty`
  are covered, since they extend `DefaultProperty`.
- **Settings-scope and worker-scope properties.** These are created with
  `PropertyHost.NO_OP` and record nothing. Measured: a property created in `settings.gradle`
  reports no provenance at all.
- **Per-script identity for build authors.** `ContributorKey.BUILD_AUTHOR` collapses every
  build script to one contributor, per spec §4, so `lib/build.gradle` and the root
  `build.gradle` are distinguished in the message text but not in identity. If
  collaborative authorization ever needs to tell them apart, the script URI is already on
  `UserCodeSource.Script` and is simply discarded for this case.

## Measured gaps

Probed against real builds rather than reasoned about:

| Case | Result |
|---|---|
| Kotlin DSL build script | works — `set by build file 'build.gradle.kts'` |
| Managed property on an extension | works |
| Task property under the **configuration cache** | **no provenance at all**, on the store run as well as a cache hit |
| Property created in `settings.gradle` | **nothing** — settings scope uses `PropertyHost.NO_OP` |
| `ConfigurableFileCollection` | **nothing** — not an `AbstractProperty` |
| Collection built by several `add` calls | only the last contribution is named |
| Init script | reads well (`set by initialization script 'init.gradle'`) but its **identity** is `BUILD_AUTHOR` |
| `settings.gradle` mutating a project property | reads well (`set by settings file 'settings.gradle'`) but its **identity** is `BUILD_AUTHOR` |

Two of these deserve attention beyond this slice:

**The configuration cache erases provenance.** With `--configuration-cache`, a task property
observed at execution time has no provenance even on the store run, because the property is
recreated by `PropertyCodec` during deserialization rather than by the user code that
configured it. Since the configuration cache is on by default in an increasing number of
builds, a provenance feature that silently goes blank under it is close to useless in
practice. Persisting the origin table (spec §10) is not optional polish.

**Init and settings scripts are misclassified.** `ContributorKey.of` maps any
`topLevelScript` to `BUILD_AUTHOR`, and `DefaultInitScriptProcessor` and
`ScriptEvaluatingSettingsProcessor` both pass `topLevelScript = true`. An init script is
usually the *environment* speaking, not the build author, and conflating them would let an
init script inherit whatever authority the build author has in collaborative mode. The
boolean should be a role (`BUILD_SCRIPT`, `SETTINGS_SCRIPT`, `INIT_SCRIPT`, `SCRIPT_PLUGIN`)
rather than a flag.

## Follow-ups if this graduates

1. Pack the two record references into a single `long` of interned ints, per the
   footprint constraint in spec §10.
2. Measure the capture cost with the flag on and off before considering a default-on
   rollout.
3. Attach provenance to collection contributions, which unlocks the ordered update trace
   that collaborative mode validates.

## Tests

```
./gradlew :model-core:test --tests "*PropertyMutationProvenanceTest*"      # 21 tests
./gradlew :model-core:embeddedIntegTest --tests "*PropertyProvenance*"     # 20 tests
```

Regression, since this touches the message text of two widely asserted errors and the
mutation path of every property:

```
./gradlew :model-core:test :file-collections:test
./gradlew :core:test --tests "*TaskProvenance*" --tests "*BuildOperationScriptPlugin*"
```

The integration tests cover the specification's §11 conformance cases that fall inside this
slice: a direct mutation during plugin application, a plugin applied by class, a build
script mutation, a deferred callback, applying a plugin from a plugin, a script plugin, an
unattributed mutation, and the flag being off.
