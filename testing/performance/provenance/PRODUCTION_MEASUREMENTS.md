# Matching baseline and production-build measurements

`measure-production.mjs` compares a no-provenance distribution, the patched distribution
with provenance disabled, and the patched distribution with origins enabled. It uses
separate pinned worktrees and Gradle user homes, and fresh daemons for independent
repetitions. No location-mode or configuration-cache claim is made by this comparison.

See the [2026-09-04 production results](PRODUCTION_RESULTS_2026-09-04.md) for measured
memory costs, per-JVM timings, and the limits of the timing conclusion.

## Baseline selection

`850edf55835529446bf63488489f6364ce5d2387` is the parent of the first provenance commit
`907b48f7ca0`. All production-source differences between that revision and `8670a358e3d`
belong to this provenance prototype, including property fields, capture/reporting, and
call-site interception. The settings, build logic, dependency declarations, and packaging
sources are unchanged. This isolates the prototype from unrelated Gradle development;
it is not a comparison against a different released Gradle version.

Build **full** distributions for both revisions. The smaller native integration-test
distribution lacks enterprise services required by Gradle's own Develocity plugin.
The baseline `gradle-model-core` jar contains no provenance classes or fields; verify
with `jar tf` and `javap -private` when reproducing. Runtime revisions are also saved
from `gradle --version` in the measurement manifest.

Example preparation, using new paths that do not already exist:

```sh
git worktree add --detach /tmp/gradle-provenance-baseline-20260904 850edf55835529446bf63488489f6364ce5d2387
./gradlew -p /tmp/gradle-provenance-baseline-20260904 :distributions-full:binInstallation --no-scan --max-workers=2
./gradlew :distributions-full:binInstallation --no-scan --max-workers=2

git worktree add --detach /tmp/gradle-provenance-production-baseline-20260904 850edf55835529446bf63488489f6364ce5d2387
git worktree add --detach /tmp/gradle-provenance-production-disabled-20260904 850edf55835529446bf63488489f6364ce5d2387
git worktree add --detach /tmp/gradle-provenance-production-originsx-20260904 850edf55835529446bf63488489f6364ce5d2387
```

The last three paths deliberately have the same length. Gradle retains many project
and cache paths; differing lengths can distort small live-heap deltas. The runner also
pads its three user-home names to equal length. Distribution paths still differ and
are recorded; this is a control on a major confound, not byte-identical heap contents.

## Workload and runner

The selected production workload is Gradle's own `help` build, at the baseline source
revision in **all** worktrees. This measures production settings/build logic, plugin
application, and project configuration, not compilation throughput or test execution.
The runner checks the source revision and refuses tracked source changes. It leaves
all source worktrees and raw results available for inspection.

Create a JSON configuration with absolute paths (replace `/repo` and `/dependency-cache`):

```json
{
  "sourceRevision": "850edf55835529446bf63488489f6364ce5d2387",
  "readOnlyCache": "/dependency-cache",
  "forks": 3,
  "warmups": 8,
  "runs": 8,
  "heapRuns": 2,
  "jvmArgs": "-Xms1536m -Xmx3300m -XX:MaxMetaspaceSize=768m -XX:+UseG1GC",
  "args": ["help", "--offline", "-Pkotlin.compiler.execution.strategy=in-process"],
  "variants": {
    "baseline": {
      "project": "/tmp/gradle-provenance-production-baseline-20260904",
      "gradle": "/tmp/gradle-provenance-baseline-20260904/packaging/distributions-full/build/bin distribution/bin/gradle"
    },
    "disabled": {
      "project": "/tmp/gradle-provenance-production-disabled-20260904",
      "gradle": "/repo/packaging/distributions-full/build/bin distribution/bin/gradle"
    },
    "origins": {
      "project": "/tmp/gradle-provenance-production-originsx-20260904",
      "gradle": "/repo/packaging/distributions-full/build/bin distribution/bin/gradle"
    }
  }
}
```

`readOnlyCache` is optional and points to the **parent** of a prepopulated `modules-2`
directory. The runner uses Gradle's `GRADLE_RO_DEP_CACHE` support rather than linking
writable caches across distributions. Populate dependencies before using `--offline`.

```sh
node --test testing/performance/provenance/measure*.test.mjs
node testing/performance/provenance/measure-production.mjs \
  --config /path/to/config.json --output /new/results/directory
```

## Experimental controls

- Three variants have identical workload sources, JVM options and build flags.
  Configuration cache, isolated projects, build cache, filesystem watching, and build
  scan publication are explicitly disabled; the task worker limit is two.
- Each variant first prepares its own build-logic outputs, dependency caches, generated
  Gradle API jar, and instrumented classpath. Preparation is excluded. Measured logs
  are checked for unexpected compile/accessor-generation task execution.
- Each of three repetitions starts a fresh daemon per variant, followed by eight
  warmups, eight timed builds and two separate heap builds. Variant order rotates
  across repetitions so every variant occupies each block position once. Only one
  measurement daemon is live at a time. User-owned normal daemons are not stopped.
- Daemon PIDs verify independence between blocks and reuse within a block. First
  warmups include daemon startup; they are not cold-filesystem measurements.
- Configuration time is main-build `projectsLoaded` through task-graph readiness;
  CLI wall time includes the rest of the invocation. Neither includes heap profiling
  in the timing sample set. The checkpoint records the configured project count.
- Heap samples use post-full-GC whole-daemon live histograms at task-graph readiness,
  while configured models remain reachable. Baseline histograms must contain no
  provenance metadata; disabled ones must contain no provenance states; enabled ones
  must contain provenance states. Shallow property-class sizes can separately expose
  the compiled-in field-layout cost even when total heap is noisy.

`samples.json`, `summary.json`, `manifest.json`, and raw logs/histograms are saved under
the output directory. Summaries retain per-repetition medians: multiple invocations in
one daemon are not independent JVM samples. Compare the direction and spread across
repetitions before attributing a percentage to provenance.

Whole-live-heap deltas are not exclusive retained sizes or peak heap. Soft references,
JIT/class-loader behavior, plugin caches, and workstation activity remain sources of
variation. No inference about configuration-cache hits is valid without provenance
persistence. This is one production build/configuration workload, not a build corpus.

## Interpreting optimization opportunities

Keep the disabled object-layout tax separate from enabled state/record allocations.
`AbstractProperty` has two added references even when tracking is disabled. Comparing
the histogram bytes per instance for the six principal scalar/collection/file property
classes exposes this cost without attributing every daemon heap difference to it.

The current registry also eagerly creates nine operation records per source. Successful
binding capture in `AbstractProperty` requests only `EXPLICIT_SOURCE` and `CONVENTION`;
failure records are constructed separately by `failureFor`. The histogram ratio of
records to origins can expose this eager-table cost. A smaller or lazy binding-record
table is a candidate follow-up, not an optimization included in these measurements.
Changing the always-present property layout is a separate, more invasive design task.
