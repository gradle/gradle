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

# Property provenance: package boundaries and review guide

This refactor follows S3 commit `af6f959a4f5`. It separates the provider-independent
provenance model and ordinary state transitions from the property/provider adapter.
It does not implement D1 or change ordinary property semantics.

D1 reporting is documented separately in [the D1 checkpoint](PROPERTY_PROVENANCE_D1.md).

## Where to start reading

| Responsibility | Package / entry point |
|---|---|
| Scoped attribution and distinct accepted facts | `org.gradle.api.internal.provenance`: `Attribution`, `MutationOccurrence`, `SemanticOperation` |
| Ordinary source/convention/update transitions | `org.gradle.api.internal.provenance.OrdinaryProvenanceState` |
| Read-only projection for diagnostics | `org.gradle.api.internal.provenance.EffectiveProvenanceView` and `UpdateSequence` |
| Acceptance and lifecycle bridge | `org.gradle.api.internal.provider.AttributedProperty` |
| Supported map recognition | `org.gradle.api.internal.provider.PropertyUpdateClassifier` |
| Captured supplier delegation | `org.gradle.api.internal.provider.ProvenanceSnapshot` |
| Project attribution bridge | `org.gradle.api.internal.provider.PropertyProvenanceHost`; core's `AttributedProjectPropertyHost` and `PropertyProvenanceRegistry` |

Read the descriptor types and `OrdinaryProvenanceStateTest` first. The state tests need
no Property, Provider, host, project or application context. Next read `AttributedProperty`
and its integration tests to check when accepted facts are supplied. Finally read the
classifier and supplier snapshot for their narrowly supported provider behavior.

The `org.gradle.api.internal.provenance` package contains only Java/JSpecify dependencies
and its own types. All eleven source files, including package-info, compile together
using only the JDK and the JSpecify annotation jar. The focused probe runner performs
this check before measuring allocations, so a dependency on Gradle's provider engine
cannot silently enter this package. D1 rendering can consume these descriptors/views
without depending on Property or Provider implementations.

## Responsibilities at the boundary

The property engine still owns value selection, validation, evaluation, producer
tracking and lifecycle. `AttributedProperty` runs the existing operation first, then
supplies attribution and accepted facts to the state. Rejections supply no accepted
fact. The adapter passes whether configuration is explicit; state never infers that
from value presence. Queries do not request attribution or evaluate suppliers.

`OrdinaryProvenanceState` owns occurrence numbering and effective source/convention/
update descriptors. It receives ordinary accepted operations rather than callbacks
or runtime objects. Binding a new source cuts ordinary updates; convention promotion
retains binding identity; updates share immutable prefixes and their captured roots.
Its methods are an internal accepted-fact boundary, not independent authorization or
value-lifecycle checks. A caller must already have accepted the operation. The state
is not a duplicate evaluator or a collaborative source-rebinding policy.

The map classifier remains beside provider internals. It recognizes the same bounded,
exact built-in map chains and returns a semantic operation; unrecognized results still
mean partial coverage. Single-map and unclassified descriptors are shared constants,
independent of origins. Compound shapes are created only when needed.

`ProvenanceSnapshot` stays in the provider package because it holds a supplier and
delegates its evaluation. It captures only the required descriptor references, never
the mutable state holder or original property. Copies still allocate no accepted
mutations. Reporting views remain demand-created, with no whole-history copy on append.

Finalization records the descriptor checkpoint before calculation and freezes it only
after calculation succeeds. The adapter then releases its attribution host. Failed
finalization leaves mutable state intact. Normal reads retain their existing path.
The extracted state adds no successful-read hook and is allocated only for opt-in
tracked scalars. Untracked properties still use `DefaultProperty`.

## What moved, and what stayed unchanged

Descriptor types and their shared-contract tests moved from `provider.provenance` to
`internal.provenance`. The adapter, host interface and supplier snapshot moved to
`internal.provider`. Ordinary transitions moved out of `AttributedProperty` into a
separate state holder. Core wiring, integration fixtures and runnable probes now
import the new locations. Internal class names changed; no public API changed.

No changes were made to `DefaultProperty`, `AbstractProperty` or `ValueState` in this
refactor. The S3 semantics, origin-only scope and captured-root limitations remain.
Historical S0–S3 evidence is unchanged and belongs to the source revisions recorded
in those artifacts. Updated runners support the new source layout; use the older
commit to reproduce the older source tree exactly.

## Validation and measured cost

598 tests pass: 529 model-core unit cases (including seven new provider-independent
state cases), 58 core regression cases and 11 embedded integration cases. Existing
ordinary/collaborative test-policy projections, enabled/disabled scalar contracts,
copy/producer/lifecycle checks and provenance scenarios still pass. Java Checkstyle
and Groovy CodeNarc checks pass for affected production, unit, integration and JMH
sources.

A test rerun initially found the old internal class locations in the integration
user home's generated Gradle API jar. Deleting that specific generated jar allowed
it to rebuild and all integration cases passed. The synthetic development version
was unchanged across the package move; this was a stale test artifact, not a runtime
provenance transport test. Configuration-cache support remains deferred to D2.

[Allocation and retention results](testing/performance/provenance/decoupled-probe-20260907.json)
use the same bounded three-fork controls as S3: JDK 25.0.4, Linux aarch64, a fixed
256 MiB heap and Serial GC. They also record the standalone compilation result.

| Dimension | S3 before extraction | After extraction |
|---|---:|---:|
| Enabled property + provenance holder shallow bytes | 80 + 0 | 48 + 48 |
| Disabled property shallow bytes | 40 | 40 |
| Steady accepted binding allocation | 80 | 80 |
| Copy allocation | 40 | 40 |
| Replace/map then discard allocation | 248 | 248 |
| Construct/bind with zero updates | 272 | 288 |
| Construct/bind with eight updates | 1,616 | 1,632 |
| Construct/bind with 4,096 updates | 688,400 | 688,416 |
| Construct/bind/finalize allocation range | 432–496 | 448–512 |

The separation costs one additional object and **16 additional shallow bytes per
tracked property**, not per update. The update-chain increment remains 168 B/update
(96 B of baseline engine work plus 72 B of provenance/snapshot overhead). Copies,
bindings and replacement/discard work show unchanged allocation. Finalization's
allocation range includes JIT escape-analysis variation. Each controlled weak-reference
probe again collected all tested unwanted original-property, supplier and host
references across all three forks.

[The short read benchmark](testing/performance/provenance/decoupled-dispatch-jmh-20260907.json)
uses the unchanged four-read enabled/disabled/mixed workload. Read allocations remain
approximately zero; consult its recorded timing intervals rather than interpreting
small mean differences as production regressions or improvements. This is a bounded
refactor check, not a whole-build performance campaign or a new retained-heap budget.
Source/class/artifact hashes and test totals are in the
[evidence manifest](testing/performance/provenance/decoupled-evidence-20260907.json).

Reproduce the semantic checks with:

```sh
./gradlew :model-core:test --tests '*AttributedPropertyTest' \
  --tests '*EffectivePropertyProvenanceTest' --tests '*AttributedDefaultPropertyTest' \
  --tests '*SharedProvenanceContractTest' --tests '*OrdinaryProvenanceStateTest' \
  --tests '*DefaultPropertyTest' \
  :core:test --tests '*PropertyProvenanceRegistryTest' \
  --tests '*DefaultPluginManagerTest' --tests '*DefaultPluginContainerTest' \
  --tests '*BuildOperationScriptPluginTest' --tests '*ScriptPluginFactorySelectorTest' \
  --tests '*ProjectBackedPropertyHostTest' --max-workers=4
./gradlew :model-core:embeddedIntegTest --tests '*PropertyAttributionIntegrationTest' \
  :model-core:checkstyleMain :core:checkstyleMain :model-core:codenarcTest \
  :core:codenarcTest :model-core:codenarcIntegTest :model-core:checkstyleJmh --max-workers=4
```

Reproduce the focused allocation, standalone compilation, retention and read checks with:

```sh
./gradlew :model-core:compileJava :core:compileJava :model-core:jmhJar --max-workers=4
python3 testing/performance/provenance/run-effective-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output /tmp/decoupled-probe.json
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-*-jmh.jar \
  PropertyAttributionBenchmark -prof gc -rf json -rff /tmp/decoupled-dispatch.json \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseSerialGC'
```

## Next milestone and current numbering

The updated roadmap is prototype commit `1e13c84d1024dc6c69c538f8b62db3e61993fb6d`.
The semantic specification revision and S0 engine baseline remain unchanged. Older
checkpoint documents use the previous D2/D4 numbering; the current sequence is:

1. D1: origin-only reporting.
2. D2: isolation/configuration-cache transport.
3. D3: broader diagnostic coverage, requiring D2.
4. D4: Java/Kotlin locations, requiring D2.
5. D5: validation and rollout.
6. D6: Groovy locations, last.

Collaboration reuses D1 before C1 and D2 before C3. D1 remains the next implementation
milestone; this refactor only establishes the reviewable package boundary for it.
