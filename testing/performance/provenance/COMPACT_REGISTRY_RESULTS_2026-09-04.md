# Compact origin registry: 2026-09-04

The change removes **1,265,800 bytes (1.21 MiB)** of live provenance objects and
containers in the same 253-project workload used for the matching-baseline experiment.

The registry now retains only the two successful-binding templates per application
source: explicit source and convention. An immutable two-reference holder replaces
the nine-slot array, including for unknown code. Both records share one descriptor;
different application sources remain distinct even when their plugin IDs match.
Concurrent first access still uses `ConcurrentHashMap.computeIfAbsent` to publish the
complete immutable holder. Located occurrences and failed operations remain fresh
records, and failed-operation kinds are rejected by the successful-binding API.

No property fields, scope rules, provider traversal, report formatting, location
capture or configuration-cache behavior changed. In particular, this does **not**
remove the compiled-in-but-disabled 8-byte property-layout tax.

## First-origin allocation

The new `PropertyProvenanceRegistryBenchmark.registerOrigins` registers 64 distinct
application sources in a fresh registry per invocation and requests both binding
kinds. Results are normalized per source, including amortized registry/map setup.
Source construction and property-state allocation are excluded. Returning the
registry keeps the registered records observable and bounds the registry's lifetime;
the benchmark does not keep growing one map across iterations.

Before: runtime at `f102c78d9a635d69c55f2e4340f194994fb07081`, with only the new
benchmark added. After: the same worktree with the compact registry, whose Git blob
is `edcda86c76939df69d46e09931fa753594ef7915`.

Linux aarch64, OpenJDK 25.0.4 Ubuntu, JMH 1.36, 256 MiB heap; two forks, three
one-second warmups and five one-second measurements per fork, GC profiler. No Gradle
test build or production measurement was running alongside either JMH run.

| Registry | Allocated bytes/source | ns/source, mean ± JMH 99.9% error |
|---|---:|---:|
| Nine-record table | 436 | 59.26 ± 2.46 |
| Two-record holder | 176 | 37.84 ± 0.52 |

This is **260 fewer allocated bytes per registered source** in this benchmark, not
260 retained bytes. The old implementation also allocated a temporary enum `values()`
array per registration. The retained table itself shrinks by 200 bytes per source on
this JVM: seven 24-byte records plus a 32-byte container reduction (56-byte array to
24-byte holder). Benchmark setup costs are amortized over 64 sources.

A separate post-change `PropertyProvenanceBenchmark.createAndBind` run with the same
JMH settings measured 72 / 96 / 152 allocated bytes per operation for disabled /
origins / locations, matching the earlier steady-state allocation checks. It primes
the source in setup and therefore does not measure the table reduction. Neither
microbenchmark establishes a whole-build speedup or a precise build-time overhead.

Reproduce on each implementation after `./gradlew :model-core:jmhJar`:

```sh
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-9.9.0-jmh.jar \
  PropertyProvenanceRegistryBenchmark \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -prof gc -jvmArgs '-Xms256m -Xmx256m' \
  -rf json -rff /absolute/path/to/registry-result.json
```

Use `PropertyProvenanceBenchmark.createAndBind` as the benchmark filter for the
steady-state check. To reproduce the old implementation, add this benchmark source
to a separate worktree at the before revision without applying the registry change.

## Configured-model footprint check

The [earlier production experiment](PRODUCTION_RESULTS_2026-09-04.md) remains the
historical pre-optimization comparison. This follow-up uses the same clean Gradle
source worktrees at `850edf55835529446bf63488489f6364ce5d2387`, updated full
distribution, JDK, JVM arguments and build flags. Origins and disabled each use one
fresh daemon, four warmup builds, then two post-full-GC `GC.class_histogram`
checkpoints via `fixture/measure.init.gradle`. Dedicated experiment user homes/caches
are reused; normal user daemons are not stopped. All checkpoints configure 253 main
projects and one main task (`help`), with no build-logic compilation during heap
samples. Measurement daemons are stopped afterward.

This is a targeted class-footprint check, **not** a rerun of the 165-build protocol:
there are no new independent timing repetitions or no-provenance baseline runs.
The samples retain whole-daemon totals for inspection, but their differences from
historical totals are not presented as a precise whole-build memory improvement.

Both enabled samples have identical provenance counts and shallow sizes:

| Live metadata | Before | After |
|---|---:|---:|
| Binding record instances | 56,961 | 12,658 |
| Origin descriptor instances | 6,329 | 6,329 |
| Property state instances | 75,263 | 75,263 |
| Finalized snapshot instances | 1,367 | 1,367 |
| Provenance classes and typed arrays, shallow bytes | 3,762,816 | 2,497,016 |

The 44,303 removed records account for 1,063,272 bytes. Replacing 6,329 nine-slot
arrays with 6,329 two-reference holders saves another 202,528 bytes, for exactly
1,265,800 bytes total. This includes the unknown-origin pair. The remaining 1,241
record arrays belong to other provenance data, such as trace snapshots, and are
unchanged. Strings, general-purpose maps/arrays, embedded property fields and native
memory are excluded from this shallow-byte sum; it is not an exclusive retained-size
or peak-heap measurement.

Both disabled samples retain no provenance states or snapshots. Their initialized
provenance metadata drops from 824 to 624 shallow bytes because the unknown-origin
templates are compact too. Per-instance sizes of the six measured property classes
remain unchanged in both modes, as expected.

[Measurement data](compact-registry-results-2026-09-04.json) includes the two JMH
forks per scenario, all 12 follow-up build checkpoints and one historical enabled
heap sample for comparison. The full original JMH reports remain in
`platforms/core-configuration/model-core/build/reports/property-provenance-registry-{before,after}.json`
and `property-provenance-compact-steady.json` in that directory.

Raw checkpoint logs and histograms are under
`/tmp/property-provenance-compact-check-20260904`; the local driver is
`/tmp/property-provenance-compact-check.mjs`. To reproduce, build
`:distributions-full:binInstallation` and use the
[production invocation](PRODUCTION_MEASUREMENTS.md) for origins and disabled with
four warmups and two separate histogram builds in a fresh daemon for each mode.
For example, replace the absolute paths below with the pinned workload, full
distribution, dedicated user home and an existing output directory:

```sh
GRADLE_RO_DEP_CACHE=/dependency-cache '/repo/packaging/distributions-full/build/bin distribution/bin/gradle' \
  -p /pinned-workload --gradle-user-home /experiment-user-home \
  help --offline -Pkotlin.compiler.execution.strategy=in-process \
  --console=plain --no-scan --no-configuration-cache --no-build-cache --no-watch-fs --max-workers=2 \
  -Dorg.gradle.isolated-projects=false \
  '-Dorg.gradle.jvmargs=-Xms1536m -Xmx3300m -XX:MaxMetaspaceSize=768m -XX:+UseG1GC' \
  -Dorg.gradle.internal.property-provenance=true -Dorg.gradle.internal.property-provenance.locations=false \
  -I /repo/testing/performance/provenance/fixture/measure.init.gradle \
  -PsampleHistogram=/output/unique-sample.histogram
```

Omit `sampleHistogram` for the four warmups; use distinct output files for the two
heap samples. Stop only this dedicated user home's daemon before/after the block.
Repeat with provenance `false`, its own pinned worktree and dedicated user home.
Do not use histogram-instrumented invocations to infer timing improvements.

## Verification

The selected suites passed: 1,313 model-core unit tests, five project-backed-host
tests and 39 provenance integration cases (including plugins, settings-origin
callbacks, cross-project attribution, optional locations and disabled diagnostics).
The registry suite now has 25 cases, covering sharing for known/unknown origins,
rejection of all seven failure kinds without interning, fresh failure records with
and without locations, concurrent first access, distinct application identity and
shadowed conventions. Main-source Checkstyle, test/integration CodeNarc, JMH packaging
and the full distribution build also passed.
