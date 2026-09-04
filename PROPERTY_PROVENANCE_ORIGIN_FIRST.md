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
| Deferred callbacks | Task registration/configuration, containers, listeners, withPlugin | Direct/deferred task and nested-plugin examples covered; other boundaries need tests |
| Languages | Java, Kotlin, Groovy, precompiled and applied scripts | Origins must be language-independent; no new Groovy line interception |
| Scalar/file operations | set/value, convention, unset/null, unsetConvention, promotion | Selected binding/copy and rejected-operation regression coverage added |
| Collections | List/set/map replacement and individual contributions | Contribution provenance not implemented; keep repeated contributions distinct |
| Provider evaluation | map, flatMap, zip, orElse, filter, opaque providers | Only limited property/map traversal; do not infer general failure causality |
| Lifecycle | Copy, replace, finalization, finalize-on-read | Copy fixed; upstream provenance can still be lost during finalization |
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
to choose a branch. Truncation and unsupported boundaries should become explicit.

Finalization must eventually snapshot the relevant provenance independently of the
discarded supplier graph, without retaining obsolete providers for diagnostics.
Copied local bindings are fixed in this increment; that is not a complete solution
for lifecycle and cache transport.

No self-reference semantics, collaborative authorization/ordering, configuration-cache
persistence, additional tracking scopes, or ConfigurableFileCollection support are
introduced here.

## Performance plan

The hot-path goals are shared origin records, lazy local state, no evaluation, no value
formatting, and no per-mutation location work in origin-only mode. Superseded explicit
origins are released on unset. Record tables are interned per user-code source and
published fully initialized for safe concurrent reads. A full history is not retained.

`PropertyProvenanceBenchmark` compares disabled, origin-only, and optional-location modes
for creation plus binding, repeated replacement, and convention plus explicit binding.
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

## Next milestones

1. Complete project-scoped origin correctness and callback/property-operation coverage.
2. Introduce stable origin descriptors and explicit source/target build and script roles.
3. Add settings/init and other hosts with correct service lifetimes, one boundary at a time.
4. Cover collection contributions and cache/isolation transport as explicit workstreams.
5. Complete causal provider tracing and then extend optional location detail.
