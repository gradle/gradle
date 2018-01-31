## New and noteworthy

Here are the new features introduced in this Gradle release.

### Dependency constraints

With [dependency constraints](userguide/managing_transitive_dependencies.html#sec:dependency_constraints), Gradle adds a mechanism to express constraints over transitive dependencies which are used during dependency resolution. In addition, dependency constraints are published when the Gradle module metadata format is used (see below). This means that, as a library author, you can share these constraints with your library's consumers - making them an appealing alternative to other existing mechanisms for managing transitive dependencies in Gradle.

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

### Advanced POM support (preview)

Gradle now supports [additional features for modules with POM metadata](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc). This includes support for _optional dependencies_, _BOM import_ and _compile/runtime scope separation_. 

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by setting `org.gradle.advancedpomsupport=true` in _gradle.properties_.

### New Gradle `.module` metadata format (preview)

In order to provide rich support for variant-aware dependency management and dependency constraints, Gradle 5.0 will define a [new module metadata format](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc), that can be used in conjunction with Ivy descriptor and Maven POM files in existing repositories.

The new metadata format is still under active development, but it can already be used in its current state in Gradle 4.6+ by setting `org.gralde.gradlemetadata=true` in _gradle.properties_.

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

You can also use this to programmatically opt-in to using the new Gradle metadata format for one repository using `metadataSources { gradleMetadata() }`.

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

## Potential breaking changes

### Added annotationProcessor configurations

The `java-base` plugin will now add an `<sourceSetName>AnnotationProcessor` configuration for each source set. This might break when the user already defined such a configuration. We recommend removing your own and using the configuration provided by `java-base`. 

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
