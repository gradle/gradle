# Handoff: AllVersionsCrossVersion flaky quarantine times out on Windows

**Status:** root cause confirmed, fix pushed as draft PR, awaiting a CI run on a Windows agent.
**PR:** https://github.com/gradle/gradle/pull/38906 (draft)
**Branch:** `bz/flaky-quarantine-crossversion-scope` @ `5ff1fcadef6`
**Date:** 2026-08-20

---

## The problem

`Flaky Test Quarantine - AllVersionsCrossVersion Java17 Adoptium Windows Amd64`
(`Gradle_Master_Check_FlakyQuarantine_Check_AllVersionsCrossVersion_11`) timed out on **13 of its
last 15 runs** on `master`.

Trigger build for the investigation:
https://builds.gradle.org/buildConfiguration/Gradle_Master_Check_FlakyQuarantine_Check_AllVersionsCrossVersion_11/116452318

It is **not a flaky test**. TeamCity reports zero failed tests; the build is killed by the
120-minute execution timeout while tests are still progressing normally.

## Root cause

Cross-version test tasks are registered per tested Gradle version **per subproject**, so
`allVersionsCrossVersionTest` schedules ~1100 test tasks across 19 subprojects
(58 versions x 19 subprojects).

Under `-PflakyTests=ONLY` the filter is a JUnit Platform tag include, evaluated during discovery
**inside the test JVM**. Gradle cannot tell up front that a task will select nothing, so it forks a
JVM for every one regardless. Only `:tooling-api` has `@Flaky` cross-version tests (9 spec files),
so roughly 1050 of those tasks start a JVM, discover nothing, and exit.

This was already documented in the codebase before the investigation —
`build-logic-commons/gradle-plugin/src/main/kotlin/gradlebuild/testcleanup/TestFilesCleanupService.kt:153`:

> `-PflakyTests=ONLY` does that to almost every task of the flaky test quarantine build for
> AllVersionsCrossVersion: it has one task per tested Gradle version per subproject, over a thousand
> of them, and only the handful in subprojects that actually have flaky cross-version tests select
> anything.

### Why it fails only sometimes

The build only fits in 120 minutes when the build cache serves nearly all those tasks. The step runs
`clean`, so the cache is the **sole** avoidance mechanism — there is no incremental fallback.

| Windows run | `CrossVersionTest FROM-CACHE` | Outcome |
|---|---|---|
| #16 | 92 / 1032 (9%) | timeout at 2:02 |
| #10 | 900 / ~986 (91%) | green at 1:48 |

In the passing run, 96% of avoidance savings came from the **remote** cache, and it ran on a
different agent than the preceding failed run — so those entries came from an earlier build of the
same revision.

Grouping Windows runs by whether the revision had been built before:

```
First run on a revision:   0 / 8 passed
Repeat run on a revision:  2 / 4 passed
```

Windows has **never** passed on a fresh revision. Linux passes cold at 1:35–1:45, but has timed out
once too (build #10, 2:02:31), so it has margin rather than immunity.

### Supporting observations

- All 398 reported test classes in the timed-out build were `org.gradle.integtests.tooling.r*`.
- The first test result appeared at 02:02:47 — 88 minutes after the first test task started at
  00:34:04 — because subprojects execute alphabetically and `:tooling-api` sorts last.
- **The ordering is not the root cause.** Reordering would not help: the build must finish all ~1100
  tasks to go green regardless of order. Do not "fix" the ordering and expect a result.
- Killed builds publish **no build scan**, so diagnosis requires the raw TeamCity log
  (`teamcity run log <id> --raw`).

## The fix (as pushed)

`.teamcity/subprojects.json` already records per subproject whether it has unit / functional /
cross-version tests. The PR adds `flakyCrossVersionTests` alongside them, and qualifies the
quarantine build's task with the subprojects that have one.

The reason for putting it there rather than anywhere else: `:checkSubprojectsInfo` already runs in
`sanityCheck`, so the staleness enforcement is free. Annotating a cross-version test anywhere fails
the build with the existing message asking for `:generateSubprojectsInfo` — the same workflow as
adding a new subproject — and regenerating adds that subproject to the quarantine build. Nothing is
hand-maintained; nothing can silently drift.

Files changed (6):

```
.teamcity/src/main/kotlin/configurations/FlakyTestQuarantine.kt        | 21 +-
.teamcity/src/main/kotlin/model/CIBuildModel.kt                        |  6 +
.teamcity/src/main/kotlin/model/GradleSubprojectProvider.kt            |  3 +-
.teamcity/subprojects.json                                             | 756 ++++++---
build-logic/build-update-utils/.../model/GradleSubproject.kt           |  2 +-
build-logic/build-update-utils/.../tasks/SubprojectsInfo.kt            | 29 +-
```

### What was verified

- `:generateSubprojectsInfo` emits `flakyCrossVersionTests: true` for `tooling-api` only.
- Planted `@Flaky` in a `:maven` cross-version test:
  - `:checkSubprojectsInfo` **fails** with
    `New project(s) added without updating subproject JSON. Please run ':generateSubprojectsInfo' task.`
  - after regenerating, the flag is `['maven', 'tooling-api']` — `:maven` joins on its own.
  - probe reverted; regenerated back to `['tooling-api']`.
- `.teamcity` DSL compiles (`cd .teamcity && ./mvnw compile`).
- Build logic compiles (`:generateSubprojectsInfo` ran successfully).
- Earlier task-graph measurements via `--dry-run`, on the discarded build-logic variant:
  1102 tasks / 19 subprojects → 58 tasks / `:tooling-api`; unchanged (1102) without `-PflakyTests`.

### What was NOT verified

- **Actual wall-clock on a real Windows agent.** This is the one thing left. The PR is a draft
  specifically so someone can trigger CI on the branch and get that number.
- Whether Windows and Linux quarantine runs share build-cache entries for the same revision. They
  ran concurrently on rev `697afbe54bff`, so this was never isolated.
- No existing gradle-private issue was searched for. Worth checking before merging.

## Open decisions

1. **Consider deleting the coverage instead of fixing it.**
   `Flaky Test Quarantine - QuickFeedbackCrossVersion` already exists on Linux and Windows, is
   **6/6 green**, runs in **26–28 min on Windows** (14 min Linux), and exercises the *same 9
   `@Flaky` tooling-api spec files* — just against ~6 Gradle versions instead of 58
   (1131 tests / 58 versions ≈ 128 tests / 6.5 versions ≈ 19.5 tests per version either way).

   If replaying those specs against 52 additional historical versions is not worth ~4 agent-hours
   per trigger, deleting the AllVersions coverage is simpler than this PR and needs no code change.
   This is a policy call and was deliberately left to the team.

2. **The quarantine build opts out of bucketing and test distribution**
   (`FlakyTestQuarantine.kt`: "not split into buckets and does not use test distribution"). That is
   why it alone has this problem. Making it use the same mechanism as every other functional test
   build would fix the class of problem rather than this instance.

## Approaches tried and discarded

Recorded so nobody re-treads them:

1. **Hardcode `:tooling-api` in `FlakyTestQuarantine.kt` + a grep guard step.** Works, but the guard
   fails the build telling you to hand-edit a Kotlin constant. Bespoke mechanism, hand-maintained.
2. **Scan sources at configuration time in `gradlebuild.cross-version-tests.gradle.kts`.** Skips
   wiring known-empty tasks into the aggregates. Fully automatic, but adds a *second* source-scanning
   mechanism with no staleness check, and makes every quarantine build pay for the scan.

Both were superseded by the `subprojects.json` approach, which reuses machinery that already exists.

## How to continue

```bash
git fetch origin
git checkout bz/flaky-quarantine-crossversion-scope

# regenerate after touching @Flaky annotations on cross-version tests
./gradlew :generateSubprojectsInfo

# what sanityCheck enforces
./gradlew :checkSubprojectsInfo

# compile the TeamCity DSL
cd .teamcity && ./mvnw compile
```

To reproduce the diagnosis on a future timeout:

```bash
teamcity run log <build-id> --raw > build.log     # killed builds have no build scan
grep -c 'CrossVersionTest FROM-CACHE' build.log   # cache hit rate is the pass/fail variable
```
