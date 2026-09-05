# Semantic update provenance: first shared building block

This increment gives diagnostics and a future contribution model one shared fact:
a successful property mutation can be a **map update**, not just a source binding.
It does not implement collaborative properties, authority, ordering, or a complete
contributor trace.

## Supported boundary

Ordinary scalar properties already have an internal `DefaultProperty.replace` method:

```java
value.replace(previous -> previous.map(String::trim).map(String::toUpperCase));
```

`previous` is the shallow copy supplied to that invocation. A chain consisting only
of known `TransformBackedProvider` map nodes rooted at that exact copy is now recorded
as `MAP_UPDATE`. All transforms inside that one accepted replacement are **one**
operation. A second replacement is a second occurrence, even from the same plugin.
The record carries the binding plugin/script origin, not the author of the transform.

By contrast, `target.set(source.map(f))` still records an explicit source: selecting a
mapped provider from another property is not an update of the target's previous plan.
The existing multi-property plugin example intentionally keeps those binding labels.

This is an internal prototype seam, not a new method on the public `Property` API.
The [plugin integration fixture](platforms/core-configuration/model-core/src/integTest/groovy/org/gradle/api/provider/PropertyUpdateProvenanceIntegrationTest.groovy)
uses an explicit internal cast in Java and the existing implementation method in
Groovy. No Kotlin or Groovy line-number interception was added.

Classification reads known provider fields only. It does not call `get`, `isPresent`,
producer/dependency queries, or transformations. The existing outer `replace` callback
still executes eagerly to construct its plan; that is unchanged property behavior.
Type validation and mutation run before the origin is recorded. A rejected mutation
records no accepted update; the attempted operation remains the existing `[set()]`
failure frame.

## Effective trace

The property stores a semantic operation record for its latest accepted binding.
The existing shallow-copy supplier chain retains earlier local records and live
upstream providers. Reporting follows this chain; it does not reconstruct updates
from plugin execution order. Finalization preserves the records in the existing
descriptor-only diagnostic snapshot.

The fixture applies an initial plugin binding, a plugin map update and a build-script
map update. A deferred callback can add another update from the same plugin:

```text
Failure trace to source:
    at task ':showUpdateProvenance' action [get()]
    at plugin 'example.updates' [map update]
    at build file 'build.gradle' [map update]
    at plugin 'example.updates' [map update]
    at plugin 'example.defaults' [explicit source]
```

Shadowed conventions remain under `Shadowed configuration`. Map dependency and opaque
provider limitations remain explicit. In particular, a map can itself return no value
despite a healthy source: this is effective configuration provenance, **not a proven
causal failure trace**.

## What stays unchanged

- Ordinary replacement `set(other)` cuts previous updates. A `replace` returning an
  unrelated provider also replaces the plan; it is not classified as a map update.
- `replace { null }` unsets the explicit plan, dropping its update chain and selecting
  the current convention.
- An ordinary `replace` captures its previous convention through the existing shallow
  copy. A later convention does **not** become a new live root of that captured plan.
  The proposed live-convention-root self-assignment semantics are separate work.
- Copies retain their local operations while upstream providers remain live. Successful
  finalization and finalize-on-read preserve the supported diagnostic chain.
- Disabled provenance does not run the classifier, consult mutation attribution, or
  change existing messages. Successful provenance reporting remains silent.
- Scalar/file-property behavior stays ordinary. List/map/set contribution tracking,
  `ConfigurableFileCollection`, other scopes and cache transport are not added.

## Deliberate unknowns

Only exact built-in map nodes are recognized, up to 128 maps in one replacement.
Flat maps, zips, filters, fallback nodes, custom subclasses, opaque callbacks, direct
property reads, and copies from a different invocation are not classified. Existing
`[explicit source]` wording for such bindings does **not** prove they are non-self
replacements. A negative classifier result means **unclassified**, not authorized.
Neither classification nor provenance makes `p.set(p.map(f))` safe; assignment-shaped
self-reference behavior is unchanged.

## Next shared steps

1. Introduce an explicit effective-source/update representation that does not rely on
   diagnostic traversal. Keep semantic update occurrences distinct from provider nodes.
2. Define contributor keys and script roles separately from diagnostic origins, runtime
   applications and source/target scope. Ambient attribution is not authority.
3. Extend structural operation classification alongside the actual supported provider
   plan semantics, with an explicit unknown result at unsupported boundaries.

Collaboration will additionally need an explicit source/update context, a complete
accepted-update trace, source rebinding that retains its update pipeline, and order
validation before observation/lifecycle closure. Diagnostic rendering may truncate;
that correctness state must not truncate or depend on the diagnostic feature flag.
Copy/isolation/cache transport will need to preserve it rather than attribute recreation
as a new contribution. Those requirements are not satisfied by this diagnostic chain.

Line numbers, settings-owned tracking and broader failure reporting are not prerequisites
for these shared steps. Performance investigation is paused.

## Verification

All 1,738 selected tests passed: 1,355 model-core unit cases, 46 provenance integration
cases, five project-host cases and 332 file-property/file-collection cases. This includes
36 new cases covering classification boundaries, one mutation versus multiple map
nodes, repeated contributors, origin/operation recording, copies/finalization, live
upstream providers, convention selection, rejected/null updates, plugin/script/deferred
attribution, successful silence and disabled compatibility. Checkstyle and unit/integration
CodeNarc checks passed. No performance measurements were run for this increment.
