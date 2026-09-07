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

# Property provenance: S0/S1 review checkpoint

Subsequent work is recorded in the [S2 checkpoint](PROPERTY_PROVENANCE_S2.md).

S0 and S1 are implemented on `asodja/provenance-prototype-20260907`. Stop here for
review before S2; nothing has been pushed. This is an internal metadata foundation,
not an enabled property feature or a collaborative runtime.

## Baselines and preservation

- Gradle engine baseline: `d41e66c4e92e3a79dfeeb8e5f2c6912f61d5d036`
  (`Merge release into master (20260907) (#39069)`), equal to local `origin/master`
  when work began. The worktree had no tracked or untracked changes. The new branch
  starts at that revision; the original `Implement-Provenance` branch remains intact.
- Reference branch and worktree remain at
  `93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a`. No prototype files, refs or existing
  worktrees were modified. The historical `850edf558…` baseline is not used here.
- Semantics baseline: `0a695690ac2a4852b7d455a31b1adf830345076c`.
  A fresh fetch of [upstream](https://github.com/asodja/gradle-provider-api-semantics)
  on 2026-09-07 found `main` at exactly this revision. The diff against the shared
  contract's pin is empty, including all four requested design documents.
- Read the handoff's
  [implementation plan](https://github.com/gradle/gradle/blob/93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a/PROPERTY_PROVENANCE_IMPLEMENTATION_PLAN.md),
  [shared contract](https://github.com/gradle/gradle/blob/93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a/PROPERTY_PROVENANCE_SHARED_CONTRACT.md),
  [origin-first notes](https://github.com/gradle/gradle/blob/93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a/PROPERTY_PROVENANCE_ORIGIN_FIRST.md)
  and [update notes](https://github.com/gradle/gradle/blob/93eb48b8b208ae2ce7138bd79d3c8d57645d4b0a/PROPERTY_PROVENANCE_UPDATES.md).
  These are reference requirements and prototype evidence, not copied runtime code.

### Reconciliation with existing Gradle behavior

The pinned [foundations](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/PROVIDER_API_FOUNDATIONS.md)
propose monotonic explicit configuration and `set(null)` as explicit missingness.
Current Gradle retains unset/null selection changes. S1 represents selection-change
operations without changing those APIs. Explicitly binding a missing Provider still
shadows a convention, and the tests exercise that existing behavior using a real
`DefaultProperty`; they do not equate it with `set(null)`.

The proposed live-convention self-assignment root differs from existing internal
`replace()`, which captures its previous plan. S1 explicitly distinguishes captured
and live roots in metadata. Test policies exercise both; no self-reference substitution
or classifier is implemented. A view is an immutable checkpoint: owners must resolve
live metadata again at a later checkpoint. The source describes the effective root,
which can be a convention even when an enclosing update plan is explicit.

The [provenance specification](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/PROPERTY_PROVENANCE.md)
and [collaborative proposal](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/COLLABORATIVE_PROPERTY_UPDATES.md)
keep dependencies, accepted contributions, optional history and authority separate.
The shared contract refines domain/occurrence identity and complete local coverage.
S1 follows those distinctions. Unknown classification conveys no non-self proof or
authority. The upstream implementation notes describe historical prototypes, whose
measurements and limitations are not evidence for this implementation.

## Scope and representation

Starting scope remains project-owned scalar properties, origins only. Settings-origin
configuration of project properties is included in the descriptor model; settings-owned
properties are deferred. There are no runtime property changes, capture hooks, flags,
authority checks, ordering, line capture, cache integration, additional ownership scopes,
collection operations or `ConfigurableFileCollection` integration.

Implementation lives in
[model-core's provenance package](platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provider/provenance).

- `ContributorKey`: equality is domain + contributor kind + logical identity. Domains
  are explicitly supplied opaque names. S1 does not manufacture cross-build identities.
- `DiagnosticOrigin`: typed plugin/script/unknown information and display text. Script
  kind comes from an application boundary, never a filename heuristic.
- `Attribution`: contributor, origin, source scope and optional application string token.
  `TargetContext` separately identifies the configured project/model. Descriptor
  equality includes its detail; contributor equality intentionally excludes that detail.
- `MutationOccurrence`: immutable accepted fact, identified by owner-supplied occurrence
  scope + sequence. Owners must allocate unique pairs per accepted mutation, including
  across copied branches; transport preserves existing pairs. It does not mint identity.
  Equal descriptors can belong to different occurrences. Scope is not contributor ID,
  build identity or an application ID. Conflicting payloads with the same pair are an
  owner/transport error, not a new occurrence; production allocation/transport is deferred.
- `SemanticOperation`: explicit/convention binding, structural update shapes (including
  a compound shape in one occurrence), selection changes and unclassified binding reason.
  Shape vocabulary does not claim runtime support for those operations. No executable
  object or configured value is part of a descriptor.
- `UpdateSequence`: immutable reverse-linked sequence with O(1) append and snapshot
  sharing, O(n) chronological materialization only on request. Reverse traversal is
  iterative. The owner determines whether it is an effective suffix or complete accepted
  trace; the type never claims completeness. It accepts update records only. Ordinary
  policies can release displaced sequences instead of keeping mandatory history.
- `EffectiveProvenanceView`: immutable source selection/knowledge, root kind, shared
  updates, separate shadowed bindings and complete-local/partial coverage. It does not
  select sources from history or query Providers. A selected occurrence cannot also be
  shadowed. Unconfigured, known unknown-author, unattributed and unavailable are distinct.

The descriptors are Java 8-compatible internal classes in a null-marked package. There
is no public API or user-visible release behavior to document yet. No serializer or
`Serializable` marker is added. Test-only data-stream adapters prove descriptor closure
and occurrence relationships without choosing D4's cache wire format.

## Executable scenarios and prototype inventory

[SharedProvenanceContractTest](platforms/core-configuration/model-core/src/test/groovy/org/gradle/api/internal/provider/provenance/SharedProvenanceContractTest.groovy)
contains 25 passing cases:

| Contract | S1 evidence | Prototype fixtures to adapt later |
|---|---|---|
| SC-01, projection SC-15 | Both policies checked at C, C→A, rebind S, update B; ordinary S→B, collaborative S→A→B; reverse trace, separate C, chronological A/B positions | `PropertyProvenanceTest`, `PropertyUpdateProvenanceIntegrationTest` for ordinary replacement/captured roots; no prototype collaborative conformance |
| SC-02 | Compound maps give one occurrence; repeated mutations share descriptors and stay distinct | `PropertyProvenanceTest` repeated replace/compound maps and `PropertyUpdateClassifierTest` |
| SC-03 | A's opaque Provider and derived map are bound by B; no target update entries or evaluation during projection | `PropertyProvenanceTest` upstream chains; `PropertyProvenanceIntegrationTest` plugin/script attribution |
| SC-04 | Real missing Provider remains explicitly selected; unconfigured/unattributed/lost/unknown-author cases distinct | `DefaultPropertyTest`, `ValueStateProvenanceTest`, convention/copy tests in `PropertyProvenanceTest` |
| SC-14 | Same labels in distinct domains/kinds differ; applications share contributor keys; copies retain occurrence pairs; source/target scopes separate | `PropertyProvenanceRegistryTest` is descriptor inspiration only, not stable contributor identity coverage |
| S1 representation exits | Descriptor-only occurrence and full checkpoint round trips; all operation kinds; immutable lists; honest unclassified coverage; captured/live roots; 0/1/8/128/4096 sequence growth | No existing production transport to port |
| SC-05/06/09, S2 | Deferred: runtime attribution and accepted-boundary wiring | `PropertyProvenanceIntegrationTest` nested/deferred/context restoration; `SettingsOriginPropertyProvenanceIntegrationTest`; `CrossProjectPropertyProvenanceIntegrationTest`; `ProjectBackedPropertyHostTest` |
| SC-10/11/16, S3 | Only root/copy metadata portions modeled here; runtime lifecycle and disabled messages deferred | `PropertyProvenanceTest`, `ValueStateProvenanceTest`, `PropertyUpdateClassifierTest`: finalization/failure/retry/shallow copy/null/unset/disabled cases |
| SC-07/08/12, C1+ | Deferred authority/order/lifecycle validation; test projections do not prove collaboration correctness | No compliant prototype runtime trace/order implementation |
| SC-13, D4/C3; full SC-15, D1 | Deferred actual isolation/cache transport and stacktrace-like renderer/failure frames | Bounded prototype snapshots and formatting tests are references, not transport or complete correctness traces |

All prototype names above refer to the handoff revision, primarily under model-core's
`src/test/groovy/org/gradle/api/internal/provider` or `src/integTest/groovy/org/gradle/api/provider`;
`ProjectBackedPropertyHostTest` is in core. Counts from the prototype are not new coverage.
Later reporting retains the design target: original problem/cause, ephemeral failed
operation, reverse effective updates, source, then separate shadowed configuration and
explicit coverage limits. Ordering conflicts use chronological occurrence positions.

## Reproducible checks

Before adding any source, ran successfully:

```sh
./gradlew :model-core:test --tests org.gradle.api.internal.provider.DefaultPropertyTest \
  :distributions-full:binDistributionZip --max-workers=4
```

This built the unmodified full distribution and passed all 230 scalar unit cases.
The initial attempt using `--no-configuration-cache` failed because this checkout enables
Isolated Projects; the corrected command above uses the repository defaults. The build's
own configuration cache is unrelated to property-provenance transport support.

Preserved local baseline distribution and test XML in `/tmp/provenance-s0-20260907/`.
The ZIP SHA-256 is
`37b6ddc14b9dcf089f8f2e7dc152a377a48a57bb99c6aaa8406aca4b346eed16`.
The build's default timestamp was `20260907000000+0000`. For later reproduction, use a
fresh detached worktree at the full baseline hash, the command above and
`-PbuildTimestamp=20260907000000+0000`; never reset this implementation or the prototype.
Logs are `/tmp/provenance-s0-build.log` and `/tmp/provenance-s1-tests.log` in this session.

After implementation, ran successfully:

```sh
./gradlew :model-core:test --tests '*SharedProvenanceContractTest' \
  --tests org.gradle.api.internal.provider.DefaultPropertyTest \
  :model-core:checkstyleMain :model-core:codenarcTest --max-workers=4
python3 testing/performance/provenance/run-shared-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output testing/performance/provenance/shared-probe-20260907.json
```

Result: 25 contract + 230 existing scalar cases pass; Java checkstyle and test CodeNarc
pass. No full repository suite or long performance campaign was run. The probe requires
JDK 25, Python 3, compiled model-core classes and an unmodified full baseline ZIP.
It excludes distribution API stubs so they cannot shadow runtime implementation classes.

## Performance evidence, controls and proposed review budgets

[Raw samples](testing/performance/provenance/shared-probe-20260907.json) include probe
source/metadata hashes, ZIP identity, environment, JVM flags, and individual samples.
Linux aarch64, OpenJDK 25.0.4; 256 MiB fixed heap, Serial GC; three fresh JVMs per variant,
rotated order, five warmup and five measured batches. Preparation/compilation is excluded.
Instrumentation measures shallow layout only; per-thread allocated bytes measure loop
allocation. These probes are not JMH, retained-heap dominator analysis or build timings.

| Probe | Evidence |
|---|---|
| Baseline / compiled-in scalar layout | Both: property 40 B, mutable state 24 B, finalized state 16 B |
| Baseline / compiled-in scalar allocation | Both: construct 64 B/op, bind 16 B/op, finalized read 0 B/op. Mutable reads 24 B/op in baseline; 0/24/24 in changed forks (JIT sensitivity) |
| Shared descriptors | Contributor 24 B, origin 24 B, scope 24 B, attribution 32 B; excludes referenced strings and shared enum objects |
| Accepted update + sequence node | 32 + 24 = 56 B/update, all three forks |
| Append 0 / 1 / 8 / 128 / 4096 updates | 0 / 56 / 448 / 7168 / 229376 B per sequence, all three forks |
| New checkpoint over any tested sequence length | 136 B/checkpoint with empty shadowed/reason lists; no update-prefix copy |

`DefaultProperty`, `AbstractProperty`, `ValueState`, `ValueState$NonFinalizedValue` and
`ValueState$FinalizedValue` compiled bytes match the preserved baseline jar exactly.
No existing production source was modified; ordinary construction never enters this
package. The comparison loads new model-core classes over baseline dependencies, not a
second full packaged distribution. S2 must measure the integrated compiled-in path anew.

Raw construction fork medians range 9.63–23.08 ns baseline, 9.52–14.78 ns compiled-in;
binding 16.37–16.77 versus 16.47–16.57 ns. At 4096 updates metadata construction fork
medians range 15.75–22.51 microseconds. This noise cannot resolve a 1% effect. No speedup,
zero-cost read, production latency or retained-heap claim follows. Linear allocation
and immutable prefix sharing are the focused S1 scaling evidence.

Proposed gates for review (not agreed overhead allowances):

| Workload / dimension | Review trigger or budget proposal |
|---|---|
| Ordinary disabled layout and allocation | Zero additional fields/state footprint, mutation capture allocations or successful-read diagnostic allocations; hard structural gate |
| Ordinary disabled configuration/read timing | Investigate a repeatable ≥1% regression once baseline noise resolves it; smaller clear regressions still matter. Current short probes cannot adjudicate this |
| Enabled scalar metadata microprobe | At most 64 B/accepted update+link, 160 B/checkpoint with empty shadowed/coverage lists, 128 B/shared descriptor bundle excluding strings; current measured 56/136/104 B |
| Enabled pilot: 10000 tracked scalar properties, one source and 8 live updates each | Propose ≤8 MiB shallow metadata excluding strings/Provider state; measure whole-daemon heap separately. Timing review trigger ≥5% configuration increase or ≥1% successful-read increase; must be calibrated with pilot noise before acceptance |
| Ordinary replacement retention | Retained metadata proportional to current source/convention and live updates/snapshots, not number of discarded replacements; no mandatory history |
| Collaboration | Only the shared 64 B/update+link proposal applies now. Complete trace is never capped. C1 must set additional mandatory state/order budgets and expected scale separately before production claims |

Before integrated timing reviews, pin workload sources independently (initial candidate:
Gradle's `help` workload at the S0 engine hash); warm compilation and caches separately,
use separate user homes/daemons with matching paths/options/instrumentation, and collect
per-daemon summaries and noise controls. Compare clean baseline, diagnostics-disabled,
origins-enabled and mandatory collaboration separately. Later line capture and cache
transport have their own variants. Reuse the prototype measurement protocol only after
updating and testing its class parsers/negative controls for this representation.
A budget breach pauses expansion for focused review, not an open-ended campaign.

## Decisions left for later milestones

No decision blocked S0/S1. Review the representation, owner-supplied domain/occurrence
namespace contract and proposed budgets before authorizing S2. Actual cross-build identity,
allocation across copied mutable branches, runtime storage/attribution and D4 wire format
remain integration decisions; strings here do not settle their encodings. Declarative
activation, source/update authority, global order and constraints belong to C1.

After S3, diagnostics and collaboration remain independently selectable. Collaboration
requires D1 reporting before C1 and reuses D4 transport before C3. Java/Kotlin lines are
D2; Groovy lines are D6, last after D5. Neither lines nor the remaining diagnostics path
is a prerequisite for origin-only collaboration. Work stops before S2; nothing has
been pushed.
