# Property provenance and collaboration: implementation from a clean baseline

## Recommendation

**Start a fresh implementation branch in the Gradle repository, not a new Provider
engine or an unrelated standalone project. Preserve this prototype as the reference.**

The prototype has answered useful questions: project-host wiring and many callback
origins work; useful plugin/script diagnostics do not require lines; copies and
finalization need explicit attention; apparently small metadata choices affect every
property and the JVM's read path. Continuing to add isolated diagnostic cases here
would bring diminishing returns for the shared contribution-model goal.

The next implementation should start with the
[shared contract](PROPERTY_PROVENANCE_SHARED_CONTRACT.md), not retrofit collaborative
correctness onto the prototype's bounded diagnostic traversal. Starting clean makes
it easier to review each semantic change and measure the disabled path against an
unmodified baseline. It does not mean throwing away the tests or lessons.

This is a reviewability and sequencing recommendation, not proof that the prototype
cannot be refactored. If the goal were only a few more ordinary diagnostics, continuing
here would be cheaper. For a shared implementation with correctness consumers, a small
clean foundation is now more valuable than further incremental diagnostic experiments.

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
| Prototype line interception | Defer initially. Do not bring optional instrumentation into every build merely to prepare for future lines |

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

One foundation serves both consumers:

- Shared contributor/origin descriptors, semantic operations and distinct accepted
  mutation occurrences.
- An effective-provenance view for both modes and one stacktrace-like renderer.
- A complete local update trace for collaboration; it may share storage with the
  effective update sequence. A bounded diagnostic view is not its substitute.
- Ordinary replacement cuts its displaced update chain. Collaborative source binding
  preserves updates. The mode, not the renderer, owns that difference.
- Collaborative updates compose immediately into one existing Provider pipeline.
  Contributor order is checked before observation/lifecycle closure. No update replay,
  automatic reordering, general callback-body analysis or second evaluator is planned.

Start with project-owned scalar properties and origin-only metadata. Settings plugins
configuring project-owned properties stay in scope; settings-owned properties do not.
No new concurrency guarantee follows. Line numbers, full history, new ownership scopes
and `ConfigurableFileCollection` are separate later decisions.

## Milestone overview

All milestones below are **planned**, not completed by the existing prototype.
Dependencies are sequential unless a bounded independent test/fixture task is clear.

| Milestone | Deliverable | Exit decision |
|---|---|---|
| M0 | Clean baseline, scope decisions and comparison controls | We can identify and compare the exact runtime/workload without prototype changes |
| M1 | Executable shared contract and representation choice | One set of facts explains both mode projections without depending on diagnostic traversal |
| M2 | Attribution and opt-in runtime storage | Correct origin/identity boundaries with the ordinary disabled path protected |
| M3 | Ordinary scalar provenance using the shared contract | Useful diagnostics, copies and finalization work without semantic changes |
| M4 | Supported previous-plan operation integration | Real provider plans produce semantic update occurrences without eager evaluation |
| M5 | Real scalar collaborative vertical slice | Source rebind, authority and ordering work together on the same metadata and renderer |
| M6 | Required operation and user-facing coverage | Each supported operation/boundary has functional and growth tests; unsupported cases remain explicit |
| M7 | Isolation and configuration-cache transport | Store and hit preserve required metadata and correctness, without attributing recreation |
| M8 | Production readiness and opt-in rollout | Representative-build correctness and performance gates pass on supported configurations |

M3 is a useful ordinary-diagnostics checkpoint. M5 is the decisive proof of the shared
design. M7 is required before broad claims about normal cache-enabled builds. Do not
wait for full diagnostic coverage or line numbers to attempt M5.

## M0 — Establish the new implementation boundary

**Work.** Choose and record an agreed clean Gradle revision when implementation starts.
Use a separate worktree/branch; do not reset this one. Build an unmodified distribution
and pin workload sources independently of engine sources. Preserve the historical
`850edf55835529446bf63488489f6364ce5d2387` baseline only for reproducing old experiments;
it is not automatically the right baseline for new Gradle development.

Confirm the shared contract, initial scalar/project scope, origin-only default, and
which Declarative Gradle boundary will eventually activate collaborative mode. Name
the owner of decisions about contributor domain, global order and permitted source
contexts. M1 can use explicit fixture-supplied policies while real integration is
being decided; M5 cannot pretend that fixture policy is production integration.

**Functional exit.** Clean baseline tests and distributions are reproducible. The
prototype's passing tests are inventoried by contract scenario, not counted as new
implementation coverage. Required tests/fixtures can run without optional line hooks.

**Performance exit.** Record baseline object layouts, construction/binding/read
allocations, and a short repeatability check for the chosen workloads. Agree timing
and memory review budgets before assessing feature results; see the gate policy below.
No expensive repeated-build campaign is needed merely to create the branch.

## M1 — Make the shared contract executable

**Work.** Add focused contract tests for shared descriptors, occurrence identity,
source selection and effective views, using explicit ordinary/collaborative policies.
Start with SC-01–04, SC-14 and the projection part of SC-15. Test a convention, update A,
source rebind, update B: ordinary ends at `S → B`, collaborative at `S → A → B`.
Test repeated A contributions, compound transforms as one mutation, and missing explicit
sources that do not fall back to conventions.

Select the representation only after these tests: metadata on structural nodes, an
immutable shared sequence, or a compact side trace. Keep mutable implementation state
private behind read-only views. Distinguish captured roots, live convention roots and
collaborative source selection. Prototype fields/classes are not the contract.

**Functional exit.** The same occurrence/origin model supports both projections and
one renderer. Local updates cannot be confused with upstream dependencies. Unknown
classification cannot be treated as authorized replacement. Add a descriptor-only
round-trip fixture to expose runtime-object leakage early; it is not a cache codec.
This milestone is a contract test model, not a working collaborative property.

**Performance exit.** Analyze and test storage growth: descriptors shared by origin;
occurrences kept distinct; updates not copied wholesale on every append. Use small
allocation/growth probes at 0, 1, 8, 128 and thousands of updates to detect accidental
quadratic bookkeeping. No full production-build measurement yet.

**Decision gate.** If the representation requires a second executable Provider graph,
full ordinary mutation history, or diagnostic traversal for correctness, revise it
here rather than building more runtime integrations around it.

## M2 — Integrate attribution and optional storage

**Work.** Introduce the project-scoped mutation seam and mode-aware state selection.
Keep contributor keys distinct from diagnostic origins, application details and target
scope. Establish build/applied/settings/init script roles at application boundaries.
Reuse Gradle-managed callback context restoration; do not infer authority from it.

Port plugin-by-ID/class, nested application, deferred/nested callbacks, root/sibling
project and settings-origin project fixtures. Preserve distinct application/occurrence
identity without accidentally giving every application a different contributor key.
Explicitly mark unresolved cross-build identities rather than inventing string rules.

**Functional exit.** SC-03, diagnostic SC-05/09, and initial SC-14/16 hold for the declared
scope. Unknown ordinary attribution is honest. Successful binding metadata is recorded
after acceptance; rejected attempts remain report-local. No settings host or general
global-factory opt-in is introduced.

**Performance exit.** Ordinary disabled property and state layouts match the clean
baseline on the measured JVM. No per-property diagnostic holder or successful-read
allocation is introduced when disabled. Check first-origin registration separately
from steady-state mutation; avoid eager tables of unused operation records. Check
mixed-state read dispatch before committing to a state hierarchy.

## M3 — Deliver ordinary scalar diagnostics on the shared foundation

**Work.** Implement explicit/convention selection, replacement, unset/promotion,
shadowed configuration, copy and finalization metadata. Render both failure and
explicitly requested configuration views. Start with missing-value queries and
rejected lifecycle mutations; preserve the original causes and disabled messages.
Use the shared effective view instead of making provider traversal the source of truth.
Optional upstream expansion remains clearly qualified and bounded.

**Functional exit.** SC-04/06/10/15/16 hold for the supported ordinary paths. A copy
preserves local occurrences while upstream values remain live where the API requires.
Finalization retains explanations without retaining discarded suppliers through
metadata; failure/retry does not commit a success snapshot. Reporting never re-evaluates
providers, prints their values or mistakes missing explicit input for convention fallback.

**Performance exit.** Run targeted binding/replacement/copy/finalization allocation
checks, including unbound properties and missing values. Check retention after source
replacement and finalization. Do the first matched ordinary baseline/disabled/origins
production smoke comparison here; escalate to profiling only if it exposes a signal.

**Usable checkpoint.** This can be an opt-in ordinary diagnostic feature, explicitly
without cache transport or full failure coverage. It is not yet collaboration.

## M4 — Integrate semantic previous-plan updates

**Work.** Implement a supported previous-plan operation primitive in the real Provider
path, initially map. It must yield a semantic mutation occurrence at acceptance, not
infer one later from rendered provider frames. The existing internal replace boundary
can be an integration reference, but does not define the new collaboration API.

Assignment-shaped `p.set(p.map(f))` support is a separately explicit semantic change,
not a consequence of recording metadata. Before claiming that syntax, implement its
structural substitution/previous-plan rules, live convention roots, sharing and cycle
tests from the [self-reference specification](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/SELF_REFERENCE_SPEC.md).
Preserve internal replace's existing
captured-root behavior. A classifier must distinguish supported evidence from unknown;
an opaque self-read remains unsupported, not a dynamically scoped previous value.

**Functional exit.** Real updates satisfy SC-02/03/06/10/11 for their declared API.
One assigned chain of transforms is one occurrence. No eager transform execution,
dependency/producer loss, shared-provider mutation or accidental fallback is introduced.
Ordinary unrelated replacement still cuts updates. Any unsupported syntax remains
explicitly unsupported; tests do not silently substitute an internal method for it.

**Performance exit.** Measure small and long update chains, shared subgraphs and source
replacement. Metadata append must not scan/copy the whole accepted history each time.
Separate structural plan-inspection/substitution cost from metadata cost; a diagnostic
node limit must not become a correctness rule. Avoid retaining callbacks in descriptors.

## M5 — Implement one real collaborative scalar property

**Work.** Integrate explicit source-binding/contributor-update contexts at an actual
Declarative Gradle entry point. Establish activation, contributor domain, default order
and source permissions for this slice. Use the proposal's convention/explicit source,
update pipeline, complete trace, local constraints and validation state.

Compose each authorized map update immediately. A source rebind changes only the source.
Validate nondecreasing contributor order before `get`, `getOrNull`, `isPresent` and
lifecycle closure; do not reorder or replay. Add local constraints and invalidation,
including constraints declared after updates. Preserve authority across supported
deferred callbacks, without lending an invoker's authority to privately stored code.

**Functional exit.** A real two-plugin plus build-author fixture satisfies SC-01–10,
the collaboration part of SC-12, and SC-15 for map-only scalar properties. It proves:

- Valid updates compute the expected result before and after a source rebind.
- Invalid order identifies the first conflicting occurrences without evaluating values.
- A local constraint can change validity without rebuilding/replaying accepted updates.
- Unknown/unauthorized mutation changes neither plan nor accepted trace.
- Successful reads permit later accepted changes until normal lifecycle closure.
- Turning off optional diagnostics cannot disable correctness checks.

**Performance exit.** Check append growth, O(1) clean-validation checks, dirty-validation
cost against update/constraint counts, and repeated update/query alternation. Allow a
complete linear scan when order/constraints require it; measure aggregate cost rather
than asserting all such workloads are linear. Keep full mutable correctness state,
even if console rendering truncates. Benchmark mandatory collaboration metadata
separately from optional diagnostics. Never call a collaboration variant with validation
removed a supported no-provenance baseline.

**Decision gate.** This is the principal continue/revise decision. If source selection
and updates cannot share a trustworthy representation with diagnostics, or correctness
requires replay/eager evaluation, revisit the integration before adding more operators.
Do not expand the prototype indefinitely to avoid this test.

## M6 — Expand the declared functional envelope

**Work.** Add flatMap and zip, then the collection append/remove operations needed by
the collaborative proposal. Keep arbitrary List/Set/Map contribution APIs explicit
sub-increments, not automatic coverage inferred from factory wiring. Each operation
must preserve values, missingness, producers, dependencies and occurrence boundaries.

Also expand ordinary diagnostic coverage to managed
extension/nested/task properties, Java/Kotlin/Groovy and precompiled/applied plugins,
required task-input validation, unsafe reads and transform exceptions. A complete
causal failure trace is not required to report an honest effective configuration trace.
Cross-build identity must be defined before claiming included-build coverage.

**Functional exit.** Each newly supported operation/boundary adds conformance and
negative tests. Branching dependencies do not become target contributions. An ordinary
unknown boundary stays explicit; collaboration does not accept unsupported structural
updates by treating them as source replacements. Settings-owned tracking and
`ConfigurableFileCollection` still require separate scope decisions.

**Performance exit.** Test branching, fan-out, shared inputs, many contributor identities,
long histories and invalid constraints. Measure occurrence/constraint growth and
registry lifetimes; do not deduplicate distinct updates to save memory. New common
failure hooks must leave successful execution free of diagnostic formatting/capture.

## M7 — Preserve the contract through isolation and configuration cache

**Work.** Implement actual managed-object/isolation/cache codecs for stable descriptors,
effective source/update metadata and mandatory correctness state. Preserve occurrence
relationships without recording deserialization as new contributions. Do not serialize
authority tokens, plugin instances or class loaders. Restore or recompute validation
against the restored order and constraints.

**Functional exit.** SC-10/13/14 hold on cache store and hit, including task properties,
copies, finalized values, missing-value reports and ordering failures. Source relocation
and supported included-build identity are tested. No task-execution authority is
invented by rehydration. Origin/line diagnostic flag changes cannot reuse an incompatible
entry silently; cache compatibility/invalidation behavior is explicit.

**Performance exit.** Measure entry-size growth, serialization/deserialization allocation
and time, cache-hit live heap, and any class-loader retention. Compare mandatory state
and optional location/history payload separately. Cache-hit measurements are not valid
before metadata and validation survive the round trip correctly.

## M8 — Validate production use and decide rollout

**Work.** Run the supported feature on a small corpus: a configuration-heavy multi-project
build, a task-execution-heavy plugin build, a representative collaborative model, and
cache store/hit variants. Include supported parallel execution without claiming new
concurrent property mutation guarantees. Document unsupported cases and migration.

**Functional exit.** Required conformance cases pass on real runtimes, not just fixture
models. Reports remain actionable and honest. Scopes, activation, failure behavior,
cache compatibility and public/internal API boundaries are reviewed. Keep opt-in rollout
until correctness and performance gates are satisfied; default enablement is a separate
product decision.

**Performance exit.** Use repeated independent daemons, matching workloads and enough
warmup to check stability. Compare the selected supported JDKs/platforms; do not reuse
the prototype's aarch64 sizes as universal constants. Resolve or explicitly accept
remaining regressions against agreed budgets. No unresolved disabled-path signal is
silently described as zero overhead.

Optional source locations can follow as their own milestone with instrumentation,
cache-key and language-coverage design. No mandatory stack walking is part of the
origin-first foundation. Do not make complete Groovy line capture a prerequisite for
either useful diagnostics or the collaborative model.

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
- M3/M5: representative smoke builds and targeted profiles only when needed to explain
  a repeatable signal. M7 adds cache-specific checks. M8 performs the full comparison.
- Keep engine/workload revisions, feature flags, JDK/JVM options, instrumentation state,
  environment, raw samples and per-daemon summaries. Warm compilation/caches separately;
  do not time build-logic recompilation or heap profiling as feature overhead.
- JMH needs matched warmup and independent forks, including mixed-state call sites.
  Measure allocated bytes, throughput/latency and growth, not a single pooled timing.
- Heap histograms provide shallow class totals and whole-daemon live bytes. Use retention
  analysis when investigating why objects survive; neither measure alone is peak or
  exclusive retained memory. Count metadata moved outside its nominal package.

**Budget policy:** structural gates above are the initial hard requirements. M0 must
also record per-workload timing/memory regression budgets and the baseline noise needed
to interpret them. A suggested initial *review trigger*, not an accepted overhead
allowance, is a repeatable disabled configuration/wall regression of 1% or more once
the experiment can resolve that difference. Any clear smaller regression can still
require investigation. Failure to detect a regression is not proof of zero cost.

Enabled ordinary and collaborative budgets must be separate. Set limits in terms of
bytes per tracked property/origin/update, absolute workload heap and configuration/read
time at the expected scale. Establish the collaborative scale/budget with the M5 pilot,
before declaring it production-ready. Do not invent a universal percentage from the
prototype's ordinary configuration workload. A breached budget pauses expansion for
targeted investigation or an explicit tradeoff decision; it does not trigger an open-ended
optimization project or justify dropping correctness records.

The existing [measurement protocol](testing/performance/provenance/PRODUCTION_MEASUREMENTS.md)
is a reusable starting point. Its class parsers and negative controls must be updated
and tested for the new storage representation before trusting its output.

## Concrete next action

Review this plan and the shared contract, then authorize a separate clean Gradle
worktree/branch and its baseline revision. The first implementation deliverable is
M0 plus M1: reproducible controls and executable two-mode contract scenarios. Do not
start by porting all runtime code, adding more line capture, or measuring the old
prototype again.

Keep one reviewable change per milestone sub-increment, with tests and evidence beside
it. M1 and M5 are explicit design checkpoints. This preserves what we learned while
giving the shared provenance/contribution implementation a coherent starting point.
