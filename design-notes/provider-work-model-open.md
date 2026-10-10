# The work model of providers: open questions

Questions still under discussion, and ideas we expect to change. The decided parts are in
[provider-work-model.md](provider-work-model.md), which this document refers to by section
number. When something here is settled, it moves there.

## Picking up

- **Branch:** `remove-visit-producer-tasks-research`, on top of
  `jvandort/scheduler/remove-visit-producer-tasks`. It holds:
  - the refactor's behavior preserved, then C2 and C1 re-applied as separate commits;
  - tests pinning current behavior;
  - these two documents.

  `zip` uses `plus()` again (fixup of the refactoring commit), with `plus()` agreeing with
  today's `isKnown`. C3's behavior change comes with change 4 of the main document.
- **Next code step:** changes 1 and P1 of the main document (`visitValueDependencies` and the
  two booleans, with the tests that keep them honest), on a new branch,
  `remove-visit-producer-tasks-research-2`.
- **Cleanup:** the bottle has a master worktree at `/home/genie/gradle-master` for
  comparisons. It can go.

## O1. The guard's end state

Decided: `get()` and `map` follow the same rule (main document, 3.6). **Not decided: whether
that rule is a check or nothing.**

- **Option 1: check.** During configuration, a read (`get()`, or any transformer) needs U known
  to be empty. With a value-only function (O2), V is enough. Leaning this way: any `get()` in
  configuration-time code is suspect, and ambiguous reads belong on the work graph.
- **Option 2: no check.** Providers stop detecting early reads of content. If that detection is
  wanted back, it should come from observing actual file reads; the configuration cache
  already records file reads during configuration.

**Steps valid under either option:**

1. Gradle's own reads stop tripping the guard. This is decided, and comes with changes 5–7 of
   the main document.
2. After the task-provider proxy is removed (main document, change 8), the guard has no
   exception left: it's "the input's U has executed". `visitGuardedDependencies` goes away.
3. An escape for functions that only read the value (O2).

**If option 1 is chosen:**

- **Reads during execution are trusted.** If the graph accounted for a read, it's allowed.
  Today's execution-time check (the tasks in the resolved U have executed) stays, so that
  undeclared reads that fail today don't turn silent. With that, level 3 (main document, 3.1)
  isn't needed.
- **Level 1 or resolving?** During configuration, should the check use level 1
  (`hasNoDependencies()`), or resolve U as today's guard does? Level 1 makes
  `files.elements.map { .. }.get()` fail even for collections that are in fact empty, which
  pushes such reads onto the work graph. That may be acceptable.
- **Smart leaves.** Leaves report "no work" whenever they can prove it: literal `files(..)` and
  `fileTree(dir)` are known empty; a configuration never is, since resolution is work. How far
  this goes is open.
- **Audit Gradle's internal reads during configuration:**
  - the nested walker's `getOrNull()`, which should read with value-only semantics, so V;
  - `FlatMapProvider.getProducer()`;
  - `OutputUnpacker`'s `getOrNull()` on `getLocationOnly()`;
  - `OrElseValueProducer`'s presence check;
  - the 13 `IgnoreUnsafeRead` call sites;
  - configuration-cache store calling `calculateExecutionTimeValue()`.
- **Deprecation** (main document, 3.7). After the proxy removal, "fails today" is "the tasks
  in the resolved U haven't executed", computable from U. During configuration the new rule is strictly stricter
  than today's, so nothing that fails today starts passing. Today's false positives (nested
  case C, combinators over task providers) are fixed by other changes.

## O2. Value-only functions and dropping content

There are two concepts, and the API for both is pending.

- **A function that only reads the value.** It doesn't need the content work to compute its
  result, but its result may still point at the content, so the content work is carried
  forward: V = the input's V, U = the input's U.
  `out.map(<value-only> { f -> Foo(file = f) })` would go from value {} and content {task} to
  the same. API options:
  - **A. Operation variants** (`mapValue`, `flatMapValue`, …). Clear, but doubles the
    transformer API.
  - **B. A marker on the function**, for example a `Transformer` wrapper. The promise sits on
    the function, where it belongs.
  - **C. None.** Code that builds something pointing at content gives that something a
    property and wires the provider in (`file.set(out)`).

  A provider method that changes how the *next* operation behaves (an earlier
  `out.valueOnly().map { .. }` idea) is rejected: it puts the promise in the wrong place.
- **Dropping the content.** Same value, U = V. It's for using a location as an input without
  depending on what produces it, and as the escape for reading a location with `get()` during
  configuration. This is what `getLocationOnly()` does today, except that it also drops V and
  exists only on properties. It needs a name, and would replace `getLocationOnly()`.

## O3. Containers and dependency management

Today a container-backed provider reports everything as V (main document, 3). Should
containers report V and C separately?

**What dependency management would need to express,** for each artifact in a resolved set:
is its location known before its work runs?

| work behind a resolved file set | location known before it runs? | set |
|---|---|---|
| resolving the graph | no; it decides which files exist at all | V. It runs inline today and is untracked; it becomes V once resolution is a node |
| tasks that produce project artifacts | yes (declared artifact file) | C |
| artifact transforms | no; their workspace is keyed by their inputs. The upstream producers of a transformed artifact also become V for that value | V |
| downloads of external artifacts | believed no, if the cache path includes a hash of the content. To confirm | V (if so) |

**Two containers seem to be enough,** because the number of sets follows the number of
questions consumers ask: "can I compute the value?" (V) and "can I use it?" (U). A third set
would need a third question.

**Letting the work express its location doesn't work.** Whether work is V or C is relative to
the value: the same task is C for its output property and V for `out.map { parse(it) }`. It
also has to stay visible statically, without running anything, by looking at a container.

**The cost:** `ResolutionBackedFileCollection` would have to classify artifacts as it visits
them. Until then, everything counts as V, which is sound and only slower (more deferral).

## O4. Mixed collections, and decomposing values

A collection property passes its elements' values through: its value *contains* them. But it
reports its elements' work as a whole, so one element with work stops every other element's
value from being interpreted (main document, 1.8):

```groovy
def deps = objects.listProperty(Object)
deps.add(provider { tasks.named("a").get() })        // no work; the value is a Task
deps.add(tasks.named("b").flatMap { it.outputFile }) // work: {b}
tasks.register("c") {
    dependsOn(deps)  // only :b, though the value [task ':a', file] contains :a
}
```

`dependsOn` isn't the main use case. The more general one is a list of `Provider<Foo>` where
both the outer providers and some inner `Foo` properties carry work. There, the fix is to
"invert": pull the elements that can be pulled, and let their own properties contribute their
work. Today a provider can only be seen through its two containers and its value. There's no
way to split it into the elements that are available now and the ones that aren't.

**Sketch: element visiting on `ProviderInternal`.**

```java
void visitElements(Consumer<ProviderInternal<?>> visitor);  // default: visitor.accept(this)
```

- **It lives on the provider, not the producer,** because a consumer needs each element's work
  *and* a way to pull its value.
- **Elements are providers:**
  - collection and map properties visit one provider per collector, a structure they already
    keep;
  - `MergeProvider` visits its items;
  - a decided `orElse` visits its chosen branch's elements;
  - opaque providers (`map`, `zip`, plain providers) visit themselves.
- **Each consumer decides per element:**
  - `dependsOn` and `files()`: the element's work, else its value;
  - nested-style walking: pull the element if its V is known empty and walk it, otherwise
    schedule its U and defer.
- **The consumer pulls,** so `TaskDependencyResolveContext` stays unchanged.

Open: how a deferred element inside an otherwise pulled collection gets verified later.

**Settled:** an element without work whose value isn't a dependency notation (like a
`RegularFile`) can simply fail, with a message pointing to `inputs.files(..)`. Passing a mixed
list of tasks and files to `dependsOn` is itself the problem.

## O5. Fixed inputs at execution

Before execution, input providers that the graph has verified could be replaced by their
realized, work-free values, by the configuration cache or otherwise. An input should be fixed
by the time its task executes. This is `ProviderInternal`'s own "changing becomes fixed once
its work has run". It's a later, separate change.

## O6. A work graph that can grow during execution

This would make the deferred cases of the main document's 3.4 exact (`flatMap` over values
produced by work, beans produced by work), and would let `orElse` schedule only the branch
that's used. It needs the configuration cache to store deferred decisions, not just edges.
Out of scope for now, but it constrains how deferral is designed.


## O7. Possible optimization: providers as walker nodes

**Background** [code]:

- **The walker's cache lasts the whole graph-building pass.** `TaskDependencyResolver` is
  build-scoped and keeps one `CachingTaskDependencyResolveContext`.
  `CachingDirectedGraphWalker` caches each node's result in a `HashMap`, matched by
  `equals`/`hashCode`.
- **Provider-valued task inputs are passed up as the provider itself,** so a provider shared by
  many tasks is visited once.
- **`dependsOn(provider)` and combinators visit their children's producers inline.**
  `DefaultTaskDependency` calls `producer.visitDependencies(context)`, and `PlusProducer`,
  `CompositeProducer` and the `orElse` producer visit their parts directly. A provider reached
  from several places is visited each time.
- **Producers are short-lived.** Most `getProducer()` implementations allocate a new one per
  call, so passing a producer up wouldn't deduplicate anything.

**The idea:** a producer that holds a child provider passes the child *provider* up
(`context.add(childProvider)`) instead of visiting its producer inline. `DefaultTaskDependency`
does the same with the provider it's given. Producers stay short-lived; the provider is the
stable node.

**The trade-off:**

- **Gain:** each shared provider is visited once per pass instead of once per path.
- **Cost:** one walker node per provider, including the many that are only used once.

Which one dominates in real builds is unknown, so **measure first.**

**What to measure,** per graph calculation:

- the number of walker nodes;
- `visitDependencies` calls per provider *identity*, which counts re-visits;
- `getProducer()` calls, which count producers allocated;
- time spent in `TaskDependencyResolver.resolveDependenciesFor`;
- allocation during graph calculation.

**How:**

- Temporary counters in `CachingDirectedGraphWalker`, `CachingTaskDependencyResolveContext`
  and `AbstractMinimalProvider.visitDependencies`, printed when graph calculation ends, behind
  an internal system property. They're for measuring, not for committing.
- A profiler (JFR allocation and CPU) on graph calculation, as a cross-check.
- Builds:
  - the performance templates in `build-logic/performance-testing`, for example
    `largeJavaMultiProject` and `largeJavaMultiProjectKotlinDsl`;
  - this repository's own build.

  Use `--dry-run`, so the numbers cover graph calculation and not execution.

**Deciding:**

- If re-visits of providers are a meaningful share of graph-calculation time, prototype the
  change on `DefaultTaskDependency` and one combinator, then measure again.
- If re-visits are rare, or the extra nodes cost more than they save, drop the idea.
