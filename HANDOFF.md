# Handoff — `IGNORE_REPO_MIRROR` kill switch + 2026-09-02 Artifactory incident

> Renamed 2026-09-08: the env var was `IGNORE_MIRROR` up to and including build 116950940.
> Anything below quoting that older name refers to the same switch.

Branch `feat/ignore-mirror-switch` · Draft PR https://github.com/gradle/gradle/pull/39042
Written 2026-09-03.

---

## 1. What this branch does

Makes `env.IGNORE_REPO_MIRROR=true` a **single, complete kill switch** that makes a CI build resolve
everything from upstream repositories instead of `repo.grdev.net`, so `check` still works during an
Artifactory outage.

**Hard requirement from Bo: one lever, nothing else.** No second TeamCity parameter. Not
`gradle.plugins.portal.url`, not `env.REPO_MIRROR_URLS`. A runbook that says "also flip these two
others" gets half-applied at 3am. The code absorbs the difficulty.

### How to use it

Set TeamCity parameter **`env.IGNORE_REPO_MIRROR` = `true` on the root `Gradle` project**. Effective next
build. No code change, no config regeneration. Remove it when the mirror is healthy.

- `env.IGNORE_REPO_MIRROR` is deliberately **not** declared in the `.teamcity` Kotlin DSL. A
  build-configuration-level parameter would outrank the project-level one and block the flip.
- **`env.REPO_MIRROR_URLS` must stay set** while the bypass is on — the reverse mapping reads it to
  recognise which URLs are mirror URLs.

### Out of scope (still grdev-pinned, flippable as their own TC params)

- `env.YARNPKG_MIRROR_URL` — JS/docs builds
- `gradle.internal.repository.url` → `env.GRADLE_INTERNAL_REPO_URL` — publishing only, irrelevant to `check`

---

## 2. The three commits

```
53badd27cd7  Make IGNORE_REPO_MIRROR a complete repo.grdev.net kill switch
45f00f5d5ca  Undo the plugin portal override early enough for settings plugin resolution
79321eaec5c  Document the upstream-load caveat for the mirror bypass
```

8 files, +124/−11.

| File | Change |
|---|---|
| `gradle/shared-with-buildSrc/mirrors.settings.gradle.kts` | `ignoreMirrors` hoisted to a `val` (CC-compatible: `providers.environmentVariable`, no `System.getenv`/`setProperty`). Reverse map mirror-URL → upstream-URL. Bypass extended to `dependencyResolutionManagement` repositories. Runbook comment at top. |
| `settings.gradle.kts`, `build-logic/`, `build-logic-commons/`, `build-logic-settings/` settings | Undo the injected portal override inside `pluginManagement { repositories { … } }`. `build-logic-commons` gains an explicit `repositories { gradlePluginPortal() }` — already the implicit default, made explicit so there is something to rewrite. |
| `build-logic/jvm/.../CiEnvironmentProvider.kt` | `collectMirrorUrls()` returns `emptyMap()`. Choke point for the whole test-fixture layer. |
| `build-logic/performance-testing/.../mirroring-init-script.gradle` | Skip building the mirror map; `mirror()` becomes a no-op. |
| `testing/internal-distribution-testing/.../RepoScriptBlockUtil.groovy` | Use the original URL in the `MirroredRepository` constructor when `isMirrorEnabled()` is false. |

### The non-obvious part — read this before touching it

`ignoreMirrors()` had existed in `mirrors.settings.gradle.kts` for years as **dead code**. Its only
call site was deleted by PR #17794 / `271636d99e8` (2021-07-21), which moved the plugin-portal
override into TeamCity.

Two things make a naive "skip mirroring" insufficient:

1. **`.teamcity/src/main/kotlin/common/CommonExtensions.kt:45`** adds
   `-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%` to essentially
   every build step, and `DefaultBaseRepositoryFactory.java:148` reads it **eagerly** into the
   `gradlePluginPortal()` URL. So the portal repository *is* a grdev URL before any script sees it.
   Hence the **reverse map**, not a skip.

2. **The mirrors script cannot be applied early enough.** The root `settings.gradle.kts`
   `plugins {}` block — and the equivalents in `build-logic`, `build-logic-commons`,
   `build-logic-settings` — resolve *before* `apply(from = mirrors.settings.gradle.kts)` runs, and
   the Kotlin DSL forbids placing that apply above a `plugins {}` block. The only hook that runs
   early enough is inside `pluginManagement { repositories { … } }` itself. **Do not try to "clean
   this up" by consolidating it back into the mirrors script — it will silently stop working.**

`RepoScriptBlockUtil.getName()`'s `_MIRROR` suffix logic is deliberately **unchanged** — those names
appear in test expectations and in `RepositoryMirrorOutputNormalizer`.

---

## 3. Verification status

### CI — the real test

https://builds.gradle.org/build/116950940 — revision `45f00f5d5caaab64242e9413b45aa234408ef5b4`,
run with `env.IGNORE_REPO_MIRROR=true`, TeamCity's grdev portal override injected, and Artifactory dead.

Final: **459 tests failed, 81,763 passed, 1,016 ignored**. Build chain finished: **151 succeeded, 93 failed** (244 builds).

Sampling 400 failures:

| Signal | Count |
|---|---|
| `grdev` anywhere in a failure | **0** |
| `Could not resolve` | 2 |
| `Cannot create a NativeToolChain named 'gcc'` | 266 |
| Non-test build failures | 1 |

**Zero grdev references against a dead Artifactory with 81k tests passing — the switch works.**

### The 459 failures WERE from this branch — corrected 2026-09-03

**This section originally claimed the failures were pre-existing on master. That was wrong.** The real
cause was found by Bo in commit `58895e7809a`:

`AbstractGradleExecuter.usingInitScript()` was gated on `RepoScriptBlockUtil.isMirrorEnabled()`, so with
the switch on it silently dropped **every** fixture init script — not just the repository-mirror one.
Toolchain, lifecycle and progress-logging fixtures all vanished. That is why the failures clustered
where they did:

- `Cannot create a NativeToolChain named 'gcc'` — the toolchain init script was never applied
- `progressLogger.downloadProgressLogged(...)` assertions in the Ivy HTTP tests — progress-logging init
  script never applied
- `BinaryNativePlatformIntegrationTest` toolchain assertions — same cause

The fix moves the guard to `withRepositoryMirrors()`, where it belongs, and leaves `usingInitScript()`
unconditional.

**Why the original conclusion was wrong, so the mistake is not repeated:** the reasoning was "every
commit touching `CommonToolchainCustomizationIntegTest` is already on master, and this branch does not
touch `platforms/native`". Both facts were true and the conclusion still did not follow — the *test* was
unchanged while its *environment* changed, because the init scripts it depended on were being discarded.
When failures cluster in unrelated-looking areas (toolchains, progress logging) right after a change to
test-fixture plumbing, suspect the plumbing, not the tests.

**Consequence for the evidence below:** build 116950940 predates `58895e7809a`. Its 459 failures are
explained by that bug and should not be read as either a master problem or a defect in the switch. The
switch's own correctness evidence — 0 grdev references — is unaffected. **A full PR-feedback chain has
not been re-run since the fix; that is the outstanding verification.**

### Known limitation — upstream capacity is the real ceiling

This is the most important operational finding, and it is a capacity limit, not a correctness one.

With the bypass on there is no caching proxy in front of anything: every agent fetches straight from
`repo.maven.apache.org`, `plugins.gradle.org` and `repo.gradle.org`. At full-stage scale that gets the
fleet throttled.

Measured on build 117129908 (Quick Feedback - Linux Only, Xperimental, ~166 agents, simulated outage with
overrides correctly propagated):

```
CompileAllBuild (117129884):
  HttpErrorStatusCodeException: Received status code 429 from server: Too Many Requests
  repo-mirror-outage-test.invalid refs: 0    grdev refs: 0
```

That single failure cascaded into **24 failed builds** (the rest all `Snapshot dependency failed`). A
lone artifact fetch can also simply time out — `Could not GET ... > Read timed out`, seen on build
116950940 against `repo.gradle.org/gradle/public`.

**In both cases the URLs had already been correctly rewritten to upstream, with zero grdev requests.** The
switch worked; upstream could not absorb what the mirror normally absorbs.

Plan for this during a real outage:

- **Do not expect a full `check` to pass.** Run reduced scope. Retry before concluding there is a regression.
- **Builds that never touched the mirror can fail too.** `.teamcity`'s `./mvnw clean verify` resolves
  directly from Maven Central at all times (hardcoded in `.teamcity/pom.xml` and the Maven wrapper — no
  mirror reference anywhere). It passed with the mirror healthy (117129934) and failed inside the bypass
  run (117129887), purely as collateral damage from the same throttling. This is *not* a gap in the
  switch's coverage; that build never used the mirror to begin with.

### Local runs (cold isolated `GRADLE_USER_HOME`, grdev returning HTTP 522)

| Scenario | Result |
|---|---|
| Baseline, no fix | FAILED on `repo.grdev.net/.../gradle-plugin-portal-prod/...` 522 · https://ge.gradle.org/s/vbgc5xoechzoc |
| `IGNORE_REPO_MIRROR=true` | `BUILD SUCCESSFUL in 6m 36s`, `grep -c grdev` = **0** · https://ge.gradle.org/s/thhpdmddhhkki |
| No env vars (non-CI path) | `BUILD SUCCESSFUL` · https://ge.gradle.org/s/ncb2vq5inbu5c |

A **positive** end-to-end run through the mirrors was not possible while grdev was down. The
mirror-enabled path is verified only indirectly, by the baseline run rewriting
`https://plugins.gradle.org/m2` → grdev and failing against it. **Re-check this once grdev is back.**

---

## 3b. Testing the bypass while the mirror is healthy

grdev came back up on 2026-09-08, so the bypass can no longer be tested by simply flipping it — with a
healthy mirror, a build passes either way and proves nothing. Simulate an outage instead, per-run, so
nothing shared is touched and no other branch is affected:

```bash
teamcity run start <buildTypeId> --branch <branch> \
  -P reverse.dep.'*'.env.REPO_MIRROR_URLS="<real value, repo.grdev.net replaced by repo-mirror-outage-test.invalid>" \
  -P reverse.dep.'*'.gradle.plugins.portal.url="https://repo-mirror-outage-test.invalid/artifactory/gradle-plugin-portal-prod/" \
  -P reverse.dep.'*'.env.IGNORE_REPO_MIRROR=true
```

**The `reverse.dep.*.` prefix is not optional on a composite/trigger build.** TeamCity does not propagate
plain `-P` parameters to snapshot dependencies. Without the prefix the overrides sit on the trigger build
while every build that actually resolves anything runs against the real mirror — and the run comes back
green having tested nothing. This bit me on build 117128838: 77,000 tests passed and the run was worthless
as bypass verification. On a *leaf* build (e.g. `..._Check_CompileAllBuild`) plain `-P` is fine, since there
are no dependencies to propagate to.

Always confirm before trusting a green result:

```bash
teamcity api "/app/rest/builds/id:<a dependency build id>/resulting-properties" \
  | grep -E 'IGNORE_REPO_MIRROR|REPO_MIRROR_URLS|plugins.portal.url'
```

It must show `env.IGNORE_REPO_MIRROR` present and the `.invalid` URLs — not `repo.grdev.net`.

`.invalid` is reserved by RFC 6761 and never resolves, so anything still pointed at the "mirror" fails
fast instead of silently succeeding against the real one. Overriding `gradle.plugins.portal.url` matters
as much as the mirror list — it is what TeamCity injects as
`-Dorg.gradle.internal.plugins.portal.url.override`.

**Run it both ways.** Without `env.IGNORE_REPO_MIRROR` the build must FAIL on a
`repo-mirror-outage-test.invalid` URL — that is what proves the simulation is faithful.

**Verified on CI, 2026-09-08** — controlled A/B on `Gradle_Master_Check_CompileAllBuild`, same branch and
agent pool, `env.IGNORE_REPO_MIRROR` the only variable:

| Build | `env.IGNORE_REPO_MIRROR` | Result |
|---|---|---|
| https://builds.gradle.org/build/117128279 | unset | **FAILURE** — `Gradle Central Plugin Repository(https://repo-mirror-outage-test.invalid/artifactory/gradle-plugin-portal-prod/)` |
| https://builds.gradle.org/build/117128278 | `true` | **SUCCESS** · https://ge.gradle.org/s/y5vw4guq54rsu |

The control failing is what makes the pass meaningful: it rules out the success coming from warm agent
caches, because the same resolution demonstrably needed the network on the same pool.

Also verified locally with a cold `GRADLE_USER_HOME`:

| Scenario | Result |
|---|---|
| Simulated outage, **no** switch | FAILED — `Gradle Central Plugin Repository(https://repo-mirror-outage-test.invalid/artifactory/gradle-plugin-portal-prod/)` |
| Simulated outage, **with** switch | `BUILD SUCCESSFUL` — 0 `repo-mirror-outage-test.invalid` refs, **0 grdev refs** (did not fall back to the now-online real mirror) · https://ge.gradle.org/s/gdactj372f7a4 |

---

## 4. The incident, and what was actually wrong

Two separate problems got tangled together. Both are resolved.

### 4a. repo.grdev.net down

JFrog Artifactory, compromised/rebuilt 2026-09-02. It is **both** the caching proxy for every
upstream repository **and** TeamCity's internal artifact storage, so an outage stops all development.
`env.IGNORE_REPO_MIRROR=true` was set on the root `Gradle` project during the incident.

### 4b. All Gradle VCS roots dead — `HTTP 400` on `git fetch`

Every `Gradle_*` build failed at change collection:

```
Failed to collect changes ... expected flush after ref listing
error: RPC failed; HTTP 400 curl 22 The requested URL returned error: 400
freeze.settings.error: Failed to load build settings from VCS
```

**Root cause: one corrupt/bloated server-side git mirror.** TeamCity keys mirrors by repository URL
**including the username**, so `gradle/gradle` had four:

```
https://github.com/gradle/gradle.git              = git-1BEEE9E9.git
https://bot-gradle@github.com/gradle/gradle.git   = git-DE5DA23E.git
https://blindpirate@github.com/gradle/gradle.git  = git-AE01687B.git
https://bot-teamcity@github.com/gradle/gradle.git = git-8D669E1D.git   ← the broken one
```

Ten VCS roots point at `github.com/gradle/gradle` (`Gradle`, `GradleMaster`, `GradleRelease`,
`GradleRelease6x/7x/8x/9x`, `GradleXperimental`, `DistributedTest_DistributedTest`,
`Parallelquarantine`). All the `bot-teamcity` ones share `git-8D669E1D.git`, so they broke together
— while Enterprise roots (different repos → different mirrors) kept working. That shared-mirror fact
is the whole explanation.

**Fix applied:**

```bash
mv /data/.BuildServer/system/caches/git/git-8D669E1D.git /var/tmp/git-8D669E1D.git.bak
```

The `.bak` is still on the server. **Delete it once CI has been healthy for a while**, not before.

**Dead ends, so nobody repeats them:** it was not the `bot-teamcity` token (verified `HTTP/2 200`,
40 chars, no stray bytes, full authenticated fetch succeeded); not server egress (Enterprise roots
fine, 50/50); not repo visibility (anonymous `ls-remote` works); not `JDK_JAVA_OPTIONS` polluting the
credential helper (the helper script has no `2>&1`, and the banner goes to stderr, which git never
parses); not ref count on `GradleMaster` alone (narrowing its branch spec changed nothing, because
nine other roots keep refilling the same mirror).

**Do not use anonymous auth as a workaround.** GitHub rate-limits unauthenticated traffic to 60/hr
per IP, and versioned settings + commit status publishing + the merge-queue integration all need
auth regardless of fetching.

---

## 5. State right now

- Branch pushed, 3 commits. Draft PR #39042 open with full verification in the body.
- `GradleMaster` branch spec **restored** to `+:refs/heads/*` / `-:refs/heads/release`. ✅
- Build queue recovering (657 and draining; it had been paused with 19,967 queued, most of which
  were cancelled during the incident).
- `env.IGNORE_REPO_MIRROR=true` still set on root `Gradle`. **Remove it once grdev is healthy.**
- Merge queue verification build https://builds.gradle.org/build/116951307 was still starved behind
  the PR chain at time of writing — worth re-checking.

## 6. Next steps

1. Re-check the merge-queue build `116951307` on master.
2. Once grdev is back: unset `env.IGNORE_REPO_MIRROR`, and verify the **mirror-enabled** path still
   rewrites correctly (the one path that could not be positively verified during the outage).
3. Investigate `Cannot create a NativeToolChain named 'gcc'` on master — unrelated to this branch but
   failing hundreds of tests.
4. Delete `/var/tmp/git-8D669E1D.git.bak` once CI is confirmed stable.
5. Mark PR #39042 ready for review.

## 7. Security note

`/app/rest/projects/id:Gradle/parameters` returns **`plugin.portal.publish.secret` in plaintext** —
it is not password-typed, so anyone with project read access can read it. Separately: the
`bot-teamcity` GitHub token was restored, **not rotated**, during an incident whose trigger was a
host compromise. "The token is correct" and "the token is safe" are different claims. Worth a look.
