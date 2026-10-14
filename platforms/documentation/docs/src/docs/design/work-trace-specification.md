# Work trace. Specification of the task input/output trace files.

## Purpose

A work trace is a pair of files Gradle writes during a build that let a reader answer,
without re-running the build:

* which task produces the file at a given path;
* which tasks consume the file at a given path;
* why a given task was scheduled (the chain from the requested tasks to it, with edge kinds);
* why task A depends on task B (explicit `dependsOn`, an input file property, or a finalizer).

Explaining why a task executed instead of being up-to-date is out of scope (see Non-goals).

## Enabling

- Internal option `org.gradle.internal.work.trace`, set as a system property or in `gradle.properties`.
- Value is a directory path. An empty value selects the default directory
  `<root project build dir>/reports/work-trace/`.
- Unset means off; nothing is collected.
- At the end of the build Gradle prints, at lifecycle level: `See the work trace at: <clickable directory url>`.

(open) Whether this becomes a public `--work-trace` flag follows the `--profile` route later.

### Forced input capture

The trace exists for observability, so running with it on makes Gradle snapshot the inputs of every task
that enters the execution engine, including tasks it would otherwise not snapshot:

- tasks that declare no outputs;
- untracked tasks (`@UntrackedTask`, `doNotTrackState()`);
- tasks that short-circuit as `NO-SOURCE`, whose non-primary inputs Gradle normally never fingerprints.

Forced capture does not change what Gradle does with the task: no execution history is stored for it,
it is neither considered up-to-date nor loaded from the build cache, and `NO-SOURCE` still short-circuits.
The only cost is the fingerprinting work.

Tasks skipped by `onlyIf` or `enabled = false` are scheduled and get a `task` record with full `scheduling`
data and declared output locations, but they never enter the engine: `inputs` is `null` and output file lists
are empty. (open) Capturing their inputs would need fingerprinting outside the engine.

## Files

The directory holds two files, each in JSON Lines format: one JSON object per line, UTF-8,
written as the build progresses. Records are distinguished by `kind`.
A reader must tolerate a truncated last line (failed or killed build)
and must ignore unknown fields and unknown kinds.

| File | Written when | Contents |
|---|---|---|
| `plan.jsonl` | the task graph is calculated (configuration time) | one `plan` record per build in the build tree |
| `work.jsonl` | the build executes work | one `projects` record, then one `task` record per task that reached execution |

The two files are separate because the task graph is not recalculated on a configuration cache hit:
no `plan` record is produced in that build, and the `plan.jsonl` from the build that stored the cache entry stays valid.
`plan.jsonl` is truncated and rewritten only when a build calculates the task graph;
`work.jsonl` is truncated and rewritten by every build that runs with the option set.

A `--dry-run` build calculates the task graph without executing it, so it writes `plan.jsonl`
and a `work.jsonl` holding only the `projects` record. This is the cheapest way to refresh the plan
and to answer the scheduling questions without running any task.

### Configuration cache

- The option value is a configuration input. Turning tracing on or off, or changing the directory,
  invalidates the configuration cache entry, so the next build reconfigures and writes `plan.jsonl`.
- `plan.jsonl` is a configuration input of the build that writes it. Its content hash is recorded in the
  entry's fingerprint after it is written, so deleting or editing the file invalidates the entry and the next
  build reconfigures and writes it again. A configuration cache hit therefore guarantees that `plan.jsonl`
  is the plan of the cached configuration.
- For this to hold, `plan.jsonl` must be deterministic for a given configuration:
  records in build order, tasks and edges sorted, no timestamps or other varying content.

(open) The directory can still hold a `plan.jsonl` from a build with different requested tasks
if that build ran last; the fingerprint only protects the entry that wrote the file.

### Identities

- A task is identified by its build-tree task path, a single string: the path of its build in the build tree
  joined with the task path, where the root build's `:` collapses.
  `:app:compileJava` is a task of the root build; `:included:lib:jar` is `:lib:jar` in the included build `included`.
  This is Gradle's task identity path. Every task reference in the trace, including the plan's requested and
  excluded task lists and all scheduling edges, uses this form.
- A project is named by its identity path, which is unique across the build tree:
  `:` for the root project, `:app` for a subproject of the root build,
  `:included:lib` for project `:lib` of the included build `included`.

### Locations

A location names a file or directory. It has one of two forms:

- Inside a project directory: `{"project": ":app", "path": "build/classes/java/main"}`.
  `path` is relative to that project's directory, uses `/` separators, and is `""` for the directory itself.
  When project directories nest (the root project directory usually contains the others),
  the deepest project directory containing the file wins.
- Outside every project directory: `{"path": "/Users/me/.gradle/caches/.../guava.jar"}`, an absolute path in native form.
  This covers the Gradle user home, transformed artifacts, external files and anything outside the checkout.

Output files under a location are listed as `/`-separated paths relative to that location;
`""` means the location itself is the file (a `FILE` output).

The `projects` record maps project identity paths to absolute directories,
so a reader can turn any location back into an absolute path.

## `plan.jsonl`

### `plan` record (one per build in the build tree)

```json
{
  "kind": "plan",
  "buildPath": ":",
  "requestedTaskPaths": [ ":app:build" ],
  "excludedTaskPaths": [ ":app:test" ],
  "tasks": [
    {
      "buildTreeTaskPath": ":app:compileJava",
      "dependencies": [ ":lib:jar" ],
      "mustRunAfter": [],
      "shouldRunAfter": [],
      "finalizedBy": []
    }
  ]
}
```

- `tasks` lists every task scheduled in that build, whether or not it later ran.
- Edges may name a task that has no record and is not in `tasks`: a task excluded with `-x` stays in the
  dependency sets of the tasks that depend on it. Such an edge is a leaf, and `excludedTaskPaths` explains it.
- `dependencies` merges explicit and input-inferred edges; the `task` record splits them.

## `work.jsonl`

### `projects` record (one per build tree, first line of the file)

```json
{
  "kind": "projects",
  "projects": [
    { "project": ":", "dir": "/Users/me/checkout" },
    { "project": ":app", "dir": "/Users/me/checkout/app" },
    { "project": ":included:lib", "dir": "/Users/me/checkout/included/lib" }
  ]
}
```

### `task` record (one per task that reached execution)

```json
{
  "kind": "task",
  "buildTreeTaskPath": ":app:compileJava",
  "implementation": "org.gradle.api.tasks.compile.JavaCompile",
  "caching": {
    "key": "3f2a...",
    "disabledReason": null,
    "disabledReasonCategory": null,
    "originBuildInvocationId": null
  },
  "scheduling": {
    "requested": false,
    "explicitDependsOn": [ ":app:classes" ],
    "inputDependencies": [
      { "property": "classpath", "tasks": [ ":lib:jar" ] }
    ],
    "finalizerOf": [],
    "mustRunAfter": [],
    "shouldRunAfter": []
  },
  "inputs": [
    {
      "property": "source",
      "attributes": [ "..." ],
      "roots": [
        { "project": ":app", "path": "src/main/java" }
      ]
    },
    {
      "property": "classpath",
      "attributes": [ "..." ],
      "roots": [
        { "project": ":lib", "path": "build/libs/lib.jar" },
        { "path": "/Users/me/.gradle/caches/modules-2/files-2.1/.../guava.jar" }
      ]
    }
  ],
  "inputValueHashes": { "options.encoding": ".." },
  "outputs": [
    {
      "property": "destinationDirectory",
      "type": "DIRECTORY",
      "location": { "project": ":app", "path": "build/classes/java/main" },
      "files": [ "com/acme/A.class", "com/acme/B.class" ]
    }
  ],
  "inputsCaptured": true
}
```

Field semantics:

- Task outcomes (executed, up-to-date, from cache, and so on) are not recorded; the trace describes
  the work, not how a particular build treated it.
- `scheduling.requested` is true when the task was requested on the command line (or is a default task).
  It is computed at execution time and does not depend on `plan.jsonl`.
- `scheduling.explicitDependsOn` are edges declared with `dependsOn` or by task dependency objects
  that are not input file properties.
- `scheduling.inputDependencies` lists, per input file property, the tasks that build its files.
  A task is scheduled because of some chain of `explicitDependsOn`, `inputDependencies` and `finalizerOf`
  edges from a requested task; ordering constraints never cause scheduling.
- `inputs` lists each input file property with its attributes and its roots.
  A root is a location: a file, or a directory whose contents the task consumes.
  Individual files under a root, their hashes and the normalization are not recorded.
  It is `null` when `inputsCaptured` is false.
- `inputsCaptured` is false only for tasks skipped by `onlyIf` and for tasks that failed before their inputs
  were snapshotted (for example, a failure while resolving an input). See "Forced input capture".
- `outputs` lists each declared output property with its location and `type` (`FILE` or `DIRECTORY`).
  `files` lists the regular files under the location, relative to it, as they exist after the task's turn
  in the build: produced by it, kept from a previous build when it was up-to-date, or loaded from the build cache.
  It is empty when nothing is there, for example after a no-source short circuit or a failure.
  No hashes are recorded.

## Answering the use cases

Queries take a path in either form; a reader converts between forms using the `projects` record.
The first three questions need only `work.jsonl`; `plan.jsonl` adds the tasks that were scheduled but never ran.

- **Producer of path P:** the `task` record with an output whose location is P or an ancestor of P,
  narrowed to the entry whose location joined with a `files` entry equals P.
  Several records can match only when outputs overlap, which is itself a finding.
- **Consumers of path P:** every `task` record with an input root that is P or an ancestor of P.
  Consumption is known at root granularity: a task with a directory root is reported as consuming
  every file under it, even when a file tree filter excludes some of them.
- **Why was T scheduled:** search from the `requested` tasks over `explicitDependsOn`, `inputDependencies`
  and `finalizerOf` edges until T; report the chain with the edge kinds and property names.
  Fall back to `plan.jsonl` for tasks that have no `task` record.
- **Why does A depend on B:** B appears in A's `explicitDependsOn`, in an `inputDependencies` entry
  (then the property name is the reason), or A is in B's `finalizerOf`.

## Non-goals and limits

- Whether and why a task executed instead of being up-to-date (outcome, changed inputs, missing history,
  forced rerun) is not part of the trace; the console, `--info` and Build Scans cover that.
- Artifact transforms are not recorded. Transformed files still appear as task inputs,
  so consumers of them are known, but no record claims to produce them.
- Inputs and outputs are recorded only for tasks that reach execution; `--dry-run` yields the plan alone.
- Locations outside project directories are absolute and machine-specific;
  project-relative locations are portable across checkouts of the same build.
- Input consumption is recorded per root, not per file; file tree include and exclude patterns are not recorded.
- No content hashes are recorded; the trace says which files, not which versions of them.
- The trace is internal and its format may change without notice.

## Validation

- Scheduling closure: every task in `plan.jsonl` is reachable from a requested task
  over the three scheduling edge kinds found in `work.jsonl`.
- Missing-dependency replay: for every input file under some record's output location,
  the producer is reachable from the consumer through declared edges.
- Overlap: no two records share an output location, and no location is nested inside another record's output location.
- Stability: two consecutive traces of an unchanged build are identical,
  and a configuration cache hit leaves `plan.jsonl` unchanged.
