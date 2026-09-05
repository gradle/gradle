# Origin-first property provenance

For the proposed foundation shared by diagnostics and collaborative properties, see
the [shared provenance contract](PROPERTY_PROVENANCE_SHARED_CONTRACT.md). This document
continues to describe the implemented ordinary-property prototype and its boundaries.
The [from-scratch implementation plan](PROPERTY_PROVENANCE_IMPLEMENTATION_PLAN.md)
recommends preserving this prototype as a reference and starting the shared implementation
from a clean Gradle baseline, with functional and performance gates at each milestone.

## Scope of this increment

Keep ordinary project-scoped property semantics unchanged. Retain successful source
and convention origins, select the effective binding, and report the failing operation
only on its failure. Preserve the stacktrace-like `Failure trace to source` format,
with shadowed conventions in the separate `Shadowed configuration` section.

Enable origin-only diagnostics with:

```text
-Dorg.gradle.internal.property-provenance=true
```

Source locations are an independent, default-off prototype option:

```text
-Dorg.gradle.internal.property-provenance.locations=true
```

Origin-only mode performs no provenance stack walking and publishes no mutation
call-site context. The existing bytecode interception still calls a small helper;
that helper checks the target property's mode before constructing a callback or
touching location thread-local state. Removing this instrumentation overhead entirely
requires a separate instrumentation/cache-key design. No process-wide location switch
is used. Disabled provenance preserves existing diagnostic messages.

This increment also snapshots a shallow copy's local provenance alongside its supplier,
while preserving live upstream provider semantics. Promoting a convention to an explicit
binding retains the convention's original origin, independently of later convention
replacement. Rejected convention and unset operations identify their attempted operation
without entering successful provenance. Original mutation exceptions remain available
as causes.

Successful scalar-property finalization now snapshots the selected local and upstream
origins before discarding the supplier graph. Finalize-on-read and copies of finalized
properties preserve this snapshot. It contains only diagnostic records and limitation
markers, never provider instances, a property host, or a failed-operation frame.
Failed finalization does not commit a snapshot. This describes the selected graph after
evaluation; mutations performed as side effects inside opaque providers/transforms remain
outside the causal-attribution guarantee.

The [semantic update increment](PROPERTY_PROVENANCE_UPDATES.md) now classifies a
supported map chain rooted at the previous-plan copy of internal scalar `replace()`
as one `MAP_UPDATE`. Repeated replacements retain distinct update frames through
copies and finalization; ordinary source replacement still cuts them. This is a first
shared operation for diagnostics and future collaboration, not a complete correctness
trace or assignment-shaped self-reference support.

## Attribution contract

The origin is the plugin/script binding a property, not the property creator or the
creator of its assigned provider. Plugin IDs are preferred; plugins without IDs retain
their class display name. Scripts retain the existing user-code source display name,
and absent context is reported as `unknown code`.

Origin, application identity, target scope, operation, and optional location are separate
concepts. Successful binding records now share a `PropertyProvenanceOrigin` descriptor:
plugin ID, plugin-class fallback, script URI when available, a display-only fallback,
or unknown. IDs come from `UserCodeSource` metadata, never from parsing display text.
The original display text is preserved so this representation change does not alter
report wording. Failure task frames remain ephemeral display-only descriptors.

Descriptors contain only an enum and strings, not plugin instances, application objects,
or class loaders. Each application source shares its descriptor across operations and
optional call-site occurrences. The record replaces its display-name reference with a
descriptor reference, adding no field to individual properties or binding records.
The registry does not merge different application sources just because their plugin
IDs or display names match.

These are not globally unique contributor keys or authorization identities. Explicit
script roles, source/target build identity, and persistence remain follow-up work. A
script named `settings.gradle.kts` is not necessarily a settings script; roles must come
from application boundaries. URI normalization is lexical, not filesystem resolution,
and does not yet provide relocatable build-relative identity.

Gradle-managed deferred callbacks should preserve the registrant's context. Private
plugin callback stores, arbitrary executors, and side-effecting provider transforms
are not fully covered: ambient context may describe the invoker rather than the author.
This limitation must not become an authorization mechanism.

### Settings-origin configuration, not settings-owned properties

The MVP remains project-scoped regardless of where configuration originates. A settings
plugin can register `gradle.beforeProject` or `gradle.lifecycle.beforeProject`, create a
property using the callback's project object factory, and configure it directly or in a
nested task callback. Its origin remains the settings plugin ID. The project's property
host owns tracking; no settings property host is required.

`SettingsOriginPropertyProvenanceIntegrationTest` covers those callback variants, project
script replacement, successful silence, disabled messages, and a negative control:
properties created by the settings plugin's injected settings object factory remain
untracked. The modern lifecycle test uses existing action isolation/context propagation;
it does not add provenance serialization or configuration-cache persistence.

### Cross-project attribution

`CrossProjectPropertyProvenanceIntegrationTest` covers root-script and sibling-script
bindings, a root plugin's deferred callback on another project's plugin manager,
context restoration after that callback, a finalized chain spanning projects, and
independent applications of the same consumer plugin with serial/parallel task execution.
The selected frame belongs to the code binding the property, not its owning project.
Upstream convention frames survive the project boundary. The opaque source deliberately
counts evaluations: reporting does not evaluate it again, successful evaluation is silent,
and disabled failures retain their original cause.

No runtime changes were needed for these cases. This does not establish cross-build
identity or isolated-project compatibility: the fixture deliberately uses ordinary
cross-project configuration, and identical plugin IDs still have identical report labels.

## Coverage inventory

The rows below are a roadmap, not a declaration that every case is implemented.

| Dimension | Cases | Current boundary / next step |
|---|---|---|
| Project-owned objects | Extension, task, nested managed and directly created properties | Project host is wired; expand object-factory coverage tests |
| Settings-owned properties | Script/plugin extension properties and nested objects | Deliberately untracked; defer until a concrete settings-property diagnostic warrants expansion |
| Settings-origin project configuration | Settings plugin beforeProject and lifecycle.beforeProject, including nested task callbacks | Covered with project-owned properties, without adding a settings tracking host |
| Cross-project configuration | Root/sibling script, deferred root plugin, upstream/finalized chain, repeated plugin applications | Covered within one build, including serial/parallel task execution; not isolated-project support |
| Init / Gradle lifecycle | Init scripts, settings/project callbacks | Audit callback attribution and service lifetimes before adding tracking |
| Multiple builds | Included builds, build logic, duplicate IDs/paths | Add stable build identity and tests; source and target builds can differ |
| Services | Shared build-service parameters and runtime state | Audit ownership, lifetime and recreation |
| Isolated execution | Worker, ValueSource, flow-action parameters | Transfer origins across isolation rather than record transport as mutation |
| Other user-code entry points | Tooling model builders, isolated lifecycle actions | Audit callback registration boundaries |
| Internal properties | Attribute/model machinery and temporary copies | Do not indiscriminately enable tracking on every factory |
| Plugin application | By ID, by class, nested application, context restoration | ID and class fallback integration tests; existing user-code context is reused |
| Deferred callbacks | Task registration/configuration, containers, listeners, withPlugin | Integration tests cover named/configureEach, withPlugin, afterEvaluate, projectsEvaluated, taskGraph.whenReady and nested deferred actions; throwing callback context restoration is unit-tested |
| Languages | Java, Kotlin, Groovy, precompiled and applied scripts | Origins must be language-independent; no new Groovy line interception |
| Scalar/file operations | set/value, convention, unset/null, unsetConvention, promotion | Selected binding/copy, null assignment, convention removal and rejected lifecycle-operation coverage; internal scalar replace classifies proven map updates |
| Collections | List/set/map replacement and individual contributions | Contribution provenance not implemented; keep repeated contributions distinct |
| Provider evaluation | map, flatMap, zip, orElse, filter, opaque providers | Property/map traversal is explicitly qualified as structural; unsupported boundaries and diagnostic limits are reported |
| Lifecycle | Copy, replace, finalization, finalize-on-read | Successful finalization preserves bounded descriptor-only snapshots; failure/retry and nested finalization are tested |
| Cache / transport | Configuration-cache store/hit, managed-object recreation | Not persisted; must inherit descriptors rather than attribute deserialization |
| Failure kinds | Missing values, rejected mutations, unsafe reads, validation, transform exceptions | Missing-value and mutation-lifecycle failures only; broader error audit pending |
| Concurrency | Parallel projects, nested callbacks, exceptions, independent build lifetimes | Location mode property-scoped; shared record tables fully initialized before publication |

For every new supported boundary, test successful silence, no extra provider evaluation,
explicit-missing versus convention selection, and disabled byte-for-byte message
compatibility. Do not claim all scopes based solely on factory wiring.

## Trace correctness work still required

The existing longer trace follows provider structure, not evaluated failure causality.
A transformation can produce a missing value from a healthy upstream source. Branches
must be explained using evidence from the existing evaluation, never by evaluating again
to choose a branch. Reports now include a `Trace limitations` section for map dependency
paths, opaque/unsupported providers, untracked properties, repeated nodes, and truncation.
Traversal is limited to 128 provider nodes and retained source/convention lists to 128
records each. Known fixed/missing leaves terminate normally. Internal non-null mapping
providers pass through without the user-map qualification.

The longer trace remains structural, not a general evaluation trace. An unsupported
provider queried directly (rather than through a tracked property) may still have no
provenance report. Snapshots preserve the supported selected chain; they do not recover
metadata from opaque wrappers, and do not implement configuration-cache transport.

No self-reference semantics, collaborative authorization/ordering, configuration-cache
persistence, additional tracking scopes, or ConfigurableFileCollection support are
introduced here.

## Performance plan

The hot-path goals are shared origin records, enabled-only inline state, no evaluation, no value
formatting, and no per-mutation location work in origin-only mode. Superseded explicit
origins are released on unset. Record tables are interned per user-code source and
published fully initialized for safe concurrent reads. A full history is not retained.
Finalized snapshots reuse the local explicit-source storage slot, so snapshot support
does not add another field to each property's metadata. Already-fixed terminal suppliers
need no graph snapshot. Finalizing a non-terminal supplier adds bounded traversal and
array allocations; benchmark this separately from mutation overhead.

The [build-measurement harness](testing/performance/provenance/README.md) adds generated
multi-project Java workloads, rotating disabled/origin/location mode timings, and separate
post-GC configured-model live-heap histograms. It distinguishes whole-daemon live bytes
from shallow provenance metadata and does not treat either as a dominator analysis.
The [2026-09-04 results](testing/performance/provenance/RESULTS_2026-09-04.md) measured
roughly 0.13 / 0.64 MiB incremental origin-only live heap for the 10 / 50-project fixtures.
Timing samples overlap and retain a warmup trend, so they do not establish a slowdown
percentage or compiled-in-but-disabled cost.

The subsequent [matching-baseline production experiment](testing/performance/provenance/PRODUCTION_RESULTS_2026-09-04.md)
runs Gradle's own 253-project configuration build with three independent daemons per
variant. The six measured property classes grow by 8 bytes each even when disabled
(about 1.42 MiB at baseline instance counts). Whole-daemon median live heap increases
by 1.77 MiB disabled and 6.33 MiB with origins relative to the no-provenance baseline.
Timing differences change direction between JVM repetitions, so no precise slowdown
claim follows. No runtime optimization was made during that measurement.
The subsequent [compact-registry change](testing/performance/provenance/COMPACT_REGISTRY_RESULTS_2026-09-04.md)
replaces the eager nine-record table with two successful-binding records per origin.
Two follow-up enabled heap checkpoints confirm 1.21 MiB fewer live provenance objects
and containers on the same workload. That change did not remove the disabled property-layout
tax or establish a whole-build timing improvement.

The [enabled-only state change](testing/performance/provenance/ENABLED_STATE_RESULTS_2026-09-05.md)
now removes both fields from `AbstractProperty`. Disabled states retain their original
24-byte layout and shared finalized singleton. Enabled mutable states hold provenance
inline in 32 bytes; enabled finalized states retain the host and diagnostic data without
the old supplier graph. All six measured property classes return to baseline size.
A matched-count production checkpoint shows another 2.42 MiB reduction across properties,
value states and provenance metadata versus the compact-registry version. Binding allocation
falls, but fixed-value finalization allocates 8 more bytes per enabled create/bind/finalize
operation. The report separates these structural/allocation results from whole-build timings.
The repeated production run measured disabled configuration 2.99% above the no-provenance
baseline, with positive differences in all three daemon repetitions. That signal still
needs profiling; restored object layouts do not establish zero disabled timing overhead.

`PropertyProvenanceBenchmark` compares disabled, origin-only, and optional-location modes
for creation plus binding, repeated replacement, convention plus explicit binding, and
binding/finalizing a three-property chain. It also covers unbound construction, fixed-value
finalization, shallow copies, and mixed tracked/untracked mutable/finalized reads.
It includes application-context restoration and the existing instrumented-call helper.
Use JMH's GC profiler for allocated bytes per operation as well as time:

```text
./gradlew :model-core:jmhJar
java -jar platforms/core-configuration/model-core/build/libs/*-jmh.jar PropertyProvenanceBenchmark -prof gc
```

These are microbenchmarks, not a build-time estimate. They do not quantify the
compiled-in-but-disabled object-layout overhead; the matching production experiment
above measures that separately. Measure retained heap separately from allocation rate,
and compare representative warm/cold, single/multi-project builds before publishing a
percentage. Cache-hit performance is
not representative until provenance is persisted correctly. The earlier semantics
repository's measurements describe a different prototype and are not results for this
increment.

### Development measurements: 2026-09-04

These measurements describe the finalization increment (`125b5479ef8`), before the
typed-origin descriptor change.

Linux AArch64, OpenJDK 25.0.4, 256 MiB heap, JMH 1.36, two forks, three one-second
warmups and five one-second measurements per fork. No Gradle test build was running
alongside this measurement. Times below are means with JMH's 99.9% error estimates;
allocated bytes are rounded. Each operation includes construction and binding, not
just an isolated provenance method.

| Operation | Mode | ns/op | Allocated B/op |
|---|---|---:|---:|
| Create and bind one property | Disabled | 13.0 ± 0.2 | 72 |
| Create and bind one property | Origins | 20.3 ± 0.2 | 96 |
| Create and bind one property | Locations | 31.9 ± 0.3 | 152 |
| Create/bind three properties and finalize the target | Disabled | 53.1 ± 1.0 | 256 |
| Create/bind three properties and finalize the target | Origins | 173.1 ± 3.4 | 904 |
| Create/bind three properties and finalize the target | Locations | 214.2 ± 6.2 | 1,144 |

The simple origin-only binding still allocates 24 additional bytes relative to disabled
mode; snapshot support did not increase that metadata footprint. The three-property
scenario adds roughly 120 ns and 648 allocated bytes in origin-only mode relative to
disabled mode. That delta includes provenance for all three bindings and the finalization
snapshot, not just the snapshot. Much of the traversal allocation is temporary;
allocated bytes are not retained-heap measurements. These figures do not establish
whole-build overhead or the cost of the compiled-in-but-disabled fields relative to
unmodified Gradle. Locations here are instrumented runtime call sites, not stack walks.

Reproduce after `:model-core:jmhJar`:

```sh
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-9.9.0-jmh.jar \
  'PropertyProvenanceBenchmark.(createAndBind|bindAndFinalizeChain)' \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -prof gc -jvmArgs '-Xms256m -Xmx256m' \
  -rf json -rff platforms/core-configuration/model-core/build/reports/property-provenance-benchmark.json
```

### Typed-origin allocation smoke check

After introducing shared descriptors, a shorter `createAndBind` run on the same JVM
and heap (one fork, two one-second warmups, three one-second measurements, GC profiler)
still measured 72 / 96 / 152 allocated bytes per operation for disabled / origins /
locations. This checks steady-state binding allocation only: the source descriptor is
created during benchmark setup, so its per-source cost and retained heap are not included.
This smoke check is not evidence of unchanged whole-build performance. Its local JSON
report is `platforms/core-configuration/model-core/build/reports/property-provenance-origin-descriptors.json`.

## Next milestones

The [clean-baseline plan](PROPERTY_PROVENANCE_IMPLEMENTATION_PLAN.md) is the proposed
execution roadmap. The priorities below describe the technical direction, not a decision
to keep expanding this prototype or a claim that the milestones have been implemented.

1. Complete shared S0–S3 milestones using the [shared contract](PROPERTY_PROVENANCE_SHARED_CONTRACT.md),
   then choose diagnostics, collaboration or both. See the
   [first map-update slice and its limits](PROPERTY_PROVENANCE_UPDATES.md) for current support.
2. Diagnostics D1–D6 includes origin reports, Java/Kotlin lines (D2), Groovy lines (D3),
   broader coverage, transport and its own rollout; it requires no collaboration milestones.
3. Collaboration C1–C4 reuses D1 reporting and D5 transport, completing each prerequisite
   first. It does not require line capture or the rest of the diagnostics path. Authority,
   ordering and source rebinding remain separate from diagnostic provider traversal.
4. Defer settings-owned tracking and other new scopes until a concrete use case warrants them;
   neither path expands ownership scope just by adding metadata or source locations.
5. Performance investigation is paused. The historical results above, including unresolved
   timing signals, remain documented rather than being treated as resolved.
