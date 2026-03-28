# Dependency Resolution Engine

This document describes the internal workings of Gradle's dependency resolution engine.
It is intended for engineers working on the resolution engine itself.

## Core Concepts

### Modules

A module represents an identity such as `com.google.guava:guava`. A module is implemented by
`ModuleResolveState`. A module may hold multiple `ComponentState` instances in memory
(representing different versions), but at most one component may be **selected** at any
given time.

### Components

A component is an instance of a module, identified by the module plus a version (e.g.
`com.google.guava:guava:31`). A component is implemented by `ComponentState`. A component's
metadata is downloaded from an external source (e.g. POM, GMM) and contains the
component's variants.

### Variants

A variant is a set of metadata within a component. It defines dependencies, files,
attributes, and capabilities. Different variants of the same component may have different
dependencies and metadata.

### Nodes

A node wraps a variant and is implemented by `NodeState`. A node turns its variant's
declared dependencies into outgoing edges. Multiple `NodeState` objects may exist in
memory without being part of the graph — creating a `NodeState` has no side effects.
A node is only considered part of the graph when it is first dequeued from the processing
queue.

A single component may have multiple nodes in the graph simultaneously (from different
variants), subject to the capability invariant described below.

### Edges

An edge connects two nodes and is implemented by `EdgeState`. Each edge has a target
module. Before selection and variant resolution, an edge has a null target node. After
selection determines the target module's selected component, and variant selection
determines which variant of that component the edge targets, the edge is attached to a
specific target node.

### Selectors

A selector is implemented by `SelectorState` and represents a dependency request targeting
a module. A selector is responsible for resolving a `ComponentSelector` (which may express
a version range or a specific version) into a concrete `ComponentIdentifier`.

Selectors are **reference-counted**. Multiple edges requesting the same version of the
same module share a single `SelectorState`. When no edges reference a selector (e.g.
because edges were removed from the graph), the selector is released.

A module tracks its selectors via `ModuleSelectors`, which is owned by the
`ModuleResolveState`.

### Constraints

A constraint is a non-hard edge. A module tracks the number of hard edges targeting it.
When this count becomes positive, pending constraints targeting that module are activated
and become concrete outgoing edges. A module tracks its pending constraints via
`PendingDependencies`, which is owned by the `ModuleResolveState`.

## Inherited State

Certain data flows through the graph via edges. When a node is dequeued, it computes
its effective inherited state from its incoming edges. This inherited state influences
how the node's outgoing edges are processed.

### Excludes

Edges may define excludes via an `ExcludeSpec`, specifying modules to exclude from their
subgraph. Nodes may also define excludes directly; in practice, node-level excludes are
distributed to each of the node's outgoing edges as if each edge declared them explicitly.

When a node is dequeued, its effective exclude set is computed as the **intersection** of
the inherited excludes from all incoming edges, **unioned** with the node's own excludes.
A module is only excluded at a node if every path from the root to that node excludes it.

The effective exclude set is used to filter the node's outgoing edges — edges targeting
excluded modules are not processed. The exclude set also flows through outgoing edges to
downstream nodes.

### Strict Versions

A node's edges define which modules it strictly controls the version for. This set of
modules is the node's **own strict versions**.

When a node is dequeued, it computes strict version state in two layers:

1. **Ancestors strict versions**: Computed from incoming edges using the same
   intersection-across-paths semantics as excludes — a module is considered strictly
   controlled only if every incoming edge carries that module in its strict version set.
2. **Own strict versions**: Defined by the node's edges.

The ancestors strict versions determine how the node's **immediate outgoing edges** behave:
if an outgoing edge targets a module that is in the inherited strict version set, the edge
registers a silenced selector that does not contribute to `selectBest`.

The own strict versions are **not** used to silence the node's own outgoing edges. They
are only considered by downstream nodes when those nodes compute what they inherit. A
downstream node inherits the union of its parent's ancestors strict versions and its
parent's own strict versions — and then intersects across all incoming edges.

This means that nodes higher in the graph with strict version declarations assume complete
control over version selection within their subgraph, as long as they have complete
coverage of all paths to that subgraph.

If strict coverage is broken — for example, a new incoming edge arrives at a node that
does not strictly control the module's version — then the node's outgoing edges targeting
that module release their silenced selectors and acquire normal selectors that participate
in selection again.

If `selectBest` encounters selectors for different strict versions (i.e. two subgraphs
disagree on the strictly controlled version), selection fails. If the strict selectors
resolve to the same version, there is no conflict. The resolution for conflicting strict
versions is for a higher node with broader visibility to declare its own strict version,
silencing the conflicting declarations below.

### Endorsing Strict Versions

An edge can **endorse** the strict versions of its target node. When a node endorses
another node's strict versions, the endorsed node's **own strict versions** are treated
as strongly as the endorsing node's own strict versions — they flow through the endorsing
node's subgraph with the same semantics.

Endorsing inherits only the target node's own strict versions. It does not inherit the
target node's inherited or endorsed strict versions.

This enables a pattern where a single variant acts as a centralized set of strict version
definitions (a platform). Other nodes can endorse that variant, effectively hoisting its
strict version declarations and treating them as their own.

### Cycles in Strict Version Propagation

The graph explicitly supports cycles. Cycles commonly arise from the endorsement pattern:
for example, `com.fasterxml.jackson.core:jackson-databind` declares an endorsing edge to
`com.fasterxml.jackson:jackson-bom`, which in turn declares strict version constraints on
`jackson-databind` and other Jackson modules, which themselves endorse `jackson-bom`.

Computing ancestors strict versions requires care in cyclic graphs. A simple intersection
of all incoming edges' strict versions does not work because cycle participants may not
yet have computed their values when another cycle member attempts to intersect them. If
an uncomputed value is treated as authoritative, it blocks new strict versions from
propagating through the cycle.

To handle this, incoming edges from nodes whose ancestors strict versions have not yet
been computed are skipped during intersection — they do not constrain the result. This
allows cycles to bootstrap: the first cycle member processed computes from its non-cycle
parent(s) and propagates forward to the next cycle member.

## Graph Traversal Algorithm

The resolution engine processes nodes using a BFS queue.

### Node Processing

When a node is dequeued, we first check if it has any incoming edges. If it does not, all
of its outgoing edges are removed.

Whenever an outgoing edge is removed from a node, the edge's former target node is added
back to the queue. This is not limited to nodes that have lost all incoming edges — any
change to a node's incoming edges may change the values it inherits through those edges,
so the node must be reprocessed.

If the node does have incoming edges, we process its outgoing edges in three passes:

1. **Selection**: For each outgoing edge's target module, run selection if needed.
2. **Metadata download**: Download metadata for all selected components that require it.
   This is done in parallel, as metadata fetching is IO-bound.
3. **Variant selection and attachment**: For each outgoing edge, select a variant from the
   target component using the edge's attributes and capabilities (or implicit defaults if
   none are explicitly defined). Create or reuse a node for that variant and attach the
   edge to it. Enqueue the target node.

Whenever an edge is attached to a target node, that target node is added to the queue.
This is because values flow through the graph via edges — when a node's incoming edges
change, the node may need to recompute inherited values. By enqueuing rather than
recomputing immediately, multiple edge changes are batched, and the node recomputes once
when dequeued, keeping recomputation proportional to the number of nodes rather than the
number of edge changes.

### Selection

Selection is the process by which a module determines which of its components is selected.
Selection is performed by the module's `ModuleResolveState` via `selectBest`. This method
takes all `SelectorState` instances targeting the module, resolves any that have not yet
been resolved (converting `ComponentSelector` to `ComponentIdentifier`), and then selects
the best resolved identifier. In the common case, this means choosing the highest version.
If only one selector exists, selection short-circuits.

When a selector loses all of its references and is released, re-selection on that module
happens eagerly and synchronously. This is not necessarily desired behavior.

### Version Change and Retargeting

When selection for a module changes (e.g. from version 30 to version 31), all incoming
edges to nodes of the previously selected component must be retargeted. For each such
edge, we:

1. Detach the edge from its current target node.
2. Run variant selection against the newly selected component's variants.
3. Attach the edge to the resulting target node.

Different edges may select different variants of the new component, since each edge
carries its own attributes and capabilities. Two nodes with the same name from different
versioned components of the same module are unrelated from the engine's perspective —
the new component's variants, dependencies, and metadata may be completely different.

The old component's nodes are enqueued as part of step 1, when their edges are detached.
When they are later dequeued and found to have no incoming edges, their outgoing edges are
removed, which may cascade further cleanup.

### Capability Invariant

The graph maintains an invariant that no two nodes may share a **capability**. This
invariant applies across all nodes in the graph, regardless of whether they belong to
the same component, the same module, or different modules entirely.

When a node is first dequeued and enters the graph, it is checked against existing nodes
for capability conflicts. If a conflict is detected:

1. Both conflicting nodes have their outgoing edges removed (which may cascade selector
   releases and further re-selections).
2. The conflict is registered for later resolution.
3. The conflicting nodes remain in the graph. Edges that targeted them continue to do so.
   However, conflicted nodes are skipped if they appear on the queue — they are not
   processed until the conflict is resolved.

Conflict resolution is **deferred** until the processing queue is empty. This is because
continued resolution may naturally eliminate one side of the conflict (e.g. a version
upgrade removes one of the conflicting nodes from the graph).

When the queue is empty and conflicts exist, the engine resolves the **first** conflict
using user-defined resolution rules. The winning node is re-enqueued and processing
continues. Only one conflict is resolved at a time before returning to queue processing.
