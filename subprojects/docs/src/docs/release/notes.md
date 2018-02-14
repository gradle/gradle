## New and noteworthy

Here are the new features introduced in this Gradle release.

### Dependency constraints

With [dependency constraints](userguide/managing_transitive_dependencies.html#sec:dependency_constraints), Gradle adds a mechanism to express constraints over transitive dependencies which are used during dependency resolution. In the future, Gradle will also allow you to publish dependency constraints when using the [Gradle module metadata format](https://github.com/gradle/gradle/blob/master/subprojects/docs/src/docs/design/gradle-module-metadata-specification.md) that is currently under development. This means that, as a library author, you can share these constraints with your library's consumers - making them an appealing alternative to other existing mechanisms for managing transitive dependencies in Gradle.

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

In the example, the version of `commons-codec` that is brought in transitively is `1.9`. With the constraint, we express that we need at lease `1.11` and Gradle will now pick that version during dependency resolution.

### JUnit Platform and JUnit Jupiter/Vintage Engine (a.k.a. JUnit 5) support

[JUnit 5](http://junit.org/junit5/docs/current/user-guide) is the latest version of the well-known `JUnit` test framework. JUnit 5 is composed of several modules:

    JUnit 5 = JUnit Platform + JUnit Jupiter + JUnit Vintage
    
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
    
You can find samples of tagged tests at `samples/testing/junitplatform/tagging` in the '-all' distribution of Gradle.         
    
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

Note that JUnit 5 requires Java 8 or higher.

### Support for optional dependencies in POM consumption

Gradle now creates a dependency constraint for each dependency declaration in a POM file with `<optional>true</optional>`. This constraint will produce the expected result for an optional dependency: if the dependency module is brought in by another, non-optional dependency declaration, then the constraint will apply when choosing the version for that dependency (e.g., if the optional dependency defines a higher version, that one is chosen).

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

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

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

### Compile/runtime scope separation in POM consumption

Since Gradle 1.0, `runtime` scoped dependencies have been included in the Java compile classpath, which has some drawbacks:

- The compile classpath is much larger than it needs to be, slowing down compilation.
- The compile classpath includes `runtime` files that do not impact compilation, resulting in unnecessary re-compilation when these files change.

Now, if this new behavior is turned on, the Java and Java Library plugins both honor the separation of compile and runtime scopes. Meaning that the compile classpath only includes `compile` scoped dependencies, while the runtime classpath adds the `runtime` scoped dependencies as well. This is in particular useful if you develop and publish Java libraries with Gradle where the api/implementation dependencies separation is reflected in the published scopes.

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by adding `enableFeaturePreview('IMPROVED_POM_SUPPORT')` in _settings.gradle_.

<!--
### Example new and noteworthy
-->

### Specifying metadata sources for repositories

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

### Specifying reasons for dependency declaration and resolution rules

In complex builds, dependency resolution results can be hard to interpret. Sometimes the reason why a [dependency declaration](userguide/customizing_dependency_resolution_behavior.html) or a [rule](userguide/inspecting_dependencies.html#sec:dependency_declaration_reasons) was added to the build script can get lost. To improve on this situation, we extended all the corresponding APIs with the capability to define a _reason_ for each declaration or rule. These reasons are shown in dependency insight reports and error messages if the corresponding declaration or rule influenced the resolution result. In the future, they will also be shown in build scans.

    dependencies {
        implementation('org.ow2.asm:asm:6.0') {
            because 'we require a JDK 9 compatible bytecode generator'
        }
    }
    

### Default JaCoCo version upgraded to 0.8.0

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.0](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

### Annotation processor configurations

It is now even easier to add annotation processors to your Java projects. Simply add them to the `annotationProcessor` configuration.

```
dependencies {
    annotationProcessor 'com.google.dagger:dagger-compiler:2.8'
    implementation 'com.google.dagger:dagger:2.8'
}

```

### Public API for defining command line options for tasks

Sometimes a user wants to declare the value of an exposed task property on the command line instead of the build script. Being able to pass in property values on the command line is particularly helpful if they change more frequently. With this version of Gradle, the task API now supports a mechanism for marking a property to automatically generate a corresponding command line parameter with a specific name at runtime. All you need to do is to annotate a setter method of a property with [Option](dsl/org.gradle.api.tasks.options.Option.html).

The following examples exposes a command line parameter `--url` for the custom task type `UrlVerify`. Let's assume you wanted to pass a URL to a task of this type named `verifyUrl`. The invocation looks as such: `gradle verifyUrl --url=https://gradle.org/`. You can find more information about this feature in the [user guide](userguide/custom_tasks.html#sec:declaring_and_using_command_line_options).

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

### Caching for Scala compilation when using the `play` plugin

The task `PlatformScalaCompile` is now cacheable.
This means that [Play projects](userguide/play_plugin.html) now also benefit from the [build cache](userguide/build_cache.html)!

### New property for debugging the build cache

With Gradle 4.6 we introduce the Gradle property [`org.gradle.caching.debug`](userguide/build_environment.html#sec:gradle_configuration_properties) which causes individual input property hashes to be logged on the console.
For example, when running `gradle compileJava -Dorg.gradle.caching.debug=true --build-cache` the output would be:

    :compileJava
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
This does not happen anymore and the info logs should be much cleaner now when the build cache is enabled.


### Better Visual Studio IDE support for multi-project builds

Previous versions of Gradle would only generate Visual Studio solution files for a given component and its dependencies.  This made it difficult to work on multiple components in a build at one time as a developer would potentially need to open multiple Visual Studio solutions to see all components.  When the `visual-studio` plugin is applied, Gradle now has a `visualStudio` task on the root project that generates a solution for all components in the multi-project build.  This means there is only one Visual Studio solution that needs to be opened to be able to work on any or all components in the build.

### Support for modelling Java agents

Gradle 4.5 enabled plugin authors to model annotation processors as a [`CommandLineArgumentProvider`](javadoc/org/gradle/process/CommandLineArgumentProvider.html) for `JavaCompile` tasks.
Now we introduce the same ability for Java agents by adding `getJvmArgumentProviders()` to [`Test`](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:jvmArgumentProviders) and [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html#org.gradle.api.tasks.JavaExec:jvmArgumentProviders).

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

See the [documentation](userguide/more_about_tasks.html#sec:task_input_nested_inputs) for information how to leverage this feature in custom plugins.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### TestKit becomes public feature

TestKit was first introduced in Gradle 2.6 to support developers with writing and executing functional tests for plugins. In the course of the Gradle 2.x releases, a lot of new functionality was added. This version of Gradle removes the incubating status and makes TestKit a public feature.

### `CompileOptions.annotationProcessorPath` property

The `CompileOptions.annotationProcessorPath` property has been promoted and is now stable.

### Build cache and task output caching

The [build cache](userguide/build_cache.html) and [task output caching](userguide/build_cache.html#sec:task_output_caching) were first introduced in Gradle 3.5.
They are used in production by different teams, including the Gradle team itself, with great results.
As of Gradle 4.6, the build cache and task output caching are no longer incubating and considered public features.

Note that the SPI to [implement your own build cache service](userguide/build_cache.html#sec:build_cache_implement) stays incubating.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Local build cache directory cleanup is now time-based

Previously, Gradle would clean up the [local build cache directory](userguide/build_cache.html#sec:build_cache_configure_local) only if the size of its contents reached 5 GB, or whatever was configured in `targetSizeInMB`.
From now on Gradle will instead clean up everything older than 7 days, regardless of the size of the cache directory.
As a consequence `targetSizeInMB` is now deprecated, and changing its value has no effect.

The minimum age for entries to be cleaned up can now be configured in `settings.gradle` via the [`removeUnusedEntriesAfterDays`](dsl/org.gradle.caching.local.DirectoryBuildCache.html#org.gradle.caching.local.DirectoryBuildCache:removeUnusedEntriesAfterDays) property:

```
buildCache {
    local {
        removeUnusedEntriesAfterDays = 30
    }
}
```

### Putting annotation processors on the compile classpath or explicit `-processorpath` compiler argument

Putting processors on the compile classpath or using an explicit `-processorpath` compiler argument has been deprecated and will be removed in Gradle 5.0. Annotation processors should be added to the `annotationProcessor` configuration instead. If you don't want any processing, but your compile classpath contains a processor unintentionally (e.g. as part of some library you use), use the `-proc:none` compiler argument to ignore it.

### Play 2.2 is deprecated in Play plugin

The use of Play 2.2 with the the Play plugin has been deprecated and will be removed with Gradle 5.0. It is highly recommended to upgrade to a newer version of [Play](https://www.playframework.com/).

### `CompilerArgumentProvider` replaced by `CommandLineArgumentProvider`

The interface [`CompilerArgumentProvider`](javadoc/org/gradle/api/tasks/compile/CompilerArgumentProvider.html) has been deprecated.
Use [`CommandLineArgumentProvider`](javadoc/org/gradle/process/CommandLineArgumentProvider.html) instead.

## Potential breaking changes

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
