# Why Gradle init scripts are used in practice

> Research for the **Declarative Gradle** effort. 
> 
> Init scripts (`init.gradle` / `init.gradle.kts`) are a DSL use case beyond build & settings files;
> 
> this document collects evidence of *why* they exist and what real-world workflows depend on them, to understand what a restricted declarative language would need to replace.
---

Init scripts exist to apply configuration **outside and across** individual build/settings files — affecting every build they attach to **without modifying project sources**.

Some capabilities are inherently imperative and cross-cutting, while some use cases are closer to having plugins and their configuration as observed in regular declaratively configured projects.

---

## Community & third-party plugins / scripts applied via init scripts

Everything here is a plugin or script you *apply* via an init script — config injection, repository enforcement, code quality, and dependency/security scanners that bundle an init script to read the build. (Develocity/CCUD CI injection is Gradle-maintained and lives in the next section.)

**Widespread, used with high confidence**

| Plugin / script | Maintainer | Category | How the init script is used | Status | Source(s) |
|---|---|---|---|---|---|
| **gradle-versions-plugin** (~3k★) | ben-manes | dependency-reporting | Officially documents init-script install — "transparently added to every Gradle project via a Gradle init script without modifying individual build files." `initscript{}` + `allprojects { apply plugin: ... }` in `init.d/`. **Strongest evidence of a popular community plugin endorsing init-script install.** | ✅ · high | [README § Using a Gradle init script](https://github.com/ben-manes/gradle-versions-plugin/blob/master/README.md#using-a-gradle-init-script) |
| **nebula.lint** | Netflix | code-quality | README recommends defining lint **rules** in an `init.gradle` (or via `apply from`) to centralize across enterprise builds. | ✅ (recommendation only) · high | [README § Getting Started](https://github.com/nebula-plugins/gradle-lint-plugin#getting-started) |
| **github-dependency-graph-gradle-plugin** | Gradle | dependency-scanner | Emits a resolved dependency-graph snapshot to GitHub's Dependency Submission API (feeds Dependabot). Standalone via `-I init.gradle … :ForceDependencyResolutionPlugin_resolveAllDependencies`. (CI delivery: gradle/actions, next section.) | ✅ · high | [README § Generate dependency reports](https://github.com/gradle/github-dependency-graph-gradle-plugin#using-the-plugin-to-generate-dependency-reports) |
| **snyk-gradle-plugin** | Snyk | dependency-scanner | Snyk CLI **bundles `lib/init.gradle` and injects it via `-I`**; registers `snykResolvedDepsJson` tasks across all projects, prints the dependency tree as JSON. | ✅ (in-script comments) · high | [lib/init.gradle § header](https://github.com/snyk/snyk-gradle-plugin/blob/main/lib/init.gradle#L17-L18) |
| **CycloneDX Gradle plugin** | OWASP | SBOM | README "Usage with Initialization Script": `initscript{}` + `rootProject { apply<CyclonedxPlugin>() }`, run `gradlew cyclonedxBom --init-script init.gradle.kts` to generate an SBOM without touching the build. | ✅ · high | [README § Usage with Initialization Script](https://github.com/CycloneDX/cyclonedx-gradle-plugin#usage-with-initialization-script) |
| **GitLab SBOM Dependency Scanning** | GitLab / Netflix | dependency-scanner | Current SBOM scanning docs instruct applying Netflix's **nebula gradle-dependency-lock-plugin** via `--init-script nebula.gradle … generateLock saveLock`. Replaces the deprecated Gemnasium path. | ✅ · high | [docs.gitlab.com § Dependency lock plugin](https://docs.gitlab.com/user/application_security/dependency_scanning/dependency_scanning_sbom/#dependency-lock-plugin) |
| **FOSSA CLI** | FOSSA | dependency-scanner | "Plugin" tactic embeds `scripts/jsondeps.gradle`, runs `gradle -I <script> jsonDeps`; the script registers a `jsonDeps` task across `allprojects` and serializes the resolved graph to JSON. | ✅ · high | [docs § plugin (`-I jsondeps.gradle`)](https://github.com/fossas/fossa-cli/blob/master/docs/references/strategies/languages/gradle/plugin.md#manually-view-plugin-output) · [jsondeps.gradle](https://github.com/fossas/fossa-cli/blob/master/scripts/jsondeps.gradle) |
| **JFrog `gradle-dep-tree`** (Xray) | JFrog | dependency-scanner | Injected via `-I` for dependency-tree generation: `gradle generateDepTrees -I <init.gradle> -Dcom.jfrog.depsTreeOutputFile=…`. Consumed by `jf audit` and the JFrog IntelliJ/Eclipse plugins. | ✅ (plugin) / ❌ (jf audit, IDE) · high | [README § Usage](https://github.com/jfrog/gradle-dep-tree#-usage) · [bundled init.gradle](https://github.com/jfrog/gradle-dep-tree/blob/main/init.gradle) |

**Potentially niche, very specific, or low confidence**

| Plugin / script | Maintainer | Category | How the init script is used | Status | Source(s) |
|---|---|---|---|---|---|
| **Black Duck Detect** (ex-Synopsys) | Black Duck | dependency-scanner | "Gradle Native Inspector" downloads `init-detect.gradle`, runs `gradlew gatherDependencies` (task defined inline) to emit a per-configuration dependency tree. | ✅ · medium | [DeepWiki § Gradle Native Inspector](https://deepwiki.com/blackducksoftware/detect/5.1-java-ecosystem-support#gradle-native-inspector) (unofficial) · [official docs (JS-rendered)](https://documentation.blackduck.com/bundle/detect/page/packagemgrs/gradle.html) |
| **gemnasium-gradle-plugin** | GitLab | dependency-scanner | `gradlew --init-script gl-gemnasium-init.gradle gemnasiumDumpDependencies` applies the plugin by FQCN to dump the dep tree as JSON. | ✅ · high · deprecated (GitLab 17.9) | [README § Using an init-script](https://gitlab.com/gitlab-org/security-products/analyzers/gemnasium-gradle-plugin/-/blob/master/README.md#using-an-init-script) |
| **dependency-check-gradle (OWASP)** | OWASP | dependency-scanner | *Can* be applied globally via `initscript{}` classpath + `gradle.allprojects { apply plugin: 'org.owasp.dependencycheck' }`, run via `-I` or `~/.gradle/init.d/`. | ❌ community workaround · medium | [discuss thread](https://discuss.gradle.org/t/init-script-for-dependency-checker/45496) · [gist](https://gist.github.com/milo-minderbinder/1e1ed911c5b2264dc659578f1baaef16#file-dependencycheck-gradle) |
| **org.sonarqube (SonarScanner)** | SonarSource | code-quality | *Can* be loaded from an init script (`initscript{}` + `allprojects apply`); Sonar staff advise against it (can yield incomplete results). | ❌ community workaround · medium | [discuss thread](https://discuss.gradle.org/t/sonarqube-gradle-on-ci-with-init-script/18224) |
| **JFrog Artifactory (build-info)** | JFrog | CI / publishing | `initscripttemplate.gradle` (materialized by JFrog CLI / Jenkins Artifactory plugin, passed via `--init-script`) registers a `BuildAdapter` listener and applies `ArtifactoryPlugin` to `root.allprojects` for build-info capture + publishing. JFrog CLI separately writes `jfrog.init.gradle` into `init.d/` for resolve/deploy routing. | ❌ (real in source, undocumented) · high | [initscripttemplate.gradle § apply](https://github.com/jfrog/build-info/blob/master/build-info-extractor-gradle/src/main/resources/initscripttemplate.gradle#L21-L23) |
| **repository-mirrors-gradle-plugin** | milo-minderbinder | repo-mirror | `init.d/repositoryMirrors.gradle` rewrites every buildscript and project repository URL to an Artifactory mirror. Plugin Portal: `co.insecurity.repository-mirrors`. | ✅ · high · low-traffic | [README § Usage](https://github.com/milo-minderbinder/repository-mirrors-gradle-plugin#usage) · [portal](https://plugins.gradle.org/plugin/co.insecurity.repository-mirrors) |
| **repoconfig-gradle-plugin** | Intershop | repo-mirror | "An init script plugin to provide special repository settings for project teams or companies," via `initscript{}` + `apply plugin`. | ✅ · high · stale (Gradle 5/JDK 8) | [README § Usage](https://github.com/IntershopCommunicationsAG/repoconfig-gradle-plugin#usage) |
| **NexusRepositoryPlugin** | Sonatype | repo-mirror | Official example: `init.gradle` `Plugin<Gradle>` that removes all non-`standard-` Maven repos, forcing resolution through a Nexus proxy. **Inline-defined (no classpath dep).** | ✅ (nexus-book, archived) · high | [nexus-book § Gradle](https://github.com/sonatype/nexus-book/blob/master/chapter-maven.asciidoc#gradle-minimal) · [example init.gradle](https://github.com/sonatype/nexus-book-examples/blob/master/gradle/init/init.gradle) |
| **gradle-init-scripts (Spring)** | VMware/Spring | repo / credentials | `init.gradle` in `GRADLE_HOME` decorating Maven repos with `repo.spring.io` Artifactory credentials. | ✅ · high · ☠️ archived (2022) | [README § init.gradle](https://github.com/spring-attic/gradle-init-scripts/blob/master/README.md#initgradle) |
| **gradle-credential-wrapper** | Chesapeake Technology | repo / credentials (custom distribution) | Custom distribution bundling `init.d/credentials.gradle` + `injectCredentials.gradle` (applies the gradle-credentials-plugin to `allprojects`, decrypts stored creds). | ✅ · medium · ☠️ dead (2020, jcenter) | [injectCredentials.gradle](https://github.com/chesapeaketechnology/gradle-credential-wrapper/blob/master/src/scripts/init.d/injectCredentials.gradle) · [init.d dir](https://github.com/chesapeaketechnology/gradle-credential-wrapper/tree/master/src/scripts/init.d) |
| **com.dvoiss.globalplugins** | David Voiss | global-config | A plugin *designed* to be distributed as a global `init.d/` script applying plugins/deps across all projects. | ✅ (gist) · medium · abandoned (2016) | [gist (init script)](https://gist.github.com/dvoiss/37cb797b42a00a9a2db6#file-remote-global-plugin-script-gradle) |
| **com.diffplug.spotless** | DiffPlug | code-quality | *Can* be applied via `initscript{}` + `allprojects`; maintainer posted a working example but questioned the fit. | ❌ (issue, not docs) · medium | [issue #680](https://github.com/diffplug/spotless/issues/680) |
| **com.adarshr.test-logger** | radarsh | test-reporting | Classpath resolves via `initscript{}` but did **not** work end-to-end (version-incompat `AbstractMethodError`). | ❌ broken · low | [issue #253](https://github.com/radarsh/gradle-test-logger-plugin/issues/253) |
| **foojay-resolver-convention** | Gradle | toolchain | A **settings** plugin; only a fragile community init-script workaround exists (the naive form fails). | ❌ (open docs request) · low | [issue #26511](https://github.com/gradle/gradle/issues/26511) |
| **ffgiff/gradle-init-scripts** | ffgiff | code-quality | Collection of `-I` init scripts wiring Checkstyle/FindBugs/ktlint/PMD/Sonar/pitest into the standard `check` task on Android builds. | ✅ · high · abandoned (2020) | [README § Usage](https://github.com/ffgiff/gradle-init-scripts#usage) |
| **mrhaki `quality.gradle` recipe** | community blog | conditional injection | Init script that, in `allprojects { afterEvaluate { … } }`, checks `hasPlugin('java')`/`'groovy'` and **conditionally** applies Checkstyle / CodeNarc to every project. | ✅ (blog tutorial) · high · 2012, legacy idiom | [mrhaki blog](https://blog.mrhaki.com/2012/10/gradle-goodness-init-script-for-adding.html) |
| **jvt.me `init.d/spotless.gradle` recipe** | community blog | conditional injection | Global init script making each `JavaCompile` task `finalizedBy spotlessApply` — but only where Spotless is already configured. | ✅ (personal recipe) · high | [jvt.me blog](https://www.jvt.me/posts/2020/05/15/gradle-spotless/) |

---

## Official Gradle / Gradle-team tooling and docs

### Develocity (Build Scan / Gradle Enterprise) CI injection — the biggest real-world use case

CI integrations install the shared `develocity-injection.init.gradle` into Gradle User Home (or pass it via `-I`), parameterized by `DEVELOCITY_INJECTION_*` env vars, to auto-inject the Develocity plugin (and optionally CCUD) into every build **without modifying project sources**. The canonical script is Gradle-maintained; each integration vendors a copy.

**Widespread, used with high confidence**

| Integration | Maintainer | How the init script is used | Status | Source(s) |
|---|---|---|---|---|
| **develocity-injection.init.gradle** (canonical) | Gradle | "An init-script that can be used by CI integrations to inject Develocity into a Gradle build." Applies the Develocity plugin via `initscript{}` + `settingsEvaluated`; parameterized by env/system props. | ✅ · high | [init-script source](https://github.com/gradle/develocity-ci-injection/blob/main/src/main/resources/develocity-injection.init.gradle) · [README §](https://github.com/gradle/develocity-ci-injection#develocity-injection-gradle-init-script) |
| **Common Custom User Data (CCUD)** | Gradle | Enriches Build Scans with Git/CI/OS metadata. Manual usage is in `settings.gradle`, but auto-injected org-wide by the Develocity init script when `…CCUD_PLUGIN_VERSION` is set. | ✅ (injection lives in develocity-ci-injection) · high | [develocity-ci-injection §](https://github.com/gradle/develocity-ci-injection#develocity-injection-gradle-init-script) · [customized-version §](https://github.com/gradle/common-custom-user-data-gradle-plugin#developing-a-customized-version-of-the-plugin) |
| **Develocity Gradle plugin** | Gradle | "Instrument all CI jobs and publish a Build Scan without modifying the underlying projects… configuring in a central place." (Plugin docs describe CI instrumentation; the init-script mechanism lives in develocity-ci-injection.) | ✅ · high | [plugin docs](https://docs.gradle.com/develocity/gradle-plugin/current/) |
| **gradle/actions setup-gradle** (GitHub Action) | Gradle | Works "by adding a set of Gradle init-scripts to the Gradle User Home." Installs build-result-capture, Develocity injection (env-parameterized), and GitHub dependency-graph init scripts. | ✅ · high | [setup-gradle.md § Custom init scripts](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md#use-of-custom-init-scripts-in-gradle-user-home) · [§ Develocity plugin injection](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md#develocity-plugin-injection) |
| **gradle/actions dependency-submission** | Gradle | CI delivery of the github-dependency-graph plugin (see previous section) via an injected init script; runs `resolveAllDependencies` and submits to GitHub (Dependabot). | ✅ · high | [dependency-submission.md § Gradle execution](https://github.com/gradle/actions/blob/main/docs/dependency-submission.md#gradle-execution) · [init-script source](https://github.com/gradle/actions/blob/main/sources/src/resources/init-scripts/gradle-actions.github-dependency-graph.init.gradle) |
| **Develocity GitLab CI/CD templates** | Gradle | Generate the injection init script at runtime, writing it to `init.d/` (recommended `copyInitScriptToGradleUserHome`) or `$CI_PROJECT_DIR` for `-I` (deprecated). | ✅ · high | [README § Gradle auto-instrumentation](https://github.com/gradle/develocity-gitlab-templates#gradle-auto-instrumentation) |
| **Jenkins Gradle plugin** | Jenkins community (tracks Gradle's script) | Global auto-injection of the Develocity + CCUD plugins into any build on the server/agents, configured in Manage Jenkins → Configure System. | ✅ · high *(2-1 vote on the "maintained by Gradle Inc." attribution; substance solid)* | [README § Develocity integration](https://github.com/jenkinsci/gradle-plugin/blob/master/README.adoc) · [bundled init-script source](https://github.com/jenkinsci/gradle-plugin/blob/master/plugin/src/main/resources/hudson/plugins/gradle/injection/init-script.gradle) |
| **Develocity TeamCity plugin** | Gradle | Agent applies `develocity-injection.init.gradle`; two parameter layers. *Source repo is **internal** (404 to the public)*, so the canonical script is cited. | ✅ · high | [canonical init-script](https://github.com/gradle/develocity-ci-injection/blob/main/src/main/resources/develocity-injection.init.gradle) |
| **Develocity Bamboo plugin** | Gradle | `develocity-init-script.gradle` (vendored copy) copied into `.gradle/init.d/`. | ❌ internal | [bundled init-script source](https://github.com/gradle/develocity-bamboo-plugin/blob/main/src/main/resources/develocity/gradle/develocity-init-script.gradle) |

**Potentially niche, very specific, or low confidence**

| Integration | Maintainer | How the init script is used | Status | Source(s) |
|---|---|---|---|---|
| **Develocity Build Validation Scripts** | Gradle | Bash runners apply init scripts via `--init-script`: `configure-build-validation.gradle` + cache-config scripts. | ❌ internal · niche tool | [init-scripts dir](https://github.com/gradle/develocity-build-validation-scripts/tree/main/components/scripts/gradle/gradle-init-scripts) |

### Other first-party Gradle features & docs

**Widespread, used with high confidence**

- **Gradle User Manual.** The [init scripts user manual](https://docs.gradle.org/current/userguide/init_scripts.html) endorses init scripts for: enterprise repositories, [plugin resolution rules](https://discuss.gradle.org/t/configure-plugin-repos-from-init-script-for-settings-plugins/42399) *(forum: configuring plugin repos from an init script for settings plugins)*, build listeners, applying custom plugins across many builds, and varying config by environment (dev vs CI). It ships an **`EnterpriseRepositoryPlugin`** example ([§ Init plugins](https://docs.gradle.org/current/userguide/init_scripts.html#sec:init_plugins)) — a `Plugin<Gradle>` that removes all repositories not pointing to the enterprise URL. (Inline-defined, no classpath dependency.)
- **Build Cache configuration.** The manual documents configuring the build cache from an init script (`settingsEvaluated { buildCache { … } }`), with a CI-detection `isPush = isCiServer` pattern. — [build_cache.html § Use cases](https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure_use_cases)

**Potentially niche, very specific, or low confidence**

- **gradle-profiler (chrome-trace).** `--profile chrome-trace` auto-injects `chrome-trace/init.gradle` applying `GradleTracingPlugin` for build-operation timing. — [init.gradle § apply](https://github.com/gradle/gradle-profiler/blob/master/subprojects/chrome-trace/init.gradle#L10) · [README § Chrome Trace](https://github.com/gradle/gradle-profiler#chrome-trace)
- **gradle-profiler-plugin.** IntelliJ plugin deploying `init.d/profiling.gradle` that attaches async-profiler on every build (starts it, stops it in a `buildFinished` hook). *(☠️ archived 2024.)* — [README § How it works](https://github.com/gradle/gradle-profiler-plugin/blob/master/README.md#how-it-works)
- **teamcity-gradle-init-scripts-plugin** (rodm) — a TeamCity plugin whose *product* is managing init scripts (upload, assign per-step via `initScriptName` or per-config via the `gradleInitScript` build feature); ships `enterprise-repository`, `task-outcomes`, `build-cache` examples. — [README § Gradle build feature](https://github.com/rodm/teamcity-gradle-init-scripts-plugin#gradle-build-feature)

### Application mechanisms (all without touching build files)

- `$GRADLE_USER_HOME/init.d/*.init.gradle(.kts)` (and similar paths) — affects every build on that machine/agent
- `-I` / `--init-script path` on the command line — ad hoc, repeatable
- Tooling API `withArguments("--init-script", …)` — how IDEs inject them

---

## Other tooling & scenarios that inject init scripts

Tools that *generate and inject* init scripts as an integration mechanism — to read or instrument a build they don't own. Almost all are internal/undocumented implementation details rather than user-authored config.

**Widespread, used with high confidence**

| Tool / scenario | Category | How the init script is used | Status | Source(s) |
|---|---|---|---|---|
| **IntelliJ IDEA Gradle sync** | IDE | On every import/sync, generates temp init scripts (`ijInit<N>.gradle`, etc.) via `--init-script`; adds IDE tooling-extension JARs to the classpath, model builders registered via the Tooling API `ServiceLoader`. Runs **alongside** user `init.d/` scripts. | ❌ internal · high · ubiquitous | [GradleInitScriptUtil.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/gradle/src/org/jetbrains/plugins/gradle/service/execution/GradleInitScriptUtil.kt) |
| **Android Studio Gradle sync** | IDE | Same IntelliJ mechanism; temp `sync.studio.tooling.gradle`. | ❌ internal · medium · ubiquitous | [JetGradlePlugin.gradle](https://github.com/JetBrains/intellij-community/blob/master/plugins/gradle/tooling-extension-impl/resources/org/jetbrains/plugins/gradle/tooling/internal/init/JetGradlePlugin.gradle) |
| **Microsoft vscode-gradle** ("Gradle for Java") | IDE | Bundled `gradle-server` generates init scripts via `--init-script`: a model/discovery script applying `com.microsoft.gradle.GradlePlugin` to register a `ToolingModelBuilder`; and a debug script attaching a JDWP agent. | ❌ internal · high | [PluginUtils.java § getInitScript()](https://github.com/microsoft/vscode-gradle/blob/main/gradle-server/src/main/java/com/github/badsyntax/gradle/utils/PluginUtils.java#L17-L41) |
| **TeamCity bundled Gradle runner** | CI runner | The built-in runner injects its own `init.gradle` (and `init_since_8.gradle` for Gradle 8+) via `--init-script` to register listeners emitting `##teamcity[…]` service messages. | ❌ internal · high | [init.gradle](https://github.com/JetBrains/teamcity-gradle/blob/master/gradle-runner-agent/src/main/scripts/init.gradle) · [injection point](https://github.com/JetBrains/teamcity-gradle/blob/master/gradle-runner-agent/src/main/java/jetbrains/buildServer/gradle/agent/tasks/GradleTasksComposer.java#L67-L90) |

**Potentially niche, very specific, or low confidence**

| Tool / scenario | Category | How the init script is used | Status | Source(s) |
|---|---|---|---|---|
| **Apache NetBeans** (built-in Gradle module) | IDE | Generates `nb-tooling.gradle` via `--init-script`; injects `netbeans-gradle-tooling.jar` so a `ToolingModelBuilder` (`NbProjectInfoBuilder`) produces the project model. | ❌ internal · medium · minority IDE | [GradleDaemon.java § INIT_SCRIPT_NAME](https://github.com/apache/netbeans/blob/master/extide/gradle/src/org/netbeans/modules/gradle/loaders/GradleDaemon.java#L45-L71) · [issue #3801](https://github.com/apache/netbeans/issues/3801) |
| **Eclipse Buildship** | IDE | Public extension point `org.eclipse.buildship.core.invocationcustomizers` (`InvocationCustomizer.getExtraArguments()`) lets integrators return `--init-script <path>`. Gradle blog documents the *intent*; the shipped sample only shows a `-P` property. | ❌ (intent in prose) · medium | [ApiExamples.md § InvocationCustomizer](https://github.com/eclipse/buildship/blob/master/docs/development/ApiExamples.md#eclipseinstalllocationgradlebuildjava) · [Gradle blog: Buildship 2.0](https://blog.gradle.org/announcing-buildship-2.0) |
| **netbeans-gradle-project** (kelemen) | IDE | Ships `nb-init-script.gradle` via `--init-script`; uses `afterProject` to inject `run`/`debug` tasks + optional JaCoCo. | ✅ (wiki) · medium · abandoned (2018) | [wiki § Automatic tasks](https://github.com/kelemen/netbeans-gradle-project/wiki/Task-Execution#automatic-tasks) |
| **Snyk CLI `--init-script=<FILE>`** | dependency-scanner | User-facing flag on `snyk test`/`snyk monitor` to pass a *custom* init script honored during dep resolution (distinct from Snyk's own bundled script in the previous section). | ✅ · high · very specific feature | [Snyk CLI docs](https://github.com/snyk/user-docs/blob/main/docs/supported-languages/supported-languages-list/java-and-kotlin/snyk-cli-for-java-and-kotlin.md) |
| **Renovate** | dependency-scanner | *Historically* injected `renovate-plugin.gradle` via `--init-script` to enumerate deps. **Current Renovate no longer does this** (JS parser); Gradle is invoked only for lockfile maintenance. | ❌ (historical) · medium | [issue #5424](https://github.com/renovatebot/renovate/issues/5424) |

---

## General "common knowledge" use cases

What the docs and practitioners say init scripts are good for, beyond the specific tools above:

- **Centralized enterprise/global config across builds** — reduce per-repo redundancy. — [Gradle docs: init scripts](https://docs.gradle.org/current/userguide/init_scripts.html) · [softaai — Understanding init scripts](https://softaai.com/understanding-init-scripts-in-gradle/) *(blog, echoes the docs)*
- **Repository/mirror enforcement** — route *all* projects *and the buildscript classpath* through a single Nexus/Artifactory proxy; real `init.gradle` examples iterate repositories and replace them with one Nexus group URL. — [Nexus proxy gist](https://gist.github.com/mosabua/cd0d5a4ddac550273157) · [Gradle Plugin Portal: mirroring docs](https://plugins.gradle.org/docs/mirroring)
- **Conditional plugin injection** — react to plugins already applied (`hasPlugin` / `afterEvaluate`; modern equivalent `pluginManager.withPlugin`). *(see the mrhaki & jvt.me recipes above)*
- **Machine-specific details** (JDK/SDK locations), **user-specific credentials** (via providers), **build listeners/custom loggers** — all enumerated as documented use cases.

---

## What this means for Declarative Gradle

The catalogue clusters into a few distinct *kinds* of use, each with different replaceability:

1. **Build introspection / model extraction (read-only, tool-driven).** IDEs (IntelliJ, Android Studio, VS Code, NetBeans, Buildship) and dependency/security scanners (Snyk, github-dependency-graph, Gemnasium, FOSSA, CycloneDX, JFrog dep-tree, Black Duck, Renovate-historical) inject init scripts to *read* a fully-resolved model of an arbitrary build without modifying it. It's how external tools observe builds they don't own.
2. **Build instrumentation (listeners / hooks).** Profilers and CI runners (gradle-profiler, TeamCity runner, build-result-capture) attach listeners and lifecycle hooks (`buildFinished`, build-operation tracing, service messages). Inherently imperative/event-driven.
3. **Cross-cutting plugin & config injection.** Develocity/CCUD, Nebula lint rules, repo-mirror plugins, gradle-versions, the mrhaki/jvt.me recipes — apply a plugin or config to all projects from outside, often *conditionally* on what's already applied.
4. **Repository / credential enforcement.** Nexus/Artifactory/Spring — override or restrict repositories and inject credentials globally.
5. **Environment-parameterized CI tooling.** Develocity injection across GitHub/GitLab/Jenkins/TeamCity/Bamboo, all driven by env vars.

**Implication.** A restricted declarative language can plausibly cover #3, #4 (conditional plugin application, repository enforcement) and some cases of #5 (env-parameterized injection) with first-class features. But **#1 and #2 are fundamentally imperative and tool-owned** — they don't belong in *user-authored* declarative build logic at all; they need a separate mechanism for external tools to observe and instrument builds. That distinction (user-facing declarative DSL vs. tool-facing introspection/instrumentation surface) is the key design question these use cases raise.

### Open questions worth deciding for the DCL design

- How does DCL support plugin-reactive conditional application?
- How are credentials / secrets (a documented init-script use case) handled declaratively?
