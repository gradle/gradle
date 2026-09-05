# Property provenance and collaboration: implementation from a clean baseline

## Design context and reading order

The upstream [Gradle Provider API semantics repository](https://github.com/asodja/gradle-provider-api-semantics)
provides the broader design context. Before implementation, read:

1. [Provider API foundations](https://github.com/asodja/gradle-provider-api-semantics/blob/main/PROVIDER_API_FOUNDATIONS.md).
2. [Property provenance](https://github.com/asodja/gradle-provider-api-semantics/blob/main/PROPERTY_PROVENANCE.md).
3. [Property provenance implementation notes](https://github.com/asodja/gradle-provider-api-semantics/blob/main/PROPERTY_PROVENANCE_IMPLEMENTATION.md).
4. [Collaborative property updates](https://github.com/asodja/gradle-provider-api-semantics/blob/main/COLLABORATIVE_PROPERTY_UPDATES.md).
5. The local [shared contract](PROPERTY_PROVENANCE_SHARED_CONTRACT.md) and this milestone plan.

The upstream documents include proposed semantics, not just existing Gradle behavior.
The `main` links above are navigation links; the shared contract records its reviewed
semantics revision. At S0, review any changes since that revision, reconcile relevant
differences explicitly, and pin the chosen semantics revision alongside the Gradle
baseline. Do not silently expand the implementation scope by following newer proposals.

## Recommendation

**Start a fresh implementation branch in the Gradle repository, not a new Provider
engine or an unrelated standalone project. Preserve this prototype as the reference.**

The prototype has answered useful questions: project-host wiring and many callback
origins work; useful plugin/script diagnostics do not require lines; copies and
finalization need explicit attention; apparently small metadata choices affect every
property and the JVM's read path. Further isolated experiments should now be tied to an explicit
shared, diagnostics or collaboration milestone rather than one combined sequence.

The next implementation should start with the
[shared contract](PROPERTY_PROVENANCE_SHARED_CONTRACT.md), not retrofit collaborative
correctness onto the prototype's bounded diagnostic traversal. Starting clean makes
it easier to review each semantic change and measure the disabled path against an
unmodified baseline. It does not mean throwing away the tests or lessons.

This is a reviewability and sequencing recommendation, not proof that the prototype
cannot be refactored. If the goal were only a few more ordinary diagnostics, continuing
here would be cheaper. For a foundation that can support either consumer, a small clean start makes the
shared guarantees easier to review without committing to finish both product paths.

This is a proposed execution plan, not authorization to create a branch, rewrite
history, push changes, or start another performance campaign. The current branch and
historical results remain intact. This document records knowledge through prototype
commit `28a6c007668`; the last runtime increment is `7260de1a7b5`.

### Keep, adapt, or leave behind

| Prototype asset | Use in the new implementation |
|---|---|
| Shared contract and upstream semantic specifications | Starting requirements; distinguish proposed behavior from existing Gradle semantics |
| Plugin/script, deferred, cross-project and settings-origin tests | Port as regression fixtures, adapting assertions to the shared contract rather than blindly copying snapshots |
| Copy, finalization, missingness and disabled-message tests | Preserve as compatibility tests; add explicit occurrence/root-selection checks |
| Project host and existing user-code context integration | Reuse the integration points; add explicit authority separately for collaboration |
| Origin descriptors and compact records | Reuse the design lessons; introduce stable contributor keys and script roles deliberately |
| Enabled-only state storage | A proven candidate, not a mandated class hierarchy; preserve the disabled footprint and test dispatch behavior |
| Performance harnesses, raw samples and workload fixtures | Port independently from runtime changes; keep old measurements tied to their revisions |
| Bounded provider traversal and finalized diagnostic snapshots | Optional diagnostic adapters only, never the complete contribution trace |
| Internal replace/map classifier | A reference case for one mutation versus several transforms; not general self-reference handling or authorization |
| Prototype line interception | Revisit in diagnostics D2/D6, with explicit language and cache-key tests; not part of shared milestones or a collaboration prerequisite |

Do not cherry-pick the entire prototype as the new baseline. Port coherent tests and
implementation ideas in reviewable increments. Do not erase the original evidence.

## What we know about performance

The [enabled-state report](testing/performance/provenance/ENABLED_STATE_RESULTS_2026-09-05.md)
documents measurements of the implementation committed in `690267ab899`, before the
later map-update increment. They are not measurements of this plan or collaboration.

| Observation | Design consequence |
|---|---|
| Two added property references made six measured classes 8 bytes larger even when disabled | Treat disabled layout as a first-class gate, not an enabled-mode optimization |
| Enabled-only states restored baseline property sizes and saved 2.42 MiB in matched property/state/metadata shallow totals versus the compact registry | Keep optional storage off ordinary disabled properties; count moved state objects as well as provenance-package objects |
| Reducing the eager operation table saved 1.21 MiB in an earlier checkpoint | Share origins; do not preallocate every operation kind for every origin |
| Additional state variants initially inhibited read-path inlining; a finalized-state guard recovered much of the microbenchmark loss | Test mixed mutable/finalized and tracked/untracked reads, not only isolated operations |
| Enabled fixed-value create/bind/finalize allocated 8 more bytes after the state change, while binding allocated less | Evaluate lifecycle tradeoffs, not just mutation cost |
| Latest repeated production experiment measured disabled configuration about 2.99% above baseline, slower in all three repetitions | Keep this as an unresolved signal; zero added property fields does not prove zero runtime overhead |

The production workload was Gradle's own 253-project `help` configuration build on
one Linux aarch64/JDK 25 environment. Median full-GC live heap was about 0.57 MiB above
baseline when disabled and 2.55 MiB above baseline with origins enabled. These are
whole-daemon live-heap deltas, not exclusive retained sizes. Enabled timing below
baseline does not establish a speedup. No collaboration, cache-hit or portable JVM
performance guarantee follows. The later semantic update work was not benchmarked.

## Scope and architecture to carry forward

One foundation serves both consumers: contributor/origin descriptors, semantic
operations, distinct mutation occurrences, and read-only effective-provenance views.
The shared contract also defines the complete accepted-update trace collaboration
will need; ordinary properties need not allocate mandatory collaboration state.

The foundation is not a combined product roadmap that requires finishing both paths.
After the shared milestones, we may deliver **diagnostics only**, **collaboration
only with its named diagnostic prerequisites**, or both. Neither path is a commitment
to finish the other. A diagnostic dependency is implemented and accepted first, then
reused; it is not reimplemented privately in the collaboration path.

Start with project-owned scalar properties and origin-only metadata. Settings plugins
configuring project-owned properties stay in scope; settings-owned properties do not.
No new concurrency guarantee follows. Full history, new ownership scopes and
`ConfigurableFileCollection` remain separate scope decisions. Line numbers are explicit
diagnostics milestones below, not a prerequisite for the shared foundation.

## Milestone overview and dependency rules

All milestones are **planned**, not completed by the prototype. S milestones form
the common foundation; D and C milestones belong to independently deliverable paths.

| Milestone | Deliverable | Prerequisites |
|---|---|---|
| S0 | Clean baseline, scope and performance controls | Agreed baseline revision |
| S1 | Executable shared contract and representation | S0 |
| S2 | Attribution and opt-in runtime storage | S1 |
| S3 | Effective source/update metadata and lifecycle integration | S2 |
| D1 | Useful origin-only scalar failure and explanation reports | S3 |
| D2 | Java/Kotlin and Kotlin DSL source line numbers | D1 |
| D3 | Broader ordinary diagnostic operations and failure coverage | D1 |
| D4 | Diagnostic provenance through isolation/configuration cache | D1 |
| D5 | Diagnostics production validation and rollout | D3 + D4; D2 for advertised Java/Kotlin line coverage |
| D6 | Groovy DSL and Groovy build-logic line numbers, last diagnostic extension | D2 + D5 |
| C1 | Real scalar collaborative slice with authority and ordering | S3 + **D1** |
| C2 | Collaborative operator and integration coverage | C1 |
| C3 | Collaborative correctness through isolation/configuration cache | C1 + **D4** |
| C4 | Collaboration production validation and rollout | C2 + C3 |

**The choice point is after S3.** S1 tests both mode projections, but completing S3
does not require implementing Declarative Gradle authority, ordering or collaborative
source rebinding. It also does not require full user-facing diagnostics or lines.

The cross-path dependencies are deliberate:

- Before C1, implement D1's origin-only renderer and failure integration. Reuse its
  frames and effective view for collaboration errors, adding ordering-specific detail.
- Before C3, implement D4's descriptor/occurrence transport. C3 extends it with the
  complete correctness trace, constraints and validation handling.
- C1–C4 do **not** depend on D2, D3, D5 or D6. D4 depends on D1, not the line milestones.
  Collaboration can therefore ship without implementing general ordinary diagnostic
  coverage, line numbers or a diagnostics product rollout.
- D1–D6 have **no C dependencies**. Diagnostics do not require collaborative activation,
  authorization, order validation or new self-assignment semantics.
- If implementation reveals another genuine dependency, name the smallest diagnostic
  deliverable and complete it first. Do not silently make one path depend on all of
  the other, or introduce a second renderer/codec to avoid the dependency.

Example execution choices after S3:

- **Diagnostics:** D1, D2, D3, D4, D5, D6. D3/D4 may be scheduled before D2;
  origin-only diagnostics are already useful at D1. Groovy line capture is last,
  after the initial diagnostics rollout; Groovy remains origin-only until D6.
- **Collaboration:** D1, C1, C2, D4, C3, C4. This intentionally includes only the two
  diagnostic prerequisites. No line-number milestone is hidden in that sequence.
- **Both:** complete each shared/prerequisite milestone once, then schedule either
  path's remaining work according to demand.

## S0 — Establish the clean baseline and controls

**Work.** Select and record a clean Gradle revision when implementation starts. Use
a separate worktree/branch; do not reset this prototype. Build the unmodified engine
and pin workload sources independently. The historical
`850edf55835529446bf63488489f6364ce5d2387` remains useful for old experiments, not an
automatic choice for new implementation.

Agree the scalar/project scope, origin-only starting mode and shared contributor
domain contract. Note the future Declarative integration decisions, but do not require
choosing global contributor order or activation to proceed with diagnostics.

**Functional exit.** Baseline distributions/tests are reproducible, and prototype
tests are inventoried by contract scenario rather than counted as new coverage.

**Performance exit.** Record object layouts and construction/binding/read allocations;
check workload repeatability and agree review budgets. No long production campaign
is needed merely to establish the branch.

## S1 — Make the shared contract executable

**Work.** Define shared descriptors, logical occurrence identity, semantic operations
and effective-provenance views. Make SC-01–04, SC-14 and projection SC-15 executable
with test policies: after convention, update A, source rebind and update B, ordinary
projects `S → B`, while collaborative projects `S → A → B`. Test one compound
mutation versus repeated mutations, and explicit-missing versus convention selection.

Choose metadata on structural nodes or a compact shared sequence behind read-only
views. Keep provider dependencies distinct from local contributions. Model captured
roots and live roots explicitly; no second executable Provider graph is required.

**Functional exit.** Both projections consume the same facts. Unknown classification
is not proof of replacement or authority. A descriptor-only round-trip test excludes
runtime objects. The collaborative policy here is a contract test, not a runtime mode.

**Performance exit.** Probe allocation/growth at 0, 1, 8, 128 and thousands of updates.
Appending must not copy the entire history; descriptor sharing must not merge distinct
occurrences. No full ordinary history or duplicate pipeline is introduced.

## S2 — Integrate attribution and optional storage

**Work.** Wire the project mutation boundary and enabled-only metadata storage. Keep
contributor keys, diagnostic origins, runtime applications and target scope separate.
Establish script roles at application boundaries. Reuse existing deferred context
propagation without treating ambient context as authority.

Port plugin-ID/class, nested application, deferred/nested callback, cross-project and
settings-origin project tests. Mark unresolved cross-build identity explicitly.

**Functional exit.** Shared attribution facts are available at accepted mutations;
failed attempts stay report-local. Unknown attribution is honest. No new settings
host or indiscriminate global-factory tracking is introduced (SC-03/05/09/14/16,
within the declared diagnostic scope).

**Performance exit.** Ordinary disabled property/state footprint matches baseline;
no per-property diagnostic holder or successful-read diagnostic allocation appears.
Check first-origin versus steady-state costs and mixed mutable/finalized read dispatch.
Do not create every operation record eagerly for every origin.

## S3 — Integrate effective source/update metadata and lifecycle

**Work.** Maintain the shared effective view for ordinary scalar bindings, conventions,
selection changes and existing supported previous-plan updates, initially the internal
replace/map case. Produce a semantic occurrence at acceptance, not from rendered
provider frames. Integrate copying and finalization so shared metadata preserves
occurrence identity, correct roots and existing live-upstream semantics.

The shared operation interface permits a future collaborative producer of the same
records, but does not implement its authority, source-rebinding pipeline or ordering.
Likewise, `p.set(p.map(f))` is not made safe by adding metadata. The existing replace
snapshot must not be changed into a live convention root.

**Functional exit.** SC-02/03/04/06/10/16 hold for supported ordinary operations;
SC-11's existing captured-root behavior is covered, with proposed live-root semantics
still explicit. Unrelated replacement cuts updates. No eager evaluation, producer loss
or invented contribution occurs. Both contract projections remain compatible with
the runtime record/view types.

**Performance exit.** Check binding/replacement/copy/finalization allocations and
retention, plus small/long update chains. Metadata must not prolong discarded supplier
graphs, copy an entire history per append, or allocate mandatory collaboration state
on ordinary properties.

**Shared completion gate.** The foundation is usable by either consumer. Choose a
path now; do not keep enlarging the shared phase until both products are implemented.

## Diagnostics path

This path explains ordinary Gradle behavior. It does not require C milestones or
change ordinary source-replacement/self-reference semantics.

### D1 — Deliver useful origin-only diagnostics

**Prerequisite:** S3.

**Work.** Implement the shared stacktrace-like renderer and ordinary scalar missing-value
and rejected-mutation integration. Offer `Failure trace to source` and requested
`Configuration trace to source`; keep shadowed conventions separate. Optional upstream
expansion is bounded and qualified, not the source of local contribution facts.
Expose reusable rendering/failure integration so C1 adds conflict detail, not a new
reporting system.

**Functional exit.** Reports preserve the original problem/cause, distinguish unknown,
unconfigured and explicit-missing sources, survive copies/finalization, and do not
evaluate providers or print values. Successful builds are silent unless explanation
was requested; disabled messages remain byte-for-byte unchanged (SC-04/06/10/15/16).

**Performance exit.** Successful execution performs no diagnostic formatting or stack
capture. Measure failure rendering separately from normal reads. Run the first matched
ordinary baseline/disabled/origins smoke build, profiling only a repeatable signal.

**Usable checkpoint:** origin-only scalar diagnostics, explicitly without cache or line
coverage. This is the diagnostic prerequisite for C1, not a commitment to D2–D6.

### D2 — Capture Java/Kotlin and Kotlin DSL line numbers

**Prerequisite:** D1. **Not required by collaboration.**

**Work.** Implement an optional location contract and eligible call-site instrumentation
for Java/Kotlin build logic and Kotlin DSL. Cover successful source/convention mutations
and the outer failed operation: direct calls, Kotlin assignment/accessors, deferred
callbacks, task actions, indirect Provider evaluation and finalized-property `set()`.
Test applied/precompiled scripts and compiled plugins according to their available
debug/source mapping; do not guess original lines from generated code.

Locations on accepted bindings are retained only after success. Failure frames and any
stack-based fallback are created only while reporting a failure. If instrumentation
scopes a lightweight call-site token around an operation, restore it in `finally`;
never retain a successful `get()` history or a rejected attempt in accepted provenance.
Map creation and callback registration lines must not masquerade as mutation lines.

**Functional exit.** Exact line assertions pass for the declared eligible paths,
including nested calls and context restoration on exceptions. Missing debug information
or unsupported interception falls back to origin-only output, not a fabricated line.
Locations never determine contributor identity, authority or order. Version the
instrumentation/cache behavior when toggling modes.

**Performance exit.** Compare clean baseline, diagnostics-disabled, origins-only and
locations-enabled modes. Keep location work out of origins-only operation; measure
classpath instrumentation/preparation separately from warm mutation and failure costs.
No mandatory per-mutation stack walking. Check per-located-occurrence allocation and
ensure any instrumentation helper cost when disabled is measured and reviewed.

If D4 is already complete, extending its optional location payload and cache flag
compatibility tests is part of D2; location capture must not silently disappear on hits.

### D3 — Expand ordinary diagnostic coverage

**Prerequisite:** D1; no dependency on D2/D6 or any C milestone.

**Work.** Extend managed extension/nested/task properties, Java/Kotlin/Groovy and
precompiled/applied plugin fixtures, required task-input validation, unsafe reads and
transform exceptions. Add collection contributions and supported provider-boundary
explanations in explicit sub-increments. Define cross-build identity before claiming
included-build coverage. This is diagnostic attribution, not permission to change
ordinary self-reference semantics.

**Functional exit.** Each advertised operation and failure path has positive/negative
tests. Upstream/branching dependencies are not target contributions; unknown boundaries
and causal limitations are explicit. Locations are asserted only when that language's
D2/D6 support exists. Settings-owned properties and `ConfigurableFileCollection` remain
separate scope decisions.

**Performance exit.** Check branching/fan-out, many origins, replacement retention and
failure formatting. New failure hooks must not introduce eager provider evaluation
or successful-operation formatting/capture.

### D4 — Persist diagnostic descriptors and effective provenance

**Prerequisite:** D1 only. D2/D3/D6 are **not** required.

**Work.** Implement descriptor/occurrence and effective-view codecs through managed
object recreation, isolation and configuration-cache store/hit. Start with the D1
origin-only scope. Preserve copying/finalization identities; deserialization is not a
new mutation. Establish reusable transport seams that C3 can extend.

**Functional exit.** Diagnostic SC-10/13/14 pass on store and hit, including task
execution failures and relocation within the supported identity scheme. No provider,
host, plugin instance or class loader enters descriptor payloads. Diagnostic mode
changes have explicit compatibility/invalidation behavior. If D2 exists, preserve
its optional locations too; otherwise encode absence honestly. Later D3 coverage and
D6 Groovy locations must extend transport tests alongside their implementation.

**Performance exit.** Measure entry growth, encode/decode time/allocation, cache-hit
live heap and retention. Test origin-only transport independently of any location
payload. No cache-hit performance claim before the provenance actually survives.

This is the diagnostic prerequisite for C3. It does not store ordering constraints,
validate contributions or serialize authority tokens.

### D5 — Validate and roll out diagnostics

**Prerequisites:** D3 + D4 for the declared release scope; D2 for advertised Java/Kotlin
line coverage. An explicitly origin-only release can omit line support. D6 is not a
prerequisite: Groovy line capture follows this initial rollout.

**Work and functional exit.** Validate ordinary diagnostics on configuration-heavy
and task-execution-heavy builds with cache store/hit. Publish a language/operation/
line-coverage matrix, fallback behavior and opt-in policy. The default sequence ships
Java/Kotlin line support here and keeps Groovy origin-only, with Groovy line capture
reserved for the final milestone D6. No collaboration milestone is needed to deliver
this release.

**Performance exit.** Run matched baseline/disabled/origins comparisons and a separate
location-mode comparison where shipped, with independent daemons and supported JVMs.
Resolve or explicitly accept regressions against agreed budgets before rollout;
default enablement is a separate decision.

### D6 — Capture Groovy DSL and Groovy build-logic line numbers

**Prerequisites:** D2's location representation and lifecycle rules + D5's initial
diagnostics rollout. This is the **last diagnostics milestone**, not a prerequisite
for the initial release or collaboration.

**Work.** Add the Groovy dynamic-dispatch/interception path for property assignment,
explicit `set`/`convention` and the other declared mutation/query forms. Cover build
and applied scripts, Groovy plugins, delegated closures, task actions and indirect
queries. Reuse D2's location format and shared renderer; JVM interception alone is
not evidence that Groovy assignment is covered.

**Functional exit.** Dedicated Groovy fixtures assert the actual user operation line,
including rejected finalized sets and nested/deferred calls. Test script roles,
delegation/overloads and absence of stale call sites. Unsupported reflective/custom
dispatch and missing line metadata have documented origin-only fallback. A bounded
failure-time fallback must not be labeled exact if it identifies only an approximate
caller. Do not claim every Groovy call shape from one successful script test.

**Performance exit.** Measure dynamic-dispatch overhead with provenance disabled,
origins-only and locations enabled; check allocation/capture counts under many mutations.
Keep successful Groovy execution free of provenance stack walks. Extend D4's location
round-trip and mode-compatibility tests for Groovy; transport is already implemented.
Repeat the relevant D5 release/regression checks for this extension and update the
published language/operation coverage matrix before shipping Groovy line support.

This is planned Groovy line support, not a request to implement it in the current
prototype or a prerequisite for the other product path.

## Collaboration path

This path implements Declarative Gradle collaborative semantics using the shared
foundation. Complete its named D prerequisites first; do not require the rest of the
diagnostics product. It can remain origin-only throughout.

### C1 — Deliver a real scalar collaborative slice

**Prerequisites:** S3 + D1. Implement and accept D1 **before** this milestone.

**Work.** Integrate explicit source-binding/contributor-update contexts at a real
Declarative entry point. Decide activation, contributor domain, default order and
source permissions. Implement one map update over the previous plan using the existing
Provider primitives, immediate composition, separate source selection, a complete update
trace, local constraints and cached validation.

A source rebind preserves accepted updates. Validate nondecreasing contributor order
before `get`, `getOrNull`, `isPresent` and lifecycle closure. Constraints may change
while mutable, including after updates; invalidate validation rather than reorder or
replay the pipeline. Preserve supported deferred authority without lending the invoker's
authority to arbitrary privately stored callbacks.

Any supported assignment-shaped syntax must implement its structural previous-plan,
live-root and cycle semantics from the
[self-reference specification](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/SELF_REFERENCE_SPEC.md).
Do not reinterpret S3's bounded diagnostic classifier or internal replace snapshot as
that implementation. New semantics are explicit and mode-scoped; ordinary behavior
remains unchanged.

**Functional exit.** Real plugins and build-author configuration prove SC-01–10,
collaborative SC-12 and SC-15 for the supported scalar/map scope: rebinding preserves
updates, invalid order fails without value evaluation, a local constraint changes
validity without replay, rejected mutations commit nothing, and successful queries
do not close mutations. Reuse D1's renderer and append required-order/first-conflict
details using exact occurrence positions. Lines are not required.

**Performance exit.** Check append growth, O(1) clean validation, dirty scans against
update/constraint counts and repeated update/query alternation. Keep the mutable trace
complete even when diagnostics are off or output is truncated. Measure mandatory
collaboration state separately from optional reporting. A validation-disabled variant
is not a compliant baseline.

**Decision gate:** if this requires a second evaluator, replay or diagnostic traversal
for correctness, revise the design before extending the collaboration path.

### C2 — Expand collaborative operations and integration

**Prerequisite:** C1. No dependency on D2, D3, D5 or D6.

**Work.** Add flatMap/zip, then required append/remove and collection contributions in
explicit sub-increments. Extend Declarative/callback and source/target scope coverage
with defined identities and authority boundaries. Reuse shared operation records and
D1's generic renderer; collaboration-specific conflict tests belong here, not in D3.

**Functional exit.** Each operation preserves value/missingness, producer/dependency
metadata, contribution boundaries, source-rebind behavior and order validation. Unknown
structure cannot authorize a replacement. Complete correctness traces never incorporate
upstream properties' updates as local contributions.

**Performance exit.** Test long sequences, branching/shared inputs, many contributors,
constraints and rejected operations. Avoid copying/scanning the accepted history merely
to append metadata; measure unavoidable validation/substitution separately.

### C3 — Persist collaborative correctness state

**Prerequisites:** C1 + D4. Complete D4 **first**, even if no other diagnostics work
beyond D1 is selected.

**Work.** Extend D4's descriptor/occurrence transport with the complete collaborative
trace, effective source/update relationships, constraints and order identity. Restore
or safely recompute validation. Do not serialize authority capabilities or attribute
rehydration as a contribution; execution boundaries establish any required authority.
Reuse codecs rather than implementing a parallel collaboration-only origin format.

**Functional exit.** Collaborative SC-10/13/14 pass on store and hit: valid plans retain
results, invalid order still fails before evaluation, occurrence identity survives
copies, and toggling optional diagnostics cannot remove mandatory correctness state.
Cover the C2 operations included in the eventual release. Neither line capture nor
broad ordinary failure coverage is necessary.

**Performance exit.** Measure mandatory trace/constraint entry growth, serialization
and restoration allocation/time, cache-hit heap and class-loader retention. Compare
optional diagnostic/location payloads separately if present.

### C4 — Validate and roll out collaboration

**Prerequisites:** C2 + C3. No dependency on D2, D3, D5 or D6.

**Work and functional exit.** Run representative Declarative plugin/model builds and
cache store/hit scenarios, including supported parallel execution without inventing
concurrent property-mutation guarantees. Review activation, authority, supported
operators/scopes and API boundaries. Ship the D1-origin-based failure explanations
and first-conflict detail with explicit opt-in/default policy.

**Performance exit.** Validate mandatory state, clean/dirty reads, realistic and long
update traces, cache costs and disabled ordinary compatibility across supported JVMs.
A manually composed equivalent Provider plan may be a diagnostic benchmark reference,
not a replacement for the correctness rules. Resolve or explicitly accept budget
tradeoffs before rollout. Collaboration does not wait for diagnostic line numbers.

## Performance policy across milestones

### Structural gates from the start

1. Ordinary disabled property/state footprint stays at baseline; no per-mutation origin
   allocation or successful-read diagnostic allocation is introduced in that mode.
2. Origin-only capture performs no stack walking or source-location context publication.
   Strings are formatted for reports, not on every successful mutation.
3. Descriptors can be shared; accepted occurrences remain logically distinct. Avoid an
   eager operation table, a global per-property map, and duplicate executable plans.
4. Provenance metadata does not keep suppliers, plugin instances or class loaders alive
   after the semantics no longer need them. Check the whole retention path, not only
   fields directly declared on a record.
5. Expected retained metadata scales with enabled properties, distinct origins and live
   occurrences/snapshots, not all historical ordinary replacements. Collaboration's
   mutable update trace remains complete; never cap correctness state for a benchmark.
6. Clean collaborative validation is a cached check. New updates/constraints invalidate
   it; optimizing dirty validation must preserve the first-conflict result and local
   ordering rules. Diagnostic formatting never drives validation.

### Comparison matrix

| Variant | Question answered |
|---|---|
| Clean runtime + ordinary workload | Matching baseline, including absence of instrumentation and metadata code |
| New runtime + ordinary diagnostics disabled | Compiled-in cost, not merely the extra cost of enabling origins |
| New runtime + ordinary origins enabled | Incremental ordinary capture/state/reporting cost |
| New runtime + collaboration, optional reports disabled | Cost of the actual mandatory collaborative semantics/state |
| Same collaboration + optional reports enabled | Additional diagnostic cost, not total collaboration overhead |
| Locations enabled, when implemented | Separate instrumentation/capture/transport cost |

Use identical ordinary workload sources for the first three variants. Compare
collaborative execution with an explicitly equivalent manually composed Provider plan
only as a diagnostic microbenchmark reference: that reference lacks collaboration's
rules and is not a compliant alternative implementation. No failures are needed in
normal hot-path benchmarks; measure report formatting separately.

### Cadence, evidence and budgets

- Each milestone: correctness tests, layout/retention checks when state changes, and
  focused allocation/scaling probes for the changed path. Do not run a whole production
  campaign after every small edit.
- D1/C1: representative smoke builds and targeted profiles for repeatable signals.
  D2/D6 add line-capture and instrumentation checks; D4/C3 add the respective cache
  checks. D5/C4 perform independent release comparisons for the selected path; D6
  repeats relevant diagnostic release checks for the final Groovy extension.
- After shared completion, run only the selected path's checks and its explicit
  prerequisites. A diagnostics release does not need a collaborative workload, and a
  collaboration release does not need a Groovy line-capture campaign.
- Keep engine/workload revisions, feature flags, JDK/JVM options, instrumentation state,
  environment, raw samples and per-daemon summaries. Warm compilation/caches separately;
  do not time build-logic recompilation or heap profiling as feature overhead.
- JMH needs matched warmup and independent forks, including mixed-state call sites.
  Measure allocated bytes, throughput/latency and growth, not a single pooled timing.
- Heap histograms provide shallow class totals and whole-daemon live bytes. Use retention
  analysis when investigating why objects survive; neither measure alone is peak or
  exclusive retained memory. Count metadata moved outside its nominal package.

**Budget policy:** structural gates above are the initial hard requirements. S0 must
also record per-workload timing/memory regression budgets and the baseline noise needed
to interpret them. A suggested initial *review trigger*, not an accepted overhead
allowance, is a repeatable disabled configuration/wall regression of 1% or more once
the experiment can resolve that difference. Any clear smaller regression can still
require investigation. Failure to detect a regression is not proof of zero cost.

Enabled ordinary and collaborative budgets must be separate. Set limits in terms of
bytes per tracked property/origin/update, absolute workload heap and configuration/read
time at the expected scale. Establish the collaborative scale/budget with the C1 pilot,
before declaring it production-ready. Do not invent a universal percentage from the
prototype's ordinary configuration workload. A breached budget pauses expansion for
targeted investigation or an explicit tradeoff decision; it does not trigger an open-ended
optimization project or justify dropping correctness records.

The existing [measurement protocol](testing/performance/provenance/PRODUCTION_MEASUREMENTS.md)
is a reusable starting point. Its class parsers and negative controls must be updated
and tested for the new storage representation before trusting its output.

## Concrete next action

Prefer a new implementation session in the separately authorized clean Gradle worktree.
This is a workflow recommendation, not a technical requirement. Carry this plan, the
shared contract and links to the prototype tests/performance evidence into that session;
do not rely on conversation history or copy the prototype runtime wholesale. Start
with S0/S1 only, reporting the selected baseline, specification revision, contract tests
and performance controls before proceeding to runtime integration.

Review the shared contract and this dependency plan, then authorize a separate clean
Gradle worktree/branch and its baseline revision. Begin with S0/S1 and proceed through
S2/S3. At shared completion, explicitly choose diagnostics, collaboration, or both.

For diagnostics, proceed to D1 and the selected D milestones: D2 adds Java/Kotlin
lines; D6 adds Groovy lines last, after the initial D5 rollout. For collaboration,
complete D1 before C1 and D4 before C3; do not automatically schedule all diagnostic
work. Reuse each completed prerequisite once.

Keep one reviewable change per milestone sub-increment, with tests and evidence
beside it. The shared completion gate, D1 usability checkpoint and C1 correctness
checkpoint let us stop or change direction without an unfinished implementation of
the other product. This document does not start runtime changes or measurements.
