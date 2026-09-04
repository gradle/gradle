# Matching-baseline production results: 2026-09-04

The prototype has a measurable memory cost even when disabled. On this workload,
the six measured property classes each grow by **8 bytes per instance**, accounting
for approximately **1.42 MiB** at the baseline instance counts. Median whole-daemon
live heap grows by **1.77 MiB disabled**, or **6.33 MiB with origins enabled**, relative
to the matching no-provenance build. Timing differences change sign between JVM
repetitions; this experiment does not establish a reliable slowdown percentage.

## What was compared

- No-provenance engine: `850edf55835529446bf63488489f6364ce5d2387`, immediately before
  the first provenance commit.
- Patched engine, disabled and origins: `8670a358e3d347d0dafb85b23f672dbe208f14b1`.
  No production code changed for this experiment.
- Workload in all three equal-length, clean worktrees: Gradle itself at
  `850edf55835529446bf63488489f6364ce5d2387`, running `help`. Each checkpoint contains
  **253 configured main-build projects** and one selected main-build task. The build's
  included settings/build-logic builds are also involved in configuration.
- Full distributions, Linux aarch64, eight available CPUs, OpenJDK 25.0.4 Ubuntu,
  G1, 1,536 MiB initial / 3,300 MiB maximum heap, 768 MiB maximum metaspace, two workers.
  Configuration cache, isolated projects, build cache, filesystem watching, and scan
  publication disabled; builds ran offline with a read-only dependency cache.

See the [protocol and reproduction commands](PRODUCTION_MEASUREMENTS.md) and
[all 165 samples and their manifest](production-results-2026-09-04.json).
There were three preparation builds, 72 warmups, **72 timed builds**, and **18 separate
heap builds**. Each variant used three independent measurement daemons, eight warmups
and eight timed builds per daemon, then two heap checkpoints. Variant order rotated
across repetitions. All nine daemon-independence checks, configured-project/task counts,
compile-task checks, and heap negative controls passed.
All 72 timing logs reported one executed task (`help`) and 241 up-to-date tasks;
no build-logic task execution contaminated the timed samples.

Preparation compiled 242 build-logic/main tasks and took about three minutes per
variant; none of those times enter the results. Normal IDE/background workstation
activity remained; no other Gradle test suite or JMH run was launched alongside the
measurements. This is a development experiment on one production configuration
workload, not a performance-lab guarantee or a compilation-throughput benchmark.

## Timing

Pooled medians and observed min–max in seconds, 24 timed samples per variant.
Configuration is main-build `projectsLoaded` through task-graph readiness. CLI wall
time includes the rest of the invocation. Heap runs are excluded from both columns.

| Engine mode | Configuration seconds | CLI wall seconds |
|---|---:|---:|
| No provenance | 7.822 (7.447–8.881) | 8.545 (8.093–9.626) |
| Patched, disabled | 7.815 (7.575–8.455) | 8.520 (8.234–9.158) |
| Patched, origins | 7.899 (7.115–8.582) | 8.608 (7.790–9.295) |

The pooled origin-only medians are about 1.0% higher for configuration and 0.7% higher
for CLI wall time, but **those are descriptive differences, not established overhead**.
Multiple builds in the same daemon are not independent JVM samples. Configuration
medians by independent repetition show why a single percentage would be misleading:

| Engine mode | Repetition 1 | Repetition 2 | Repetition 3 |
|---|---:|---:|---:|
| No provenance | 7.750 s | 7.933 s | 7.871 s |
| Patched, disabled | 7.720 s | 7.808 s | 8.011 s |
| Patched, origins | 8.015 s | 7.994 s | 7.610 s |

Relative configuration-median differences versus baseline are −0.40%, −1.57%, +1.79%
for disabled and +3.41%, +0.77%, −3.31% for origins. The direction changes, and the
third enabled repetition includes a lower-time interval. JVM warmup, scheduling and
workstation variation remain plausible contributors. These data prove neither a
timing regression nor zero timing overhead. More controlled repetitions/profiling are
needed before claiming a small percentage or choosing an optimization on timing alone.

## Configured-model live heap

Six post-full-GC histogram samples per variant. Values are median and observed min–max
in MiB, with models/task graph still reachable. They are whole-daemon live bytes, not
peak heap or exclusive/dominator-retained sizes.

| Engine mode | Live MiB | Median difference vs no provenance |
|---|---:|---:|
| No provenance | 652.387 (651.751–652.952) | — |
| Patched, disabled | 654.156 (653.348–654.389) | +1.769 |
| Patched, origins | 658.714 (658.243–659.106) | +6.327 |

Enabling origins adds about **4.56 MiB** relative to the disabled patched engine.
These deltas also include non-provenance daemon/cache variation. The following class
measurements provide a more direct explanation of part of that footprint.

### Always-present property layout

Bytes per instance were identical across every histogram for each engine mode.
Patched sizes are the same with provenance disabled or enabled.

| Property class | Baseline bytes | Patched bytes | Baseline median instances |
|---|---:|---:|---:|
| `DefaultProperty` | 40 | 48 | 100,253 |
| `DefaultMapProperty` | 48 | 56 | 52,071 |
| `DefaultDirectoryVar` | 48 | 56 | 19,287 |
| `DefaultSetProperty` | 40 | 48 | 7,867 |
| `DefaultListProperty` | 40 | 48 | 4,489 |
| `DefaultRegularFileVar` | 40 | 48 | 2,761 |

The added `provenanceHost` and `provenance` references in `AbstractProperty` explain
the 8-byte increase on this JVM. Applying that increase to these baseline median
counts gives 1,493,824 bytes (1.42 MiB). This is a layout estimate for these six
classes, not the exact attribution of the whole-daemon heap delta. It applies even
to properties that retain no provenance state; it does not broaden tracking scope.

### Enabled metadata

Every enabled heap sample has exactly the same counts:

- 75,263 `PropertyProvenanceState` instances;
- 56,961 `PropertyProvenanceRecord` instances;
- 6,329 `PropertyProvenanceOrigin` descriptors;
- 1,367 finalized trace snapshots.

Provenance classes and their typed arrays total 3,762,816 shallow bytes. This omits
referenced strings, general-purpose maps/arrays, embedded property fields and native
class/JIT memory; do not call it an exclusive retained-size measurement. Baseline
histograms contain no provenance metadata. Disabled histograms contain no states or
snapshots and only 824 shallow bytes of initialized provenance metadata, including
the nine unknown-origin record templates.

## Recommended follow-up

1. First reduce the eager record table. It creates nine records per origin, while
   successful bindings request only explicit-source and convention records; failure
   records are constructed separately. Avoiding the seven unused per-source templates
   could remove roughly 1 MiB of record objects on this workload, before array savings.
   This is a source/histogram-based estimate, not an implemented or measured improvement.
2. Separately investigate moving enabled-only storage out of the always-present
   property layout. Eliminating the 8-byte tax is a more invasive design change and
   must preserve disabled behavior, lifecycle/copy semantics and attribution tests.
3. Re-run the same baseline comparison after either change. Use longer warmup/more
   independent repetitions or a quieter machine before publishing a timing percentage.

No additional tracking scopes, line-capture expansion, configuration-cache persistence,
collaborative semantics or runtime optimization were introduced by this experiment.
The stacktrace-like report format is unchanged.

Raw local logs/histograms remain under
`/tmp/property-provenance-production-measurements-matched-20260904`; the committed
samples include extracted property sizes and metadata counts. Before this run, a
native-distribution pilot failed because enterprise services were missing, and an
initial full-distribution preparation was stopped to equalize worktree path lengths.
Neither attempt is included in these results. Source worktrees and result directories
were retained for inspection; no user-owned daemons or worktrees were removed.
