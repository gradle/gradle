# Origin-first property provenance

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

## Attribution contract

The origin is the plugin/script binding a property, not the property creator or the
creator of its assigned provider. Plugin IDs are preferred; plugins without IDs retain
their class display name. Scripts retain the existing user-code source display name,
and absent context is reported as `unknown code`.

Origin, application identity, target scope, operation, and optional location are separate
concepts. This increment still uses display-only records. Stable typed descriptors,
explicit script roles, build identity, and persistence remain follow-up work; display
strings are not contributor authorization identities.

Gradle-managed deferred callbacks should preserve the registrant's context. Private
plugin callback stores, arbitrary executors, and side-effecting provider transforms
are not fully covered: ambient context may describe the invoker rather than the author.
This limitation must not become an authorization mechanism.

## Coverage inventory

The rows below are a roadmap, not a declaration that every case is implemented.

| Dimension | Cases | Current boundary / next step |
|---|---|---|
| Project-owned objects | Extension, task, nested managed and directly created properties | Project host is wired; expand object-factory coverage tests |
| Settings | Script, plugin, extensions and nested objects | Not tracked by this increment; introduce a suitable host and explicit script role |
| Init / Gradle lifecycle | Init scripts, settings/project callbacks | Audit callback attribution and service lifetimes before adding tracking |
| Multiple builds | Included builds, build logic, duplicate IDs/paths | Add stable build identity and tests; source and target builds can differ |
| Services | Shared build-service parameters and runtime state | Audit ownership, lifetime and recreation |
| Isolated execution | Worker, ValueSource, flow-action parameters | Transfer origins across isolation rather than record transport as mutation |
| Other user-code entry points | Tooling model builders, isolated lifecycle actions | Audit callback registration boundaries |
| Internal properties | Attribute/model machinery and temporary copies | Do not indiscriminately enable tracking on every factory |
| Plugin application | By ID, by class, nested application, context restoration | ID and class fallback integration tests; existing user-code context is reused |
| Deferred callbacks | Task registration/configuration, containers, listeners, withPlugin | Integration tests cover named/configureEach, withPlugin, afterEvaluate, projectsEvaluated, taskGraph.whenReady and nested deferred actions; throwing callback context restoration is unit-tested |
| Languages | Java, Kotlin, Groovy, precompiled and applied scripts | Origins must be language-independent; no new Groovy line interception |
| Scalar/file operations | set/value, convention, unset/null, unsetConvention, promotion | Selected binding/copy, replace, null assignment, convention removal and rejected lifecycle-operation coverage |
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

The hot-path goals are shared origin records, lazy local state, no evaluation, no value
formatting, and no per-mutation location work in origin-only mode. Superseded explicit
origins are released on unset. Record tables are interned per user-code source and
published fully initialized for safe concurrent reads. A full history is not retained.
Finalized snapshots reuse the local explicit-source storage slot, so snapshot support
does not add another field to each property's metadata. Already-fixed terminal suppliers
need no graph snapshot. Finalizing a non-terminal supplier adds bounded traversal and
array allocations; benchmark this separately from mutation overhead.

`PropertyProvenanceBenchmark` compares disabled, origin-only, and optional-location modes
for creation plus binding, repeated replacement, convention plus explicit binding, and
binding/finalizing a three-property chain.
It includes application-context restoration and the existing instrumented-call helper.
Use JMH's GC profiler for allocated bytes per operation as well as time:

```text
./gradlew :model-core:jmhJar
java -jar platforms/core-configuration/model-core/build/libs/*-jmh.jar PropertyProvenanceBenchmark -prof gc
```

These are microbenchmarks, not a build-time estimate. A baseline with provenance fields
removed is still needed to quantify disabled-mode object-layout overhead. Measure
retained heap separately from allocation rate, and compare representative warm/cold,
single/multi-project builds before publishing a percentage. Cache-hit performance is
not representative until provenance is persisted correctly. The earlier semantics
repository's measurements describe a different prototype and are not results for this
increment.

### Development measurements: 2026-09-04

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

## Next milestones

1. Complete project-scoped origin correctness and callback/property-operation coverage.
2. Introduce stable origin descriptors and explicit source/target build and script roles.
3. Add settings/init and other hosts with correct service lifetimes, one boundary at a time.
4. Cover collection contributions and cache/isolation transport as explicit workstreams.
5. Complete causal provider tracing and then extend optional location detail.
