---
name: gradle-work-trace
description: "Answer questions about a Gradle build's task wiring from a work trace instead of guessing from build scripts. Use whenever the user asks which task produces or writes a file, which tasks consume or read a file or directory, why a task runs or was scheduled, why task A depends on task B, how a task ended up in the task graph, or wants to find overlapping outputs or missing task dependencies. Trigger on phrases like 'who produces build/libs/foo.jar', 'what writes this file', 'which tasks use this directory', 'why does compileJava run', 'why is this task in the graph', 'why does X depend on Y', 'work trace', 'plan.jsonl', 'work.jsonl'. Not for explaining why a task was not up-to-date or was rerun."
---

# Gradle work trace

Gradle can write a *work trace*: two JSON Lines files that record, for the requested tasks,
the scheduled task graph with edge kinds and, per executed task, its input file roots and produced output files.
It answers wiring questions exactly, where reading build scripts and plugins only gives a guess.

The feature is internal and experimental. Format reference:
`platforms/documentation/docs/src/docs/design/work-trace-specification.md` in the gradle/gradle repository.

## 1. Produce the trace

Run the build the user cares about with the system property set. Use the exact tasks and flags
they normally run, because the trace covers only the tasks scheduled by that invocation:

```
./gradlew <tasks> -Dorg.gradle.internal.work.trace=
```

- An empty value writes to `<root project>/build/reports/work-trace/`. A non-empty value is a directory path.
- The property can also go in `gradle.properties` as `systemProp.org.gradle.internal.work.trace=`.
- Check the output for `See the work trace at: <url>`. If that line is missing, the Gradle version in use
  does not support the work trace: tell the user instead of falling back to guessing silently.
- To answer only scheduling questions ("why is X scheduled", "why does A depend on B"), add `--dry-run`.
  It computes the graph without running any task, writes `plan.jsonl`, and leaves `work.jsonl` empty.
- Producer and consumer questions need a real run: inputs and outputs are recorded only for tasks that execute.
  Up-to-date and from-cache tasks still count as executed and are recorded fully, so a re-run is cheap.
- Turning the property on or off invalidates the configuration cache entry once. That is expected.

Reuse an existing trace only if it was produced by the same task selection and the build has not changed since.
`work.jsonl` is rewritten by every traced build; `plan.jsonl` only when the task graph is calculated
(on a configuration cache hit, it is the one from the build that stored the entry).

## 2. Query the trace

Use the bundled script (Python 3, standard library only) from the root project directory:

```
python3 <skill-dir>/work_trace.py summary
python3 <skill-dir>/work_trace.py producer  <path>
python3 <skill-dir>/work_trace.py consumers <path>
python3 <skill-dir>/work_trace.py why       <task>
python3 <skill-dir>/work_trace.py depends   <taskA> <taskB>
python3 <skill-dir>/work_trace.py task      <task>      # raw record, for details the commands don't print
```

- `--dir <trace dir>` when a non-default directory was used; `--root <root project dir>` when not running
  from the root project directory.
- Paths may be absolute or relative to the root build directory.
- Tasks are build-tree paths: `:app:compileJava` in the root build, `:included:lib:jar` for `:lib:jar`
  in included build `included`. An unambiguous suffix such as `compileJava` or `app:jar` also works.

If the script does not fit the question, read the files directly: they are small JSON objects, one per line.
`plan.jsonl` holds one `plan` record per build; `work.jsonl` holds one `task` record per executed task with
`scheduling` (`requested`, `explicitDependsOn`, `inputDependencies[{property, tasks}]`, `finalizerOf`,
`mustRunAfter`, `shouldRunAfter`, `inputDependenciesError`), `inputs[{property, roots}]`,
`outputs[{property, type, location, files}]` and `inputsCapturingSkipped`. Empty arrays are omitted at any depth,
so treat a missing array as empty; `requested` and `inputsCapturingSkipped` appear only when `true`.
Ignore unknown fields and a truncated last line. The formal schema is `work-trace-schema.json` next to the spec.

## 3. Interpret the answer correctly

- **Edge kinds matter.** `dependsOn` is an explicit wiring; `input property 'x'` means the dependency is inferred
  from a file input carrying its producer, which is the idiomatic way; `finalizedBy` schedules a task after another.
  `mustRunAfter`/`shouldRunAfter` only order tasks and never cause scheduling. Report the kind and property name.
- **Blind spots for consumer queries.** Tasks with `inputsCapturingSkipped: true` have no recorded inputs:
  lifecycle tasks without actions, tasks without declared outputs, untracked tasks, `NO-SOURCE` tasks,
  tasks skipped by `onlyIf`/`enabled = false`, and tasks that failed early. The script lists them; mention them
  when a "who consumes" answer could be incomplete.
- **Root granularity.** A task whose input root is a directory is reported as consuming every file under it,
  even if a filter excludes some. Say so when it matters.
- **No producer found** usually means a source file, a file from outside the build (Gradle user home, external),
  or an artifact transform output; transforms are not recorded.
- **Several producers** of one path means overlapping outputs. That is a build problem worth reporting.
- **Missing dependency check.** If task C consumes a file that task P produces but the `why`/`depends` query finds no
  path from C to P, the build has an undeclared dependency and is order-dependent. Report it.
- **Plan-only tasks.** Tasks scheduled but not executed (a failure stopped the build, or `--dry-run`) appear only
  in `plan.jsonl`, where explicit and input-inferred edges are merged; the script labels those edges "plan only".
- **Unresolved input dependencies.** `scheduling.inputDependenciesError` means Gradle could not compute which
  tasks build the task's input files, so its input edges may be missing. The script reports it; mention it.
- **Excluded tasks.** An edge can point to a task excluded with `-x`; it has no record and appears in
  `excludedTaskPaths`.
- **Out of scope.** The trace does not record task outcomes, why a task was not up-to-date, build cache keys,
  file hashes, artifact transforms, or tasks that build plugins of included builds (`pluginManagement { includeBuild }`).
  For "why did it re-run", use `--info`, `--scan` or the console output instead.

## 4. Report

Answer with the task paths, edge kinds and property names from the trace. When the user then wants
the wiring changed, use the trace to locate the responsible task and property, and then look in the build scripts
or plugin code for where that task or property is configured.
