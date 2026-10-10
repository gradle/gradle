# The work model of providers

How a `Provider` describes the work needed for its value, what's wrong with that today, and
the parts of a replacement that are decided. Questions that are still open live in
[provider-work-model-open.md](provider-work-model-open.md). This document only states what we
are fairly sure of.

"Today" means the tip of `remove-visit-producer-tasks-research`: `visitProducerTasks` and
`visitContentProducerTasks` are removed, and observable behavior matches master except where
noted.

Every claim is marked with how it was established:

- **[test]**: pinned by a test on the branch;
- **[exp]**: observed in a throwaway build;
- **[code]**: read from the code, not run.

## Terms

- **Value.** What `get()` returns.
- **Work.** Nodes of the work graph (tasks, artifact transforms, other work nodes) that must
  run for a value to be correct.
- **Work graph.** It is computed before execution and stored by the configuration cache.
  It's static: edges can't be added once execution starts.
- **`ValueProducer`.** A provider's description of its work. It has:
  - `visitDependencies` (scheduled work),
  - `visitGuardedDependencies` (work checked by the `map`/`filter` guard; named
    `visitContentDependencies` until recently),
  - `isKnown`.
- **Guard.** The check in `TransformBackedProvider` and `FilteringProvider`: before running a
  transformer, fail if any task among the upstream's content dependencies hasn't executed.

## 1. What's wrong today

### Missing or wrong dependencies

| # | Symptom | Evidence |
|---|---|---|
| 1.1 | `dependsOn(provider { tasks.a }.orElse(taskB.output))` schedules nothing. The left value is a task, but because the right side has work, the value is never interpreted. | [test] |
| 1.2 | `inFile = taskA.output.map { null }.orElse(taskB.output)` schedules only `:a`. At execution time the value falls back to `b.txt`, and the consumer fails validation because `:b` never ran. | [test] |
| 1.3 | An output `Property<T>` backed by a field gets its producer only when its annotated getter is called on the decorated task. Reached through an `@Internal` getter that reads the field, the property has no producer, and `dependsOn` resolves its value as a task name. Whether it works depends on whether something called the annotated getter earlier. | [test] |
| 1.4 | `@Nested Property<Foo>`: the nested provider's own work is never scheduled. `producer.map { t -> Foo(t.name) }` doesn't schedule `:producer`, while an `@Input Property<String>` set to `producer.map { it.name }` does. | [exp] for the nested case; [code] for the `@Input` comparison |
| 1.5 | `@Nested Property<Foo>` set from a mapped task output: the graph-time walk hits the guard, the error is recorded as an `InvalidValue`, nothing is scheduled, and the consumer fails *at execution* with "Failed to query the value of … 'foo'". This happens even when the transformer never reads the file. | [exp] |
| 1.6 | `flatMap` computes its outer value during graph building, to find the inner provider, and reports only the inner provider's work. The outer value's work is never scheduled. It reads the outer value with `IgnoreUnsafeRead`, which only means that the read doesn't finalize the outer property; the `map` guard still applies. | [code] |
| 1.7 | `dependsOn(providers.gradleProperty("x"))` depends on the task named by the property *only if nothing has read the property before*. `Providers.memoizing` switches its producer from the delegate's to `noProducer()` after the first read. | [code] |
| 1.8 | `dependsOn(listProperty)` with one element from `provider { tasks.a }` and another with work: `tasks.a` is dropped. | [code] |

### The guard

| # | Symptom | Evidence |
|---|---|---|
| 1.9 | It fires whenever the upstream has content work, whether or not the transformer reads the content: `out.map { f -> Foo(file = f) }` fails during configuration. That transformer only uses the location, and its result should carry `out`'s content work forward (value {} and content {task}, before and after). There's no way to say so. | [exp] |
| 1.10 | It's trivially bypassed: `get()` is never guarded (`out.get().asFile.text`), and neither is `flatMap { provider { it } }`. | [code] |
| 1.11 | It has a hole: a task provider's task is exempt (`guarded = false`), so `taskProvider.map { it.outFile }.map { it.text }` passes before the task has run. | [code] |
| 1.12 | It only sees tasks. It resolves with `newTaskResolver()`, which ignores artifact transforms and anything behind them. | [code] |

### APIs

| # | Symptom | Evidence |
|---|---|---|
| 1.13 | `getLocationOnly()` drops *all* work, value work included, and exists only on `FileSystemLocationProperty`. There's no equivalent for a `Provider<File>`. Its producer is unknown, so `dependsOn` and `files()` interpret its value. | [code] |
| 1.14 | `Providers.memoizing` drops its delegate's work (`noProducer()`), and once memoized reports a fully fixed execution-time value, losing "changing content". It memoizes on any read, including missing values. Today's only users wrap work-free providers (Gradle properties), so the only visible symptom is 1.7. | [code] |

## 2. What's broken in the model

**2.1 There's no notion of value work.** A provider's work is one set (`visitDependencies`).
Nothing distinguishes:

- **value work**: work that must run before the value can be computed;
- **content work**: work that produces what the value points at.

For example, a task output property has a location that's configured (no value work), and
content produced by its task.

**2.2 The second set is a guard set, not a model.** Across all producers,
`visitGuardedDependencies` is the scheduled set, minus the task of a task provider, which is
then projected to tasks by the guard. It used to be called "content", which it isn't in
`ProviderInternal`'s sense, and it isn't value work either. It's an exception list for one
heuristic.

**2.3 The task provider's task is a proxy.** `map` can't see what a transformer reads, so
anything derived from a task provider is assumed to need the task. Without it,
`provider.map { file(it.out1.absolutePath) }` (where `out1` is a plain `File` field) wouldn't
schedule the task [test, existing]. The `guarded = false` flag exists only so the guard
doesn't block `taskProvider.map { it.name }`. And `flatMap` is used as the escape hatch from
the proxy, which is why it drops outer work (1.6).

**2.4 `isKnown` has two meanings.**

- **"The producer's dependencies are exhaustive."** This is what `OrElseValueProducer` uses
  to choose branches.
- **"Interpret the value as a dependency notation."** This is what `DefaultTaskDependency`
  and `ProviderBackedFileCollection` use.

Combinators compute it as "any part is known". That only works because of an unwritten
invariant: a producer that isn't known has no work. Mixing parts loses the unknown ones
(1.1, 1.8).

**2.5 There's no way to ask "can this value be computed now?".** Nested walking, `flatMap` and
`orElse` all need to know. Each improvises:

- nested walking calls `getOrNull()` and defers the error (1.5);
- `flatMap` evaluates its outer value while computing its producer (1.6);
- `orElse` calls `calculateExecutionTimeValue()` and re-opens its evaluation scope on every
  visit.

**2.6 Producers aren't snapshots.** `OrElseValueProducer` captures its branch producers when
created, but decides which to use when visited. That mixes stale producers with fresh
presence, needs the owner stashed to detect cycles, and runs evaluation during graph
building and inside the guard.

**2.7 Some values define work.** On a static graph, the work behind `flatMap` over a value
produced by work, or a `@Nested` bean produced by work, can't be known before that work runs.
Today these cases fail late or read unbuilt outputs instead of being handled explicitly.

**2.8 The guard has to guess.** It decides, from the producer, whether the transformer will
read content. Any producer-level answer is wrong for some transformers (1.9, 1.11).

## 3. The model

### Two sets of work

For every provider:

- **V**, value work: what must run before the value can be computed.
- **C**, content work: what produces what the value points at.
- **U = V ∪ C**, usable work: what must run for the value to be used. **The work graph
  schedules U.**

| Provider | V | C |
|---|---|---|
| task output property | ∅ | {task} |
| task provider | ∅ | ∅ (3.5; today {task}, the proxy of 2.3) |
| provider backed by a dependency container (`FileCollection.getElements()` etc.) | see below | see below |
| `provider { .. }` | ∅ | ∅ (untracked; the callable is the user's responsibility) |
| `ProviderFactory.fileContents(Provider<RegularFile> file)` | U of `file` | ∅ (the `fileContents(RegularFile)` overload has no work) |

**Providers backed by a dependency container** involve several kinds of work, which one
`TaskDependencyContainer` can't tell apart. Taking a configuration's files as the example:

- **Resolving the configuration.** It's needed to compute the value, but it isn't on the work
  graph: the provider's value factory runs it inline when the value is computed. Until
  resolution becomes a node, it's untracked, like the callable of `provider { .. }`.
- **Work that produces the content of files whose locations are already known**, such as the
  tasks that build project artifacts. This is C.
- **Work without which the locations aren't known**, such as artifact transforms, whose output
  location depends on their inputs. This is V.

The container reports all of this together, so such a producer reports the whole container
as V. That's sound but conservative (3.1). How containers could report V and C separately is
open.

### Propagation rules

- **`map`, `zip`, `filter`:** V = U = the inputs' U. The function is opaque, so it's assumed
  to read content, and its result is assumed to still point at that content.
- **`orElse`:** the first present branch, decided when the producer is requested (3.3).
- **Collections, `merge`, `plus`:** the union of their parts.
- **Memoizing:** passes through its delegate's work. Memoizing caches the value, not the work.
- **`flatMap`:** see 3.4.

### Representation

A `ValueProducer` keeps visiting its work, and answers structurally whether each set is
known to be empty:

```java
void visitDependencies(TaskDependencyResolveContext context);       // U
void visitValueDependencies(TaskDependencyResolveContext context);  // V
boolean hasNoDependencies();        // U is known to be empty
boolean hasNoValueDependencies();   // V is known to be empty
```

No method for C is needed: content only ever propagates as part of U, and every rule above can
be stated in terms of U and V.

**Why visitors and booleans, not getters returning containers:**

- **The scheduler stays as it is.** `CachingDirectedGraphWalker` matches nodes by
  `equals`/`hashCode`. Today the provider is the walker's node, and its producer is traversed
  inline.
- **Getters would have to allocate.** They'd either return new containers per call (new walker
  nodes, and more allocation), or keep persistent ones on producers (more memory, on objects
  that are mostly recreated by every `getProducer()` call).
- **Visiting allocates nothing new,** and "known to be empty" is answered without visiting
  anything.

**How the booleans are computed:**

- **Leaves answer with constants.** A task output property has `hasNoValueDependencies()`
  true and `hasNoDependencies()` false. `provider { .. }` has both true.
- **Combinators combine their parts with `&&`.**
- **A producer backed by a dependency container answers `false`,** whatever the container holds
  right now. That keeps 3.2 deterministic for `fileCollection.elements`.

**There's one unsafe mistake: a producer that says it has work but visits none.**

- Pullability stays conservative: we defer.
- But 3.2 treats it as reported work, and doesn't interpret the value. If the value was a
  dependency notation, that dependency is silently lost.

The mistake can only be made in a `ValueProducer` implementation. That's a small set in one
package, not any `TaskDependencyContainer` anywhere. Three things keep it honest:

- the booleans are abstract, with no default;
- an architecture test restricts where `ValueProducer` is implemented;
- tests check that `hasNo…()` being true means the matching visit adds nothing.

A container that forwards into another when visited is harmless: only producers answer the
booleans, and scheduling expands containers anyway.

### 3.1 Pullability

There are two questions, one per set:

- **Can the value be computed now?** All of V has executed. Asked by readers that don't
  interpret content, such as `@Nested` walking.
- **Can the value be used now?** All of U has executed. Asked by readers that run opaque
  functions, such as `flatMap`.

Each question can be answered at three levels, which differ in cost and in when they can be
asked. They're described here for V; U is the same.

| level | question | needs | meaningful |
|---|---|---|---|
| 1 | is V **known** to be empty? | `hasNoValueDependencies()`; nothing is visited | during graph building |
| 2 | is V **actually** empty? | visiting and resolving every `TaskDependencyContainer` in V | during graph building |
| 3 | has everything in V **executed**? (empty counts) | the execution state of every node in V | during execution; at graph time it equals level 2 |

**Graph-time decisions use level 1.** These are `orElse` (3.3), `flatMap` and `@Nested` (3.4).

- **It must be sound.** It may say "not known to be empty" when V is in fact empty, but never
  the reverse.
- **Errors are therefore always false negatives,** which lead to the safe path of 3.4: schedule
  the outer work, defer, verify.
- **The main source of false negatives is containers.** A producer backed by a dependency
  container is never known to be empty, and through the `map` rule neither is anything mapped
  from it. So `files.elements.map { .. }` used in a `flatMap` or a nested value is always
  deferred, and `orElse` over it schedules both branches. In the worst case, a deferred value
  then fails verification where level 2 would have pulled it at graph time. That's a clear late
  failure, never a wrong answer.
- **Untracked `provider { .. }`** counts as empty by convention. That's the user's
  responsibility, not a hole in the model.

**Level 2 is not planned.** Resolving containers is expensive and re-entrant (resolution can
depend on providers), and the answer is needed in the middle of computing a producer. The
scheduler does resolve these containers when it builds the graph, so if level 2 is ever
needed, it should reuse that result rather than resolve on demand.

**The deferred verification of 3.4 needs less than level 3.** It only has to show that every
dependency discovered when the consumer runs is already a transitive dependency of the
consumer's node, so it has run by then. That's a graph-membership check, using the
object-to-node mapping the scheduler already does. No execution state is needed.

Level 3 would only be needed by checks made during execution against arbitrary work. Whether
there will be any depends on the guard, which is open.

### 3.2 Dependency notations (`dependsOn`, `files()`)

If the producer reports any dependency (`!hasNoDependencies()`), use it. Otherwise
interpret the value as a dependency notation. If the value isn't one, fail, as
`dependsOn(provider { 1 })` does today.

This replaces `isKnown`.

- **It's a structural answer, not a resolved one.** A producer backed by a container answers
  `hasNoDependencies()` with `false` whatever the container holds, so
  `dependsOn(fileCollection.elements)` behaves the same whether or not the collection currently
  has build dependencies.
- **`orElse` gets branch-following behavior for free.** The decided `orElse` (3.3) reports only
  the chosen branch's work. If that branch reports nothing, the value, which is that branch's
  value, is interpreted.

### 3.3 `orElse`

Walk `[left, right]` when the producer is requested:

- **pullable and missing:** skip it;
- **pullable and present:** include it and stop;
- **not pullable:** include it and continue.

The result is a plain producer. No owner, no re-opened scope, no visit-time evaluation.

### 3.4 Work that defines work (`flatMap`, `@Nested`)

| | can pull when | if it can pull |
|---|---|---|
| `flatMap` | the outer's U is known empty (the function is opaque) | apply the function at graph time. The result has V = the inner's V, and U = the inner's U ∪ the outer's U |
| `@Nested` walking | the provider's V is known empty (walking doesn't run user code on the bean) | walk the bean at graph time; schedule the provider's U as well as the bean's properties' work |

When it can't pull, in both cases:

1. Schedule the outer U.
2. Defer: when the consumer is about to execute, pull, and check that all work discovered
   then is already a transitive dependency of the consumer (3.1).
3. If it isn't, fail with a message naming the late work.

Unpacking errors are never deferred silently.

**`flatMap` needs the task-provider proxy removed first.** With the proxy (today), a task
provider's U is never empty, so `taskProvider.flatMap { .. }` would always defer.

- When the inner work is the same task (`taskProvider.flatMap { it.outputFile }`), the
  deferral is harmless.
- When it reaches other work (`taskA.flatMap { it.inputFile }`, with `inputFile` wired to
  `taskB`'s output), `:b` isn't scheduled, and verification fails late. Today that schedules
  `:b`.

With task providers free of work (3.5), `taskProvider.flatMap { .. }` pulls at graph time, and
its result has the inner provider's work, as today.

### 3.5 Task providers

A task provider has no work of its own: V = C = ∅. Its value, the `Task`, is available during
configuration, and a task's results are reachable through its output properties, which carry
their own work.

- **`dependsOn(taskProvider)` and `files(taskProvider)` keep working** by interpreting the value
  (3.2): the producer reports nothing, so the `Task` is resolved as a dependency notation.
- **The proxy of 2.3 is removed.** Values derived from a task's outputs must come from output
  properties, via `flatMap { it.outProp }` or `map` on the property. Derivations through plain
  fields (`map { file(it.out1.absolutePath) }`) or through `map { it.outProp.get() }` no longer
  depend on the task.
- **`flatMap` becomes the way to reach a task's outputs,** not an escape hatch (3.4).
- **One over-approximation goes away:** an `@Input` String set to `taskProvider.map { it.name }`
  stops scheduling the task.
- **Deprecation can be precise.** During graph building, compute each consumer's dependencies
  with and without the proxy, and warn when a task is reachable *only* through it. The warning
  can name the consumer and the task.

### 3.6 Reads and the guard

- **`get()` and `map` follow the same rule.** Reading a value directly is exactly as safe as
  running a function on it. Whatever the guard becomes (open), it applies to both.
- **Gradle's own reads follow the rules users follow,** and are made more precise where they
  need to be. With 3.3 and 3.4, `orElse`, `flatMap` and `@Nested` decide with pullability
  instead of evaluating, so Gradle itself no longer trips the guard.

### 3.7 Rolling out behavior changes

- **New failures are deprecated first.**
- **What fails today keeps failing.** It isn't downgraded to a deprecation.
- **False positives today become nothing,** not deprecations.

During a deprecation cycle, that means evaluating both today's check and the new one, and
classifying each case by the pair of answers.

## 4. Concrete changes

Each item is its own commit with tests, and states its behavior change. Changes that depend on
open questions are listed in the open document.

| # | Change | Behavior change | Breaking? |
|---|---|---|---|
| P1 | Add the architecture test that restricts `ValueProducer` implementations, and the test check that `hasNo…()` agrees with visiting (Representation). | None. | No |
| P2 | Done: `visitContentDependencies` is renamed `visitGuardedDependencies`, and the javadoc describes `isKnown`, `visitDependencies` (U) and the guarded set as they are. | None. | No |
| 1 | Add `visitValueDependencies`, `hasNoDependencies()` and `hasNoValueDependencies()` with the propagation rules. No consumers yet. Unit tests for the rules. | None. | No |
| 3 | Memoizing passes through its delegate's work and changing content. | Fixes 1.7, 1.14. | No |
| 4 | Replace `isKnown` with the rule in 3.2. Needs 1. `plus()` then drops `noProducer()` unconditionally (today it only does next to a known producer, to agree with `isKnown`). | Fixes 1.1. `zip` with no part reporting work gets its value interpreted: `dependsOn(valueSource.zip(provider { "t" }) {..})` schedules `:t`. Value-source and memoized providers are interpreted consistently. `dependsOn(provider)` whose value isn't a notation fails, as it already does for unknown producers. | Minor |
| 5 | `orElse` as in 3.3. Needs 1. | Fixes 1.1 and 1.2. Flips two pinned tests. | No |
| 6 | `@Nested` as in 3.4. Needs 1. | Fixes 1.4 and 1.5: more work scheduled, and failures at graph time instead of execution. | Some builds fail earlier, with a clearer message |
| 7 | `flatMap` as in 3.4. Needs 1 and 8. | Fixes 1.6. | Some builds fail with a clear message instead of reading unbuilt outputs |
| 8 | Remove the task-provider proxy (3.5). Needs 4, so that `dependsOn(taskProvider)` interprets the value. Deprecate first, warning when a task is reachable only through the proxy, then remove it together with the task producer's `guarded` flag. | `dependsOn` and `files()` of a task provider unchanged. Derivations through plain fields or `map { it.outProp.get() }` lose the task. | **Yes**, after a deprecation cycle |
| 9 | Producer attachment for field-backed output properties (1.3), independent of the rest. One possible approach: apply output roles when the property walker visits a task's properties, not only from generated getters. | Fixes 1.3. | No |

There's no change 2 any more. `zip` using `plus()` is part of the refactoring commit, with
`plus()` made to agree with today's `isKnown`, and its behavior change (C3) is part of change 4.

### Order and prerequisites

```
P1, P2
1 ──► 4 ──► 8 ──► 7
1 ──► 5, 6
3, 9            (independent)
```

Proposed sequence: P1, P2, 1, 3, 4, 5, 9, 6, 8 (deprecation, then removal), 7. P1 can land
together with 1.

### Breaking changes, in summary

- **Removing the task-provider proxy (8).** Builds that derive values from a task through
  plain fields, or through `map { it.outProp.get() }`, lose the dependency on the task. The
  migration is `flatMap { it.outProp }`, and the deprecation names the consumer and the task.
- **Earlier failures (6, 7).** These replace late or silent misbehavior with a graph-time
  error.
- **Minor (4).** Values of providers without work are interpreted consistently by
  `dependsOn`.
