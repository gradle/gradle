# Project-property provenance build measurements

This standalone development harness complements `PropertyProvenanceBenchmark` with
generated multi-project Java builds. It is not a regression gate or a claim about
all Gradle builds. It requires Node.js, a JDK with `jcmd`, and a Gradle distribution
containing this prototype. It uses no external npm dependencies or plugin repositories.

The [2026-09-04 exploratory results](RESULTS_2026-09-04.md) include timing ranges,
configured-model live heap, and explicit limits on what the data establishes.

```sh
node --test testing/performance/provenance/measure.test.mjs
./gradlew :distributions-native:binInstallation
node testing/performance/provenance/measure.mjs \
  --gradle 'platforms/native/distributions-native/build/bin distribution/bin/gradle' \
  --output /tmp/property-provenance-build-measurements \
  --projects 10,50 --tasks 40 --warmups 3 --runs 9 --heapRuns 3 --coldRuns 1
```

The output directory must not already exist; its parent must exist. The harness
generates fixtures, gives them a dedicated Gradle user home, and stops only daemons
in that user home. Raw build logs, live histograms, samples, environment details, and
median/min/max summaries remain there. Compilation, initial cache population, and
mode warmups are excluded from warm timing. Run without concurrent tests/builds.
An existing IDE or other machine activity can still add noise.

## Workload

Each subproject applies `java-library` and a compiled buildSrc model plugin, contains
one Java source file, and depends on the preceding project. `checkpoint` selects all
subprojects' `assemble` tasks and model tasks. After initial compilation, Java outputs
are up to date; normal dependency resolution, task-graph construction, validation, and
up-to-date checks still happen.

Each project has three extension properties and 40 model tasks with four scalar
properties each: convention-only, convention plus explicit source, a finalized chain,
and replaced explicit bindings. With the defaults this is 1,630 / 8,150 explicitly
created fixture properties, plus properties created by Gradle's Java plugins and
buildSrc. Every model task is realized and remains reachable at the heap checkpoint.
This intentionally exercises both everyday Java build structure and property-heavy
plugin patterns; it is not a corpus of production builds. It does not model Kotlin or
Android plugin costs, compilation throughput, or a fresh dependency download.

## Measurements and controls

- **Warm timing:** three warmup rounds and nine measured rounds; each round runs all
  three modes in rotating order in the same daemon. Record main-build `projectsLoaded`
  through task-graph readiness, and separately end-to-end CLI wall time. Report median
  and range, not a confidence/significance claim from nine samples.
- **Heap:** three separate rounds after timing, using `jcmd GC.class_histogram` without
  `-all`. This requests full GC with the configured main build and task graph still
  reachable. Histogram totals are post-GC **whole-daemon live bytes**, not allocated
  bytes or peak heap. Provenance class/array entries are **shallow live sizes**, not
  exclusive/dominator-retained sizes; they omit referenced strings, general-purpose
  arrays/maps, and fields embedded in other objects. The total includes daemon caches
  and possibly other build models, so mode deltas are an estimate, not exact attribution.
  Heap-run timings include intrusive GC/attach work and must not be used as timing data.
- **Cold daemon:** one sample per mode by default, restarting the dedicated daemon
  each time but retaining disk/script caches and up-to-date outputs. This is a smoke
  comparison, not statistically useful cold-build timing. Increase `--coldRuns` for
  repeated samples. It is not cold filesystem/fresh-checkout performance.

All modes use 512 MiB initial/maximum heap, G1 GC, two workers, no configuration cache,
no build cache, and no filesystem watching. Successful builds produce no provenance
reports. Location mode uses existing instrumented Java call sites, not stack walking
or full Groovy line support.

The disabled mode is the **same patched distribution**, with provenance switched off.
It does not measure the cost of compiled-in property fields or interception relative
to Gradle without the prototype. That requires a matching source baseline distribution;
an unrelated released Gradle version would confound the result. Configuration-cache
hits remain outside scope because provenance persistence is not implemented.
