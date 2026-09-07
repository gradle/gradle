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

# Property provenance: S3 review checkpoint

S3 integrates effective local provenance after S2 commit `efdec13fe7e` on
`asodja/provenance-prototype-20260907`. It stops at the shared-completion gate.
No diagnostics renderer, collaboration pipeline, transport, line capture or new
property scope is included. This checkpoint records the implementation prepared for
shared-completion review.

## Baseline and semantics

The unmodified engine remains `d41e66c4e92e3a79dfeeb8e5f2c6912f61d5d036`.
The semantics revision remains `0a695690ac2a4852b7d455a31b1adf830345076c`, reviewed
against upstream in S0; see [the reconciliation and controls](PROPERTY_PROVENANCE_S0_S1.md).
The shared roadmap/contract are those at prototype handoff
`93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a`. The prototype has been preserved.

S3 deliberately preserves the reconciled differences: ordinary null/unset still
selects the current convention, `replace` captures the previous supplier plan,
and `p.set(p.map(f))` remains cyclic. A later convention does not replace a captured
replace root. Upstream inputs within the captured supplier remain live until the
ordinary engine finalizes the value. Proposed live-convention/self-assignment and
monotonic collaborative configuration semantics are not introduced.

## Implementation

`AttributedProperty` maintains the selected source, current convention, persistent
local update sequence and latest accepted mutation. Binding a new source discards
displaced local updates. Convention changes only change the selected source while
ordinary selection is implicit. Promotion preserves the convention's binding
occurrence and records a distinct selection occurrence. Explicit missing providers
remain selected; reporting never queries presence, values or producer tasks.

The internal `replace` path recognizes up to 128 consecutive exact built-in
`TransformBackedProvider` maps ending at the captured previous provider. A compound
map chain produces one accepted occurrence containing its map shapes. Repeated calls
produce distinct occurrences and append one persistent sequence node each. The
classifier reads structural fields only. It does not invoke provider methods or
transformers; ordinary acceptance still performs its existing type checks.

An unrecognized returned provider (including unrelated returns, flatMap, wrappers,
custom subclasses or over-budget chains) becomes `UNCLASSIFIED_BINDING` with partial
coverage and cuts the local update projection. A negative classification does not
prove a non-self relationship or authorize anything. A normal direct binding is
still a binding fact; maps on upstream providers never become local contributions.

`ProvenanceSnapshot` captures the supplier, type and immutable descriptors without
retaining the original property. It delegates value, producer and execution-time
queries to the existing supplier engine in the same evaluation scopes as the ordinary
shallow copy. It adds no evaluator or transform replay. Copies share occurrence and
update-prefix identity and allocate no accepted mutations. Reporting views are created
only on request. The baseline has no independently mutable scalar copy operation;
this milestone does not invent one or a new mutable-branch identity protocol.

Successful final-value calculation freezes a descriptor-only view and releases the
additional attribution host. `AbstractProperty` immediately installs the returned
supplier and finalized state. Failed calculation installs no snapshot and preserves
mutable metadata and the original exception. The checkpoint is captured before
calculation, so opaque evaluation side effects do not relabel the plan being finalized.
Finalization-on-read uses this same path. Ordinary reads gain no provenance hooks.

The ordinary-engine changes are two protected final helpers: provider sanitization
in `DefaultProperty`, reused by ordinary set and attributed replace, and supplier
capture in `AbstractProperty`. The latter preserves baseline shallow-copy behavior:
creating a copy during evaluation does not itself enter an evaluation scope. Neither
helper changes existing evaluation algorithms. `ValueState` remains byte-identical
to S0; disabled property/state layouts gain no fields. All opt-in origin/scope wiring remains as documented in
[the S2 checkpoint](PROPERTY_PROVENANCE_S2.md).

## Validation

591 tests pass: 522 model-core unit cases, 58 core regression cases and 11 embedded
integration cases. The ordinary scalar suite runs both disabled and enabled (230
cases each), including supplier liveness, missing values, producer dependencies,
copy behavior, execution-time values, lifecycle closure and exact existing messages.

The S3 tests exercise SC-02/03/04/06/10/16 and SC-11's captured-root behavior:
compound/repeated updates, upstream/local separation, explicit missing, rejected
replace, promotion, clear/null, partial classification, 128/129-map boundary,
4,096 distinct runtime updates, frozen copies, failed/successful/read finalization,
nested replace callback mutations, copying during evaluation and unchanged direct
self-reference failure.
Descriptor-graph checks reject providers/runtime objects in views. The new integration
case verifies plugin source/update identities and shared occurrences through copy
and finalization. All S1 ordinary/collaborative test-policy projections still pass;
that is descriptor compatibility, not runtime collaboration conformance.

Java Checkstyle and Groovy CodeNarc checks pass for changed production/unit/integration
sources. Reproduce with:

```sh
./gradlew :model-core:test --tests '*AttributedPropertyTest' \
  --tests '*EffectivePropertyProvenanceTest' --tests '*AttributedDefaultPropertyTest' \
  --tests '*SharedProvenanceContractTest' --tests '*DefaultPropertyTest' \
  :core:test --tests '*PropertyProvenanceRegistryTest' \
  --tests '*DefaultPluginManagerTest' --tests '*DefaultPluginContainerTest' \
  --tests '*BuildOperationScriptPluginTest' --tests '*ScriptPluginFactorySelectorTest' \
  --tests '*ProjectBackedPropertyHostTest' \
  :model-core:checkstyleMain :model-core:codenarcTest --max-workers=4
./gradlew :model-core:embeddedIntegTest --tests '*PropertyAttributionIntegrationTest' \
  :model-core:codenarcIntegTest --max-workers=4
```

## Focused performance evidence

[Raw allocation/retention data](testing/performance/provenance/effective-probe-20260907.json)
compares the preserved S0 distribution, compiled-in disabled code and enabled code.
Three rotated forks use JDK 25.0.4 on Linux aarch64, a fixed 256 MiB heap and Serial GC,
with five warmup and five measured batches. Attribution is prebuilt and shared to
isolate S3 storage; real registry/first-origin costs remain separately measured in S2.
Allocation numbers below are bytes per operation, not elapsed-time claims.

| Workload | S0 | Compiled-in disabled | Enabled S3 |
|---|---:|---:|---:|
| Property shallow layout | 40 | 40 | 80 |
| Steady binding | 16 | 16 | 80 |
| Shallow copy | 24 | 24 | 40 |
| Construct, bind, finalize | 80 | 80 | 432–496 |
| Replace/map then discard by binding | 112 | 112 | 248 |
| Construct/bind, zero updates | 80 | 80 | 272 |
| Construct/bind, one update | 176 | 176 | 440 |
| Construct/bind, eight updates | 848 | 848 | 1,616 |
| Construct/bind, 128 updates | 12,368 | 12,368 | 21,776 |
| Construct/bind, 4,096 updates | 393,296 | 393,296 | 688,400 |

The chain increment is 168 B/update enabled versus 96 B baseline: 32 B occurrence,
24 B persistent sequence node and 16 B extra snapshot fields account for the 72 B
difference. Appending does not copy prior occurrences or a reporting view. A focused
first run exposed eager view construction; removing it reduced copy allocation from
184 B (median) to 40 B and chain increments from 312 B to 168 B. The final raw
artifact describes the final implementation only. Compound shape descriptors allocate
on demand for multi-map updates; there are no per-origin operation tables.

Controlled weak-reference probes hold (a) only the metadata, (b) a copy after an
unrelated rebind, or (c) a finalized property. All three forks collect every tested
unwanted supplier/original-property/host reference. The copy intentionally retains
its captured supplier. These probes and structural tests are focused retention
checks, not whole-daemon or class-loader dominator analysis.

[The short JMH read check](testing/performance/provenance/effective-dispatch-jmh-20260907.json)
uses the unchanged S2 four-read workload (half mutable, half finalized), two forks,
three one-second warmups and three one-second measurements with GC profiling. All
variants report approximately zero bytes/read operation. Timing intervals overlap;
this is not evidence of a production speedup or a resolution of the proposed 1%
regression threshold. See raw results for scores/errors and
[the evidence manifest](testing/performance/provenance/effective-evidence-20260907.json)
for source, class and artifact hashes.

The S0/S1 64 B occurrence+link proposal remains met at 56 B. The disabled structural
and allocation controls pass. The 8 MiB/10,000-property pilot allowance remains a
proposal: allocation totals are not retained-heap measurements and no whole-build
pilot budget is declared passed. Long chains are constructed and projected, not
fully evaluated; this work does not repair the baseline evaluator's deep-chain stack
limits. Finalization allocation varies with JIT escape analysis. No long performance
campaign or configuration-cache performance claim is included.

Reproduce the bounded probes and read benchmark with:

```sh
./gradlew :model-core:compileJava :model-core:jmhJar --max-workers=4
python3 testing/performance/provenance/run-effective-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output /tmp/effective-probe.json
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-*-jmh.jar \
  PropertyAttributionBenchmark -prof gc -rf json -rff /tmp/effective-dispatch.json \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseSerialGC'
```

## Shared completion gate

No decision blocks the implemented S3 scope. Review the classifier's deliberate
partial boundary, enabled costs and copy/finalization storage before expanding it.
Choose diagnostics, collaboration, or both as independently selectable consumers.
D1 origin reporting is the shared next prerequisite for collaboration; C3 reuses D4
transport. Preserve stacktrace-like reporting as the target. Java/Kotlin lines are
D2; Groovy lines are D6, last. Settings-owned properties, additional property scopes,
ConfigurableFileCollection, runtime authority/order and cache integration remain
deferred. Cross-build persistent identity and future mutable-copy namespaces remain
consumer/transport decisions. This checkpoint does not start either consumer path.
