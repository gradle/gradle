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

### Tasks without input snapshots

The trace records inputs only when the execution engine fingerprints them. Gradle does not fingerprint:

- tasks with no actions (lifecycle tasks such as `build` or `check`);
- tasks that declare no outputs;
- untracked tasks (`@UntrackedTask`, `doNotTrackState()`);
- tasks that short-circuit as `NO-SOURCE`;
- tasks skipped by `onlyIf` or `enabled = false`.

These tasks still get a `task` record with full `scheduling` data and their declared output locations,
so scheduling chains through them stay intact, but `inputs` is `null` and their output file lists are empty.
Consumer queries cannot see them.

(open) Forcing input capture for these tasks when tracing is on. The capture step is gated only on whether
task history is maintained, so decoupling the two is small for the first three cases; `NO-SOURCE` needs the
short-circuit step to fingerprint non-primary inputs, and `onlyIf` skips never enter the engine at all.

## Files

The directory holds two files, each in JSON Lines format: one JSON object per line, UTF-8,
written as the build progresses. Records are distinguished by `kind`.
A reader must tolerate a truncated last line (failed or killed build)
and must ignore unknown fields and unknown kinds.

| File | Written when | Contents |
|---|---|---|
| `plan.jsonl` | the task graph is calculated (configuration time) | one `plan` record per build in the build tree |
| `work.jsonl` | the build executes work | one `task` record per task that reached execution |

The two files are separate because the task graph is not recalculated on a configuration cache hit:
no `plan` record is produced in that build, and the `plan.jsonl` from the build that stored the cache entry stays valid.
`plan.jsonl` is truncated and rewritten only when a build calculates the task graph;
`work.jsonl` is truncated and rewritten by every build that runs with the option set.

A `--dry-run` build calculates the task graph without executing it, so it writes `plan.jsonl`
and an empty `work.jsonl`. This is the cheapest way to refresh the plan
and to answer the scheduling questions without running any task.

Only the root work graph is traced: the one that schedules and runs the requested tasks.
A build tree also runs separate work graphs while configuring, to build included plugin builds
(`pluginManagement { includeBuild(...) }`) and other build logic. Those graphs calculate their own task
plans and execute tasks before the root graph exists; they are ignored, so that they neither overwrite
the files nor leak build-logic tasks into the trace. (open) Tracing build-logic work graphs into their own
files.

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
  excluded task lists and all scheduling edges, uses this form. A record's own identity is its `id` field.

### Locations

A location names a file or directory. It is a string in one of two forms:

- Inside the root build directory: a path relative to it with `/` separators, such as `app/build/classes/java/main`;
  `""` is the root build directory itself. Included builds that live under the root directory are covered too.
- Outside it: an absolute path in native form, such as `/Users/me/.gradle/caches/.../guava.jar`.
  This covers the Gradle user home, transformed artifacts, external files, and included builds located elsewhere.

A reader tells the forms apart by whether the string is absolute.

Output files under a location are listed as `/`-separated paths relative to that location;
`""` means the location itself is the file (a `FILE` output).

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
      "id": ":app:compileJava",
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

### `task` record (one per task that reached execution)

```json
{
  "kind": "task",
  "id": ":app:compileJava",
  "implementation": "org.gradle.api.tasks.compile.JavaCompile",
  "scheduling": {
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
      "roots": [ "app/src/main/java" ]
    },
    {
      "property": "classpath",
      "roots": [
        "lib/build/libs/lib.jar",
        "/Users/me/.gradle/caches/modules-2/files-2.1/.../guava.jar"
      ]
    }
  ],
  "outputs": [
    {
      "property": "destinationDirectory",
      "type": "DIRECTORY",
      "location": "app/build/classes/java/main",
      "files": [ "com/acme/A.class", "com/acme/B.class" ]
    }
  ],
  "inputsCaptured": true
}
```

Field semantics:

- Task outcomes (executed, up-to-date, from cache, and so on) are not recorded; the trace describes
  the work, not how a particular build treated it.
- `scheduling.requested` is present, with the value `true`, only for tasks requested on the command line
  (or default tasks). It is computed at execution time and does not depend on `plan.jsonl`.
- `scheduling.explicitDependsOn` are edges declared with `dependsOn` or by task dependency objects
  that are not input file properties.
- `scheduling.inputDependencies` lists, per input file property, the tasks that build its files.
  A task is scheduled because of some chain of `explicitDependsOn`, `inputDependencies` and `finalizerOf`
  edges from a requested task; ordering constraints never cause scheduling.
  On a configuration cache run the restored file collections no longer carry their producer tasks,
  so the property is attributed by matching its input roots against the declared output locations of the
  task's dependency successors. (open) Record the attribution at configuration time instead.
- Build cache data (cache key, caching-disabled reasons, origin of reused outputs) is not recorded.
- `inputs` lists each input file property with its roots.
  A root is a location: a file, or a directory whose contents the task consumes.
  Only file and directory inputs are recorded: no individual files under a root, no hashes,
  no normalization or other fingerprinting settings, and no value (non-file) input properties.
  It is `null` when `inputsCaptured` is false.
- `inputsCaptured` is false when the engine did not fingerprint the task's inputs; see "Tasks without input
  snapshots". It is also false for tasks that failed before their inputs were snapshotted.
- `outputs` lists each declared output property with its location and `type` (`FILE` or `DIRECTORY`).
  `files` lists the regular files under the location, relative to it, as they exist after the task's turn
  in the build: produced by it, kept from a previous build when it was up-to-date, or loaded from the build cache.
  It is empty when nothing is there, for example after a no-source short circuit or a failure.
  No hashes are recorded.

## Answering the use cases

Queries take a path in either form; a reader relativizes an absolute path against the root build directory
before matching. The first three questions need only `work.jsonl`; `plan.jsonl` adds the tasks that were
scheduled but never ran.

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
- Locations outside the root build directory are absolute and machine-specific;
  root-relative locations are portable across checkouts of the same build.
- Input consumption is recorded per root, not per file; file tree include and exclude patterns are not recorded.
- No content hashes are recorded; the trace says which files, not which versions of them.
- Work graphs that build build logic (included plugin builds) are not traced; see "Files".
  A nested build tree such as `buildSrc` has its own trace service and writes its own directory
  under its own build directory.
- The trace is internal and its format may change without notice.

## Validation

- Scheduling closure: every task in `plan.jsonl` is reachable from a requested task
  over the three scheduling edge kinds found in `work.jsonl`.
- Missing-dependency replay: for every input file under some record's output location,
  the producer is reachable from the consumer through declared edges.
- Overlap: no two records share an output location, and no location is nested inside another record's output location.
- Stability: two consecutive traces of an unchanged build are identical,
  and a configuration cache hit leaves `plan.jsonl` unchanged.
