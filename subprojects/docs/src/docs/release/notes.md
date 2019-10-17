The Gradle team is excited to announce a new major version of Gradle, @version@.

A major highlight of this release is the [vastly improved feature set in dependency management](#dependency-management). Some of the features were released in stages, but with Gradle 6.0 they are stable and production ready. We publish [Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html) by default, which makes these new features available between projects _and_ binary dependencies.

In the JVM ecosystem, we've made [incremental Java and Groovy compilation faster](#faster-incremental-java), added [support for JDK13](#java-13) and provided [out of the box support for javadoc and source jars](#javadoc-sources-jar). For Scala projects, we've updated the [Zinc compiler](#zinc-compiler) and made it easier to select which version of Zinc to use.

For Gradle [plugin authors](#plugin-ecosystem), we've added new APIs to make it easier to lazily connect tasks and properties together, [made useful services available to worker API actions](#worker-api-services) and Gradle will [complain at runtime if a task appears misconfigured](#task-problems).

In the [native ecosystem](#native-ecosystem), we've added support for Visual Studio 2019 and the latest C++ standards.

This release contains some updates to help [protect the integrity and security of your build](#security). 

As always, we also incorporated some [smaller changes](#quality-of-life) and [many other fixed issues](#fixed-issues).

This release features changes across the board, but these release notes only list what's new since Gradle 5.6.
You can review the [highlights since Gradle 5.0 here](https://gradle.org/whats-new/gradle-6/).

We would like to thank the following community contributors to this release of Gradle:

[Nathan Strong](https://github.com/NathanStrong-Tripwire),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Daniel Santiago](https://github.com/danysantiago),
[Tetsuya Ikenaga](https://github.com/ikngtty),
[Sebastian Schuberth](https://github.com/sschuberth),
[Andrey Mischenko](https://github.com/gildor),
[Shintaro Katafuchi](https://github.com/hotchemi),
[Alex Saveau](https://github.com/SUPERCILEX),
[Mike Kobit](https://github.com/mkobit),
[Tom Eyckmans](https://github.com/teyckmans),
[Artur Dryomov](https://github.com/ming13),
[szhem](https://github.com/szhem),
[Nigel Banks](https://github.com/nigelgbanks),
[Sergey Shatunov](https://github.com/Prototik),
[Dan Sănduleac](https://github.com/dansanduleac),
[Vladimir Sitnikov](https://github.com/vlsi),
[Ross Goldberg](https://github.com/rgoldberg),
[jutoft](https://github.com/jutoft),
[Robin Verduijn](https://github.com/robinverduijn),
[Pedro Tôrres](https://github.com/t0rr3sp3dr0),
[Michael Berry](https://github.com/MikeBerryFR),
[Evgeny Mandrikov](https://github.com/Godin),
and [Robert Stupp](https://github.com/snazy).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

### Compatibility Notes

A Java version between 8 and 13 is required to execute Gradle. Java 14 and later versions are not yet supported.

Java 6 and 7 can still be used for [compilation and forked test execution](userguide/building_java_projects.html#sec:java_cross_compilation). Just like Gradle 5.x, any supported version of Java can be used for compile or test.

This version of Gradle is tested with 
* Android Gradle Plugin 3.4, 3.5 and 3.6
* Kotlin 1.3.21 through 1.3.50

Other versions may or may not work.

<a name="dependency-management"></a>
## Dependency management improvements

The dependency management documentation has been reorganised and structured around use cases to help users find the information they need faster.
We've improved the [terminology section](userguide/dependency_management_terminology.html) to explain the commonly used terms.

The [publication of Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html) is now the default when using the `maven-publish` or `ivy-publish` plugins.

Many of the features below rely on the production or consumption of additional metadata not found in Ivy or Maven POM files.

### Sharing dependency versions between projects

Gradle offers an easy way to [recommend and share versions](userguide/platforms.html) between projects called _platforms_.

With Gradle platforms, more context around version declaration are available, versions can be recommended and strict versions are enforced.

For interoperability, builds can also leverage [integration with Maven BOMs](userguide/platforms.html#sub:bom_import).

### Handling mutually exclusive dependencies

Gradle uses [_component capabilities_](userguide/component_capabilities.html) to allow plugins and builds to [detect and resolve implementation conflicts](userguide/dependency_capability_conflict.html) between mutually exclusive dependencies.

A well-known example in the JVM world is competing logging implementations. Component capabilities let builds configure which dependency to select.

### Upgrading versions of transitive dependencies

Issues with dependency management are often about dealing with transitive dependencies. Often developers incorrectly fix transitive dependency issues by adding direct dependencies. To avoid this, Gradle provides the concept of dependency constraints to [influence the version](userguide/dependency_constraints.html) of transitive dependencies.

### Aligning versions across multiple dependencies

[Dependency version alignment](userguide/dependency_version_alignment.html) allows builds to express that different modules belong to the same logical group (like a platform) and need to have identical (a.k.a _aligned_) versions in a dependency graph.

A well-known example in the JVM world is the Jackson libraries.

### Expressing intent with context

When declaring a dependency, a build [can provide more context](userguide/rich_versions.html) to Gradle about its version, including version preferences within a range, strict version requirements or rejected versions.  Developers can also provide human readable descriptions for why a dependency is used or needed.

### Tweak published metadata 

Gradle allows builds to [fix or enrich traditional metadata](userguide/component_metadata_rules.html) with information that could not be published before, such as dependency constraints, rich versions, capabilities and variants. These are called _component metadata rules_.

Component metadata rules also make it possible to [map additional published artifacts](userguide/component_metadata_rules.html#making_variants_published_as_classified_jars_explicit) to new Gradle variants.

### Modeling feature variants and optional dependencies

Gradle provides the ability to model [optional features](userguide/feature_variants.html) of a library.  Each feature can have its own set of dependencies and can be consumed separately.

With feature variants, Gradle provides first-class support for to [create and publish test fixtures](userguide/java_testing.html#sec:java_test_fixtures).  Test fixtures can be consumed by other projects in a multi-project build.

<a name="javadoc-sources-jar"></a>
### Built-in javadoc and sources packaging and publishing

You can now activate Javadoc and sources publishing for a Java Library or Java project:

```
java {
    publishJavadoc()
    publishSources()
}
```

Using the `maven-publish` or `ivy-publish` plugin, this will not only automatically create and publish a `-javadoc.jar` and `-sources.jar` but also publish the information that these exist as variants in Gradle Module Metadata.
This means that you can query for the Javadoc or sources _variant_ of a module and also retrieve the Javadoc (or sources) of its dependencies.

If activated, a Java and Java Library project automatically provides the `javadocJar` and `sourcesJar` tasks.

<a name="faster-incremental-java"></a>
## Faster incremental Java and Groovy compilation

When analyzing the impact of a changed class, the incremental compiler can now exclude classes that are an implementation detail of another class.
This limits the number of classes that need to be recompiled.

For instance, if you have:

```
class A {}

class B {
    static void foo() {
        A a = new A();
        // ... use A
    }
}

class C {
    void bar() {
        B.foo();
    }
}
```

When `A` is changed, Gradle previously recompiled all 3 source files, even though `B` did not change in a way that required `C` to be recompiled. 

In Gradle 6.0, Gradle will only recompile `A` and `B`. For deep dependency chains, this may greatly reduce the number of files that require recompilation within a compilation task.

If `A`, `B` and `C` were all in different projects, Gradle would skip recompiling `C` through [compilation avoidance](userguide/java_plugin.html#sec:java_compile_avoidance). 

This was contributed by [Robert Stupp](https://github.com/snazy).

<a name="java-13"></a>
## Support for Java 13

Gradle now supports running with [Java 13](https://openjdk.java.net/projects/jdk/13/).

<a name="zinc-compiler"></a>
## Update to newer Scala Zinc compiler

The Zinc compiler has been upgraded to version 1.3.0. Gradle no longer supports building for Scala 2.9. 

This fixes some Scala incremental compilation bugs and improves performance. 

The minimum Zinc compiler supported by Gradle is 1.2.0 and the maximum tested version is 1.3.0.

To make it easier to select the version of the Zinc compiler, you can now configure a `zincVersion` property:
```
scala {
    zincVersion = "1.2.1"
}
```

Please note that the coordinates for the supported version of Zinc has changed since Zinc 1.0. 
If you try to use the `com.typesafe.zinc:zinc` compiler, Gradle will switch to the new Zinc implementation with a default version (1.3.0).

<a name="task-problems"></a>
## Problems with tasks called out during build

Tasks that define their inputs or outputs incorrectly can cause problems when running incremental builds or when using the build cache.
As part of an ongoing effort to bring these problems to light, Gradle now reports these problems as deprecation warnings during the build.

When issues are detected, Gradle will show warnings when run with `--warning-mode=all`:
```
> Task :myTask
Property 'inputDirectory' is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.
Property 'outputFile' is not annotated with an input or output annotation. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.
```

Deprecation warnings will always show up in [build scans](https://scans.gradle.com/s/txrptciitl2ha/deprecations) regardless of the command-line arguments used.

See the user manual for [how to address these deprecation warnings](userguide/more_about_tasks.html#sec:task_input_validation). 

<a name="security"></a>
## Security improvements

### Protecting the integrity of builds

Gradle will now emit a deprecation warning when resolving dependencies, pulling cache hits from a remote build cache, retrieving text resources, and applying script plugins with the insecure HTTP protocol.

We encourage all users to switch to using HTTPS instead of HTTP.
Free HTTPS certificates for your artifact server can be acquired from [Lets Encrypt](https://letsencrypt.org/).
The use of HTTPS is important for [protecting your supply chain and the entire JVM ecosystem](https://medium.com/bugbountywriteup/want-to-take-over-the-java-ecosystem-all-you-need-is-a-mitm-1fc329d898fb).

For users that require the use of HTTP, Gradle has several new APIs to continue to allow HTTP on a case-by-case basis.

For repositories:
```kotlin
repositories {
    maven {
        url = "http://my-company.example"
        allowInsecureProtocol = true
    }
    ivy {
        url = "http://my-company.example"
        allowInsecureProtocol = true
    }
}
```

For script plugins:
```groovy
apply from: resources.text.fromInsecureUri("http://my-company.example/external.gradle")
```

The new APIs:
- [`HttpBuildCache.allowInsecureProtocol`](dsl/org.gradle.caching.http.HttpBuildCache.html#org.gradle.caching.http.HttpBuildCache:allowInsecureProtocol) 
- [`IvyArtifactRepository.allowInsecureProtocol`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html#org.gradle.api.artifacts.repositories.IvyArtifactRepository:allowInsecureProtocol)
- [`MavenArtifactRepository.allowInsecureProtocol`](dsl/org.gradle.api.artifacts.repositories.MavenArtifactRepository.html#org.gradle.api.artifacts.repositories.MavenArtifactRepository:allowInsecureProtocol)
- [`TextResourceFactory.fromInsecureUri(Object)`](dsl/org.gradle.api.resources.TextResourceFactory.html#org.gradle.api.resources.TextResourceFactory:fromInsecureUri(java.lang.Object))

### Deprecation of HTTP services

On January 13th through January 15th, 2020, some of the most widely used artifact servers in the JVM ecosystem
will drop support for HTTP and will only support HTTPS. Their announcements can be found below:

 - [Sonatype: Maven Central](https://central.sonatype.org/articles/2019/Apr/30/http-access-to-repo1mavenorg-and-repomavenapacheorg-is-being-deprecated/)
 - [JFrog: JCenter](https://jfrog.com/blog/secure-jcenter-with-https/)
 - [Pivotal: Spring](https://spring.io/blog/2019/09/16/goodbye-http-repo-spring-use-https)

We will be [decommissioning the use of HTTP](https://blog.gradle.org/decommissioning-http) with Gradle provided services.

### Signing Plugin now uses SHA512 instead of SHA1

A low severity security issue was reported in the Gradle signing plugin.

More information can be found below:

 - [Gradle GitHub Advisory](https://github.com/gradle/gradle/security/advisories/GHSA-mrm8-42q4-6rm7)
 - [CVE-2019-16370](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2019-16370)
 
This was contributed by [Vladimir Sitnikov](https://github.com/vlsi).

### Support for in-memory signing with subkeys

Gradle now supports [in-memory signing](userguide/signing_plugin.html#sec:in-memory-keys) with subkeys.

This was contributed by [szhem](https://github.com/szhem).

<a name="quality-of-life"></a>
## Usability improvements

### Automatic shortening of long command-lines for Java applications on Windows

When Gradle detects that a Java process command-line will exceed Windows's 32,768 character limit, Gradle will attempt to shorten the command-line by passing the classpath of the Java application via a ["classpath jar"](https://docs.oracle.com/javase/tutorial/deployment/jar/downman.html). 

The classpath jar contains a manifest with the full classpath of the application. Gradle will only pass the generated jar on the command-line to the application. If the command-line is still too long, the Java process will fail to start as before.

If the command-line does not require shortening, Gradle will not change the command-line arguments for the Java process.

### More consistent & robust file deletion on Windows

Deleting complex file hierarchies on Windows can sometimes fail with errors like `Unable to delete directory ...`.  In the past, Gradle has used workarounds in some but not all cases when deleting files.

Gradle now deletes files in a consistent way that should avoid failures when cleaning output files of a task.

### Windows line endings: `gradle init` generates `.gitattributes` file

To ensure Windows batch scripts retain the appropriate line endings, `gradle init` now generates a `.gitattributes` file.

This was contributed by [Tom Eyckmans](https://github.com/teyckmans).

### Wrapper reports download progress as percentage

Gradle now reports the progress of the distribution downloaded. 

Initially contributed by [Artur Dryomov](https://github.com/ming13).

### Wrapper tries to recover from an invalid Gradle installation

If the wrapper determines a Gradle distribution installed by the wrapper is invalid, the wrapper will attempt to re-install the distribution.  Previous versions of the wrapper would fail and require manual intervention.

### Daemon logs contain the date and timestamp

When logging messages to the Gradle daemon log, our log format only contain the time and not the date. 

Gradle now logs with ISO-8601 date timestamps.

<a name="plugin-ecosystem"></a>
## Features for plugin authors

### New types available as managed properties

Gradle 5.5 introduced the concept of a [_managed property_ for tasks and other types](userguide/custom_gradle_types.html#managed_properties). Gradle provides an implementation of the getters and setters for a managed property. This simplifies plugin implementation by removing a bunch of boilerplate.

In this release, it is possible for a task or other custom types to have an abstract read-only property of type `ConfigurableFileTree` and `NamedDomainObjectContainer<T>`. 

### New `ConfigurableFileTree` and `FileCollection` factory methods

Previously, it was only possible to create a `ConfigurableFileTree` or a fixed `FileCollection` by using the APIs provided by a `Project`. However, a `Project` object is not always available, for example in a project extension object or a [worker action](userguide/custom_tasks.html#worker_api).

The `ObjectFactory` service now has a [fileTree()](javadoc/org/gradle/api/model/ObjectFactory.html#fileTree--) method for creating `ConfigurableFileTree` instances.

The `Directory` and `DirectoryProperty` types both have a new `files(Object...)` method to create fixed `FileCollection` instances resolving files relativel to the referenced directory.
- [`Directory.files(Object...)`](javadoc/org/gradle/api/file/Directory.html#files-java.lang.Object++...++-)
- [`DirectoryProperty.files(Object...)`](javadoc/org/gradle/api/file/DirectoryProperty.html#files-java.lang.Object++...++-)

See the user manual for how to [inject services](userguide/custom_gradle_types.html#service_injection) and how to [work with files in lazy properties](userguide/lazy_configuration.html#sec:working_with_files_in_lazy_properties). 

### Injectable `FileSystemOperations` and `ExecOperations` services

In the same vein, doing file system operations such as `copy()`, `sync()` and `delete()` or running external processes via `exec()` and `javaexec()` was only possible by using the APIs provided by a `Project`. Two new injectable services now allow to do all that when a `Project` instance is not available.

See the [user manual](userguide/custom_gradle_types.html#service_injection) for how to inject services and the [`FileSystemOperations`](javadoc/org/gradle/api/file/FileSystemOperations.html) and [`ExecOperations`](javadoc/org/gradle/process/ExecOperations.html) api documentation for more details and examples.

<a name="worker-api-services"></a>
### Services available in Worker API actions

The following services are now available for injection in [tasks that use the Worker API](userguide/custom_tasks.html#worker_api) and the `WorkAction` classes:
- [ObjectFactory](javadoc/org/gradle/api/model/ObjectFactory.html)
- [ProviderFactory](javadoc/org/gradle/api/provider/ProviderFactory.html)
- [ProjectLayout](javadoc/org/gradle/api/file/ProjectLayout.html)
- [FileSystemOperations](javadoc/org/gradle/api/file/FileSystemOperations.html)
- [ExecOperations](javadoc/org/gradle/process/Execoperations.html)

These services can be injected by adding them to the constructor arguments of the `WorkAction` implementation:

```
abstract class ReverseFile implements WorkAction<ReverseParameters> {
    private final FileSystemOperations fileSystemOperations

    @Inject
    public ReverseFile(FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations
    }

    @Override
    public void execute() {
        fileSystemOperations.copy {
            from parameters.fileToReverse
            into parameters.destinationDir
            filter { String line -> line.reverse() }
        }
    }
}
```

See the [user manual](userguide/custom_gradle_types.html#service_injection) for further information on injecting services into custom Gradle types.

### New convenience methods for bridging between a `RegularFileProperty` or `DirectoryProperty` and a `File`

- [`DirectoryProperty.fileValue(File)`](javadoc/org/gradle/api/file/DirectoryProperty.html#fileValue-java.io.File-)
- [`DirectoryProperty.fileProvider(Provider<File>)`](javadoc/org/gradle/api/file/DirectoryProperty.html#fileProvider-org.gradle.api.provider.Provider-)
- [`RegularFileProperty.fileValue(File)`](javadoc/org/gradle/api/file/RegularFileProperty.html#fileValue-java.io.File-)
- [`RegularFileProperty.fileProvider​(Provider<File>)`](javadoc/org/gradle/api/file/RegularFileProperty.html#fileProvider-org.gradle.api.provider.Provider-)
- [`ProjectLayout.dir(Provider<File>)`](javadoc/org/gradle/api/file/ProjectLayout.html#dir-org.gradle.api.provider.Provider-)

<a name="native-ecosystem"></a>
## Features for native developers

### IntelliSense support for C++17 and latest C++ standard within Visual Studio

Gradle will now generate IDE solutions honoring the C++17 `/std:cpp17` and latest C++ standard `/std:cpplatest` compiler flags.

Visual Studio IntelliSense will help you write great code with these new standards.

### Support for Visual Studio 2019

Gradle now supports building application and libraries with [Visual Studio 2019](https://docs.microsoft.com/en-us/visualstudio/releases/2019/release-notes).

## Features for Gradle tooling providers

### Test output as progress event

Users of the latest Tooling API can listen to the new [`TestOutputEvent`](javadoc/org/gradle/tooling/events/test/TestLauncher.html) progress event type that contains the test output.
With that, tooling providers can use the [`TestLauncher`](javadoc/org/gradle/tooling/TestLauncher.html) API to launch tests and show the test output on the fly.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### C++ and Swift support

We promoted all the new native plugins (i.e. `cpp-application`, `cpp-library`, `cpp-unit-test`, `swift-application`, `swift-library`, `xctest`, `visual-studio` and `xcode`).
Note that all [software model plugins will be phased out](https://blog.gradle.org/state-and-future-of-the-gradle-software-model) instead of being promoted.

### New incremental tasks API

The new [`InputChanges`](dsl/org.gradle.work.InputChanges.html) API for implementing incremental tasks has been promoted.
See the [user manual](userguide/custom_tasks.html#incremental_tasks) for more information.

### IDE integration types and APIs.
 
We promoted all API elements in `ide` and `tooling-api` sub-projects that were introduced before Gradle 5.5.

### Some long existing incubating features have been promoted

* All pre-5.0 incubating APIs have been promoted.
* The [lazy configuration API](userguide/lazy_configuration.html) has been promoted.
* Enabling [strict task validation](javadoc/org/gradle/plugin/devel/tasks/ValidateTaskProperties.html#setEnableStricterValidation-boolean-) has been promoted.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
