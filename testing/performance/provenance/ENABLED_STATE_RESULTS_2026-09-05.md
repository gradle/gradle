# Enabled-only property state: 2026-09-05

The two provenance fields have been removed from `AbstractProperty`. All six measured
property classes have their original no-provenance object sizes again, in both modes.
Disabled mutable state remains 24 bytes, and disabled finalization still uses the
shared 16-byte singleton. Provenance storage has not simply been moved into every
state object.

## Implementation and lifecycle

`ValueState.newPropertyState` checks the host switch once at construction. Enabled
properties receive `NonFinalizedValueWithProvenance`, reusing the existing host field
and holding two metadata references and three flags inline. Its 32-byte size replaces
the old 24-byte mutable state plus a separately allocated 24-byte metadata object
after binding. The extra flags fit into padding; disabled states have no added fields.

After successful finalization, a 32-byte `FinalizedValueWithProvenance` retains the
host needed for later failures and immutable diagnostic data. It does not retain the
old mutable state, convention supplier or provider graph. Already-fixed values keep
their existing binding records; evaluated provider chains keep descriptor-only trace
snapshots. Failed evaluation leaves the property mutable and retryable. This separate
enabled final-state object costs allocation that the disabled singleton avoids.

`PropertyProvenanceState` is now a read-only view. Shallow copies detach its diagnostic
fields into an immutable 24-byte object, retaining neither the mutable value state nor
the host through that metadata object. Their suppliers remain live exactly as before.
No detached metadata is allocated for a copy made before any provenance was recorded.

The general-purpose `ValueState.newState` factories, including the copier factory
used by `ConfigurableFileCollection`, are unchanged in behavior and layout. Only the
ordinary-property construction path opts into the new variant. Scope, stacktrace-like
report formatting, shadowed conventions, failure-only operation capture, optional
locations and configuration-cache behavior are unchanged.

## Actual object-size check

An `Instrumentation.getObjectSize` probe against the rebuilt full distribution,
OpenJDK 25.0.4 Ubuntu aarch64, with the production experiment's 1,536 / 3,300 MiB heap
settings, measured actual constructed instances, not just field-layout surrogates:

| Property class | Before | After, disabled or enabled | No-provenance baseline |
|---|---:|---:|---:|
| `DefaultProperty` | 48 | 40 | 40 |
| `DefaultMapProperty` | 56 | 48 | 48 |
| `DefaultListProperty` | 48 | 40 | 40 |
| `DefaultSetProperty` | 48 | 40 | 40 |
| `DefaultDirectoryVar` | 56 | 48 | 48 |
| `DefaultRegularFileVar` | 48 | 40 | 40 |

At the original baseline median instance counts, this removes the previously measured
1,493,824-byte (1.42 MiB) property-layout tax. This calculation isolates the six
property classes; whole-daemon heap also includes states, metadata, caches and noise.
The scratch probe is `/tmp/property-provenance-layout-k6346f/ActualLayoutProbe.java`.

### Configured-model structural comparison

An enabled checkpoint in the new production run (`origins`, repetition 0, heap 0)
has exactly the same six property-class instance counts as the first enabled heap
checkpoint in the compact-registry follow-up. Comparing those class groups directly:

| Shallow bytes | Compact registry | Enabled-only states | Change |
|---|---:|---:|---:|
| Six property classes | 9,534,312 | 8,040,416 | −1,493,896 |
| Value-state objects | 4,941,632 | 5,705,176 | +763,544 |
| Provenance classes and typed arrays | 2,497,016 | 690,704 | −1,806,312 |
| Combined | 16,972,960 | 14,436,296 | **−2,536,664** |

This is **2.42 MiB fewer shallow bytes** in these groups versus the already compact
registry, including the larger enabled states. It is not a whole-daemon difference or
an exclusive retained-size measurement. General-purpose maps, strings, class-loader
caches and native memory are outside these groups. The exact property-byte difference
uses this checkpoint's counts; the earlier 1.42 MiB layout estimate uses baseline
median counts.

The inline representation has 87,055 enabled mutable states and 2,097 enabled finalized
states. Relative to untracked state layouts, those add 696,440 and 67,104 bytes,
respectively. The separate 75,263 × 24-byte metadata objects are gone. The 12,658
binding records, 6,329 origin descriptors and 1,367 finalized snapshots are unchanged.
The remaining ordinary/copier states retain their original layouts. Do not interpret
the smaller provenance-package subtotal alone as the net memory saving: inline states
now live in the provider package and must be counted too.

These enabled-state, record, descriptor and snapshot counts are identical across all
six enabled heap samples. No standalone or detached provenance-state objects occur
in this workload. All eighteen heap samples confirm the six baseline property sizes.

## Measurement identity

The before JMH implementation is `4d0c00f73a1`, the compact two-record registry, with
only the new benchmark scenarios added. After runtime sources are identified by Git
blobs, since the complete change and report are committed together:

- `AbstractProperty.java`: `52722c6290115ece80d40692f85c525b76c7db8b`
- `ValueState.java`: `816ea9b0b04cbc916f1aa68b8303b1c51bf50661`
- `PropertyProvenanceState.java`: `6c5332ae8a41b75b92006d65e1df42a28fa47fad`

The [earlier matching-baseline experiment](PRODUCTION_RESULTS_2026-09-04.md) and
[compact-registry follow-up](COMPACT_REGISTRY_RESULTS_2026-09-04.md) remain historical
results, not measurements of this implementation.

## Microbenchmark method and read-path check

`PropertyProvenanceBenchmark` now also measures unbound construction, fixed-value
finalization, shallow copies, and a mixed read loop containing tracked/untracked and
mutable/finalized properties at the same call site. Existing creation/binding,
replacement, convention and three-property finalization scenarios remain.

JMH 1.36, OpenJDK 25.0.4 Ubuntu aarch64, 256 MiB heap, two forks, one-second iterations
and GC profiler. The before and first-pass runs used two warmups and three measured
iterations. The final run uses five warmups and three measured iterations. The
stronger warmup was chosen after seeing a timing outlier in the before run and
fork-dependent lambda allocation in the first pass. Raw samples must be retained;
these runs are allocation/diagnostic checks, not a precise across-the-board timing
comparison. No Gradle tests or production measurements run alongside JMH.

The first enabled-state implementation increased origin-mode mixed reads from about
4.64 to 7.61 ns/read. `-XX:+PrintInlining` showed a non-inlined virtual call to
`ValueState.maybeFinalizeOnRead`: four state variants now shared the read site. An
early return for finalized states keeps them out of that mutable call site's type
profile. A targeted two-fork, three-warmup/five-measurement run then measured
4.84 ± 0.08 ns/read (JMH 99.9% error). It is allocation-free to profiler precision;
this does not prove zero whole-build overhead. The guard changes no state transition:
finalized reads already required neither host validation nor further finalization.

Reproduce the final allocation checks after `./gradlew :model-core:jmhJar`:

```sh
java -jar platforms/core-configuration/model-core/build/libs/gradle-model-core-9.9.0-jmh.jar \
  PropertyProvenanceBenchmark -f 2 -wi 5 -i 3 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m' -rf json -rff /absolute/path/to/results.json
```

To reproduce before, add only this benchmark source to a separate worktree at
`4d0c00f73a1`. Use equal warmup settings for a new timing comparison. The local JMH
reports use the `property-provenance-state-` prefix under
`platforms/core-configuration/model-core/build/reports/`; the JIT diagnostic log is
`/tmp/property-provenance-inline-read-jit.log`.

### Allocated bytes per operation

Rounded GC-profiler results, before → final implementation:

| Operation | Disabled | Origins | Locations |
|---|---:|---:|---:|
| Construct, no binding | 72 → 64 | 72 → 72 | 72 → 72 |
| Construct and bind | 72 → 64 | 96 → 72 | 152 → 128 |
| Convention and explicit binding | 72 → 64 | 96 → 72 | 208 → 184 |
| Construct/bind three properties, finalize target | 256 → 232 | 904 → 864 | 1,144 → 1,104 |
| Construct/bind/finalize fixed value | 72 → 64 | 96 → 104 | 152 → 160 |
| Replace existing binding | 0 → 0 | 0 → 0 | 56 → 56 |
| Shallow copy of bound property | 24 → 24 | 48 → 48 | 48 → 48 |
| Mixed reads, per read | 0 → 0 | 0 → 0 | 0 → 0 |

The ordinary origin-only binding saves 24 bytes per operation. Enabled unbound
construction stays flat: the 8-byte property reduction is offset by the larger inline
state. Fixed-value finalization costs 8 more allocated bytes overall in enabled mode:
the new final state adds 32 bytes, offset by the 24-byte saving during binding. Its
origin-mode timing was about 23.1 ns before and 27.9 ns after; this is a memory/layout
tradeoff, not a claim that every operation is faster. The tested provider chain still
saves 40 allocated bytes overall. Other finalization patterns may differ.

Final mixed-read means are 4.70 / 4.85 / 4.87 ns for disabled / origins / locations.
The first-pass replacement benchmark had one origin-mode fork allocating a 16-byte
capturing lambda per call; the longer-warmup final run showed no allocation in either
fork. This is a reason to retain raw fork/iteration data rather than infer metadata
allocation from one short run. A 316.7 ns outlier in one before `createAndBind`
iteration likewise makes its mean unsuitable for a claimed speedup percentage.

## Repeated production-build measurements

The workload is Gradle's own `help` build at
`850edf55835529446bf63488489f6364ce5d2387`: 253 configured main-build projects and
one main-graph task, including configuration of the included build logic. The baseline
engine is that same revision, before the first provenance change. Disabled and origins
use the rebuilt implementation identified above. All three workload worktrees are
clean, pinned to the baseline revision, and use equal-length paths and separate,
equal-length Gradle user homes. This keeps the workload constant while changing the
engine; the patched distribution's version string names its parent commit, so use the
runtime blob identities to identify the measured implementation.

The existing [production protocol](PRODUCTION_MEASUREMENTS.md) was repeated with
three independent measurement daemons per mode, rotating mode order by repetition.
Each daemon ran eight warmups, eight timing builds and two separate full-GC heap
checkpoints. Including three preparation builds, all **165 builds** succeeded:
72 warmups, 72 timing samples and 18 heap samples. Preparation and warmups are excluded
from the statistics. Every timing log reports `242 actionable tasks: 1 executed, 241
up-to-date`; no build-logic compilation executed during timing or heap measurements.
All dedicated experiment daemons were stopped afterward.

Environment: Linux aarch64, eight CPUs, OpenJDK 25.0.4 Ubuntu, G1, 1,536 MiB initial /
3,300 MiB maximum heap, 768 MiB maximum metaspace and two workers. Builds ran offline
with Kotlin compilation in-process, a read-only dependency cache, configuration cache,
isolated projects, build cache, filesystem watching and build scans disabled. No JMH
or test runs overlapped the experiment; ordinary workstation background activity
remained. Heap checkpoints cover the whole daemon at main task-graph readiness after
full GC, not peak memory or exclusive retained size.

### Pooled medians

Timing medians use 24 samples per mode; heap medians use six.

| Mode | Configuration (s) | Wall (s) | Live heap (MiB) | Heap vs baseline (MiB) |
|---|---:|---:|---:|---:|
| No-provenance baseline | 7.834 | 8.523 | 651.361 | — |
| Prototype, disabled | 8.068 | 8.795 | 651.930 | +0.569 |
| Prototype, origins | 7.681 | 8.373 | 653.914 | +2.553 |

Enabled minus disabled live heap is 1.984 MiB. The disabled provenance-package
subtotal is only 624 bytes, with no enabled states or snapshots. Its remaining
whole-daemon difference must not be labeled property metadata or attributed entirely
to this change. Enabled provenance-package shallow bytes are 690,704, **excluding**
the inline states accounted for in the structural comparison above.

### Independent-daemon configuration medians

Each cell uses eight timing builds in its own daemon. Percentage changes compare the
corresponding repetition's median, not paired individual builds.

| Repetition | Baseline (s) | Disabled (s) | Disabled change | Origins (s) | Origins change |
|---|---:|---:|---:|---:|---:|
| 0 | 7.963 | 8.135 | +2.17% | 7.692 | −3.40% |
| 1 | 7.655 | 7.988 | +4.35% | 7.642 | −0.16% |
| 2 | 7.819 | 7.955 | +1.74% | 7.629 | −2.43% |

**Disabled configuration is 2.99% above baseline in the pooled medians, and slower in
all three repetitions.** Wall time is 3.20% higher. This is an unresolved timing
signal, not evidence of zero disabled overhead. Origins configuration is 1.96% below
baseline, but that does not establish a provenance speedup. Only three independent
JVMs per mode, finite warmup and workstation background activity limit interpretation;
the 24 builds are not 24 independent JVM samples. The baseline predates the entire
prototype, so this experiment cannot isolate the timing effect of the storage change.

The next performance investigation should compare disabled execution on the baseline,
compact-registry parent and this implementation under equal, longer warmup, using
allocation/JIT or CPU profiles to explain the disabled signal. The confirmed layout
savings do not depend on resolving that timing question. Enabled fixed-value
finalization's extra allocation is a separate, bounded tradeoff to keep visible.

See [production samples and manifest](enabled-state-production-2026-09-05.json) for
all preparation/warmup/timing/heap samples, per-daemon summaries, mode controls and
the parsed historical structural checkpoint. Local full logs and histograms are in
`/tmp/property-provenance-enabled-state-production-20260905`. The manifest embeds the
runner configuration; rerun with a new output directory:

```sh
node testing/performance/provenance/measure-production.mjs \
  --config /tmp/property-provenance-production-config-20260904.json \
  --output /absolute/path/to/new-results-directory
```

## Verification

See [microbenchmark samples](enabled-state-jmh-2026-09-05.json) for before, first-pass,
targeted read-guard and final runs, including raw per-fork iterations.

All 1,702 selected tests passed: 1,326 model-core unit cases, five project-backed-host
cases, 39 provenance integration cases, and 332 file-property/file-collection cases.
The thirteen new storage-focused cases cover unchanged disabled fields/singleton,
one-time host opt-in, the non-property/copier factory boundary, detached immutable
copies, finalized-state retention, failures without a recorded binding and read guards
for both tracked and untracked states.
Existing cases cover convention replacement/promotion/unset, failed and nested
finalization, cross-project/settings-plugin attribution, locations and byte-for-byte
disabled diagnostics. Checkstyle, CodeNarc, JMH packaging and the full distribution
build passed. Eleven measurement-harness tests also passed.
