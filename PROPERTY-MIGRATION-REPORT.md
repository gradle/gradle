# Lazy Property Migration Report

> Generated: 2026-05-15
> Branch: `gradle10/provider-api-migration`
> Excluded: `signing` tasks and plugins (by decision)

## Executive Summary

| Category | Count |
|---|---|
| Properties still pending migration (`@ToBeReplacedByLazyProperty`) | ~110 methods across 46 files |
| Properties already migrated (`@ReplacesEagerProperty`) | ~400 methods across 85 files |
| Properties excluded from migration (`@NotToBeReplacedByLazyProperty`) | ~38 methods |

**Note:** `JavaExec.getJvmArgs()` is incorrectly annotated with `@ToBeReplacedByLazyProperty` despite already returning `ListProperty<String>`. This annotation should be removed.

---

## 1. Fully Unmigrated Types (no `@ReplacesEagerProperty` at all)

### Core API Interfaces

| Class | Pending Properties | Types |
|---|---|---|
| `VerificationTask` | `getIgnoreFailures()` | `boolean` |
| `PatternFilterable` | `getIncludes()`, `getExcludes()` | `Set<String>` |
| `CopySpec` | `isCaseSensitive()`, `getIncludeEmptyDirs()`, `getDuplicatesStrategy()` | `boolean`, `DuplicatesStrategy` |
| `Manifest` | `getAttributes()`, `getSections()` | `Attributes`, `Map<String, Attributes>` |
| `DeploymentDescriptor` | `getFileName()` | `String` |

### Credentials

| Class | Pending Properties | Types |
|---|---|---|
| `PasswordCredentials` | `getUsername()`, `getPassword()` | `String` |
| `HttpHeaderCredentials` | `getName()`, `getValue()` | `String` |
| `AwsCredentials` | `getAccessKey()`, `getSecretKey()`, `getSessionToken()` | `String` |
| `HttpBuildCacheCredentials` | `getUsername()`, `getPassword()` | `String` |

### Tasks

| Class | Pending Properties | Types |
|---|---|---|
| `SourceTask` | `getSource()` | `FileTree` |
| `AbstractCopyTask` | `isCaseSensitive()`, `getIncludeEmptyDirs()`, `getDuplicatesStrategy()`, `getIncludes()`, `getExcludes()`, `getFilteringCharset()` | `boolean`, `DuplicatesStrategy`, `Set<String>`, `String` |
| `Copy` | `getDestinationDir()` | `File` |
| `Sync` | `getDestinationDir()` | `File` |
| `GradleBuild` | `getStartParameter()`, `getDir()`, `getTasks()`, `getBuildName()` | `StartParameter`, `File`, `List<String>`, `String` |
| `AbstractTestTask` | `getIgnoreFailures()` | `boolean` |
| `AbstractCodeQualityTask` | `getIgnoreFailures()` | `boolean` |
| `AbstractCompile` | `getSourceCompatibility()`, `getTargetCompatibility()` | `String` |
| `AbstractScalaCompile` | `getSource()` | `FileTree` |

### Extensions

| Class | Pending Properties | Types |
|---|---|---|
| `JavaPluginExtension` | `getSourceCompatibility()`, `getTargetCompatibility()` | `JavaVersion` |
| `GradlePluginDevelopmentExtension` | `isAutomatedPublishing()` | `boolean` |
| `CheckstyleExtension` | `getConfigFile()`, `getConfig()` | `File`, `TextResource` |
| `CodeNarcExtension` | `getConfig()`, `getConfigFile()` | `TextResource`, `File` (**blocked:** Gradleception test failures) |

### JaCoCo Violation Rules (all interfaces)

| Class | Pending Properties | Types |
|---|---|---|
| `JacocoViolationRulesContainer` | `isFailOnViolation()`, `getRules()` | `boolean`, `List<JacocoViolationRule>` |
| `JacocoViolationRule` | `isEnabled()`, `getElement()`, `getIncludes()`, `getExcludes()`, `getLimits()` | `boolean`, `String`, `List<String>`, `List<JacocoLimit>` |
| `JacocoLimit` | `getCounter()`, `getValue()`, `getMinimum()`, `getMaximum()` | `String`, `BigDecimal` |

### Other

| Class | Pending Properties | Types |
|---|---|---|
| `ForkOptions` | `getJavaHome()` | `File` |
| `JavaCompile` | `getSource()` | `FileTree` |
| `CodeNarc` | `getConfigFile()`, `getSource()` | `File`, `FileTree` |
| `Checkstyle` | `getConfigFile()` | `File` |

---

## 2. Partially Migrated Types (both `@ReplacesEagerProperty` and `@ToBeReplacedByLazyProperty`)

| Class | Migrated | Pending | Pending Properties |
|---|---|---|---|
| `Test` | 7 | 3 | `getFailFast()` (`boolean`), `getIncludes()` / `getExcludes()` (`Set<String>`) |
| `GroovyCompile` | 1 | 1 | `getSource()` (`FileTree`) |
| `Groovydoc` | 11 | 1 | `getSource()` (`FileTree`) |
| `Pmd` | 7 | 1 | `getSource()` (`FileTree`) |
| `AntlrTask` | 8 | 1 | `getSource()` (`FileTree`) |
| `Javadoc` | 8 | 1 | `getSource()` (`FileTree`) |
| `ScalaDoc` | 4 | 1 | `getSource()` (`FileTree`) |
| `Jar` | 1 | 2 | `getManifest()` (`Manifest`), `getMetaInf()` (`CopySpec`) |

---

## 3. Incorrectly Annotated

| Class | Property | Issue |
|---|---|---|
| `JavaExec` | `getJvmArgs()` | Already returns `ListProperty<String>` but is annotated with `@ToBeReplacedByLazyProperty`. Annotation should be removed. |

---

## 4. Unmigrated Types Breakdown

### Types with straightforward lazy equivalents

| Eager Type | Lazy Equivalent | Count | Example Classes |
|---|---|---|---|
| `String` | `Property<String>` | 18 | AbstractCompile, AwsCredentials, DeploymentDescriptor, GradleBuild, HttpBuildCacheCredentials, JacocoLimit, JacocoViolationRule, PasswordCredentials, HttpHeaderCredentials |
| `boolean` | `Property<Boolean>` | 12 | AbstractCodeQualityTask, AbstractCopyTask, AbstractTestTask, CopySpec, GradlePluginDevelopmentExtension, JacocoViolationRule, JacocoViolationRulesContainer, Test, VerificationTask |
| `File` | `RegularFileProperty` / `DirectoryProperty` | 7 | Checkstyle, CheckstyleExtension, CodeNarc, Copy, ForkOptions, GradleBuild, Sync |
| `Set<String>` | `SetProperty<String>` | 4 | PatternFilterable, Test |
| `List<String>` | `ListProperty<String>` | 3 | GradleBuild, JacocoViolationRule |
| `BigDecimal` | `Property<BigDecimal>` | 2 | JacocoLimit |
| `JavaVersion` | `Property<JavaVersion>` | 2 | JavaPluginExtension |
| `DuplicatesStrategy` | `Property<DuplicatesStrategy>` | 2 | AbstractCopyTask, CopySpec |

### `FileTree getSource()` -- recurring pattern (10 classes)

The single most common unmigrated property. All `SourceTask` subclasses inherit this:

`SourceTask`, `JavaCompile`, `GroovyCompile`, `AbstractScalaCompile`, `ScalaDoc`, `Groovydoc`, `Javadoc`, `Pmd`, `AntlrTask`, `CodeNarc`

Likely blocked because `FileTree` in `SourceTask` is built from `source(Object...)` calls and converting that pattern to `ConfigurableFileCollection` across all subclasses is a larger refactoring.

### Types without simple lazy equivalents

| Eager Type | Count | Classes | Challenge |
|---|---|---|---|
| `TextResource` | 2 | CheckstyleExtension, CodeNarcExtension | No provider-based replacement for TextResource exists |
| `Manifest` | 1 | Jar | Complex mutable nested object (attributes + sections) |
| `CopySpec` | 1 | Jar (`getMetaInf()`) | Read-only DSL object, comment says "should probably stay eager" |
| `StartParameter` | 1 | GradleBuild | Internal complex configuration object |
| `Attributes` | 1 | Manifest interface | `java.util.jar.Attributes` -- mutable map-like |
| `Map<String, Attributes>` | 1 | Manifest interface | Nested mutable map of sections |
| `List<JacocoViolationRule>` | 1 | JacocoViolationRulesContainer | Nested mutable objects with their own lifecycle |
| `List<JacocoLimit>` | 1 | JacocoViolationRule | Nested mutable objects |

---

## 5. Known Blockers

| Property | Blocker |
|---|---|
| `CodeNarcExtension.getConfig()` / `getConfigFile()` | "Causes Gradleception test failures" |
| `Jar.getMetaInf()` | "This should probably stay eager" |
| `CreateStartScripts.getRelativeClasspath()` | Marked `unreported = true` -- protected method, skipped |

---

## 6. Summary by Area

| Area | Fully Migrated | Partially Migrated | Not Migrated |
|---|---|---|---|
| Core copy tasks | -- | -- | AbstractCopyTask, Copy, Sync, SourceTask |
| Code quality | -- | Pmd, Checkstyle (task) | CheckstyleExtension, CodeNarc, CodeNarcExtension, AbstractCodeQualityTask |
| JVM compilation | ScalaCompile | GroovyCompile, Javadoc, ScalaDoc | AbstractCompile, JavaCompile, AbstractScalaCompile, ForkOptions |
| Testing | -- | Test | AbstractTestTask |
| Credentials | -- | -- | PasswordCredentials, HttpHeaderCredentials, AwsCredentials, HttpBuildCacheCredentials |
| JaCoCo | -- | -- | JacocoViolationRulesContainer, JacocoViolationRule, JacocoLimit |
| Packaging | -- | Jar | Manifest, DeploymentDescriptor |
| Application plugin | -- | -- | GradlePluginDevelopmentExtension |
| Build infra | -- | -- | GradleBuild, JavaPluginExtension |
| Ant/ANTLR | -- | AntlrTask | -- |

---

## 7. Migration Candidates by Difficulty

Each of the 110 remaining `@ToBeReplacedByLazyProperty` annotations is
categorized below: **A** = do not migrate, **B** = easy candidate,
**C** = doable with caveats. Counts: A ≈ 26, B ≈ 61, C ≈ 23.

### 7.A. Do not migrate (~26)

#### A1. Blocked by explicit comment

| File:line | Method | Reason |
|---|---|---|
| `platforms/jvm/code-quality/.../CodeNarcExtension.java:49` | `getConfig()` | Causes Gradleception test failures |
| `platforms/jvm/code-quality/.../CodeNarcExtension.java:66` | `getConfigFile()` | Causes Gradleception test failures |
| `platforms/jvm/platform-jvm/.../Jar.java:193` | `getMetaInf()` | "Should probably stay eager"; returns `CopySpec` |
| `platforms/jvm/plugins-application/.../CreateStartScripts.java:381` | `getRelativeClasspath()` | `unreported=true`, protected method |

#### A2. Returns a configurable domain object (builder-style, not a value)

- `platforms/jvm/platform-jvm/.../Manifest.java:34,42` — `getAttributes()`, `getSections()`
- `platforms/software/signing/.../Signature.java:372,383,395` — `getSignatory()`, `getSignatureType()`, `getSignatureSpec()`
- `platforms/software/signing/.../SignatureSpec.java:32,47` — `getSignatory()`, `getSignatureType()`
- `platforms/software/signing/type/SignatureTypeProvider.java:25,30` + `AbstractSignatureTypeProvider.java:33,50` — `getDefaultType()`, `getTypeForExtension()`
- `platforms/software/signing/.../SigningExtension.java:152,210,220,231` — `isRequired()`, `getSignatory()`, `getSignatureType()`, `getSignatureTypes()`
- `platforms/jvm/ear/.../DeploymentDescriptor.java:39` — `getFileName()`

#### A3. Computed/derived properties

- `platforms/software/signing/.../Sign.java:265,300` — `getSignaturesByKey()` (computed map), `getSingleSignature()` (throws if not exactly one)
- `platforms/software/signing/.../SignOperation.java:183,194` — `getSignatures()`, `getSingleSignature()`

#### A4. `SourceTask.getSource()` base method

- `subprojects/core/src/main/java/org/gradle/api/tasks/SourceTask.java:70` — base `FileTree` getter; separate refactor concern (overrides listed in 7.B.3 below all hinge on this)

### 7.B. Easy migration candidates (~61)

#### B1. Internal task classes — simple values, best ROI

| File:line | Method | Replacement |
|---|---|---|
| `platforms/jvm/language-jvm/.../AbstractCompile.java:80` | `getSourceCompatibility()` | `Property<String>` |
| `platforms/jvm/language-jvm/.../AbstractCompile.java:100` | `getTargetCompatibility()` | `Property<String>` |
| `platforms/jvm/testing-jvm/.../Test.java:418` | `getFailFast()` | `Property<Boolean>` |
| `platforms/jvm/testing-jvm/.../Test.java:801` | `getIncludes()` | `SetProperty<String>` |
| `platforms/jvm/testing-jvm/.../Test.java:825` | `getExcludes()` | `SetProperty<String>` |
| `platforms/software/testing-base/.../AbstractTestTask.java:371` | `getIgnoreFailures()` | `Property<Boolean>` |
| `platforms/jvm/code-quality/.../AbstractCodeQualityTask.java:59` | `getIgnoreFailures()` | `Property<Boolean>` |
| `subprojects/core/.../AbstractCopyTask.java:189` | `isCaseSensitive()` | `Property<Boolean>` |
| `subprojects/core/.../AbstractCopyTask.java:207` | `getIncludeEmptyDirs()` | `Property<Boolean>` |
| `subprojects/core/.../AbstractCopyTask.java:233` | `getDuplicatesStrategy()` | `Property<DuplicatesStrategy>` |
| `subprojects/core/.../AbstractCopyTask.java:423` | `getIncludes()` | `SetProperty<String>` |
| `subprojects/core/.../AbstractCopyTask.java:442` | `getExcludes()` | `SetProperty<String>` |
| `subprojects/core/.../AbstractCopyTask.java:593` | `getFilteringCharset()` | `Property<String>` |
| `subprojects/core/.../Sync.java:106` | `getDestinationDir()` | `DirectoryProperty` |
| `subprojects/core/.../Copy.java:100` | `getDestinationDir()` | `DirectoryProperty` |
| `subprojects/core/.../GradleBuild.java:53` | `getStartParameter()` | `Property<StartParameter>` |
| `subprojects/core/.../GradleBuild.java:73` | `getDir()` | `DirectoryProperty` |
| `subprojects/core/.../GradleBuild.java:103` | `getTasks()` | `ListProperty<String>` |
| `subprojects/core/.../GradleBuild.java:136` | `getBuildName()` | `Property<String>` |
| `platforms/jvm/jvm-compiler-worker/.../ForkOptions.java:58` | `getJavaHome()` | `RegularFileProperty` or `Property<File>` |

#### B2. Self-contained value containers — single-PR migrations

**JaCoCo rules** (`platforms/jvm/jacoco-workers/.../`):
- `JacocoLimit.java:40,56,72,88` — `getCounter()`, `getValue()` → `Property<String>`; `getMinimum()`, `getMaximum()` → `Property<BigDecimal>`
- `JacocoViolationRule.java:39,55,70,85,92` — `isEnabled()` → `Property<Boolean>`; `getElement()` → `Property<String>`; `getIncludes()`/`getExcludes()` → `ListProperty<String>`; `getLimits()` → `ListProperty<JacocoLimit>`
- `JacocoViolationRulesContainer.java:43,50` — `isFailOnViolation()` → `Property<Boolean>`; `getRules()` → `ListProperty<JacocoViolationRule>`

**Credentials** (all `Property<String>`):
- `platforms/software/credentials-api/.../AwsCredentials.java:31,43,57` — `getAccessKey()`, `getSecretKey()`, `getSessionToken()`
- `platforms/software/credentials-api/.../PasswordCredentials.java:34,50` — `getUsername()`, `getPassword()`
- `platforms/software/credentials-api/.../HttpHeaderCredentials.java:37,53` — `getName()`, `getValue()`
- `platforms/core-execution/build-cache-http/.../HttpBuildCacheCredentials.java:44,66` — `getUsername()`, `getPassword()`

#### B3. `getSource()` `FileTree` overrides — coordinated refactor

All extend `SourceTask`. Migrate `SourceTask.getSource()` first (see A4), then these flow:

- `platforms/jvm/language-groovy/.../GroovyCompile.java:294`
- `platforms/jvm/language-groovy/.../Groovydoc.java:141`
- `platforms/jvm/language-java/.../JavaCompile.java:126`
- `platforms/jvm/javadoc/.../Javadoc.java:230`
- `platforms/jvm/scala/.../ScalaDoc.java:81`
- `platforms/jvm/scala/.../AbstractScalaCompile.java:233`
- `platforms/jvm/code-quality/.../Pmd.java:176`
- `platforms/jvm/code-quality/.../CodeNarc.java:79`
- `platforms/jvm/antlr/.../AntlrTask.java:290`

### 7.C. Candidates with caveats (~23)

#### C1. Public-API interfaces — bigger blast radius

| File:line | Method | Replacement | Caveat |
|---|---|---|---|
| `platforms/jvm/plugins-java-base/.../JavaPluginExtension.java:47` | `getSourceCompatibility()` | `Property<JavaVersion>` | Touched by every Java DSL build |
| `platforms/jvm/plugins-java-base/.../JavaPluginExtension.java:64` | `getTargetCompatibility()` | `Property<JavaVersion>` | Same |
| `platforms/jvm/plugins-java-base/.../JavaPluginExtension.java:312` | `getAutoTargetJvmDisabled()` | `Property<Boolean>` | Public API |
| `subprojects/core-api/.../file/CopySpec.java:99` | `isCaseSensitive()` | `Property<Boolean>` | Many implementors |
| `subprojects/core-api/.../file/CopySpec.java:114` | `getIncludeEmptyDirs()` | `Property<Boolean>` | Same |
| `subprojects/core-api/.../file/CopySpec.java:134` | `getDuplicatesStrategy()` | `Property<DuplicatesStrategy>` | Same |
| `subprojects/core-api/.../file/CopySpec.java:419` | `getFilteringCharset()` | `Property<String>` | Same |
| `subprojects/core-api/.../util/PatternFilterable.java:75` | `getIncludes()` | `SetProperty<String>` | Widely implemented |
| `subprojects/core-api/.../util/PatternFilterable.java:83` | `getExcludes()` | `SetProperty<String>` | Same |
| `subprojects/core-api/.../VerificationTask.java:37` | `getIgnoreFailures()` | `Property<Boolean>` | Implemented by many tasks |
| `platforms/extensibility/plugin-development/.../GradlePluginDevelopmentExtension.java:155` | `isAutomatedPublishing()` | `Property<Boolean>` | Public extension |
| `subprojects/core/.../SourceTask.java:183` | `getIncludes()` | `SetProperty<String>` | Overrides `PatternFilterable` |
| `subprojects/core/.../SourceTask.java:202` | `getExcludes()` | `SetProperty<String>` | Same |

#### C2. `TextResource`-backed config (internal type, awkward as lazy)

- `platforms/jvm/code-quality/.../Checkstyle.java:76` — `getConfigFile()` (returns `TextResource.asFile()`)
- `platforms/jvm/code-quality/.../Checkstyle.java:183` — `getConfigDirectory()` (related)
- `platforms/jvm/code-quality/.../CheckstyleExtension.java:53,70` — `getConfigFile()`, `getConfig()`
- `platforms/jvm/code-quality/.../CodeNarc.java:69` — `getConfigFile()`

#### C3. `Signature.java` computed metadata (defaults derived from other state)

`platforms/software/signing/.../Signature.java`:
- `:198` — `getToSign()` → `RegularFileProperty`
- `:218` — `getName()` (computed default)
- `:249` — `getExtension()` (depends on signatureType)
- `:276` — `getType()` (depends on toSign + signatureType)
- `:303` — `getClassifier()` (depends on source artifact)
- `:325` — `getDate()` (defaults to file lastModified)
- `:357` — `getFile()` (computed from toSign + signatureType)
- `platforms/software/signing/.../Sign.java:379` — `isRequired()` → `Property<Boolean>`
- `platforms/software/signing/.../SigningExtension.java:544` — `getSignatories()` (extension, many downstream refs)

---

## 8. Recommended Migration Order

Tackle in this order — small, self-contained changes first, then expand outward:

1. **JaCoCo rules bundle** — `JacocoLimit` + `JacocoViolationRule` + `JacocoViolationRulesContainer` (11 props, single PR, internal types only).
2. **Credentials classes** — `AwsCredentials`, `PasswordCredentials`, `HttpHeaderCredentials`, `HttpBuildCacheCredentials` (10 props, all `Property<String>`, identical shape across 4 types).
3. **`AbstractCompile.getSourceCompatibility`/`getTargetCompatibility`** — paired migration; mirrors patterns already done in the branch.
4. **Test/code-quality `ignoreFailures` + `failFast` + `includes`/`excludes`** — `AbstractTestTask`, `AbstractCodeQualityTask`, `Test`.
5. **`SourceTask.getSource()` + 9 overrides** — coordinated refactor; do `SourceTask` first.

**Defer:**
- Group 7.A (blockers: Gradleception failures, signing domain redesign).
- Group 7.C public-API interfaces — easier once internal callers already expect Provider values.
- Group 7.C `Signature` computed metadata — needs careful provider wiring with defaults.
