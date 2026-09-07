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

# Property provenance: D1 origin-only diagnostics checkpoint

D1 follows package-separation commit `080019473b9` and the updated roadmap at
`1e13c84d1024dc6c69c538f8b62db3e61993fb6d`. The engine and semantics baselines remain
those recorded in S0/S1. This checkpoint stops before D2 transport; it adds no source
line capture, collaboration, authorization, ordering or additional property scopes.

## Activation and requested explanations

The existing internal switch now enables origin-only failure reporting as well as
tracking for project-owned scalar properties:

```sh
./gradlew --no-configuration-cache -Dorg.gradle.internal.property-provenance=true help
```

This example is for a consumer build. The Gradle repository's own checks retain their
existing configuration-cache/Isolated Projects settings. D1 does not claim provenance
on consumer configuration-cache hits; transport is the next milestone, D2.

A missing required `get()` or rejected scalar mutation automatically includes
`Failure trace to source`. Successful builds print no reports. An explicit internal
explanation seam is available on enabled properties and their snapshots:

```groovy
println tracked.configurationTrace
println tracked.shallowCopy().configurationTrace
```

These are internal experimental getters on `DiagnosticProperty` and
`DiagnosticProvenanceSnapshot`, not additions to public `Property`/`Provider` APIs.
They return a string; the caller chooses whether to print it. Java/Kotlin callers
can use the internal diagnostic type or pass a descriptor view to
`ProvenanceRenderer.configuration(view)`. Explanation never evaluates a provider,
executes a transform, queries producer tasks or formats configured values.

## Reporting contract and separation

`ProvenanceRenderer` remains in the provider-independent `internal.provenance` package.
It consumes only `EffectiveProvenanceView` and an optional `FailedOperation` describing
a report-local failed call. The entire package, including the renderer, compiles using
only the JDK and JSpecify annotations. `FailedOperation` is not an accepted occurrence,
a mutation history entry, an ordering constraint or an authority token.

The renderer emits stacktrace-like `at` frames in reverse effective-update order,
followed by the selected source. A typical configured view looks like:

```text
Configuration trace to source for extension.message (build 'build', project ':target'):
    at update map [plugin 'second'; source build 'build', scope ':source']
    at update map -> map [plugin 'first'; source build 'build', scope ':source']
    at explicit source [plugin 'source'; source build 'build', scope ':source']
Shadowed configuration (not selected):
    at convention [plugin 'defaults'; source build 'build', scope ':source']
Local configuration only; provider dependencies and failure causality are not inferred.
```

Failure reports use the same projection, with a failed-operation frame when applicable.
The original problem remains before the report. Source build/scope stays separate from
the target's owner. Named properties use their model display name; anonymous properties
keep their opaque local token. Plugin IDs, plugin-class fallbacks and script origins
come from the existing attribution descriptors, not JVM stack walking or filename-role
inference. Existing Gradle error locations may still show script lines; those are not
new provenance line capture.

Unconfigured sources, unattributed bindings, unavailable provenance and known bindings
with unknown authors remain distinct. An explicit missing provider remains the explicit
source and never selects a fallback convention. Captured convention roots are identified
and are not duplicated under shadowed configuration. Unclassified replace bindings
retain their partial-coverage reason, including after subsequent recognized updates.
Repeated occurrences from the same author remain repeated frames.

Output is bounded to 64 update frames, 16 compound shape/details entries per category
and 512 input characters per descriptor label. Earlier-update omission is explicit;
the selected root, separate shadowed section and partial-coverage notes remain visible.
Control characters are escaped/replaced to keep labels readable. These are rendering
limits only: no accepted sequence or effective view is truncated or rewritten.
There is no upstream provider expansion in D1, and no claim of evaluated failure
causality. Future collaboration must validate its full correctness state independently
of these display limits.

## Property integration and failure preservation

`DefaultPropertyFactory` selects `DiagnosticProperty`, a field-free subclass of
`AttributedProperty`, only for an enabled project provenance host. The state adapter
stays separate from reporting. Direct internal `AttributedProperty` construction remains
available for descriptor-only tests/integration. Disabled factories still construct
exactly `DefaultProperty`.

`DiagnosticProperty` adds failure-only guards around required queries and scalar
mutation entry points. Value/provider `set` and `convention`, unset operations,
convention promotion, aliases and internal replace retain ordinary acceptance checks.
Rejected attempts do not append or replace accepted facts. A null-returning replace
uses the existing base clear operation without reentering the public set reporting
guard, so its failed call is correctly labelled `replace`.

`PropertyProvenanceDiagnostics` is a reusable public internal failure adapter. It
preserves MissingValueException/IllegalStateException/IllegalArgumentException categories,
keeps the original exception object as its direct cause and preserves the original
cause chain. Already-decorated failures are not decorated again. Unsupported failure
categories remain unchanged. This gives C1 a shared integration point: its original
conflict problem can carry conflict details while reusing the same provenance report.

A mutable property's failed-call attribution is obtained only on failure. If lookup is
unavailable, the report says `unknown caller origin`; it does not mask the original
rejection. **After finalization, caller origin is also unknown**, because the property
has intentionally released its runtime attribution host. Accepted source/update origins
remain available from the frozen descriptors. D1 does not retain a project, registry or
application context solely to recover a future failed caller.

`DiagnosticProvenanceSnapshot` is likewise field-free. Its supplier and descriptor
references are copied by the existing snapshot implementation; it retains neither
the original property nor the mutable provenance state. Missing reports and requested
explanations therefore survive copying, successful finalization and finalization-on-read.
Failed finalization preserves the existing mutable state and failure behavior.

No evaluator is added. `getOrNull`, `getOrElse`, presence queries, normal reads, producer
tracking and live upstream inputs keep their existing behavior. No successful operation
formats diagnostics or performs provenance stack capture. Exception construction and
its normal JVM stack capture occur only on failure. Automatic required-query reports apply to tracked property/snapshot `get()` calls;
arbitrary derived or untracked Providers queried directly are not covered. Broader
provider-boundary, transform/unsafe-read/task validation coverage belongs to D3, after D2.

## Validation

636 focused tests pass: 560 model-core unit cases, 58 core regressions and 18 embedded
integration cases. Coverage includes both 230-case ordinary scalar suites (disabled
and tracking-only), the shared ordinary/collaborative test-policy projections, nine
renderer cases and 22 diagnostic-adapter cases.

New checks cover exact reverse report structure, distinct repeated authors, captured
roots, explicit missing versus convention selection, unknown/unconfigured/unattributed/
unavailable states, partial coverage, 4,096-update truncation, bounded labels, lazy
requested explanations, mutation aliases/rejections, null replace, original-cause
identity, duplicate-decoration prevention and unavailable failed-call attribution.
Plugin and settings-origin project configuration reports are verified through the
normal build failure output. Successful builds are silent without an explicit request,
and disabled failures retain their original messages. Java Checkstyle and Groovy
CodeNarc checks pass for affected production/unit/integration/JMH sources.

Reproduce with:

```sh
./gradlew :model-core:test --tests '*AttributedPropertyTest' \
  --tests '*EffectivePropertyProvenanceTest' --tests '*AttributedDefaultPropertyTest' \
  --tests '*SharedProvenanceContractTest' --tests '*DefaultPropertyTest' \
  --tests '*OrdinaryProvenanceStateTest' --tests '*ProvenanceRendererTest' \
  --tests '*DiagnosticPropertyTest' :model-core:checkstyleMain \
  :model-core:codenarcTest --max-workers=4
./gradlew :model-core:embeddedIntegTest --tests '*PropertyAttributionIntegrationTest' \
  --tests '*PropertyProvenanceDiagnosticsIntegrationTest' \
  :model-core:codenarcIntegTest :model-core:checkstyleJmh --max-workers=4
./gradlew :core:test --tests '*PropertyProvenanceRegistryTest' \
  --tests '*DefaultPluginManagerTest' --tests '*DefaultPluginContainerTest' \
  --tests '*BuildOperationScriptPluginTest' --tests '*ScriptPluginFactorySelectorTest' \
  --tests '*ProjectBackedPropertyHostTest' --max-workers=4
```

## Focused performance evidence and limits

[Allocation/retention and standalone-compilation evidence](testing/performance/provenance/d1-probe-20260907.json)
uses three rotated forks, five warmup/five measured batches, JDK 25.0.4 on Linux
aarch64, a fixed 256 MiB heap and Serial GC. Attribution is prebuilt/shared in this
probe; whole-build registry integration is covered separately by the smoke builds.

D1 adds no property or snapshot fields. Enabled property+holder layout remains 48+48 B;
disabled property layout remains 40 B. Successful binding, copy and replace/discard
allocation remain 80, 40 and 248 B respectively. Building 4,096 updates still allocates
688,416 B enabled versus 393,296 B baseline/disabled, with the same constant increment
as before D1. Controlled weak-reference checks collect all tested unwanted property,
supplier and host references across all forks.

Failure work is measured separately. A missing required query allocates approximately
4,776–4,845 bytes enabled versus 1,648 B baseline;
a rejected finalized mutation allocates 4,848 B enabled versus 1,296 B baseline.
Formatting a prebuilt requested view allocates 1,480 B with zero updates, 6,592 B with
eight updates and 44,792–52,712 B for the bounded projection of 4,096 updates. These
figures include no extra successful-read work; full failure samples include exception
construction and decoration. They are allocation observations, not retained-heap claims.

[The short four-read JMH check](testing/performance/provenance/d1-dispatch-jmh-20260907.json)
reports approximately zero bytes/op for disabled, origins-enabled and mixed dispatch.
Means and reported errors are 15.473 ± 0.244, 15.992 ± 0.445 and 15.322 ± 0.288 ns/op
respectively (two forks; three one-second warmups and measurements). The enabled mean
is higher in this run, but the intervals overlap. This does not resolve the proposed
1% review threshold or establish production read overhead.

[The first matched smoke builds](testing/performance/provenance/d1-smoke-20260907.json)
use the unmodified S0 ZIP, the candidate with diagnostics disabled and the same candidate
with origins enabled. The identical workload configures 1,000 project scalar properties,
eight updates each, finalizes half and reads all from a task alongside `help`. Every
run verifies the same checksum and absence of unsolicited reports. Two isolated daemons
per variant use equal-length directory layouts, separate user homes/script caches,
three warmups and three measurements; variant order is rotated. Configuration and build
caches are disabled for these consumer builds. All six daemons are distinct and stopped.

| Per-daemon median client wall time | S0 baseline | Candidate disabled | Origins enabled |
|---|---:|---:|---:|
| Fork 0 | 0.330 s | 0.326 s | 0.331 s |
| Fork 1 | 0.352 s | 0.348 s | 0.332 s |

There is no repeatable slowdown across these smoke forks, so no profiling campaign was
started. This small workload includes client startup/communication, is not a release
benchmark and cannot establish a production percentage or whole-daemon heap budget.
The allocation, read and smoke evidence is tied to source/class/artifact hashes in
[the D1 evidence manifest](testing/performance/provenance/d1-evidence-20260907.json).
Historical S0–S3/refactor measurements remain unchanged.

Reproduce the bounded checks with:

```sh
./gradlew :model-core:compileJava :core:compileJava :model-core:jmhJar \
  :distributions-full:binDistributionZip --max-workers=4
python3 testing/performance/provenance/run-effective-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output /tmp/d1-probe.json
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-*-jmh.jar \
  PropertyAttributionBenchmark -prof gc -rf json -rff /tmp/d1-dispatch.json \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseSerialGC'
python3 testing/performance/provenance/run-diagnostic-smoke.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --candidate-zip packaging/distributions-full/build/distributions/gradle-9.9.0-bin.zip \
  --output /tmp/d1-smoke.json
```

## Review boundary and next step

Review the report wording/display bounds, internal explanation seam, and honest
unknown-caller fallback after finalization. None blocks the implemented D1 scope.
D2 is next: descriptor/occurrence transport through recreation, isolation and
configuration-cache store/hit, starting with these origin-only reports. D3 broader
coverage and D4 Java/Kotlin lines follow D2; Groovy lines remain D6, last. Collaboration
may reuse D1 now for C1 and must reuse D2 before C3. No consumer path beyond D1 has
been started in this checkpoint.
