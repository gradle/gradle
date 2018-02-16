The Gradle team is pleased to announce Gradle 4.6.

First and foremost, this release of Gradle features extensive improvements to dependency management. You can now [declare dependency constraints for transitive dependencies](#dependency-constraints-for-transitive-dependencies) and avoid problems caused by oft-hidden upstream dependency changes. 

This release of Gradle also includes crucial features for Maven dependency compatibility: support for [importing BOMs](#bom-import), [optional dependencies](#support-for-optional-dependencies-in-pom-consumption), and [compile/runtime separation when consuming POMs](#compile/runtime-scope-separation-in-pom-consumption). For now you must enable these features by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` to your _settings.gradle_ file, as they break backward compatibility in some cases.

Next, this release of Gradle includes built-in support for JUnit Platform and the JUnit Jupiter/Vintage Engine, also known as [JUnit 5 support](#junit-5-support). You can use the new filtering and engines functionality in JUnit 5 using the examples provided below and in the documentation.

Also regarding testing, you can now improve your testing feedback loop when running JVM-based tests using the [new fail-fast option for `Test` tasks](#fail-fast-option-for-test-tasks), which stops the build immediately after the first test failure.

This version of Gradle also comes with a couple especially useful new APIs for task development. You can now [declare custom command-line flags for your custom tasks](#tasks-api-allows-custom-command-line-options), for example: `gradle myCustomTask --myfoo=bar`. In addition, [tasks that extend `Test`, `JavaExec` or `Exec` can declare rich arguments](#rich-command-line-arguments-for-test,-javaexec-or-exec-tasks) for invoking the underlying executable. This allows for better modeling of tools like annotation processors.

Speaking of annotation processors, it is now more convenient to declare dependencies that are annotation processors through the [new `annotationProcessor` dependency configuration](#convenient-declaration-of-annotation-processor-dependencies). Using a separate dependency configuration for annotation processors is a best practice for improving performance.

[Kotlin DSL v0.15.6](https://github.com/gradle/kotlin-dsl/releases/tag/v0.15.6) is included in this release of Gradle, and features  initialization scripts support, nicer script compilation error reporting, performance improvements, and better IntelliJ IDEA integration. Details are available in the linked release notes.

We hope you will build happiness with Gradle 4.6, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).

## Upgrade instructions

Switch your build to use Gradle 4.6 RC1 quickly by updating your wrapper properties:

`gradle wrapper --gradle-version=4.6-rc-1`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Dependency constraints for transitive dependencies

With [dependency constraints](userguide/managing_transitive_dependencies.html#sec:dependency_constraints), Gradle adds a mechanism to express constraints over transitive dependencies which are used during dependency resolution. 

In the future, Gradle will also allow you to _publish_ dependency constraints when using the [Gradle module metadata format](https://github.com/gradle/gradle/blob/master/subprojects/docs/src/docs/design/gradle-module-metadata-specification.md) (currently under development) such that library authors will be able to share these constraints with library consumers. This makes dependency constraints a better alternative over other existing mechanisms for managing transitive dependencies in Gradle.

    dependencies {
        implementation 'org.apache.httpcomponents:httpclient'
    }

    dependencies {
        constraints {
            // declare versions for my dependencies in a central place
            implementation 'org.apache.httpcomponents:httpclient:4.5.3'
            // declare versions for transitive dependencies
            implementation 'commons-codec:commons-codec:1.11'
        }
    }

In the example, the version of `commons-codec` that is brought in transitively is `1.9`. With the constraint, we express that we need at least `1.11` and Gradle will now pick that version during dependency resolution.

### BOM import

Gradle now [provides support](userguide/managing_transitive_dependencies.html#sec:bom_import) for importing [bill of materials (BOM) files](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies), which are effectively `.pom` files that use `<dependencyManagement>` to control the dependency versions of direct and transitive dependencies. It works by declaring a dependency on a BOM.

    dependencies {
        // import a BOM
        implementation 'org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE'
    
        // define dependencies without versions
        implementation 'com.google.code.gson:gson'
        implementation 'dom4j:dom4j'
    }

Here, for example, the versions of `gson` and `dom4j` are provided by the Spring Boot BOM.

**Note:** This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

### Support for optional dependencies in POM consumption

Gradle now creates a dependency constraint for each dependency declaration in a POM file with `<optional>true</optional>`. This constraint will produce the expected result for an optional dependency: if the dependency module is brought in by another, non-optional dependency declaration, then the constraint will apply when choosing the version for that dependency (e.g., if the optional dependency defines a higher version, that one is chosen).

**Note:** This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

### Compile/runtime scope separation in POM consumption

Since Gradle 1.0, `runtime` scoped dependencies have been included in the Java compile classpath, which has some drawbacks:

- The compile classpath is much larger than it needs to be, slowing down compilation.
- The compile classpath includes `runtime` files that do not impact compilation, resulting in unnecessary re-compilation when these files change.

Now, if this new behavior is turned on, the Java and Java Library plugins both honor the separation of compile and runtime scopes. Meaning that the compile classpath only includes `compile` scoped dependencies, while the runtime classpath adds the `runtime` scoped dependencies as well. This is in particular useful if you develop and publish Java libraries with Gradle where the api/implementation dependencies separation is reflected in the published scopes.

**Note:** This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

### JUnit 5 support

[JUnit 5](http://junit.org/junit5/docs/current/user-guide) is the latest version of the well-known `JUnit` test framework. JUnit 5 is composed of several modules:

> JUnit 5 = JUnit Platform + JUnit Jupiter + JUnit Vintage

The `JUnit Platform` serves as a foundation for launching testing frameworks on the JVM. `JUnit Jupiter` is the combination of the new [programming model](http://junit.org/junit5/docs/current/user-guide/#writing-tests)
 and [extension model](http://junit.org/junit5/docs/current/user-guide/#extensions) for writing tests and extensions in JUnit 5. `JUnit Vintage` provides a `TestEngine` for running JUnit 3 and JUnit 4 based tests on the platform.
    
Gradle now provides native support for `JUnit Jupiter/Vintage Engine` on top of `JUnit Platform`. To enable `JUnit Platform` support, you just need to add one line to your `build.gradle`:

    test {
        useJUnitPlatform()
    }

Moreover, [Tagging and Filtering](http://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering) can be enabled via:

    test {
        useJUnitPlatform {
            // includeTags 'fast'
            excludeTags 'slow'
            
            // includeEngines 'junit-jupiter', 'junit-vintage'
            // excludeEngines 'custom-engine'
        }
    }
    
You can find more information on [test grouping and filtering in the Java Plugin documentation](userguide/java_plugin.html#test_grouping).
    
#### JUnit Jupiter Engine
    
To enable `JUnit Jupiter` support, add the following dependencies:

    dependencies {
        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.0.3'
        testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.0.3'
    }      

Put your first `Jupiter` test into `src/test/java/foo/bar`:

    package foo.bar;
    
    import org.junit.jupiter.api.Test;
    
    public class JUnitJupiterTest {
        @Test
        public void ok() { 
        }
    }
    
Now you can run `gradle test` to see the results of your JUnit 5 tests! 

You can find a sample of test with `JUnit Jupiter` at `samples/testing/junitplatform/jupiter` in the '-all' distribution of Gradle. 

#### JUnit Vintage Engine

If you want to run JUnit 3/4 tests on `JUnit Platform`, you should add extra `JUnit Vintage Engine` dependency:

    test {
        useJUnitPlatform()
    }
    
    dependencies {
        testCompileOnly 'junit:junit:4.12' 
        testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:4.12.3' 
    }
    
You can mix JUnit 3/4 tests with `Jupiter` tests without the need to rewrite old tests.
A sample of mixed tests can be found at `samples/testing/junitplatform/engine` in the '-all' distribution of Gradle.         

**Note:** Use of JUnit 5 features requires Java 8 or higher.

### Fail fast option for Test tasks

Gradle now supports stopping [`Test`](dsl/org.gradle.api.tasks.testing.Test.html) tasks after the first failed test. Projects with large test suites can take a long time to execute even though a failure occurred early on leading to unnecessary wait times (especially on CI). To enable this fail fast behavior in your build file, set the `failFast` property to `true`:

    test {
        failFast = true
    }

In addition, this behavior can be enabled from the command line for individual build invocations. An invocation looks like:

    gradle integTest --fail-fast

More information is available in the [Java Plugin documentation for test execution](userguide/java_plugin.html#sec:test_execution).

<!--
### Example new and noteworthy
-->

### Customizable metadata file resolution

Gradle now allows you to explicitly state for [which metadata files](userguide/repository_types.html#sub:supported_metadata_sources) it should search in a repository. Use the following to configure Gradle to fail-fast resolving a dependency if a POM file is not found first.

    repositories {
         mavenCentral {
             metadataSources {
                 mavenPom() // Look for Maven '.pom' files
                 // artifact() - Do not look for artifacts without metadata file
             }
         }
    }

This avoids a 2nd request for the JAR file when the POM is missing, making dependency resolution from Maven repositories faster in this case.

### Allow declared reasons for dependency and resolution rules

In complex builds, it can become hard to interpret dependency resolution results and why a [dependency declaration](userguide/customizing_dependency_resolution_behavior.html) or a [rule](userguide/inspecting_dependencies.html#sec:dependency_declaration_reasons) was added to a build script. To improve on this situation, we extended all the corresponding APIs with the capability to define a _reason_ for each declaration or rule. 

These reasons are shown in dependency insight reports and error messages if the corresponding declaration or rule influenced the resolution result. In the future, they will also be shown in build scans.

    dependencies {
        implementation('org.ow2.asm:asm:6.0') {
            because 'we require a JDK 9 compatible bytecode generator'
        }
    }
    

### Convenient declaration of annotation processor dependencies

It is now even easier to add annotation processors to your Java projects. Simply add them to the `annotationProcessor` configuration:

    dependencies {
        annotationProcessor 'com.google.dagger:dagger-compiler:2.8'
        implementation 'com.google.dagger:dagger:2.8'
    }

Declaring annotation processors on a [separate configuration improves performance](https://blog.gradle.org/incremental-compiler-avoidance#about-annotation-processors) by preserving incremental compilation for tasks that don't require annotation processors.

### Tasks API allows custom command-line options

Sometimes a user wants to declare the value of an exposed task property on the command line instead of the build script. Being able to pass in property values on the command line is particularly helpful if they change more frequently. With this version of Gradle, the task API now supports a mechanism for marking a property to automatically generate a corresponding command line parameter with a specific name at runtime. All you need to do is to annotate a setter method of a property with [Option](dsl/org.gradle.api.tasks.options.Option.html).

The following examples exposes a command line parameter `--url` for the custom task type `UrlVerify`. Let's assume you wanted to pass a URL to a task of this type named `verifyUrl`. The invocation looks as such: `gradle verifyUrl --url=https://gradle.org/`. You can find more information about this feature in the [documentation on declaring command-line options](userguide/custom_tasks.html#sec:declaring_and_using_command_line_options).

    import org.gradle.api.tasks.options.Option;

    public class UrlVerify extends DefaultTask {
        private String url;

        @Option(option = "url", description = "Configures the URL to be verified.")
        public void setUrl(String url) {
            this.url = url;
        }

        @Input
        public String getUrl() {
            return url;
        }

        @TaskAction
        public void verify() {
            getLogger().quiet("Verifying URL '{}'", url);

            // verify URL by making a HTTP call
        }
    }

### Rich command-line arguments for Test, JavaExec or Exec tasks

Gradle 4.5 added the possibility to add [`CommandLineArgumentProvider`](javadoc/org/gradle/process/CommandLineArgumentProvider.html)s to [`CompileOptions`](dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:compilerArgumentProviders), thus enabling plugin authors to better model tools like annotation processors.

Now we introduce `CommandLineArgumentProvider`s to [`Exec`](dsl/org.gradle.api.tasks.Exec.html#org.gradle.api.tasks.Exec:argumentProviders), [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html#org.gradle.api.tasks.JavaExec:jvmArgumentProviders) and [`Test`](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:jvmArgumentProviders), both to model command line arguments (`Exec` and `JavaExec`) as well as JVM options (`JavaExec` and `Test`).

For example, the built-in [`jacoco`](userguide/jacoco_plugin.html) plugin [uses this new feature](https://github.com/gradle/gradle/blob/12a25cce43317e28690183097f8f87130a67318e/subprojects/jacoco/src/main/java/org/gradle/testing/jacoco/plugins/JacocoPluginExtension.java#L137-L156) to declare the inputs and outputs of the JaCoCo agent added to the test task.

    class JacocoAgent implements CommandLineArgumentProvider {

        private final JacocoTaskExtension jacoco;

        public JacocoAgent(JacocoTaskExtension jacoco) {
            this.jacoco = jacoco;
        }

        @Nested
        @Optional
        public JacocoTaskExtension getJacoco() {
            return jacoco.isEnabled() ? jacoco : null;
        }

        @Override
        public Iterable<String> asArguments() {
            return jacoco.isEnabled() ? ImmutableList.of(jacoco.getAsJvmArg()) : Collections.<String>emptyList();
        }

    }

    task.getJvmArgumentProviders().add(new JacocoAgent(extension));
    
For this to work, [JacocoTaskExtension](dsl/org.gradle.testing.jacoco.plugins.JacocoTaskExtension.html) needs to have the correct input and output annotations.

See the [documentation about tasks with nested inputs](userguide/more_about_tasks.html#sec:task_input_nested_inputs) for information how to leverage this feature in custom plugins.

### Logging options for debugging build caching

This version of Gradle introduces a property [`org.gradle.caching.debug`](userguide/build_environment.html#sec:gradle_configuration_properties) which causes individual input property hashes to be logged on the console.
For example, when running `gradle compileJava -Dorg.gradle.caching.debug=true --build-cache` the output would be:

    > Task :compileJava
    Appending taskClass to build cache key: org.gradle.api.tasks.compile.JavaCompile_Decorated
    Appending classLoaderHash to build cache key: 0e1119759d236086191ff6fbd26c610f
    Appending actionType to build cache key: _BuildScript_$_run_closure1_769114ba780efdb8aed68d70a323d160
    Appending actionClassLoaderHash to build cache key: c383666cbb0f1bf25d832b944f228c44
    Appending actionType to build cache key: org.gradle.api.tasks.compile.JavaCompile_Decorated
    Appending actionClassLoaderHash to build cache key: 0e1119759d236086191ff6fbd26c610f
    Appending inputPropertyHash for 'options.class' to build cache key: 74824162f3f111308fa9dc95c82b65a6
    Appending inputPropertyHash for 'options.compilerArgs' to build cache key: 8222d82255460164427051d7537fa305
    ...
    Appending inputPropertyHash for 'source' to build cache key: 55786645cf0e6dcf6a3d48b1b43bf687
    Appending outputPropertyName to build cache key: destinationDir
    Build cache key for task ':compileJava' is 2221655c6648a7e9baf61a6234de8658

In earlier versions of Gradle, this output was logged on the `INFO` log level.
This does not happen anymore, and the `--info` logs should be much less verbose now while the build cache is enabled.

### Caching for Scala compilation when using the `play` plugin

The task `PlatformScalaCompile` is now cacheable. 
This means that [Play projects](userguide/play_plugin.html) written in Scala now also benefit from the [build cache](userguide/build_cache.html)!

### Improved Visual Studio IDE support for multi-project builds

Previous versions of Gradle would only generate Visual Studio solution files for a given component and its dependencies.  This made it difficult to work on multiple components in a build at one time as a developer would potentially need to open multiple Visual Studio solutions to see all components.  When the `visual-studio` plugin is applied, Gradle now has a `visualStudio` task on the root project that generates a solution for all components in the multi-project build. This means there is only one Visual Studio solution that needs to be opened to be able to work on any or all components in the build.

### Honour cache-expiry settings in the presence of detached configurations

Gradle allows dependency cache expiry (i.e `cacheChangingModulesFor`) to be set on a per-configuration basis. However, due to a bug in previous versions of Gradle, if a dependency was first resolved via a configuration using the default (24hr) expiry settings, any other resolve _in the same build invocation_ would get the same result.

Normally this wouldn't be a big deal, since most users set the same expiry everywhere using `configurations.all`. The catch is that plugins like `io.spring.dependency-management` use _detached configurations_, which are excluded from `configurations.all`. If a build was using one of these plugins, the _detached configuration_ could be resolved first, causing later resolves to obtain the same (possibly stale) result.

This [nasty cache-expiry bug](https://github.com/gradle/gradle/issues/3019) has now been fixed. Users can trust that Gradle will return the most up-to-date `SNAPSHOT` or version available as long as the [dependency cache expiry is set](userguide/troubleshooting_dependency_resolution.html#sec:controlling_dependency_caching_programmatically) correctly.

### Default JaCoCo version upgraded to 0.8.0

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.0](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Build cache and task output caching marked stable

The [build cache](userguide/build_cache.html) and [task output caching](userguide/build_cache.html#sec:task_output_caching) were first introduced in Gradle 3.5.
They are used in production by different teams, including the Gradle team itself, with great results.
As of Gradle 4.6, the build cache and task output caching are no longer incubating and considered public features.

Note that the SPI to [implement your own build cache service](userguide/build_cache.html#sec:build_cache_implement) stays incubating.

### TestKit marked stable

[TestKit](userguide/test_kit.html) was first introduced in Gradle 2.6 to support developers with writing and executing functional tests for plugins. In the course of the Gradle 2.x releases, a lot of new functionality was added. This version of Gradle removes the incubating status and makes TestKit a stable feature.

### `CompileOptions.annotationProcessorPath` property

The `CompileOptions.annotationProcessorPath` property has been promoted and is now stable.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Putting annotation processors on the compile classpath or explicit `-processorpath` compiler argument

Putting processors on the compile classpath or using an explicit `-processorpath` compiler argument has been deprecated and will be removed in Gradle 5.0. Annotation processors should be added to the `annotationProcessor` configuration instead. If you don't want any processing, but your compile classpath contains a processor unintentionally (e.g. as part of some library you use), use the `-proc:none` compiler argument to ignore it.

### Play 2.2 is deprecated in Play plugin

The use of Play 2.2 with the the Play plugin has been deprecated and will be removed with Gradle 5.0. It is highly recommended to upgrade to a newer version of [Play](https://www.playframework.com/).

### `CompilerArgumentProvider` replaced by `CommandLineArgumentProvider`

The interface [`CompilerArgumentProvider`](javadoc/org/gradle/api/tasks/compile/CompilerArgumentProvider.html) has been deprecated.
Use [`CommandLineArgumentProvider`](javadoc/org/gradle/process/CommandLineArgumentProvider.html) instead.

## Potential breaking changes

### Local build cache directory cleanup is now time-based

Previously, Gradle would clean up the [local build cache directory](userguide/build_cache.html#sec:build_cache_configure_local) only if the size of its contents reached 5 GB, or whatever was configured in `targetSizeInMB`.
From now on Gradle will instead clean up everything older than 7 days, regardless of the size of the cache directory.
As a consequence `targetSizeInMB` is now deprecated, and changing its value has no effect.

The minimum age for entries to be cleaned up can now be configured in `settings.gradle` via the [`removeUnusedEntriesAfterDays`](dsl/org.gradle.caching.local.DirectoryBuildCache.html#org.gradle.caching.local.DirectoryBuildCache:removeUnusedEntriesAfterDays) property:

    buildCache {
        local {
            removeUnusedEntriesAfterDays = 30
        }
    }

### Added annotationProcessor configurations

The `java-base` plugin will now add an `<sourceSetName>AnnotationProcessor` configuration for each source set. This might break when the user already defined such a configuration. We recommend removing your own and using the configuration provided by `java-base`.

### Changes to Visual Studio IDE configuration
[`VisualStudioExtension`](dsl/org.gradle.ide.visualstudio.VisualStudioExtension.html) no longer has a `solutions` property.  There is now only a single solution for a multi-project build that can be accessed through the [`VisualStudioRootExtension`](dsl/org.gradle.ide.visualstudio.VisualStudioRootExtension.html) on the root project.  For instance:

    model {
        visualStudio {
            solution {
                solutionFile.location = "vs/${name}.sln"
            }
        }
    }

### Removed Visual Studio IDE tasks
There are no longer tasks to generate Visual Studio solution files for each component in the build.  There is now only a single task (`visualStudio`) in the root project that generates a solution containing all components in the build.

### Removed `StartParameter.taskOutputCacheEnabled`

The deprecated property `StartParameter.taskOutputCacheEnabled` has been removed.
Use `StartParameter.buildCacheEnabled` instead.

### HttpClient library upgraded to version 4.5.5

Gradle has been upgraded to embed [HttpClient version 4.5.5](https://archive.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.5.x.txt) over 4.4.1.

### Kotlin Standard Library for Java 8 artifact changes

Gradle now bundles the Kotlin Standard Library Java 8 artifact `kotlin-stdlib-jdk8` instead of `kotlin-stdlib-jre8` as a follow up to the upgrade to Kotlin 1.2. This change might affect your build, please see the [Kotlin documentation](http://kotlinlang.org/docs/reference/whatsnew12.html#kotlin-standard-library-artifacts-and-split-packages) about this change.

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->
 - [Thomas Broyer](https://github.com/tbroyer) - Add annotationProcessor configuration for each source set (gradle/gradle#3786)
 - [Sergei Dryganets](https://github.com/dryganets) - Improved gpg instructions in signing plugin documentation (gradle/gradle#4023)
 - [Kevin Macksamie](https://github.com/k-mack) - Fix xref id to java-gradle-plugin section of user guide (gradle/gradle#4179)
 - [Devi Sridharan](https://github.com/devishree90) - Make `PlatformScalaCompile` cacheable (gradle/gradle#3804)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
