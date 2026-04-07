# Gradle Upgrade Guide: 8.0 → 9.5.0

> This guide serves as both an AI coding assistant ruleset and a human-readable upgrade reference.
> It covers every version transition from Gradle 8.0 through 9.5.0.
> Sources: Official upgrade guides, release notes, and community feedback from Gradle Slack channels.

## Overview

This document provides comprehensive information for upgrading Gradle builds across all minor versions from 8.0 to 9.5.0. Each section details breaking changes, deprecations, behavioral changes, and new features for each version transition. Use this guide to plan and execute your upgrade strategy.

## General Upgrade Strategy

### Recommended Upgrade Path

1. **Assess Your Current State**
   - Note your current Gradle version
   - Review the upgrade sections between your version and your target version
   - Check if any breaking changes directly impact your build

2. **Upgrade Incrementally**
   - Move through one or two minor versions at a time
   - Run `./gradlew --version` to verify the upgrade
   - Execute your full test suite after each upgrade
   - Test with your CI/CD pipeline

3. **Monitor Deprecation Warnings** ⚠️ _This is the #1 community-reported issue (98K+ forum views)_
   - Run with `gradle help --warning-mode=all` before major version upgrades
   - Or add `org.gradle.warning.mode=all` to your `gradle.properties` permanently
   - Address deprecations proactively (they become breaking changes in future versions)
   - Update plugins and dependencies to versions compatible with your target Gradle version
   - Do NOT skip deprecation warnings across multiple versions — they accumulate and make major upgrades much harder

4. **Handle Plugin Compatibility**
   - Verify that third-party plugins support your target Gradle version
   - Kotlin Gradle Plugin and Android Gradle Plugin are especially important to check
   - Update plugins before upgrading Gradle when possible

5. **Test Thoroughly**
   - Run full build, all tests, and integration tests
   - Test on multiple JVM versions if applicable
   - Verify IDE synchronization (IntelliJ, Android Studio, Visual Studio Code)
   - Test with both local and remote caches if using build cache

6. **Major Version Upgrades**
   - For upgrades like 8.x → 9.0.0, allocate extra time for testing
   - Review the entire major version section carefully
   - Consider temporary workarounds for migration period
   - Plan for plugin author updates if you maintain build plugins

### Pre-Upgrade Checklist

- [ ] Review all breaking changes and deprecations for your version range
- [ ] Update Gradle wrapper to target version in gradle/wrapper/gradle-wrapper.properties
- [ ] Ensure all plugins have compatible versions published
- [ ] Run deprecation warnings check on current version
- [ ] Backup or commit build configuration before upgrading
- [ ] Test upgrade in a feature branch first
- [ ] Allocate time for CI/CD pipeline updates

---

## Upgrade from 8.0 to 8.1

### Breaking Changes

- **Kotlin DSL Compilation Warnings**: Kotlin DSL scripts now emit compilation warnings to console output. If your build consumes or validates console output, these warnings may cause failures. Filter or suppress them appropriately.

- **Kotlin Compiler Options**: Setting `freeCompilerArgs` directly on Kotlin DSL configuration now fails. Use `freeCompilerArgs.addAll()` instead:
  ```kotlin
  // ❌ No longer works
  kotlinDsl {
      freeCompilerArgs = listOf("-Xjsr305=strict")
  }

  // ✅ Use addAll instead
  kotlinDsl {
      freeCompilerArgs.addAll("-Xjsr305=strict")
  }
  ```

- **External Process Configuration Time**: Using unsupported APIs to start external processes at configuration time is no longer allowed when configuration cache is enabled.

- **New API Additions**: New method additions may clash with existing code. Watch for `JavaExec.getJvmArguments()` and `JavaExecSpec.getJvmArguments()` if you have custom implementations.

### Deprecations

- **Core Plugin Configuration**: Mutating `setCanBeConsumed`/`setCanBeResolved` on core plugin configurations is deprecated.
- **Reserved Configuration Names**: "detachedConfiguration" and "detachedConfigurationX" are now reserved names.
- **JavaPluginExtension**: Calling select methods without the java component present is deprecated.
- **WarPlugin**: `WarPlugin#configureConfiguration(ConfigurationContainer)` is deprecated.
- **Test Tasks**: Relying on conventions for custom Test tasks without explicit `testClassesDirs`/`classpath` is deprecated.
- **GMM Modification**: Modifying GMM after a publication has been populated is deprecated.
- **JVM Versions**: Running tests on JVM versions 6 and 7 is deprecated.
- **Kotlin DSL Precompiled Scripts**: Applying scripts published with Gradle < 6.0 is deprecated.
- **Kotlin DSL with KGP**: Applying kotlin-dsl with KGP < 1.8.0 is deprecated.
- **Version Catalog Access**: Accessing libraries/bundles from version catalogs in plugins {} block of Kotlin scripts is deprecated.
- **ValidatePlugins**: Running without Java Toolchain is deprecated.
- **org.gradle.util Members**: WrapUtil, GUtil, ConfigureUtil are deprecated.
- **JvmVendorSpec.IBM_SEMERU**: Use `JvmVendorSpec.IBM` instead.
- **Custom Build Layout**: Setting custom build layout on StartParameter and GradleBuild is deprecated.
- **org.gradle.cache.cleanup**: This property is deprecated.
- **Relative Java Paths**: Using relative paths to specify Java executables is deprecated.
- **Task Convention Access**: Accessing `Task.getConvention()`/`getExtensions()` from task action at execution time is deprecated.
- **Empty Test Execution**: Running test task successfully when no test executed is deprecated.

### Behavioral Changes

- **CACHEDIR.TAG Files**: These are now created in global cache directories for compatibility with backup tools.
- **Configuration Cache Options**: The property `org.gradle.unsafe.configuration-cache` is renamed to `org.gradle.configuration-cache`.
- **Configuration Cache Promotion**: Configuration cache is promoted from experimental to stable.

### Community-Reported Issues (from Gradle Forum)

**compileClasspath Configuration No Longer Accepts Dependencies** (18,715 views)
- Error: "Dependencies can not be declared against the `compileClasspath` configuration"
- The `compileClasspath` is an internal resolved configuration; you can no longer declare dependencies against it directly
- Before (Gradle 7.x):
  ```groovy
  dependencies {
    compileClasspath enforcedPlatform(project(":project-dependencies"))
  }
  ```
- After (Gradle 8.0+):
  ```groovy
  dependencies {
    implementation enforcedPlatform(project(":project-dependencies"))
    // or compileOnly for compile-only dependencies
    compileOnly enforcedPlatform(project(":project-dependencies"))
  }
  ```

**Android Namespace Now Required** (17,570 views)
- Gradle 8.0+ with AGP requires explicit `namespace` in all Android subprojects (including Flutter/React Native plugins)
- Error: "Namespace not specified for plugin 'com.android.library'"
- Fix: add `namespace` to each module's `build.gradle`, or use a root `subprojects` block as a mass workaround

**Empty Test Suite Now Fails** (Community)
- Test tasks now fail if no tests are discovered (previously passed silently)
- Fix: add `failOnNoDiscoveredTests = false` to your test configuration if you have modules with no tests yet

### Notable New Features

- **Configuration Cache is Now Stable**: You can enable it without experimental warnings.
- **Version Catalog IDE Support**: Plugin alias version catalog accessors no longer show false errors in IDEs.

---

## Upgrade from 8.1 to 8.2

### Breaking Changes

- **Kotlin 1.8.20 Upgrade**: Kotlin dependency is upgraded. **OOM Issue Known**: Kotlin 1.8.20 has a known out-of-memory issue with compilation avoidance. Workaround:
  ```properties
  # In gradle.properties
  kotlin.incremental.useClasspathSnapshot=false
  ```

- **Groovy, Ant, Tools Upgrades**: Groovy 3.0.17, Ant 1.10.13, CodeNarc 3.2.0, PMD 6.55.0, JaCoCo 0.8.9. These may introduce subtle behavior changes.

- **Plugin Kotlin DSL Compatibility**: Plugins compiled with Gradle >= 8.2 using Kotlin DSL functions like `the<T>()` and `configure<T>()` cannot run on Gradle <= 6.1. Ensure your plugin's minGradleVersion is set appropriately.

- **Task Configuration Order**: Deferred/avoided configuration of some tasks may change the order in which tasks are configured. If your build depends on specific configuration ordering, verify behavior after upgrade.

### Deprecations

- **Annotation Processor Generated Sources**: CompileOptions methods for generated sources are deprecated.
- **Configuration Usage Warnings**: Using configurations incorrectly now emits runtime warnings. Review your configuration usage patterns.
- **Plugin Conventions**: Access to plugin conventions (Convention API) is deprecated. Use direct configuration instead.
- **Plugin-Specific Conventions**: Base, application, java, war, ear, and project-report plugin conventions are deprecated.
- **Configuration.getAll()**: This method is deprecated.
- **Automatic Test Framework Dependencies**: Relying on automatic implementation dependencies is deprecated.
- **BuildIdentifier API**: `BuildIdentifier.getName()` and `isCurrentBuild()` are deprecated.

### Notable New Features

- **Kotlin DSL Property Assignment**: Property assignment with `=` operator is now available (incubating feature).

---

## Upgrade from 8.2 to 8.3

### Breaking Changes

- **Project.buildDir Deprecation**: The deprecated `Project.buildDir` can cause script compilation failure if warnings-as-errors is enabled. Migrate to `Project.layout.buildDirectory`:
  ```groovy
  // ❌ Deprecated
  println buildDir

  // ✅ Use layout.buildDirectory instead
  println layout.buildDirectory
  ```

- **TestLauncher API**: `TestLauncher` API no longer ignores build failures. Tests now properly fail the build if there are issues.

- **Variant Selection**: Fixed variant selection behavior with `ArtifactView`/`ArtifactCollection`. Attributes are now captured lazily, which may change resolution behavior in edge cases.

- **Kotlin 1.9.0 Upgrade**: Kotlin dependency is upgraded. This drops support for Kotlin language/API level 1.3, which affects plugins targeting Gradle < 7.0.

- **Java Toolchain Finalization**: Eager evaluation of Configuration attributes may finalize the Java toolchain earlier than before.

### Deprecations

- **Project.buildDir**: Use `project.layout.buildDirectory` instead.
- **ClientModule Dependencies**: Use Component Metadata Rules instead.
- **Develocity Plugin Version**: Gradle 9.0 will require Develocity plugin 3.13.1 or later.
- **Invalid URL Decoding**: Deprecated invalid URL decoding behavior.
- **SelfResolvingDependency**: This dependency type is deprecated.

---

## Upgrade from 8.3 to 8.4

### Breaking Changes

- **Kotlin 1.9.10 Upgrade**: Minor Kotlin version bump with potential compatibility impacts.

- **XML Parsing Security**: XML parsing now requires recent parsers with secure parsing enabled. If you use the EAR plugin or custom XML parsing:
  - **AGP Users**: Upgrade to Android Gradle Plugin 8.3.0 or later
  - **EAR Plugin**: Ensure your EAR plugin configuration doesn't rely on external XML entities. These are now forbidden for security reasons.

### Deprecations

- **GenerateMavenPom**: Internal methods of GenerateMavenPom are deprecated.

---

## Upgrade from 8.4 to 8.5

### Breaking Changes

- **Kotlin 1.9.20 Upgrade**: Minor version bump.

- **Groovy Task Conventions**: The groovy-base plugin now configures source/target compatibility. This may affect builds that manually set these properties. Verify your compilation compatibility settings still work as expected.

- **Provider.filter Argument Type**: The argument type changed from `Predicate<T>` to `Spec<T>`:
  ```groovy
  // ❌ Old way (may not work)
  providers.provider { listOf(1, 2, 3) }
    .filter { it > 1 }

  // ✅ New way with Spec
  providers.provider { listOf(1, 2, 3) }
    .filter(new Spec() {
      boolean isSatisfiedBy(Integer element) {
        return element > 1
      }
    })
  ```

### Deprecations

- **org.gradle.util Members**: `VersionNumber.parse()` and `VersionNumber.compareTo()` are deprecated.
- **Resolved Configuration Dependencies**: Depending on resolved configurations is deprecated.
- **Non-Existent Project Directories**: Including projects without an existing directory emits warnings.

### Community-Reported Issues (from Gradle Forum)

**Java 21 Groovy Compilation Error** (13,842 views)
- Error: "Unsupported class file major version 65" when compiling Groovy code with Java 21
- Root cause: Groovy 3.0.17 (bundled in Gradle 8.5) doesn't fully support Java 21 bytecode
- Fix: upgrade Groovy dependency in your build to 3.0.20+, or wait for Gradle 8.6+ which bundles a compatible version
- Primarily affects projects with Groovy source code (not just Groovy DSL build scripts)

**Java 21 Works on Earlier Gradle Versions With Caveats** (5,500 views)
- Some users report Java 21 working with Gradle 8.3/8.4, but official support starts at 8.5
- Running on unsupported combinations may work but can produce subtle bytecode issues
- Always use the officially supported Gradle version for your JDK

### Notable New Features

- **Full Java 21 Support**: Gradle can now run on Java 21 without restrictions.

---

## Upgrade from 8.5 to 8.6

### Breaking Changes

- **JaCoCo 0.8.11 Upgrade**: Minor version bump that may affect code coverage measurements.

- **DependencyAdder Renamed**: `DependencyAdder` is renamed to `DependencyCollector`. Update any custom code referencing this class.

### Deprecations

- **registerFeature with Main Source Set**: Calling `registerFeature()` using the main source set is deprecated. This is a **critical** deprecation that will error in Gradle 9.0. If you use optional dependencies via `registerFeature('optional') { usingSourceSet sourceSets.main }`, you must create a separate source set instead:
  ```kotlin
  // ❌ Deprecated — will error in 9.0
  java {
    registerFeature("optional") {
      usingSourceSet(sourceSets.main)
    }
  }

  // ✅ Create a dedicated source set
  val optional by sourceSets.creating
  java {
    registerFeature("optional") {
      usingSourceSet(optional)
    }
  }
  ```
- **Publishing with Custom Names**: Publishing artifact dependencies with explicit names different from artifactId to Maven is deprecated.
- **ArtifactIdentifier**: This API is deprecated.
- **DependencyCollector Mutation**: Mutating DependencyCollector dependencies after they have been observed is deprecated.

---

## Upgrade from 8.6 to 8.7

### Breaking Changes

- **Kotlin 1.9.22 Upgrade**: Minor version bump.

- **SSH/Git Dependency Updates**:
  - Apache SSHD upgraded to 2.10.0
  - JSch replaced by com.github.mwiede:jsch 0.2.16
  - Eclipse JGit upgraded to 5.13.3
  - **Impact**: SSH operations now use Apache SSHD instead of JSch. If you have custom SSH configurations or rely on specific JSch behaviors, verify they work with the new implementation.

- **Archive Handling**: Apache Commons Compress upgraded to 1.25.0. This may change how archives are read/written, potentially affecting archive checksums.

- **Bytecode Generation**: ASM upgraded to 9.6.

- **Version Catalog Parser**: Upgraded to TOML spec 1.0.0. Ensure your version catalog TOML files are spec-compliant.

### Deprecations

- **Plugin Conventions**: Registration of plugin conventions now emits warnings.
- **Kotlin DSL Task References**: Referencing tasks/domain objects by `"name"()` syntax in Kotlin DSL is deprecated. Use direct references instead.
- **Invalid URL Decoding**: Deprecated invalid URL decoding behavior continues to emit warnings.

### Notable New Features

- **Java 22 Support**: Gradle can now compile, test, and run on Java 22.

---

## Upgrade from 8.7 to 8.8

### Breaking Changes

- **Problems API Changes**: The Problems API has changed. The `label`/`description` approach is replaced with an `id()` method. Update any code using the Problems API.

- **Collection Properties**: Incubating `insert`/`append` methods on collection properties have been removed.

- **Tool Upgrades**: Groovy 3.0.21, ASM 9.7. These are maintenance updates with potential compatibility impacts.

### Deprecations

- **Configuration Mutation After Observation**: Mutating a configuration after it has been observed is deprecated. All changes to observed configurations will become errors in Gradle 9.0.
  ```groovy
  // ❌ Deprecated pattern
  def config = configurations.runtimeClasspath
  config.dependencies.add(dependency)  // Mutation after observation

  // ✅ Mutate before observation
  configurations.runtimeClasspath.dependencies.add(dependency)
  ```

- **Filtered Configuration Methods**: The `file()` and `fileCollection()` methods on filtered configurations are deprecated.
- **Namer Inner Classes**: Inner Namer classes on Task and Configuration are deprecated.
- **Unix Mode Permissions**: Unix mode-based file permissions are deprecated. Use the new FilePermissions API instead.
- **Local Build Cache**: Setting retention period directly on local build cache is deprecated.
- **Kotlin DSL gradle-enterprise Block**: The `gradle-enterprise` plugin block extension in Kotlin DSL is deprecated.

---

## Upgrade from 8.8 to 8.9

### Breaking Changes

- **Toolchain Provisioning**: The toolchain provisioning mechanism now uses a new `.ready` marker file. Existing toolchains will be re-provisioned on first use. This is generally transparent but may take longer on the first build.

- **Kotlin 1.9.23 Upgrade**: Minor version bump.

- **Daemon Log File Encoding**: Daemon log file encoding is now UTF-8 (was dependent on system default). This may affect log file parsing if you relied on specific encoding.

- **Compilation Against Gradle Implementation**: Compiling against Gradle implementation classpath is no longer implicit. If your build code imports Gradle internal classes, you must explicitly declare the dependency.

- **Configuration Cache Packages**: Configuration cache internal packages have moved under `org.gradle.internal`. If you access these internals, update imports.

- **File-System Watching**: File-system watching is disabled on macOS 11 and earlier. This may slightly impact build performance on older macOS versions.

- **Annotation Processor Output**: JDK8-based compiler output with annotation processors may change. Verify that any annotation processor-generated code works correctly.

### Deprecations

None specifically called out beyond those already listed.

### Community-Reported Issues (from Gradle Forum)

**Version Catalogs Don't Work Inside buildSrc Convention Plugins** (7,089 views, 25 posts — most discussed)
- Version catalogs (TOML) work in your main `build.gradle.kts` but NOT inside precompiled script plugins in `buildSrc`
- You can make catalogs available to `buildSrc/build.gradle.kts` by adding a `buildSrc/settings.gradle.kts`:
  ```kotlin
  // buildSrc/settings.gradle.kts
  dependencyResolutionManagement {
    versionCatalogs {
      create("libs") {
        from(files("../gradle/libs.versions.toml"))
      }
    }
  }
  ```
- However, inside convention plugins themselves (e.g., `buildSrc/src/main/.../my-conventions.gradle.kts`), catalog accessors like `libs.commons.text` are NOT available — you must use string-based notation instead
- No official solution exists yet (GitHub #15383)

**Configuration Cache Breaks JGit and Similar Plugins** (2,753 views, 13 posts)
- JGit and other plugins that hold non-serializable state fail with configuration cache enabled
- Workaround: disable configuration cache for affected tasks, or restructure to use `ValueSource` for Git operations
- Consider using `providers.exec()` for Git commands instead of JGit

**Configuration Cache Cannot Be Disabled Per-Task Easily** (1,684 views)
- No clean way to selectively opt tasks out of configuration cache
- Workaround: use `notCompatibleWithConfigurationCache()` in task implementation

---

## Upgrade from 8.9 to 8.10

### Breaking Changes

- **JavaCompile JRE Requirement**: JavaCompile tasks may now fail with JRE (Java Runtime Environment) instead of JDK, even if no compilation is needed. Use a JDK instead of JRE.

- **Tool Upgrades**: Kotlin 1.9.24, Ant 1.10.14, JaCoCo 0.8.12, Groovy 3.0.22. These maintenance updates may have subtle compatibility impacts.

### Deprecations

- **JVM < 17 Deprecated**: Running Gradle on JVM versions older than 17 is deprecated. Gradle 9.0 will require JVM 17+. Plan your infrastructure upgrade accordingly.
- **Non-Consumable Ivy Configurations**: Consuming non-consumable configurations from Ivy is deprecated.
- **Cross-Project Configuration Extension**: Extending configurations in different projects is deprecated.

### Notable New Features

- **Java 23 Support**: Gradle now supports Java 23 for compilation, testing, and running.
- **Configuration Cache Performance**: ~28% speedup on cache hits compared to previous versions.
- **String Deduplication**: String deduplication in configuration cache reduces memory usage.

---

## Upgrade from 8.10 to 8.11

### Breaking Changes

- **Kotlin 2.0.20 Upgrade**: This is a major Kotlin version bump (from 1.9.x to 2.0.x), but Gradle build scripts still use Kotlin language version 1.8 for compatibility. However:
  - If you write plugins or build logic in Kotlin, you may encounter issues from Kotlin 2.0 breaking changes
  - New Kotlin plugins targeting Gradle 8.11+ may require Kotlin 2.0.20
  - Review the Kotlin 2.0 migration guide if you maintain Kotlin-based build plugins

- **Daemon JVM Configuration Type Change**: `UpdateDaemonJvm.jvmVersion` type changed from String to `Property<JavaLanguageVersion>`. Update any code setting this property.

- **Name Matching Behavior**: Numbers are now treated as word boundaries in name matching. This may cause ambiguity or unexpected matches if your build uses numeric components in task/configuration names.

### Deprecations (Extensive List)

- **ForkOptions.javaHome**: Deprecated in 8.11 (but later un-deprecated in 8.14 without full replacement).
- **Buildscript Configuration Mutation**: Mutating buildscript configurations is deprecated.
- **Maven Variant Selection by Configuration Name**: Selecting Maven variants by configuration name is deprecated.
- **Manual Configuration Container Addition**: Manually adding to configuration container is deprecated.
- **ProjectDependency.getDependencyProject()**: This method is deprecated.
- **ResolvedConfiguration File Methods**: `getFiles()` on ResolvedConfiguration and LenientConfiguration is deprecated.
- **AbstractOptions**: This base class is deprecated.
- **Dependency.contentEquals()**: This comparison method is deprecated.
- **Project.exec/javaexec**: These methods are deprecated. Use injected ExecOperation or ProviderFactory.exec instead.
- **Detached Configuration Extension**: Using extendsFrom with detached configurations is deprecated.
- **Gradle.useLogger**: This method is deprecated.
- **Compile Options Setters**: Unnecessary setters on compile options and doc tasks are deprecated.
- **Javadoc Verbosity**: `Javadoc.isVerbose()`/`setVerbose()` are deprecated.
- **Task.getProject() from Task Action**: Calling from within a task action is deprecated.
- **Groovy Space Assignment**: The `space assignment` syntax in Groovy is deprecated.
- **DependencyInsightReportTask.getDependencySpec()**: This method is deprecated.
- **ReportingExtension.baseDir**: This property is deprecated.

---

## Upgrade from 8.11 to 8.12

### Breaking Changes

- **Kotlin 2.0.21 Upgrade**: Minor Kotlin 2.0.x version bump.

- **Ant 1.10.15, Zinc 1.10.4 Upgrades**: Maintenance updates.

- **Swift SDK Discovery**: Swift SDK discovery now passes `--sdk macosx` to xcrun. This may affect builds using the native-swift plugin.

- **Source Level Deprecation of TaskContainer.create**: The `TaskContainer.create()` methods now have source-level deprecation warnings. These may be reported as errors if `warnings-as-errors` is enabled. Update to use the new API:
  ```groovy
  // ❌ Deprecated
  tasks.create('myTask', SomeTask)

  // ✅ Preferred approach
  tasks.register('myTask', SomeTask)
  ```

### Deprecations

- **Ambiguous Artifact Transformation Chains**: Chains with ambiguous artifact transformations are deprecated.
- **init Task**: The `init` task must run alone. Running it alongside other tasks is deprecated.
- **Task.getProject() from Task Action**: No longer requires STABLE_CONFIGURATION_CACHE flag; applies to all builds.
- **Groovy Space-Assignment Syntax**: A sed command is provided for bulk replacement.

### Community-Reported Issues (from Gradle Forum)

**project.exec() Replacement Confusion** (3,196 views + 1,427 views across multiple topics)
- `project.exec {}` at execution time is now deprecated; multiple replacement patterns exist:
  ```kotlin
  // ❌ Deprecated
  tasks.register("myTask") {
    doLast {
      project.exec { commandLine("git", "status") }
    }
  }

  // ✅ Option 1: Use Exec task type
  tasks.register<Exec>("myTask") {
    commandLine("git", "status")
  }

  // ✅ Option 2: Inject ExecOperations (for custom task classes)
  abstract class MyTask : DefaultTask() {
    @get:Inject abstract val execOps: ExecOperations
    @TaskAction fun run() {
      execOps.exec { commandLine("git", "status") }
    }
  }

  // ✅ Option 3: providers.exec() for lazy evaluation
  val gitStatus = providers.exec { commandLine("git", "status") }
    .standardOutput.asText
  ```

**Task.getProject() at Execution Time** (2,522 views)
- Warning: "Invocation of Task.project at execution time has been deprecated"
- Common in tasks that resolve dependencies or access other project files in `doLast`:
  ```groovy
  // ❌ Deprecated — project access in doLast
  tasks.register('copyResources') {
    doLast {
      copy {
        from project(':other').file('src/main/resources/config.json')
        into 'build/deploy'
      }
    }
  }

  // ✅ Resolve at configuration time, use at execution time
  tasks.register('copyResources') {
    def configFile = project(':other').file('src/main/resources/config.json')
    doLast {
      copy {
        from configFile
        into 'build/deploy'
      }
    }
  }
  ```
- Key principle: evaluate all `project` references at configuration time, store results in variables or properties

**Groovy Space-Assignment Migration at Scale** (Community)
- The deprecated `property value` syntax (space-separated assignment) requires bulk migration to `property = value`
- Sed command for bulk fix: `sed -i 's/^\(\s*\)\(archiveBaseName\|archiveVersion\|archiveClassifier\|destinationDir\) /\1\2 = /g' build.gradle`

---

## Upgrade from 8.12 to 8.13

### Breaking Changes

- **JvmTestSuite API Changes**: The `testType` property is removed. Use `testSuiteName` instead:
  ```groovy
  // ❌ No longer available
  testing {
    suites {
      test {
        testType = TestSuiteType.UNIT_TEST
      }
    }
  }

  // ✅ Use testSuiteName
  testing {
    suites {
      test {
        targets.all {
          testTask.configure {
            // configure test task
          }
        }
      }
    }
  }
  ```

- **Test/JaCoCo Plugin Changes**: Test Report Aggregation and JaCoCo Aggregation plugins now produce a single test results variant per suite instead of multiple.

- **Report Configuration**: Replace `testType` with `testSuiteName` on `JacocoCoverageReport` and `AggregateTestReport`.

- **BuildLauncher Fix**: `BuildLauncher.addJvmArguments` no longer overrides `org.gradle.jvmargs`. If you relied on this behavior, use explicit property setting instead.

- **ASM 9.7.1 Upgrade**: Maintenance update.

- **Source Level Deprecation of Project.task**: The `Project.task()` methods now have source-level deprecation. Use `Project.tasks.register()` or `Project.tasks.create()` instead.

### Deprecations

- **AttributeContainer Recursive Querying**: Recursively querying AttributeContainer in lazy provider is deprecated.
- **VariantTransformConfigurationException**: This exception type is deprecated.
- **UpdateDaemonJvm Properties**: `jvmVersion`/`jvmVendor` properties are deprecated. Use `languageVersion`/`vendor` instead.
- **Boolean Properties with is-Prefix**: Boolean properties with `is` prefix and Boolean type now emit deprecation warnings (Groovy 4 alignment).

---

## Upgrade from 8.13 to 8.14

### Breaking Changes

- **Gradle Wrapper Format**: The Gradle Wrapper is now an executable JAR with Main-Class attribute. This should be transparent but may affect custom wrapper handling.

- **Settings.getDefaults() Removed**: This method is completely removed. Use `Settings.defaults(Action)` instead:
  ```groovy
  // ❌ Removed
  settings.defaults { ... }

  // ✅ Use this pattern
  settings.defaults(it -> {
    // configuration
  })
  ```

- **Guava 33.4.6 Upgrade**: Maintenance update.

- **Tool Upgrades**: Groovy 3.0.24, JaCoCo 0.8.13, SLF4J 2.0.17. These are maintenance updates.

- **EclipseClasspath Configuration**: `EclipseClasspath.baseSourceOutputDir` is now a `DirectoryProperty` instead of a simple property.

- **JavaExec Toolchain Default**: JavaExec now uses the toolchain from the java extension by default instead of the current JVM. Explicitly set toolchain if you need different behavior.

### Deprecations

- **Null Attribute Keys**: Looking up attributes using null keys is deprecated.
- **Groovy String-to-Enum Coercion**: Coercion of Groovy strings to enum Property types is deprecated.
- **Groovydoc.getAntGroovydoc()**: This method is deprecated.
- **GradlePluginDevelopmentExtension**: The constructor and `pluginSourceSet` are deprecated.
- **IdeaModule Directories**: `testResourcesDirs`/`testSourcesDirs` now emit warnings. Use `testSourceDirectories`/`testResourceDirectories` instead.
- **StartParameter Configuration Cache**: `isConfigurationCacheRequested()` now emits warnings.
- **ForkOptions.getJavaHome/setJavaHome**: These were un-deprecated (reversing earlier 8.11 deprecation) as no stable replacement exists yet.

### Community-Reported Issues

**Incremental Compilation Performance Variance** (Community)
- Measured on Gradle 8.14.1: incremental compile can be dramatically slower than full compile in some configurations
- Example: 25.86s incremental vs 1.85s non-incremental for a 53-file single-module project on GitHub Actions
- Profile your build with Gradle Profiler before and after upgrading to establish baselines

**Configuration Cache Size Growth** (Community)
- Configuration cache can grow very large over time and does not automatically prune old entries
- Manual deletion of `.gradle/configuration-cache` may be required periodically
- No auto-cleanup mechanism exists yet; plan for periodic maintenance in CI environments

**Version Catalog Extension** (Community)
- Version catalogs cannot be extended or amended after initial declaration
- If you need to add versions dynamically, use a settings plugin or a custom dependency resolution strategy instead

**8.14 as the Last Stop Before 9.0** (General)
- 8.14 is widely regarded as the best "staging" version before the 9.0 jump
- Run `gradle help --warning-mode=all` extensively here to catch every deprecation
- Many teams use 8.14 as a long-term stable version while waiting for plugin ecosystem to catch up with 9.0

---

## Upgrade from 8.14 to 9.0.0 — MAJOR VERSION UPGRADE

### ⚠️ CRITICAL: This is a major version upgrade with extensive breaking changes

This is the largest upgrade in this guide. Plan accordingly, allocate extra testing time, and consider rolling out to your organization carefully.

### Minimum Requirements

- **JVM 17+ Required**: Gradle daemon now requires Java 17 or later (up from Java 8). The Wrapper and launcher can still invoke on Java 8, but the daemon running your build needs JVM 17+.
  - **Tooling API and TestKit**: Remain compatible with Java 8 for applications using these APIs
  - **Infrastructure Impact**: Update CI/CD agents, developer machines, and build servers to have Java 17+ available
  - **Temporary Workaround**: Set `org.gradle.java.home` to point to a JVM 17+ installation if your default is older

- **Minimum Plugin Versions**:
  - Kotlin Gradle Plugin: 2.0.0 (was 1.6.10)
  - Android Gradle Plugin: 8.4.0 (was 7.3.0)
  - Gradle Enterprise/Develocity Plugin: 3.13.1 (was 3.0)

### Language and Runtime Updates

#### Kotlin 2.2.0 (was 2.0.21 in 8.12)

Gradle embeds Kotlin 2.2.0. **Build scripts now use Kotlin language version 2.2** (was 1.8 in 8.x).

**Breaking Changes from Kotlin 2.0, 2.1, and 2.2**:

- **Script Labels Removed**: Script labels like `this@Build_gradle` no longer work. Use `project`, `settings`, or `gradle` instead:
  ```kotlin
  // ❌ No longer works
  this@Build_gradle.version = "1.0"

  // ✅ Use direct reference
  project.version = "1.0"
  // or
  version = "1.0"
  ```

- **Kotlin 1.4-1.7 Dropped**: These language versions are no longer supported for build logic. If you have plugins targeting these versions, they must be updated.

- **Kotlin 2.0 Compatibility Guide**: Review https://kotlinlang.org/docs/releases.html#release-details for all breaking changes in Kotlin 2.0, 2.1, and 2.2.

#### Groovy 4.0.27 (was 3.0.24)

Groovy 4.0 is a major version with significant breaking changes:

- **Legacy Packages Removed/Renamed**: Groovy modules were restructured for Java Platform Module System (JPMS) compliance. If you import from groovy.util or other legacy packages, update imports:
  ```groovy
  // Old Groovy 3.x
  import groovy.util.Eval

  // Groovy 4.0 - check Groovy migration guide for new location
  ```

- **is-Prefixed Boolean Properties**: The `is` prefix is no longer automatically recognized by Groovy 4 for property access. Update code:
  ```groovy
  // ❌ May not work in Groovy 4
  if (task.isEnabled) { }

  // ✅ Use explicit getter
  if (task.getEnabled()) { }
  // or property without 'is' prefix if it exists
  if (task.enabled) { }
  ```

- **DELEGATE_FIRST Closures**: Closures with `@Delegate(strategy=Closure.DELEGATE_FIRST)` now prefer the delegate for dynamic lookups, which may break code expecting owner precedence.

- **Private Property/Method Access**: Private properties and methods may become inaccessible in closures due to Groovy bugs. Use public accessors instead.

- **Super Method Access**: Super methods may be inaccessible when calling from Groovy 3.x compiled code in a Groovy 4.x runtime. Use explicit delegation if needed.

#### JSpecify Nullability Annotations (Replaces JSR-305)

Gradle 9.0 uses JSpecify nullability annotations. Combined with Kotlin 2.2, this is stricter:

- **Unbounded Generics**: Must be qualified with bounds. For example:
  ```kotlin
  // ❌ In Kotlin 2.2 with JSpecify
  val provider: Provider<T>  // T is unbounded

  // ✅ Qualify with bound
  val provider: Provider<T : Any>
  ```

- **Non-Nullable Type Parameters**: `T` in `Property<T>` is not nullable by default. Cannot use `Property<String?>`:
  ```kotlin
  // ❌ Cannot use nullable
  val prop: Property<String?> = objects.property(String::class.java)

  // ✅ Use non-nullable
  val prop: Property<String> = objects.property(String::class.java)
  // For nullable values, use Provider and handle separately
  ```

- **Map Value Nullability**: Maps with nullable values cannot be passed where `Map<String, *>` is expected. Use explicit types.

### Removed APIs (All 8.x deprecations are now errors)

Before upgrading, run `gradle help --warning-mode=all` on Gradle 8.14 to identify all deprecated APIs in your build.

**Major API Removals**:

- **Convention API (Complete Removal)**:
  - `Project.getConvention()`
  - `Task.getConvention()`
  - `Convention` class
  - All convention plugin classes (WarPluginConvention, BasePluginConvention, EarPluginConvention, ApplicationPluginConvention, JavaPluginConvention, GroovyPluginConvention, etc.)

  Migration strategy: Use direct extension/property access or explicit configuration:
  ```groovy
  // ❌ Old Convention API (9.0: removed)
  java {
    sourceCompatibility = JavaVersion.VERSION_11
  }
  convention {
    // convention properties no longer available
  }

  // ✅ Use direct configuration
  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  ```

- **jcenter() Repository**: Removed. Use `mavenCentral()`, `gradlePluginPortal()`, or other repositories instead.

- **JvmVendorSpec.IBM_SEMERU**: Use `JvmVendorSpec.IBM` instead.

- **GroovySourceSet and ScalaSourceSet**: These interfaces are removed.

- **Custom Build Layout Options**: CLI options `-c`/`--settings-file` and `-b`/`--build-file` are removed. Use environment variables or configuration instead:
  ```bash
  # ❌ No longer works
  gradle -c /custom/settings.gradle build

  # ✅ Use environment variable
  GRADLE_USER_HOME=/custom gradle build
  ```

- **Project.exec/javaexec()**: Use injected `ExecOperation` or `ProviderFactory.exec()` instead:
  ```groovy
  // ❌ Removed
  exec {
    commandLine 'echo', 'hello'
  }

  // ✅ Use injected operation
  @Inject
  abstract ExecOperations getExecOperations()

  doSomething {
    execOperations.exec {
      commandLine 'echo', 'hello'
    }
  }
  ```

- **Unix Mode File Permissions**: Removed. Use `FilePermissions` API:
  ```kotlin
  // ❌ Removed (mode bits)
  file.setMode(0644)

  // ✅ Use FilePermissions API
  file.setPermissions(owner.read.write, group.read, other.read)
  ```

- **org.gradle.util Members**: All of these are removed:
  - `CollectionUtils`
  - `ConfigureUtil` — Plugin authors: replace with `Project.configure()` or the `Action` API:
    ```java
    // ❌ Removed in 9.0
    public void xyz(Closure<MyXyz> closure) {
      this.xyz = ConfigureUtil.configure(closure, new MyXyz());
    }

    // ✅ Use Project.configure() for Closure support
    public void xyz(Closure<MyXyz> closure) {
      this.xyz = new MyXyz();
      getProject().configure(this.xyz, closure);
    }

    // ✅ Also add Action-based overload (recommended)
    public void xyz(Action<? super MyXyz> action) {
      this.xyz = new MyXyz();
      action.execute(this.xyz);
    }
    ```
  - `ClosureBackedAction` — Also removed; use `Action<T>` directly

- **Kotlin DSL**: Several removals:
  - Task reference syntax `"name"()` no longer works
  - Cannot access libraries/bundles from version catalogs in `plugins {}` block
  - Eager artifact configuration accessors removed

- **WriteProperties.outputFile**: Removed. Use `destinationFile` instead.

- **org.gradle.cache.cleanup**: Property removed.

- **buildCache.local.removeUnusedEntriesAfterDays**: Removed.

- **kotlinDslPluginOptions.jvmTarget**: Removed.

- **gradle-enterprise Kotlin DSL Plugin Block**: The `gradle-enterprise` block extension is removed. Use the full plugin block.

- **ArtifactIdentifier**: Removed.

- **IdeaModule.testSourceDirs/testResourceDirs**: Removed. Use test directories properties instead.

- **Selected Groovy Modules**: These modules are removed from the distribution:
  - groovy-test
  - groovy-console
  - groovy-sql

- **Many Other Deprecated APIs**: Run `gradle help --warning-mode=all` on 8.14 first to find all.

### Task Changes

- **ValidatePlugins**: Now requires the Java Toolchains plugin to function. Apply the plugin if you use ValidatePlugins.

- **Archive Task Reproducibility**: Archive tasks (Jar, War, Zip, Ear, Tar) now produce reproducible archives by default:
  - Files are ordered deterministically
  - Timestamps are fixed (preserveFileTimestamps = false by default)
  - File permissions are normalized
  - If you need non-deterministic output, explicitly set `reproducibleFileOrder = false`

- **Test Tasks Without Classpath**: Test tasks without explicit `classpath` and `testClassesDirs` silently stop executing tests. They don't fail; they just run no tests. If you have custom Test task configurations, verify they have explicit classpath.

- **Test Failure on No Tests**: If a test task has test sources present but no tests match filters, the task now fails instead of succeeding silently:
  ```gradle
  // If this test task finds matching source but no tests pass filters
  test {
    filter {
      includeTestsMatching '*Specific'
    }
  }
  // Will fail if no tests match, instead of succeeding with 0 tests run
  ```

- **Stale Outputs**: Gradle no longer automatically deletes stale outputs located outside the build directory. Clean up manually if needed.

- **Model/Component Tasks**: The `model` and `component` tasks are only added when the rule-based plugin is applied. These are rarely used; if you depend on them, apply the rule-based model plugin.

- **Ear/War Plugin**: These plugins now build ALL artifacts during `assemble`, not just the main one. This may change what gets built.

- **Implicit Artifact Building**: Gradle no longer implicitly builds artifacts from visible configurations during `assemble`. Configure explicitly which artifacts should be built.

### Configuration Cache Changes

- **Build Event Listeners**: Unsupported build event listeners now cause configuration cache problems instead of being silently skipped.

- **Incompatible Task Cache Handling**: Cache entry is always discarded for incompatible tasks in warning mode.

### API Changes

- **Final Methods**: Methods on public API types are now final (AndSpec.and, GenerateBuildDashboard.aggregate).

- **Injection Getters**: Classes extending Gradle types must implement injection getters as abstract methods.

- **ConfigurationVariant.getDescription**: Now a `Property<String>` instead of a simple method.

- **RootComponentIdentifier**: New subtype introduced. Update dependency component processing if needed.

- **Artifact Signing**: Now matches OpenPGP key version (RFC 9580 compliance). Signing behavior may change.

- **C++/Swift Plugins**: No longer depend on software model. Configure toolChains at the top level:
  ```kotlin
  // ❌ Was via software model
  model {
    toolChains {
      clang(Clang) { }
    }
  }

  // ✅ Configure at top level
  toolChains {
    withType<Clang>().configureEach {
      // configure Clang
    }
  }
  ```

- **Scala Plugins**: No longer create unresolvable configurations.

### Settings Changes

- **Project Directories**: Project directories must exist and be writable.

### Updated Default Tool Versions

- **Checkstyle**: 10.24.0 (from prior version)
- **CodeNarc**: 3.6.0
- **PMD**: 7.13.0
- **JUnit Jupiter**: 5.12.2
- **TestNG**: 7.11.0
- **Spock**: 2.3
- **Eclipse JGit**: 7.2.1

### Plugin Compatibility and Runtime Requirements

- **Plugins Built with Kotlin DSL on Gradle 9.x**: Require Gradle >= 8.11 to run
- **Plugins Built with Groovy DSL on Gradle 9.x**: Require Gradle >= 7.0 to run
- **Kotlin Gradle Plugin**: Minimum 2.0.0
- **Android Gradle Plugin**: Minimum 8.4.0

### Community-Reported Issues (from Slack and Gradle Forum)

The following real-world issues were reported during Gradle 9.0 upgrades:

**Convention API Removal** (Community — widely reported)
- This is the most impactful breaking change
- Affects both build configuration and plugin code
- Many plugins still rely on conventions; wait for plugin updates
- Consider forking plugins if they cannot be updated

**Kotlin 2.2 and JSpecify Nullability** (Community)
- Causes widespread compilation failures
- Unbounded generics and nullable types are stricter
- Requires updating build logic and plugins to use proper bounds
- May require dependency updates

**Groovy 4.0 Compatibility** (Community)
- Private property access in closures breaks
- Boolean property access patterns change
- Legacy package imports fail
- Affects many existing build.gradle files

**Incremental Compilation Performance** (Community)
- Some regressions reported with JDK 21
- Consider using JDK 20 if performance is critical
- Monitor daemon memory usage

**Configuration Cache with Programmatic API** (Community)
- No Settings plugin API like buildCache has
- Programmatic configuration cache setup limited
- Consider waiting for 9.1+ if you use advanced configuration cache features

**Plugin Updates Lag** (Community)
- Many plugins don't have compatible versions immediately after 9.0 release
- Consider staging upgrades to plugins first
- Test with plugin snapshots if available

**Build Script Classpath Leak** (GitHub #37256)
- In some cases, gradle-api-9.0.0.jar leaks into compilation classpath
- May cause conflicts with bundled dependencies
- Monitor for unusual compilation failures

**Configuration Cache Changes Breaking Included Builds** (Community)
- Configuration cache changes in Gradle 9 prevent using outputs from included builds during configuration of the root build
- Builds that previously used included build outputs for dependency configuration may break
- Error messages reference cases that "shouldn't have worked" in prior versions
- Workaround: restructure builds to avoid cross-build configuration-time dependencies

**Remote Debugging Broken in Gradle 9.x** (Community)
- Setting `-Dorg.gradle.debug=true` via Tooling API `setJvmArguments()` no longer pauses the daemon for debugger attachment
- Previously worked in Gradle 8.x; broken as of 9.x
- Affects developers debugging custom model builder plugins
- Workaround: investigate alternative debug connection methods or use `--debug-jvm` flag

**Kotlin DSL Migration Struggles** (Community)
- Teams migrating from Groovy to KTS hit issues with `ext` properties on `ProductFlavor`
- `ext.envApiKey` on product flavors doesn't work in KTS; use `extra` properties or a map-based approach instead
- Pattern: iterate over flavors and access custom properties via `extra.get("key")` or use a separate data structure

**ServiceReference as Task Input** (Community)
- Using `@ServiceReference` as a task `@Input` is problematic
- Service references aren't designed to be inputs directly; extract the data from the service into a separate `@Input` property instead

**Test Worker Heap Space vs Daemon Heap** (Community)
- `org.gradle.jvmargs` only configures the Gradle daemon heap, not test worker processes
- Test workers (GradleWorkerMain) need separate configuration:
  ```groovy
  tasks.withType(Test).configureEach {
      maxHeapSize = "4g"
  }
  ```

**JVM Args Compatibility Across Versions** (Community)
- Teams want to use newer JVM flags (e.g., `-XX:+UseCompactObjectHeaders` from JDK 25) in `gradle.properties` without breaking builds on older JDKs
- No built-in way to conditionally apply JVM args based on JVM version
- Workaround: use init scripts or environment-specific properties files

**manifest {} Convention Removed** (Forum, 1,218 views)
- Top-level `manifest { }` blocks no longer work; the convention has been removed
- Before:
  ```groovy
  ext {
    sharedManifest = manifest {
      attributes('Built-By': System.properties['user.name'])
    }
  }
  ```
- After:
  ```groovy
  java.manifest {
    attributes('Built-By': System.properties['user.name'])
  }
  ```

**registerFeature() with Main Source Set Now Errors** (Forum, 1,283 views)
- This was deprecated in 8.6 and now errors in 9.0
- Using `registerFeature('optional') { usingSourceSet sourceSets.main }` for optional dependencies fails
- You must create a separate source set (see 8.5→8.6 section above for migration pattern)

**Task.getProject() in Custom Download/Resolution Tasks** (Forum)
- Accessing `getProject().getDependencies()` or `getProject().getConfigurations()` in `@TaskAction` methods now errors
- Solution: wrap dependency resolution in `@InputFiles` with a `Provider<FileCollection>`:
  ```java
  // ❌ Errors in 9.0
  @TaskAction void action() {
    var config = getProject().getConfigurations()
      .detachedConfiguration(getProject().getDependencies()
        .create("com.example:artifact:" + version));
    config.resolve();
  }

  // ✅ Move to lazy provider
  @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
  protected Provider<FileCollection> getArtifact() {
    return getVersion().map(v ->
      getProject().getConfigurations().detachedConfiguration(
        getProject().getDependencies().create("com.example:artifact:" + v)
      ));
  }
  ```

**"Deprecated Gradle features" Message Without Details** (Forum, 98,937 views — #1 topic)
- The most common error message across all Gradle upgrades
- Users see "Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0" but no specifics
- Fix: always run `gradle help --warning-mode=all` to see the actual deprecation warnings
- Can also add `org.gradle.warning.mode=all` to `gradle.properties` for persistent visibility
- Many users hit this because they ignored 8.x deprecation warnings for multiple versions

---

## Upgrade from 9.0.0 to 9.1.0

### Breaking Changes

- **ASM 9.8 Upgrade**: Minor update.

- **Groovy 4.0.28 Upgrade**: Minor patch in Groovy 4.0 series.

- **Multi-String Dependency Notation**: The notation "group:name:version:extension:classifier" (multiple colons) is deprecated and will be removed. Use single-string notation:
  ```groovy
  // ❌ Multi-string format deprecated
  dependencies {
    implementation 'org.example:mylib:1.0:jar:all'
  }

  // ✅ Use single-string format
  dependencies {
    implementation 'org.example:mylib:1.0'
  }
  ```

### Deprecations

- **ReportingExtension.file(String)**: This method is deprecated.
- **ReportingExtension.getApiDocTitle()**: This method is deprecated.
- **JavaForkOptions.setAllJvmArgs()**: This method is deprecated. Use individual argument setting instead.
- **archives Configuration**: The `archives` configuration is deprecated.
- **Configuration.visible Property**: This property is deprecated and no longer has any effect in 9.0.
- **Non-String projectProperties in GradleBuild**: Passing non-string values as project properties in GradleBuild task is deprecated.
- **Project Properties for Toolchain**: Using project properties (-P) to configure toolchain is deprecated. Use system properties (-D) instead.

---

## Upgrade from 9.1.0 to 9.2.0

### Breaking Changes

- **Kotlin 2.2.20 Upgrade**: Patch update in Kotlin 2.2 series.

- **ObjectFactory.dependencyCollector() Removed**: The incubating `ObjectFactory#dependencyCollector()` method is removed.

- **Lazy Initialization of Consumable Configurations**: Consumable configurations in bundled plugins are now initialized lazily. This means that `configure` actions may not run at configuration time, only when the configuration is accessed. If your build depends on immediate configuration, declare explicit dependencies or access the configuration explicitly.

- **ValidatePlugins Java Requirements**: ValidatePlugins now has stricter Java version requirements. Ensure your toolchain is properly configured.

### Deprecations

- **Project.container() Methods**: These methods are deprecated. Use managed properties or `ObjectFactory.domainObjectContainer()` instead:
  ```kotlin
  // ❌ Deprecated
  val myContainers = project.container(MyDomainObject::class.java)

  // ✅ Use ObjectFactory
  val myContainers = objects.domainObjectContainer(MyDomainObject::class.java)
  ```

- **RuleSource-Based Dependency Management**: Rule-based dependency management APIs are deprecated.

- **registerFeature Without Java Plugin**: Calling `registerFeature()` without the Java plugin applied is deprecated.

### Notable New Features

- **Build Performance**:
  - 40% shorter time to first task execution in large builds
  - 7-12% less memory usage overall

- **Daemon Toolchain Feature**: Now stable. You can reliably use the toolchain configuration to manage compiler and tool versions.

- **Windows ARM Support**: Native support for Windows ARM (ARM64) architecture.

---

## Upgrade from 9.2.0 to 9.3.0

### Breaking Changes

- **Project Referential Equality**: Project instances no longer guarantee referential equality. Do not use `project1 == project2` for equality checks. Use `project1.equals(project2)` or compare project paths:
  ```kotlin
  // ❌ No longer reliable
  if (project1 === project2) { }

  // ✅ Use equals or path comparison
  if (project1 == project2) { }
  if (project1.path == project2.path) { }
  ```

- **TestNG Output**: TestNG output may change when using versions before 6.9.13.3. Upgrade TestNG if you see unexpected output format changes.

- **Tool Upgrades**: Kotlin 2.2.21, Jansi 2.4.2, ASM 9.9, Groovy 4.0.29, JaCoCo 0.8.14. These may introduce subtle behavior changes.

### Deprecations

- **Wrapper.getAvailableDistributionTypes()**: This method is deprecated.
- **Publishing Dependencies on Unpublished Projects**: This pattern is deprecated. Ensure all dependencies are published or local before publishing dependent projects.
- **Legacy Usage Attribute Values**: The legacy usage attribute values are deprecated. Use the new values.
- **Module Coordinates to Depend on Current Project**: Using "group:name:version" notation to depend on the current project is deprecated.
- **ModuleVersionSelector to ModuleComponentSelector**: This conversion is deprecated.
- **DomainObjectCollection.findAll(Closure)**: Use the Spec-based variant instead.
- **Test Task Methods Taking Closure**: Use action-based APIs instead of Closure-based ones.
- **apply false in Precompiled Script Plugins**: The `apply false` pattern in precompiled scripts is deprecated.
- **version in Precompiled Settings Script Plugins**: Specifying version in precompiled settings scripts is deprecated.
- **Dependencies.getProject()**: This method is deprecated. Use project references directly instead.

---

## Upgrade from 9.3.0 to 9.4.0

### Breaking Changes

- **ProjectBuilder Layout Consistency**: ProjectBuilder now enforces that `layout.settingsDirectory` matches `projectDir`. Builds using custom settings directories with ProjectBuilder will need adjustment.

- **java-gradle-plugin Scope Change**: The `java-gradle-plugin` plugin now adds `gradleApi()` to the `compileOnlyApi` scope instead of `api`. This prevents leaking Gradle API to consumers:
  ```gradle
  // In Gradle 9.4, gradleApi() is no longer exposed to plugins using your plugin
  // If consumers need gradleApi(), they must declare it themselves
  ```

- **Stricter Plugin Validation**: Automatic plugin validation is stricter. Plugins are validated for correct structure and dependencies. This may cause build failures for malformed plugins.

- **CodeNarc Compilation Classpath**: CodeNarc compilation classpath is now set by default, which introduces a task dependency on compile. If you have custom CodeNarc tasks, ensure they don't create circular dependencies.

- **System Property Priority for Wrapper**: System property precedence for Wrapper execution changed to: CLI arguments > User Home > Project. If you relied on specific precedence, adjust your configuration.

- **Kotlin 2.3.0 Upgrade**: Major Kotlin version bump (from 2.2.x to 2.3.x). This may introduce breaking changes. Review Kotlin 2.3 migration guide.

- **Zinc 1.12.0 Upgrade**: Scala compilation upgrade.

### Deprecations

- **CreateStartScripts Exit Environment Variable**: The exit environment variable in CreateStartScripts is deprecated.

### Community-Reported Issues (from Slack channels)

**Serialization and Isolation Errors in TestKit** (Community, GitHub #37256)
- TestKit serialization fails with certain build logic
- Use workarounds like avoiding inline classes or using explicit serialization

**Build Cache Communication Stalling** (Community)
- Build cache operations may stall in certain network conditions
- Some teams downgraded back to 9.3.1
- Monitor network connectivity; consider timeout settings

**Artifact Cache Filtering Regression** (introduced in final, not in RC)
- Artifact cache filtering may fail silently
- Verify artifact resolution behavior in your build

**Plugin Classpath Leak**: gradle-api-9.4.0.jar may leak into compilation classpath
- Monitor for unexpected dependencies in compiled output
- Use explicit classpath filters if needed

**Kotlin Accessor Build Cache Overhead** (Community)
- Kotlin accessor generation has 2-3x overhead vs. local compilation
- Consider disabling build cache for certain modules if needed

**IDE Sync Performance** (Community)
- IDE synchronization is slower with build logic ABI changes
- Wait for IDE updates or use --parallel with caution

**Incremental Compilation Regression** (Community)
- 40-second regression in incremental builds observed when comparing Gradle 8.x to 9.x with JDK 21
- Root cause traced to AGP-specific transformations
- Use Gradle Profiler to measure before/after impact in your build

**Foojay Toolchains Plugin Compatibility** (Gradle team)
- Foojay Toolchains plugin needed Kotlin configuration workarounds for 9.4.0
- Plugin authors supporting multiple Gradle versions (7.6 through 9.4.0+) face increasing compatibility burden
- Team considering rewriting the plugin in Java to avoid Kotlin version alignment issues

**IDE Sync buildSrc Validation Failures** (Community)
- IDE sync fails completely if buildSrc has formatting issues (e.g., Spring JavaFormat plugin)
- Workaround: check `idea.sync.active` system property and skip validation during sync
- Affects teams using code formatting plugins in buildSrc

**Develocity/TeamCity Integration Issues** (Community)
- Develocity plugin doesn't work on multi-node TeamCity installations
- IDE starter may fail to download Android Studio due to URL schema changes
- Check for plugin overhead on heavily-loaded CI servers

### Notable New Features

- **Java 26 Support**: Gradle now supports Java 26 for daemon and toolchains.
- **Native Terminal Integration**: OSC 9;4 escape sequences for native terminal support on macOS/Linux.

---

## Upgrade from 9.4.0 to 9.5.0

### Breaking Changes

- **Kotlin 2.3.20 Upgrade**: Patch update in Kotlin 2.3 series.

- **Precompiled Settings Plugin Validation**: Precompiled settings plugins are now validated at compile time. Invalid plugin requests fail the build instead of failing at runtime:
  ```kotlin
  // ❌ Invalid plugin reference now fails at compile
  plugins {
    id("non.existent.plugin") version "1.0"
  }

  // ✅ Only reference valid plugins
  plugins {
    id("valid.published.plugin") version "1.0"
  }
  ```

- **Windows Start Script Rework**: The Windows start script (.bat file) has been significantly refactored:
  - OS variable check removed (no longer checks if running on Windows)
  - `endlocal` is called before app invocation
  - `& CALL` now suppresses the "Terminate batch job?" prompt
  - `exit /b` replaced with `"%COMSPEC%" /c exit`
  - **Migration Note**: If you upgraded the wrapper BEFORE upgrading to 8.14 or 9.0, you may see one-time errors. Upgrade to 8.14 or 9.0 first, then to 9.5.

  ```batch
  REM Before 9.5.0
  exit /b %ERRORLEVEL%

  REM After 9.5.0
  "%COMSPEC%" /c exit /b %ERRORLEVEL%
  ```

- **Environment/Properties Tracking for Configuration Cache**: Calls to `System.getenv()` and `System.getProperties()` with `.values()` are now tracked as Configuration Cache input. This may cause cache invalidation if environment changes.

- **Dependency Lockfile Line Endings**: Dependency lockfiles now consistently use Unix line endings (LF) regardless of platform. If you version control lockfiles, expect line ending changes.

- **Dependency Verification**: Armored key rings now correctly render non-ASCII characters.

- **outgoingVariants/resolvableConfigurations Reports**: These reports now hide attributeless variants by default. Use `--all` flag to show all variants:
  ```bash
  # Before 9.5.0
  gradle outgoingVariants  # showed all variants

  # After 9.5.0
  gradle outgoingVariants  # hides attributeless variants
  gradle outgoingVariants --all  # shows all variants
  ```

### Deprecations

- **CreateStartScripts.exitEnvironmentVar**: Now a complete no-op. Remove if used.
- **DomainObjectCollection.findAll(Closure)**: Use Spec-based variant.
- **Test Task Closure Methods**: Use action-based APIs.
- **apply false in Precompiled Script Plugins**: Use proper plugin declarations.
- **version in Precompiled Settings Script Plugins**: Remove version declarations.
- **Dependencies.getProject()**: Use direct project references.

### Community-Reported Issues (from Slack channels)

**Kotlin DSL Plugin Version Constraints** (Kotlin team, Gradle team)
- Strict Kotlin Gradle Plugin version requirements introduced
- Plugins must explicitly declare compatible KGP versions
- May affect plugin compatibility with older projects

**Transform Identification Event Emission Changes**
- Event emission timing or content may have changed
- Build cache behavior may be affected
- Monitor for unexpected cache invalidations

**Test Report Path Generation Changed** (Community, GitHub #37256)
- Test report paths now use random hash instead of class name
- May affect CI/CD test reporting and parsing
- Update test report parsing logic if you have custom tooling

**buildSrcKotlinDSL Test Failures**
- Tests in buildSrc using Kotlin DSL may fail with KGP constraints
- Ensure buildSrc gradle.properties matches main build

**Shadow Plugin Compatibility** (Gradle team)
- Shadow plugin may break with strict KGP version constraint introduced in 9.5.0
- kotlin-dsl-plugins enforces `strictly 2.3.10` on kotlin-stdlib
- Plugin authors must explicitly manage Kotlin version alignment

**Kotlin 2.4.0/2.5.0 Gradle Version Requirements** (Community)
- Kotlin 2.4.0 deprecates support for Gradle 7.6–8.13
- Kotlin 2.5.0 will drop support entirely; minimum becomes Gradle 8.14.x
- Users on Gradle 8.0–8.13 must upgrade to 8.14.x+ before adopting Kotlin 2.5.0
- Plan Gradle and Kotlin upgrades together to avoid version matrix conflicts

**Native Build Tools and noexec Home Directories** (Community)
- Gradle native build tools fail when home directories have `noexec` mount policy
- `.gradle/native` directory needs exec permissions
- Common in enterprise environments with security policies
- Symptoms: breakpoints don't get hit when debugging IntelliJ projects

### Notable New Features

- **Type-Safe Kotlin Accessors for Precompiled Settings Plugins**: Full IDE support and type safety for settings plugin configuration.
- **Automatic Wrapper Download Retry**: Gradle Wrapper now retries failed downloads automatically.
- **Lock Domain Object Collections**: Collections can now be locked to prevent further modifications.
- **Grouped Help Output**: `gradle help` output is now grouped for better readability.

---

## Quick Reference

### Kotlin and Groovy Versions by Gradle Version

| Gradle Version | Embedded Kotlin | Embedded Groovy | Min Java (Gradle) | Min Java (Toolchain) |
|----------------|-----------------|-----------------|-------------------|----------------------|
| 8.0            | 1.8.10          | 3.0.13          | 8                 | 6                    |
| 8.1            | 1.8.10          | 3.0.13          | 8                 | 6                    |
| 8.2            | 1.8.20          | 3.0.17          | 8                 | 6                    |
| 8.3            | 1.9.0           | 3.0.17          | 8                 | 6                    |
| 8.4            | 1.9.10          | 3.0.17          | 8                 | 6                    |
| 8.5            | 1.9.20          | 3.0.17          | 8                 | 6                    |
| 8.6            | 1.9.20          | 3.0.17          | 8                 | 6                    |
| 8.7            | 1.9.22          | 3.0.17          | 8                 | 6                    |
| 8.8            | 1.9.23          | 3.0.21          | 8                 | 6                    |
| 8.9            | 1.9.23          | 3.0.22          | 8                 | 6                    |
| 8.10           | 1.9.24          | 3.0.22          | 8                 | 6                    |
| 8.11           | 2.0.20          | 3.0.22          | 8                 | 6                    |
| 8.12           | 2.0.21          | 3.0.24          | 8                 | 6                    |
| 8.13           | 2.0.21          | 3.0.24          | 8                 | 6                    |
| 8.14           | 2.0.21          | 3.0.24          | 8                 | 6                    |
| 9.0.0          | 2.2.0           | 4.0.27          | **17**            | 6                    |
| 9.1.0          | 2.2.20          | 4.0.28          | **17**            | 6                    |
| 9.2.0          | 2.2.20          | 4.0.28          | **17**            | 6                    |
| 9.3.0          | 2.2.21          | 4.0.29          | **17**            | 6                    |
| 9.4.0          | 2.3.0           | 4.0.29          | **17**            | 6                    |
| 9.5.0          | 2.3.20          | 4.0.29          | **17**            | 6                    |

*Note: Kotlin DSL uses Kotlin 1.8 language version for 8.x, then switches to matching Kotlin version in 9.0+*

### Minimum Plugin Versions for Gradle 9.0+

When upgrading to Gradle 9.0 or later, ensure these plugins meet minimum versions:

| Plugin                         | Min Version for 9.0 | Notes                          |
|--------------------------------|---------------------|--------------------------------|
| Kotlin Gradle Plugin           | 2.0.0               | Major version bump from 1.6.10 |
| Android Gradle Plugin          | 8.4.0               | Major version bump from 7.3.0  |
| Gradle Enterprise / Develocity | 3.13.1              | Was 3.0                        |

### Critical Path Forward

**For Gradle 8.x Users Planning 9.0 Upgrade**:

1. Ensure JVM 17+ is available in your environment
2. Update plugins to compatible versions FIRST
3. Test upgrade on a branch with your full test suite
4. Address Convention API usage (largest breaking change)
5. Review Kotlin 2.2 and Groovy 4.0 breaking changes
6. Handle JSpecify nullability in type signatures
7. Verify IDE and tooling compatibility before rolling out

**For Gradle 8.14 Users**:

- This is the last stable 8.x release before 9.0
- 9.0 is the next major version; plan accordingly
- Consider upgrading through intermediate versions for better control

**For Gradle 9.x Users**:

- Upgrades between 9.x versions are generally straightforward
- Each version includes performance improvements and bug fixes
- Test thoroughly for breaking changes noted in each section
- Community-reported issues suggest checking for TestKit serialization, build cache stalling, and artifact resolution edge cases
- Watch for Kotlin DSL plugin version constraint issues, especially with Shadow plugin (9.5.0)
- Plan Gradle and Kotlin version upgrades together — Kotlin 2.5.0 will require minimum Gradle 8.14.x
- Enterprise environments: verify `.gradle/native` exec permissions and debug connection behavior

---

## Deprecation Removal Schedule

When you upgrade to a new Gradle version, ALL deprecations from the previous version that have been in effect for 1+ years become breaking changes and are removed.

**Strategy**:
1. Before upgrading, run `gradle help --warning-mode=all` to see all deprecations
2. Fix deprecations in your build first
3. Then upgrade Gradle
4. This prevents surprise failures after upgrade

This guide provides the information you need to plan each upgrade step carefully and understand the impact of breaking changes before they affect your builds.
