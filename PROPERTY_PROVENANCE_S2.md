/*
 * Copyright 2026 Gradle and contributors.
 *
 * Licensed under the Creative Commons Attribution-Noncommercial-ShareAlike 4.0 International License.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://creativecommons.org/licenses/by-nc-sa/4.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

# Property provenance: S2 review checkpoint

S2 adds opt-in attribution and storage for project-owned scalar properties on
`asodja/provenance-prototype-20260907`, after S0/S1 commit `2356d6a65b0`.
The unmodified engine baseline and pinned semantics remain those recorded in
[the S0/S1 checkpoint](PROPERTY_PROVENANCE_S0_S1.md). This checkpoint stops before S3;
there is no runtime collaboration, effective-plan projection or user-facing renderer.

## Changes and supported boundary

The internal switch is `-Dorg.gradle.internal.property-provenance=true`, default false.
It is read once by a build-scoped registry through `InternalOptions`, not on mutations.
An enabled project host implements `PropertyProvenanceHost`; the public scalar factory
selects `AttributedProperty` only for that host. Ordinary hosts still construct exactly
`DefaultProperty`. No fields or methods were added to `AbstractProperty`, `DefaultProperty`
or `ValueState`, and their compiled bytes still match the preserved S0 distribution.

The enabled subclass stores one latest accepted `MutationOccurrence`, not a history or
an effective provenance view. Existing scalar supplier/convention/selection mutation
methods run their normal lifecycle and type checks before recording. Aliases funnel to
one boundary and produce one occurrence; rejected operations do not request attribution
or replace the accepted record. Lazy validation remains an evaluation failure after an
accepted binding. Queries, presence checks and producer queries have no capture hooks.
Messages and causes remain unchanged; successful builds do not emit provenance reports.
`getLastAcceptedMutation()` and `getProvenanceTarget()` are internal inspection seams.

`Binding(Explicit)` only records what was bound. It does not prove a non-self relationship.
S2 does not classify internal replace/map chains or synthesize structural updates from
provider derivations. Those classifications and effective source/convention selection
belong to S3. The S1 ordinary/collaborative projection tests remain unchanged and passing.

Managed extension/task scalar getters and direct project object-factory properties use
this path. Global/settings-owned properties, file/collection factories, deprecated
any-type reconstruction factories, and direct internal `new DefaultProperty` calls remain
untracked. This prevents rehydration from appearing as an accepted user mutation and
keeps actual transport support deferred. The factory's existing special
`Property<ExternalModuleDependencyBundle>` compatibility case follows the same factory
path, but this is no claim of collection contribution tracking.

## Attribution and identity

Plugin and script application boundaries supply source context using subclasses of the
existing `UserCodeSource.Binary` and `.Script` types. This preserves their existing
`instanceof` contracts. Disabled applications create the original source types.

- Plugin ID is preferred, with a plugin-class-name fallback. The application target
  supplies source build/project scope. A property configured elsewhere keeps its own
  project scope separately through the project host.
- `ScriptPluginFactorySelector` passes `topLevelScript` through to the application
  boundary. Top-level project/settings/init roles follow the target kind; applied
  scripts are explicitly applied scripts. Roles never come from a basename.
  Configuring script URIs are retained independently of the property's owning project.
- Project build scripts in one contributor domain share `BuildAuthor`, while retaining
  distinct origins/scopes. Settings and environment roles and applied-script contributors
  use separate key kinds. Missing script URI cannot invent a stable applied contributor.
- Each build-scoped registry allocates one opaque session-local contributor-domain UUID.
  It is independent of display labels, build paths and application tokens. Separate
  registries with identical plugin labels do not alias. This defines diagnostic identity
  within this run, not stable cross-build/cache identity or plugin alias canonicalization.
- Each tracked property gets a build-registry-scoped occurrence namespace, then local
  accepted sequence numbers. Repeated bindings reuse attribution but not occurrence
  identity. There is no global map of properties or eager table of operations per origin.
  Mutable-copy namespace branching remains S3 work; S2 copies do not claim provenance.
- The registry caches descriptors lazily by existing application ID. Keys contain only
  that ID; values contain only the S1 descriptors and a string application token.
  The cache holds no user-code source, runtime application, plugin instance, transform
  or class loader. It is build-scoped; disabled registries allocate no descriptor cache.
  Sources/hosts still have their ordinary runtime lifetimes.

Existing Gradle-managed deferred application contexts are reused. Nested callbacks and
plugin application restore the registrant's context, including exceptional exits. No
new ThreadLocal, stack walk, source-line token or callback wrapper is introduced for
provenance. Absent or unsupported unscoped contexts produce `UNKNOWN`, rather than
inferring a contributor from a label or the configured target. Ambient attribution is
best-effort diagnostic information: arbitrary private callback stores/executors are not
made trustworthy, and no attribution record conveys authorization.

`ScopeIdentity.buildIdentity` currently uses build-tree paths from
`ConfigurationTargetIdentifier`. These describe source/target scope within the supported
run; the separate contributor domain prevents unrelated logical contributors aliasing.
Persistent scope identity, relocation and comparisons across independent build trees
remain explicitly unsupported. Included-build correctness/transport is not claimed.

## Validation

Final passing checks: **342 cases** (274 model-core unit, 58 core unit, 10 integration),
with no failures or skips in those selected suites. This includes 19 new scalar unit
cases, seven new registry cases and ten new integration cases, alongside the S1 contract
and existing property/plugin/script/host compatibility tests.

```sh
./gradlew :model-core:test --tests '*AttributedPropertyTest' \
  --tests '*SharedProvenanceContractTest' \
  --tests org.gradle.api.internal.provider.DefaultPropertyTest \
  :core:test --tests '*PropertyProvenanceRegistryTest' \
  --tests '*DefaultPluginManagerTest' --tests '*DefaultPluginContainerTest' \
  --tests '*BuildOperationScriptPluginTest' --tests '*ScriptPluginFactorySelectorTest' \
  --tests '*ProjectBackedPropertyHostTest' \
  :model-core:checkstyleMain :core:checkstyleMain \
  :model-core:codenarcTest :core:codenarcTest --max-workers=4
./gradlew :model-core:embeddedIntegTest --tests '*PropertyAttributionIntegrationTest' \
  :model-core:codenarcIntegTest --max-workers=4
./gradlew :model-core:checkstyleJmh --max-workers=4
```

The integration fixtures adapt the prototype scenarios to accepted descriptors, not to
prototype failure text. Coverage includes plugin ID/class, nested application/restoration,
`withPlugin`, `afterEvaluate`, deferred task configuration and nested deferred registration;
root/sibling cross-project script binding; Kotlin top-level and misleadingly named applied
Groovy scripts; settings script configuration; settings plugin `beforeProject` and
`lifecycle.beforeProject`, each direct and nested; settings-owned negative controls;
managed extension/task scalars; enabled file-property exclusion; and disabled messages.

Initial test corrections preserved existing Gradle semantics: String properties sanitize
numeric inputs, so rejection tests now use incompatible Boolean values; nested task
registration moved out of a guarded lazy task configuration into `afterEvaluate`.
The integration task is `embeddedIntegTest`; `integTest` is not a test task in this baseline.

The integration suite explicitly excludes configuration-cache transport and isolated
cross-project configuration. No successful cache-hit, broader property scope or new
self-assignment semantics is inferred from these tests. Full repository suites were not run.

## Focused performance evidence

[Raw allocation/layout samples](testing/performance/provenance/attribution-probe-20260907.json)
and [JMH dispatch samples](testing/performance/provenance/attribution-dispatch-jmh-20260907.json)
are retained with [source/environment identities](testing/performance/provenance/attribution-evidence-20260907.json).
The old S1 evidence remains unchanged.

The standalone probe uses the preserved S0 full ZIP for baseline, and compiled model-core
and core classes over those baseline dependencies for the changed variants. It is not a
second full distribution comparison. Three independent JVMs per variant rotate execution
order, with five warmup/five measured batches. Environment: Linux aarch64, OpenJDK 25.0.4,
`-Xms256m -Xmx256m -XX:+UseSerialGC`. Compilation is outside measured loops.

| Allocation/layout check | Result across the three forks |
|---|---|
| Disabled property / mutable state / finalized state | 40 / 24 / 16 B, matching S0 |
| Disabled direct or factory construction | 64 B/op, matching baseline |
| Disabled binding | 16 B/op, matching baseline |
| Enabled property shallow layout | 56 B (ordinary value state still separate) |
| Enabled factory construction | 360 B/op, including the per-property namespace string and temporary construction allocation |
| Enabled steady accepted binding | 48 B/op: ordinary 16 B binding plus 32 B occurrence |
| Cached attribution lookup | 0 B/op |
| First accepted application | 344 B/op including new scoped source, context registration/restoration, first descriptors/cache entry and binding |
| Matched new-application registration without binding | 120 B/op; first-capture increment is 224 B in this fixture |

First-capture and steady-state measurements are separate. Registry attribution descriptors
are constructed only on first accepted use of an application; replacements in the same
application allocate no descriptor bundle, operation table or history node. Record memory
is not proportional to the number of replacements. Shallow object size is not exclusive
retained heap, and neither these numbers nor string lengths are portable across JVMs.

The short standalone read loops showed substantial JIT sensitivity, including 0/24 B
single-read samples for baseline/changed classes with identical bytecode and 0/96 B for
four mixed reads. These samples are retained, not presented as diagnostic allocations or
used for a slowdown percentage. Their timing noise cannot adjudicate the proposed 1%
disabled review trigger.

A single bounded JMH follow-up used two forks, three one-second warmups and three
one-second measurements per mode, with the GC profiler and the same heap/GC options:

| Four reads (half mutable, half finalized) | Mean ± JMH reported error | Allocation |
|---|---|---|
| New runtime, tracking disabled | 16.336 ± 1.319 ns/op | approximately zero |
| New runtime, all tracked | 16.901 ± 2.617 ns/op | approximately zero |
| New runtime, tracked/untracked mixed | 16.527 ± 1.119 ns/op | approximately zero |

Intervals overlap. This follow-up tests subclass read dispatch; its attribution host is
preconstructed and it does not measure first-origin capture, real build configuration,
line capture or cache behavior. It does not establish zero overhead or a production
regression bound. No long performance campaign or speculative optimization followed.

Reproduce after compiling the two changed modules:

```sh
python3 testing/performance/provenance/run-attribution-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output /tmp/attribution-probe.json
./gradlew :model-core:jmhJar --max-workers=4
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-*-jmh.jar \
  PropertyAttributionBenchmark -prof gc -rf json -rff /tmp/attribution-dispatch.json \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseSerialGC'
```

The S0/S1 budgets remain proposals. Disabled property/state layout and allocation gates
pass. The 32 B occurrence is below the proposed 64 B occurrence+link review budget; no
update link is needed in S2. Enabled construction's namespace allocation is a visible
cost to review with S3's storage/identity work. Plugin/script infrastructure also gains a
registry reference and an enable check per application when disabled: unchanged property
layout is not a claim of zero whole-build compiled-in cost. Production timing, whole-daemon
heap, and class-loader dominator analysis remain outside these focused checks.

## S3 boundary and remaining decisions

S2 is complete for this slice; no decision blocked it. Review the enabled subclass choice,
session-local namespaces, first-capture/construction costs and the integration boundaries
before extending the foundation.

S3 still needs effective source/convention/update records, internal replace classification,
copy identity and successful/failing finalization semantics. In particular, the S2 enabled
subclass retains its provenance host after finalization; S3 must address that lifetime
when replacing live metadata with descriptor-only finalized state. S2's latest accepted
record is not a finalized effective snapshot and must not be rendered as one.

There is no authority, ordering/validation trace, runtime collaboration, line capture,
configuration-cache integration or new ownership scope here. After S3, choose diagnostics,
collaboration or both; collaboration reuses D1 reporting and D4 transport. Java/Kotlin lines
remain D2, and Groovy lines D6 last. Work stops before S3; nothing has been pushed.
