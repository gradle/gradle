# Lazy Property Migration Report

> Generated: 2026-03-31
> Branch: `gradle10/provider-api-migration`
> Excluded: `signing` tasks and plugins (by decision)

## Executive Summary

| Category | Count |
|---|---|
| Properties still pending migration (`@ToBeReplacedByLazyProperty`) | ~53 methods |
| Properties already migrated (`@ReplacesEagerProperty`) | ~500+ methods |
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

## 7. Biggest Migration Gaps

1. **Core copy infrastructure** (AbstractCopyTask/CopySpec hierarchy) -- 6+ properties, affects Copy and Sync tasks
2. **Credentials** -- 7 properties across 4 interfaces (PasswordCredentials, HttpHeaderCredentials, AwsCredentials, HttpBuildCacheCredentials)
3. **JaCoCo violation rules** -- 11 properties across 3 interfaces
4. **`getSource()` FileTree pattern** -- 10 classes all inheriting from SourceTask
5. **Code quality extensions** -- CheckstyleExtension, CodeNarcExtension (partially blocked by TextResource type and Gradleception failures)
