# Shared property-provenance contract

## Status and purpose

Proposed internal contract, version 1. This document defines the shared foundation
for ordinary-property diagnostics and collaborative-property correctness. It is not
a public API, a declaration of implemented support, or a new Provider evaluator.

It refines the [Property Provenance](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/PROPERTY_PROVENANCE.md)
and [Collaborative Property Mode](https://github.com/asodja/gradle-provider-api-semantics/blob/0a695690ac2a4852b7d455a31b1adf830345076c/COLLABORATIVE_PROPERTY_UPDATES.md)
specifications at semantics revision `0a695690ac2a4852b7d455a31b1adf830345076c`.
Their value, source-selection and ordering semantics remain authoritative.

**Share accepted-mutation facts, effective-provenance views and rendering. Keep
authorization, source/update composition and ordering policy in the property mode.**

Collaborative mode still maintains one Provider pipeline, composes each accepted
update immediately, and validates contributor order before observation or lifecycle
closure. It neither reorders updates nor reconstructs/replays a pipeline at `get()`.
Metadata may accompany structural nodes or live in a side sequence. The contract
requires neither a duplicate Provider graph nor two copies of the same update trace.

## 1. Shared vocabulary

These are conceptual types and invariants, not proposed Java signatures or storage
layouts. An implementation may intern descriptors and use compact references.

| Concept | Contract |
|---|---|
| `ContributorKey` | Stable logical contributor, distinct from an invocation or display label. Cases include plugin ID, plugin-class fallback, build author, applied-script contributor, settings/environment roles, and unknown. |
| `DiagnosticOrigin` | Typed plugin/script/unknown descriptor and display information. Script roles come from application boundaries, never filenames. Source paths identify the configuring script, not the target property's project. |
| `Attribution` | Contributor key, origin, source-scope context, and optional diagnostic application/location detail. Attribution alone conveys no authority. |
| `MutationOccurrence` | One successfully accepted mutation, with attribution and its semantic operation. Repeated mutations are distinct even if all descriptor fields match. |
| `TargetContext` | The property/model and build/project scope being configured. Kept separate from the contributor's source scope. |
| `FailureOperation` | Ephemeral query or attempted mutation attached to a problem; never an accepted occurrence. |

The context establishing contributor identities must define their namespace/equality.
Within one contributor domain, the same plugin contributor can have multiple runtime
applications. Several build files can share `BuildAuthor` while retaining different
origins. Do not merge unrelated domains merely because plugin IDs or project paths
match. Source/target build identity and an application ID must not be silently folded
into the contributor key. Exact cross-build key encoding is deferred, not guessed.

Persistable descriptors contain no plugin instances, class loaders, hosts, authority
tokens or runtime application objects. Application detail may be an optional token;
it is not a stable contributor identity. Locations are optional diagnostic detail and
must not affect classification, source selection, authorization or ordering.

Logical occurrence identity must survive sharing and copying: two occurrences from
the same plugin must not collapse, and copying an occurrence must not create a new
mutation. This does not require a global counter or UUID. Sequence positions or
scoped tokens suffice if references remain unambiguous, including after transport.

## 2. Semantic operation, not method name

The property implementation supplies a classification at the mutation boundary:

```text
Binding(Explicit | Convention)
Update(shape)
SelectionChange(ClearExplicit | ClearConvention | PromoteConvention)
UnclassifiedBinding(reason)
```

`Update(shape)` means a structurally recognized operation over the previous plan.
Initial shapes are map, flatMap, zip, append and remove as their plan semantics become
supported. A compound shape can describe multiple transforms inside one mutation;
it is still one occurrence and one contributor-ordering entry. Operation tags do
not assert commutativity or permit reordering. No transform closures or values belong
in the provenance descriptor; executable structure remains in the Provider plan.

`Binding(Explicit)` states what was bound, not why it is missing. Only an implementation
that understands the relevant structure may additionally establish that an assignment
is a non-self replacement. An opaque or unsupported assignment must not be treated as
proven non-self merely because the classifier failed to find a previous-plan read.

Examples:

| Mutation or derivation | Shared fact |
|---|---|
| Plugin B binds a provider created by A | Binding attributed to B; A's provider dependencies are separate information |
| `target.set(source.map(f))`, with a supported non-self relationship | One explicit binding, not a map update of target |
| Supported previous-plan update containing `map(f).map(g)` | One update occurrence, not two contributions |
| Two accepted updates from the same plugin | Two occurrences in acceptance order |
| `val q = p.map(f)` without assignment | No property mutation |
| A self-read hidden in `provider { p.get() }` | No invented structural update; existing unsupported-cycle behavior remains |

An ordinary implementation can accept an unclassified binding under its existing
semantics and expose a partial explanation. Collaboration must reject an operation it
cannot establish as an allowed source binding or supported update; it cannot authorize
it using a negative diagnostic-classification result.

An authorized source context may bind an opaque provider without explaining all of
its dependencies. Unknown dependency structure alone is not missing contributor
authority; neither permits inventing a structural update or a causal failure path.

## 3. Two consumers of the same facts

### Effective provenance view

```text
EffectiveProvenanceView:
    sourceSelection: Explicit | Convention | Unconfigured | Unknown
    selectedSource: Known(occurrence) | Unattributed | Unconfigured | Unavailable(reason)
    updates: effective local update occurrences, in application order
    shadowedConfiguration: non-selected binding occurrences
    coverage: CompleteLocal | Partial(reasons)
```

The view describes the effective plan at the requested checkpoint. The property mode
supplies which source and updates remain effective; the renderer does not reconstruct
that decision from chronological history or by evaluating providers. Reading a view
must not evaluate values, execute transforms or query producer tasks. It may be backed
by mutation metadata on known structural nodes, not necessarily a standalone list.

Explicit selection is independent of value presence. An explicitly selected missing
provider does not select the convention. An unconfigured property, a known binding
with unknown author, and a binding whose provenance was lost are different cases.
`CompleteLocal` concerns the target property's accepted effective configuration only;
it does not claim complete upstream dependency coverage or proven failure causality.

Local updates are distinct from upstream properties' mutations. A report may expand
upstream provenance with explicit boundary/coverage information, but those upstream
occurrences must not enter the target property's contributor-order validation.
Side inputs to zip/map operations are dependencies, not additional contributions to
the target. Diagnostic history, if offered, is another optional view, not the source
of effective selection or order validation.

Root selection must follow the actual plan semantics. A live convention root must be
resolved as live metadata at the checkpoint, not accidentally frozen by a diagnostic
snapshot. Conversely, the existing internal `replace()` captures its previous plan;
provenance must not turn that root into a live convention. The proposed ordinary
self-assignment rules and today's internal replace behavior are not interchangeable.
`sourceSelection` describes that effective root, not just the current cell's
`isExplicit` flag: an explicit plan containing an update can still descend from a
captured or live convention root. A root used in the effective local chain must not
also be called shadowed merely because copied metadata still contains that binding.

### Complete accepted-update trace

```text
AcceptedUpdateTrace:
    all accepted local structural-update occurrences, in acceptance order
```

Collaborative validation consumes this trace and the effective contributor order.
It must include every accepted update while the property remains mutable, including
repeated contributors. Source bindings, conventions, constraints, provider derivations
and failed attempts are not update entries. A source rebind does not clear the trace.
Constraints have their own correctness state and may carry origins for explanations.

In collaborative mode, effective updates and the accepted-update trace can share one
sequence. They are separate contracts, not a requirement for duplicate storage.
A partial/truncated `EffectiveProvenanceView` is never implicitly promoted into a
complete trace. Diagnostics may abbreviate output; validation may not omit entries.

In ordinary mode, replacement can discard old effective updates. Retaining discarded
updates for optional history does not make them part of the new effective plan.

## 4. Mutation boundary and mode ownership

Conceptually:

```text
resolve attribution and semantic operation without evaluating the proposed value
check lifecycle/type/structural preconditions
if collaborative: authorize using an explicit source/update context
apply the mode's source binding or immediate pipeline composition
commit accepted occurrence and corresponding provenance state
if collaborative update: append to the complete trace and invalidate order validation
```

Checks required to reject an unauthorized mutation must occur before changing the
property. Accepted plan state and its provenance must describe the same mutation
checkpoint. A rejected mutation commits neither a plan change nor an occurrence;
its attribution is available only on the failure. This is not a transaction rolling
back unrelated side effects of user code, such as an eager `replace` callback making
other mutations. No new concurrency or atomic-access guarantee is introduced.

Ordinary mode may use best-effort user-code attribution. Unsupported propagation
must not be promoted into trustworthy contributor identity. Collaboration requires
an explicit context supplied by Declarative Gradle, with authority checked separately
from the shared record. Deferred execution must preserve the registrant's context;
invoking privately stored code must not silently lend it the invoker's authority.

| Accepted change | Ordinary-property policy | Collaborative-property policy |
|---|---|---|
| Non-self explicit source binding | Replace source and cut the displaced effective update chain | Authorized source context replaces source, preserving updates |
| Supported previous-plan update | Retain the previous effective root/updates according to that API's semantics; append one update | Authorized contributor composes immediately; append one complete trace entry |
| Convention binding/replacement | Follow normal source/convention and any supported live-root semantics | Change convention source; preserve updates; explicit source still takes precedence |
| Unset/promotion/other selection changes | Follow the existing API; do not invent an update occurrence | Only expose when mode policy defines and authorizes them; do not infer permission from this contract |
| New ordering constraint | Not part of ordinary provenance semantics | Change local constraints and invalidate validation; do not reorder the pipeline |

Global contributor order, local constraint resolution, activation of collaborative
mode and the list of authorized source-binding contexts belong to Declarative Gradle.
The shared contract does not decide them from plugin application order or display text.

## 5. Observation, lifecycle, flags and transport

- Collaborative `get`, `getOrNull` and `isPresent` validate the accepted-update trace
  before evaluating the existing pipeline. Invalid order identifies the first conflict
  without running Provider transformations. A successful query does not end mutation.
- An accepted update or constraint invalidates cached order validation. Source rebinding
  changes the effective source view but not the contributor sequence; implementations
  may conservatively invalidate validation for other accepted changes too.
- Lifecycle closure validates before closing mutations. `disallowChanges` requires no
  value evaluation. `finalizeValue` validates then performs normal finalization;
  finalize-on-read validates before its normal behavior. No snapshot of successful
  finalization is committed when evaluation fails.
- Copies preserve local occurrence identity and the appropriate effective view while
  retaining the existing live-upstream semantics. Copying is not a new contribution.
  Do not merge copied upstream updates into another property's local ordering trace.
- Finalized diagnostics may use descriptor-only snapshots. Dropping validation state
  is permissible only once no further validation/mutation needs it; required explanation
  metadata must remain. Frozen snapshots do not retain failed-operation frames.
- Ordinary diagnostics can be disabled and their metadata omitted; existing messages
  remain byte-for-byte unchanged. Collaboration's mandatory identity/operation/trace
  state cannot be disabled with a diagnostic flag. Optional lines/history/rendering can.
- Isolation and cache transport must preserve required descriptors, occurrence relations,
  effective selection and correctness state. Rehydration is not a mutation. Do not carry
  transient authority tokens; an execution boundary must establish any needed authority.
  Restore or safely recompute validation against the restored constraints/order.

Transport is a contract requirement before claiming support for those boundaries,
not an instruction to implement cache support now. A first project-scoped slice may
explicitly exclude it. Likewise, attribution from settings code does not require
tracking settings-owned properties or adding any other ownership scope.

## 6. Shared reporting contract

The same renderer consumes an effective view in either mode:

1. Lead with the original property problem and preserve its cause.
2. For failure reporting, start `Failure trace to source` with the ephemeral failed
   operation, followed by updates from latest to earliest, then the selected source.
3. For a requested explanation, use `Configuration trace to source` without a failed
   operation. Successful builds remain silent unless explanation was requested.
4. Show shadowed conventions separately under `Shadowed configuration`, not as update
   steps. The same occurrence reached through shared metadata is not a new occurrence;
   do not collapse different occurrences just because their descriptors match.
5. Report partial coverage, opaque boundaries and omitted entries explicitly. Do not
   label a structural dependency path as proven failure causality. Never evaluate to
   choose branches or print configured values merely to explain provenance.

For collaboration conflicts, the ordering component additionally supplies the required
relationship and conflicting occurrence references/positions. The renderer shows that
pair in **acceptance order**, separately from the reverse effective trace. It does not
infer ordering constraints, determine authority, or perform validation itself.

## 7. Shared conformance scenarios

These are acceptance specifications for subsequent implementation, not passing tests
or a statement that current Gradle implements collaborative mode.

Let `C` be a convention from a defaults plugin, `S` an explicit source from the build
author, and `A`/`B` two authorized update contributors. A source is bound, then A updates,
the explicit source is replaced, then B updates:

| Checkpoint | Ordinary effective view (supported previous-plan updates) | Collaborative effective view | Collaborative update trace |
|---|---|---|---|
| Bind C | C | C | empty |
| Accept A | C → A | C → A | A |
| Bind S | S | S → A | A |
| Accept B | S → B | S → A → B | A, B |

The collaborative failure projection, without line numbers, is:

```text
Failure trace to source:
    at task ':show' action [get()]
    at plugin 'B' [map update]
    at plugin 'A' [map update]
    at build author [explicit source]

Shadowed configuration:
    at plugin 'defaults' [convention]
```

With required order `A < B`, the trace `B, A` must fail before value evaluation;
binding S between those updates does not repair it. A valid local constraint can
change the effective order and make the already composed pipeline valid, without replay.

| ID | Required assertion |
|---|---|
| SC-01 | Source-rebinding sequence above yields the different mode projections using the same descriptor/occurrence contract. |
| SC-02 | One assigned update containing multiple maps yields one occurrence; two updates by A yield two entries. |
| SC-03 | A creates a provider, B binds it: B owns the binding; upstream operations are not target contributions. |
| SC-04 | An explicitly selected missing provider never falls back to C; unconfigured and unattributed remain distinguishable. |
| SC-05 | Unknown/unclassified ordinary attribution produces honest partial information; collaboration cannot authorize it from the diagnostic fallback. |
| SC-06 | Rejected mutation-time authorization/lifecycle/type checks append nothing; the failed attempt is report-local and original causes survive. Lazy value-validation failures remain evaluation failures, not rejected historical bindings. |
| SC-07 | Invalid contributor order fails on query and lifecycle closure without evaluating transforms; queries alone do not close mutations. |
| SC-08 | A later update or constraint invalidates validation; changing constraints does not reorder or replay accepted operations. |
| SC-09 | Deferred/nested callbacks preserve registrant attribution and, where required, explicit authority; nested plugin application restores context. |
| SC-10 | Copy/finalization retain the correct source/update occurrences; failed finalization commits no successful snapshot and live upstream inputs stay live where required. |
| SC-11 | Supported ordinary live-convention-root updates reflect later conventions; internal replace snapshots retain their existing captured-root behavior. |
| SC-12 | Diagnostic truncation cannot hide an ordering violation beyond the rendered prefix, and disabling diagnostics does not disable collaboration validation. |
| SC-13 | Cache/isolation round trips preserve required occurrence relationships and scoped descriptors, without adding deserialization contributions. |
| SC-14 | Same labels in different contributor domains do not alias; multiple applications within a domain can share a contributor without collapsing occurrences. |
| SC-15 | Both renderers use the same reverse effective projection, separate shadowed configuration, and chronological conflict pairs; no value evaluation/printing is introduced. |
| SC-16 | Disabled ordinary diagnostics retain existing messages and semantics; no new scope or self-reference behavior follows from adding metadata. |

## 8. Current implementation and next increment

At prototype commit `7260de1a7b5`, typed diagnostic origins, successful source/convention
records, a narrow internal replace/map-update classification, shallow-copy preservation
and bounded finalized diagnostic snapshots exist. See the
[implemented map-update slice](PROPERTY_PROVENANCE_UPDATES.md).

The current `PropertyProvenanceOrigin` is not a `ContributorKey` or authority token.
The current `PropertyProvenanceTrace` is bounded diagnostic traversal, not a complete
`AcceptedUpdateTrace`. Its provider frames must not be reinterpreted as contributions.
`PropertyUpdateClassifier.isMapUpdate == false` is not proof of replacement. Existing
`[explicit source]` fallback wording does not establish complete semantic classification.

The next increment should make SC-01 through SC-04 executable as focused model tests
of the shared facts and projections, with explicit ordinary/collaborative transition
policies. No new Provider evaluator or public API is needed for those contract tests.
Then adapt the supported ordinary scalar path to that contract. Collaboration can
consume the same occurrences when its explicit context and pipeline are implemented;
do not claim collaboration conformance from a metadata-only test model.

Deliberately deferred implementation choices are concrete Java types/storage, cross-build
key encoding, cache wire format, additional operation classifiers and scope wiring.
Declarative activation, global order and authorized source contexts remain integration
decisions from the collaborative proposal. None requires line numbers or renewed
performance investigation to define or test this shared contract.
