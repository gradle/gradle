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
before. With it on, build logic also gets the **call site** of its own mutations, because
classpath instrumentation bakes those in as constants. Mutations Gradle performs on the
user's behalf are named by their contributor alone — putting a position on those means
walking the stack, which is a second, opt-in switch:

```
-Dorg.gradle.internal.property-provenance.stack-walk=true
```

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
  It was configured by:
    given its convention by plugin 'com.example.feature'
    -> set by plugin 'com.example.feature'
    -> set by build file 'build.gradle'
```

Where the mutation was written in instrumented build logic, the entry carries its call
site:

```
  given its convention by plugin 'java-library'
  -> set by plugin 'com.example.feature' at FeaturePlugin.java:12
  -> set by build file 'build.gradle.kts' at build.gradle.kts:7
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
| A property mutated as a side effect inside a `Provider` transform | **whoever evaluated it** |

The last two are wrong answers rather than absent ones — see finding 5 above. The
transform row is narrower than it sounds: setting a property to a mapped provider is
attributed to whoever calls `set`, and reading a property records nothing. What
misattributes is a lambda that mutates some *other* property while being evaluated.

Not probed, and expected to need work: tooling model builders, worker actions, build
services, flow actions, and `beforeProject`/`afterProject` from init scripts.

## The mutation chain

`MutationTrace` is an append-only list of records on the property, allocated only when the
host tracks provenance. Mutations are kept **even once superseded** by a replacing `set`:
for diagnostics, "the plugin set it and then the build script overwrote it" is the
interesting fact, and the specification (§7) explicitly permits keeping it.

The trace is bounded at 32 records so a property mutated in a loop cannot grow without
limit; anything beyond that is counted and reported as "-> and N later mutation(s) not
retained". A single mutation still renders as one sentence rather than a list of one.

This also replaced the two separate fields the first version kept: `getExplicitMutation()`
and `getConventionMutation()` are now derived by scanning the trace backwards, which only
happens when a message is actually rendered.

## Call sites

Locations from **instrumentation** are free and come with provenance itself. Locations from
the **stack walk** are the opt-in switch, because they cost microseconds per mutation and
defeat record interning — a location is per call site, not per contributor, so a located
record has to be allocated. Walked locations are capped at 2000 captures per build,
mirroring the cap `DefaultProblemDiagnosticsFactory` puts on its own stack captures.

The walk cost scales with the number of frames it must skip: about 0.9 µs when user code is
adjacent, ~2.5 µs at 15 internal frames and ~5 µs at 30 in a microbenchmark, and 7.2 µs on
average in a real build. A capture that finds nothing is the worst case, since it walks the
whole 50-frame cap.

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

## Call sites from classpath instrumentation

A call site is a compile-time constant, so build-logic instrumentation can hand it over for
free rather than having it discovered by a stack walk. `PropertyMutationInterceptor` rewrites

```java
property.set(value)                       // in instrumented build logic
```

into a call carrying its own position, which `PropertyCallSites` publishes for exactly the
duration of the mutation:

```java
PropertyCallSites.set(property, value, "FeaturePlugin.java", 12)
```

Three pieces, none of which needed the annotation processor:

- `InstrumentingClassTransform` already tracked `sourceFileName` and `methodInsLineNumber`
  for its instrumentation-time reporting listener. `CallSiteSource` just exposes them to
  interceptors, so no interceptor signature changed.
- `PropertyMutationInterceptor` matches `set` on anything `isInstanceOf` `Property` — the
  hierarchy query matters, because `p.set(x)` on a `RegularFileProperty` compiles to
  `INVOKEINTERFACE RegularFileProperty.set` — and emits the two constants inline. Inline
  rather than through `DefaultBridgeMethodBuilder`, which would generate one synthetic
  bridge method per distinct call site.
- `PropertyCallSites` performs the mutation itself, so the position is published for that
  mutation only. Publishing it *before* the call and clearing on read would leave a stale
  position whenever the next mutation was not instrumented, and a wrong line is worse than
  no line.

Verified end to end with the stack walk **disabled**, so a location can only have come from
an instrumented call site: a Java plugin in `buildSrc` reports
`set by plugin 'com.example.feature' at FeaturePlugin.java:12`. Both `buildSrc` output and
Kotlin DSL scripts are instrumented in an ordinary build, confirmed by checking that a
`System.getProperty` call in each is tracked as a configuration cache input.

### How much it actually covers

Measured on the gradle/gradle build with locations on and the walk still enabled:

```
locations from instrumentation   2,817   ( 2.8%)
locations from the stack walk   75,717   (75.4%)
no location found               21,951   (21.9%)
```

2.8% is lower than it sounds, for two reasons that the mutation kind histogram separates:

```
SET_CONVENTION 69,482   SET_SOURCE 27,639   ADD_ALL 2,160   ADD 1,194   UNSET 8
```

- The interceptor currently matches only `set`, which is 27.5% of mutations. **69% are
  conventions**, and widening to `convention`, `add` and `put` is straightforward.
- More fundamentally, of the 27,639 `set` calls only about 10% originate in instrumented
  build logic. The rest are Gradle's own plugins, and the distribution is not instrumented.

That is the real shape of it: **instrumentation cannot cover mutations Gradle performs on
the user's behalf, and those are the majority.** But it covers precisely the mutations a
user writes, which are the ones worth pointing at — and for those it is free, exact, and
never fails to find a frame.

So the default policy is: **a line number for user code and external plugins, a contributor
alone for everything else.** `java-library` set the convention; there is no line to show for
that and no user action behind it. The stack walk stays available for the cases where a
position on a Gradle-internal mutation is genuinely wanted, at microseconds each.

One practical note for anyone changing the interceptor: bump `DECORATION_FORMAT` in
`InstrumentingClassTransform`, and expect to clear instrumented-jar caches in a dev loop —
the bump alone did not invalidate the integration test's transform cache here.

What is and is not covered is a question of **how the caller was compiled**, not of whether
it is a script or a plugin:

| Caller | Call site |
|---|---|
| Java plugin in `buildSrc` or a plugin jar | yes |
| Kotlin DSL build script (`prop.set(x)`) | yes |
| Groovy build script, either `prop = "x"` or `prop.set(x)` | no |
| Gradle's own distribution code | no — not instrumented |

Groovy is the odd one out because it compiles *both* forms through dynamic dispatch rather
than a plain `INVOKEINTERFACE`, so there is no JVM call site to rewrite. Covering it needs
the Groovy call interception path, which is a separate mechanism.

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
  `PropertyHost.NO_OP` and record nothing. Measured on gradle/gradle: 64% of all properties
  and 65% of all mutations are outside project scope and therefore invisible. See
  "Tracking coverage" above for what that population actually consists of.
- **Per-script identity for build authors.** `ContributorKey.BUILD_AUTHOR` collapses every
  build script to one contributor, per spec §4, so `lib/build.gradle` and the root
  `build.gradle` are distinguished in the message text but not in identity. If
  collaborative authorization ever needs to tell them apart, the script URI is already on
  `UserCodeSource.Script` and is simply discarded for this case.

## Cost

Measured on this branch, not estimated. Time is min-of-5 over 100,000 mutations of 20,000
properties; retained size is a heap delta over 500,000 live properties. "Baseline" is the
same benchmark with the two provenance fields stripped out of `AbstractProperty`.

| | ns per mutation | retained per property |
|---|---|---|
| Baseline (no provenance code) | 3.4 | 63.8 B unset / 80.1 B set |
| Provenance compiled in, flag **off** | 5.1 (**+1.7**) | 72.2 B unset / 88.1 B set (**+8 B**) |
| Flag **on**, one mutation | 13.7 (**+10**) | **+1 B** |
| Flag **on**, two or more mutations | 13.7 | +80 B, then ~4–5 B per record |
| Locations on | + ~7 µs per capture, capped at 2000 captures | unchanged |

A single mutation is held as the interned `MutationRecord` itself, so it costs no allocation
at all; only a second mutation promotes to a `MutationTrace`. That matters because the
average configured property in the gradle/gradle build is mutated 1.18 times.

To turn that into build impact, a 30-subproject `java-library` build running `assemble`
performs **1,951 property mutations** and creates **4,480 properties** (measured with
temporary counters): roughly 65 mutations and 150 properties per subproject.

Extrapolating to a 1,000-subproject build (~65,000 mutations, ~150,000 properties):

- **flag off**: ~0.1 ms of extra time, ~1.2 MB of extra heap;
- **flag on**: ~0.6 ms of extra time, and a few MB of traces;
- **locations on**: ~2 ms total, because the budget caps captures at 2,000.

So **time is not the problem in any mode** — the whole feature is microseconds on a large
build. The cost that matters is memory, and specifically the 88 B trace.

That 88 B is `MutationTrace` + an `ArrayList` + its backing array, allocated on the first
mutation. Most properties are mutated once, so the obvious fix is to hold a single
`MutationRecord` reference directly and only promote to a list on the second mutation,
which would take the common case to zero extra allocation. That is the first thing to do
before considering default-on.

The +1.7 ns and +8 B paid with the flag **off** are the price of compiling the feature in at
all: one boolean field, one reference field, and a branch on the mutation path.

### Tracking coverage on the gradle/gradle build

Cost is only half the question; the other half is how much of the build is actually seen.
Measured on the same run:

```
properties created with a tracking host      95,003   (36%)
properties created with a non-tracking host 168,248   (64%)

mutations attributed to a contributor        99,936   (35%)
mutations tracked but unattributed              549   ( 0.2%)
mutations on an untracked property          184,310   (65%)
```

**Only about one property mutation in three is visible.** The reason is structural rather
than subtle: `ProjectScopeServices` is the *only* place that provides a tracking
`PropertyHost`. Everything created outside a project — `BasicGlobalScopeServices` and the
worker services both return `PropertyHost.NO_OP` — records nothing at all.

Sampling where the untracked properties are created (first 60,000 creations, which the top
entries almost entirely account for):

| count | created by |
|---|---|
| 22,494 | `DefaultMutableAttributeContainer` via `DefaultAttributesFactory.mutable` |
| 20,739 | `IsolatedManagedValue.isolate` — value isolation re-creating properties |
| 6,922 | `DefaultManagedObjectRegistry.newInstance` |
| 4,627 | `FreezableAttributeContainer` |
| 1,449 | `DefaultDependencyLockingProvider` |
| 600 | `DirectoryPropertyManagedFactory.fromState` via isolation |
| 576 + 288 | a third-party plugin's extension calling `ObjectFactory.property` |
| 414 + 138 | `DefaultCacheConfigurations` (settings scope) |
| 272 + 139 | `DefaultDependencyResolutionManagement`, `DefaultResolutionStrategy` |

That splits into three quite different populations:

1. **Internal machinery that arguably should not be tracked** — attribute containers and the
   managed object registry account for ~34,000 of the sample. These are not properties a
   user configures.
2. **Isolation and deserialization re-creating properties** — another ~21,000. Provenance
   genuinely belongs to the original mutation, not to the copy. This is the same mechanism
   that erases provenance under the configuration cache, now with a number attached.
3. **Real user-facing configuration that is silently missed** — properties created from a
   settings-scope, build-tree-scope or otherwise non-project `ObjectFactory`, including a
   third-party plugin's own extension objects. These are exactly the ones a user would
   expect to be attributed.

The 549 tracked-but-unattributed mutations are a small population with a clear shape: 343 of
them come from the Kotlin plugin mutating properties from its own asynchronous compiler
runner — the "plugin's own thread" gap, showing up in a real build — and most of the rest
are Gradle constructing internal objects with no user code on the stack.

**What this means.** The headline "35% attributed" understates the useful coverage, because
much of the remainder is machinery no one would want attributed. But population 3 is a
genuine gap, and closing it is mostly a wiring change: settings and build scope need a
tracking `PropertyHost` too, which is straightforward because `UserCodeApplicationContext` is
cross-build-session scoped and does not need a project. The one obstacle is that
`MutationOriginRegistry` is build-tree scoped while the global host is global scoped, so the
registry would need to move or the host would need to be defined per build.

### Cost on the gradle/gradle build

A distribution was built from this branch with temporary counters and run against
gradle/gradle itself (`gradle help`, 252 subprojects, isolated projects and the
configuration cache disabled so every project configures):

```
properties created                       263,281
properties configured at least once       85,242
properties mutated more than once         13,703
mutations recorded                       100,485
longest trace                                  8 records
mutations dropped by the 32-record cap         0
```

The longest trace on this build is 8 records and nothing hits the per-property cap, so
**collaborative retention — every mutation kept, no cap — costs exactly the same here as the
diagnostics mode does.** The distinction only starts to matter on a build that mutates one
property more than 32 times.

Combining those counts with the measured per-property rates:

| | added heap | added time |
|---|---|---|
| No provenance in the codebase at all | 0 | 0 |
| Compiled in, flag **off** | 2.2 MB | ~0.2 ms |
| **Plugin-id-only, full retention** | **3.3 MB** | ~1 ms |
| Line numbers as implemented (capped at 2,000 captures) | 3.5 MB | ~14 ms |
| Line numbers on every mutation (uncapped) | **12 MB** | **~720 ms** |

Reading the rows:

- The 2.2 MB is the two extra fields on 263,281 properties, paid whether or not provenance
  is switched on. It is an upper bound, since that is properties *created* over the build
  rather than simultaneously live.
- Plugin-id-only adds ~1.1 MB on top: 71,539 properties hold a single shared record and cost
  nothing, and 13,703 traces cost 80 B each.
- Uncapped line numbers add ~9.8 MB, because every one of the 100,485 mutations needs its own
  record (~86 B including its location string) instead of a shared one. The **time** is the
  real objection though. Measured with the budget lifted, all 100,485 captures cost **718 ms**
  at a mean of **7.2 µs** each — far more than a microbenchmark suggests, because 22% of them
  find no user frame at all and walk the full 50-frame cap for nothing.
- Capping locations, as implemented, keeps both to noise.

For scale, a daemon running this build holds several GB, so even the uncapped 12 MB is well
under a percent of heap. Plugin-id-only provenance is, on this evidence, close to free.

### Earlier measurement, before the single-record fix

A distribution was built from this branch with temporary counters and run against
gradle/gradle itself (`gradle help`, 252 subprojects, isolated projects and the
configuration cache disabled so every project is configured):

```
properties created  263,281
properties with at least one recorded mutation   85,242
mutations recorded  100,485
```

That is ~1,000 properties and ~400 mutations per subproject, and an average trace length of
**1.18 records** — the overwhelming majority of configured properties are configured exactly
once.

Applying the measured rates:

| | added heap |
|---|---|
| The two fields, whether or not the flag is on | ~2.2 MB |
| Flag on, **as implemented** | **~1.2 MB** |
| Flag on, had the trace been allocated eagerly | ~6.9 MB |
| Flag on, one record per mutation (locations on every record) | ~14.3 MB |

Time is ~0.9 ms for all 100,485 mutations, against a configuration phase of over three
minutes.

Two caveats. The 263,281 is properties *created* over the whole build, not simultaneously
live, so the 2.2 MB field cost is an upper bound. And a Gradle daemon running this build
holds several GB, so even the unoptimised 6.9 MB is around a tenth of a percent of heap.

The second and third rows are the same design measured before and after holding the first
record directly in the property's existing field. Because the average trace is 1.18 records,
that removes about 85% of the cost: the eager `ArrayList` was paying for a list that almost
never gets a second element. Total for gradle/gradle with the flag on is therefore about
**3.4 MB**, of which 2.2 MB is paid whether the flag is on or not.

### What a full trace would cost

The numbers below were measured before the single-record optimisation, so the first row is
now +1 B rather than +81 B; the per-additional-record rates are unchanged and are the point
of the table. Collaborative mode needs the
*complete ordered* update trace retained while the property is still mutable, so it is worth
knowing how the cost scales with trace length. Measured over 200,000 live properties, as
bytes per property above an untracked baseline of 87.6 B:

| records per property | interned records | one record per mutation |
|---|---|---|
| 1 | +81 B | +152 B |
| 2 | +81 B | +241 B |
| 4 | +81 B | +369 B |
| 8 | +104 B | +681 B |
| 16 | +145 B | +1297 B |
| 32 | +170 B (est.) | **OutOfMemoryError** |

Two things fall out of this.

**Interning is what makes a full trace affordable.** With records interned per
(contributor, kind), a trace costs ~81 B for the first record and then roughly 4–5 B per
additional one — it is a list of shared references, and the first four fit the `ArrayList`'s
initial capacity. Without interning it is ~80 B *per record*, a 20× difference, and 200,000
properties × 32 records exhausted the heap.

**So the retained record must carry nothing per-occurrence.** Anything that varies per
mutation — a call site, an `ApplicationId` — makes every record a distinct object and puts
you in the right-hand column. This matters for the specification: §3 puts `applicationId` on
the mutation occurrence rather than the interned origin, which is exactly what defeats
interning. Since §4 also says the application ID is *not* used for contributor ordering, it
should be left out of the retained trace, or held in a parallel `int[]` at 4 B per record,
rather than turning every record into an object. Call-site locations are diagnostics and can
stay budget-capped, as they are here.

Applying the measured rates to a 1,000-subproject build extrapolated from the java-library
measurement (~65,000 mutations, ~33,000 configured properties), a complete retained trace
costs roughly **3 MB** with interned records and roughly **8 MB** without. The
caveat is that this build is trivially small per project; a build with heavy plugins will
have many times more mutations, and the rates above are what should be applied to it, not
this total.

Two mitigations the specification already allows:

- §10 scopes the full ordered trace to *collaborative* properties; ordinary properties keep
  only compact effective provenance, which is what this prototype does.
- The trace is needed while the property is mutable and being validated. After lifecycle
  closure it can be released, so this is peak configuration-time memory, not permanent
  retention.

The first of those container changes is now implemented: the first record is held directly
in the property's existing field, so the common case allocates nothing. A collaborative
trace should additionally be an `int[]` of interned record IDs rather than an
`ArrayList<MutationRecord>`, which would take the multi-record case from ~80 B to ~40 B plus
4 B per record.

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

1. Persist provenance across the configuration cache, without which the feature is blank in
   any build that uses it.
2. Attach provenance to collection contributions, which unlocks the ordered update trace
   that collaborative mode validates.
3. Give `UserCodeSource.Script` a role rather than a boolean, so init and settings scripts
   stop being classified as the build author.

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
