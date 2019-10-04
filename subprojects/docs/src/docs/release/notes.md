The Gradle team is excited to announce a new major version of Gradle, @version@.

This release features [vastly improved feature set in dependency management](#improved-dependency-management-feature-set), [support for running Gradle with Java 13](#support-for-java-13), [built-in packaging and publishing of javadoc and sources](#javadoc-and-sources-packaging-and-publishing-is-now-a-built-in-feature-of-the-java-plugins), ... [n](), and more.

These release notes list what's new since Gradle 5.6, but you can review the [highlights since Gradle 5.0 here](https://gradle.org/whats-new/gradle-6/).

Read the [Gradle 6.0 upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.x.

We would like to thank the following community contributors to this release of Gradle:
[Nathan Strong](https://github.com/NathanStrong-Tripwire),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Tetsuya Ikenaga](https://github.com/ikngtty),
[Sebastian Schuberth](https://github.com/sschuberth),
[Andrey Mischenko](https://github.com/gildor),
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

other versions may or may not work.

## Improved Dependency Management feature set

Note that some of the features below were released in stages in Gradle versions before 6.0.
However, with Gradle 6.0 they are now stable, production ready and available between projects _and_ binary dependencies.

### Rich versions declaration

When declaring a dependency version, [more context](userguide/rich_versions.html) can be provided, including preferences within ranges, strict requirements (including downgrades), rejected versions and human readable descriptions for why a dependency is used.

### Dependency constraints

Enables [influencing the version](userguide/dependency_constraints.html) of transitive dependencies.

### Capabilities

Gives the ability to [configure](userguide/component_capabilities.html), [detect and resolve implementation conflicts](userguide/dependency_capability_conflict.html).
A well-known example in the JVM world: competing logging implementations.

### Support for platforms

Offers an easy way to [recommend and share versions](userguide/platforms.html) between projects.
With native Gradle platforms, rich versions declaration is available and strict versions are endorsed.
Projects can also leverage the [integration with Maven BOMs](userguide/platforms.html#sub:bom_import).

### Alignment of dependency versions

Provides the ability to declare that a set of dependencies belong together and need to be [aligned](userguide/dependency_version_alignment.html).
A well-known example in the JVM world: the Jackson library and its many parts.

### Feature variants instead of optional dependencies

Provides the ability to model [optional features](userguide/feature_variants.html) of a library, each with their own dependencies

### First class test fixtures support

Allows to [create and publish test fixtures](userguide/java_testing.html#sec:java_test_fixtures), enabling their consumption in other projects

### Publishing and consuming Gradle Module Metadata

The [publication of Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html) is now the default when using the `maven-publish` or `ivy-publish` plugins.

### Component Metadata Rules

Allows you to [enrich traditional metadata](userguide/component_metadata_rules.html) with information that could not be published before (dependency constraints, rich versions, capabilities, …).
Includes the possibility to [add Gradle variants](userguide/component_metadata_rules.html#making_variants_published_as_classified_jars_explicit), mapping to additional published artifacts.

### Improved documentation

Dependency management documentation has been reorganised and structured around use cases to help users find the information they need faster.

## Faster incremental Java compilation

When analyzing the impact of a changed class, the incremental compiler can now exclude classes that are an implementation detail of another class.  This limits the number of classes that need to be recompiled.

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

## Support for Java 13

Gradle now supports running with [Java 13](https://openjdk.java.net/projects/jdk/13/).

## Built-in javadoc and sources packaging and publishing

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

## Update to newer Scala Zinc compiler

The Zinc compiler has been upgraded to version 1.2.5. Gradle no longer supports building for Scala 2.9. 

This fixes some Scala incremental compilation bugs and improves performance. 

The minimum Zinc compiler supported by Gradle is 1.2.0 and the maximum version is 1.2.5.

To make it easier to select the version of the Zinc compiler that's compatible with Gradle, you can now configure a `zincVersion` property:
```
scala {
    zincVersion = "1.2.1"
}
```

Please note that the coordinates for the supported version of Zinc has changed since Zinc 1.0. You may no longer use a `com.typesafe.zinc:zinc` dependency.

## Automatic shortening of long command-lines for Java applications on Windows

When Gradle detects that a Java process command-line will exceed Windows's 32,768 character limit, Gradle will attempt to shorten the command-line by passing the classpath of the Java application via a ["classpath jar"](https://docs.oracle.com/javase/tutorial/deployment/jar/downman.html). 

The classpath jar contains a manifest with the full classpath of the application. Gradle will only pass the generated jar on the command-line to the application. If the command-line is still too long, the Java process will fail to start as before.

If the command-line does not require shortening, Gradle will not change the command-line arguments for the Java process.

## Problems with task definitions called out during build

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

## Smaller quality of life improvements

### More consistent & robust file deletion on Windows

Deleting complex file hierarchies on Windows can sometimes fail with errors like `Unable to delete directory ...`.  In the past, Gradle has used workarounds in some but not all cases when deleting files.

Gradle now deletes files in a consistent way that should avoid failures when cleaning output files of a task.

### `gradle init` generates `.gitattributes` file

To ensure Windows batch scripts retain the appropriate line endings, `gradle init` now generates a `.gitattributes` file.

This was contributed by [Tom Eyckmans](https://github.com/teyckmans).

### Wrapper reports download progress

Gradle now reports the progress of the distribution downloaded. 

Initially contributed by [Artur Dryomov](https://github.com/ming13).

## Features for plugin authors

### New types available as managed properties

Gradle 5.5 introduced the concept of a [_managed property_ for tasks and other types](userguide/custom_gradle_types.html#managed_properties). Gradle provides an implementation of the getters and setters for a managed property. This simplifies plugin implementation by removing a bunch of boilerplate.

In this release, it is possible for a task or other custom types to have an abstract read-only property of type `ConfigurableFileTree` and `NamedDomainObjectContainer<T>`. 

### New `ConfigurableFileTree` and `FileCollection` factory methods

Previously, it was only possible to create a `ConfigurableFileTree` or a fixed `FileCollection` by using the APIs provided by a `Project`.

However, a `Project` object is not always available, for example in a project extension object or a [worker action](userguide/custom_tasks.html#worker_api).

The `ObjectFactory` service now has a [fileTree()](javadoc/org/gradle/api/model/ObjectFactory.html#fileTree--) method for creating `ConfigurableFileTree` instances.

The `Directory` and `DirectoryProperty` types now both have a `files(Object...)` method to create fixed `FileCollection` instances resolving files relativel to the referenced directory.
- [`Directory.files(Object...)`](javadoc/org/gradle/api/file/Directory.html#files-java.lang.Object++...++-)
- [`DirectoryProperty.files(Object...)`](javadoc/org/gradle/api/file/DirectoryProperty.html#files-java.lang.Object++...++-)

See the user manual for how to [inject services](userguide/custom_gradle_types.html#service_injection) and how to [work with files in lazy properties](userguide/lazy_configuration.html#sec:working_with_files_in_lazy_properties). 

### Injectable `FileSystemOperations` and `ExecOperations` services

In the same vein, doing file system operations such as `copy()`, `sync()` and `delete()` or running external processes via `exec()` and `javaexec()` was only possible by using the APIs provided by a `Project`. Two new injectable services now allow to do all that when a `Project` instance is not available.

See the [user manual](userguide/custom_gradle_types.html#service_injection) for how to inject services and the [`FileSystemOperations`](javadoc/org/gradle/api/file/FileSystemOperations.html) and [`ExecOperations`](javadoc/org/gradle/process/ExecOperations.html) api documentation for more details and examples.

### Services available in Worker API actions

The following services are now available for injection in `WorkAction` classes:
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

## Features for native developers

### IntelliSense support for C++17 and latest C++ standard within Visual Studio

Gradle will now generate IDE solution honoring the C++17 `/std:cpp17` and latest C++ standard `/std:cpplatest` compiler flag.
The Visual Studio IntelliSense will help you write great code with those new standard.

### Support for Visual Studio 2019

Gradle now officially supports building application and libraries with Visual Studio 2019.

## Security

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

On January 13th, 2020 through January 15th, 2020, some of the most widely used artifact servers in the JVM ecosystem
will drop support for HTTP and will only support HTTPS. Their announcements can be found below:

 - [Sonatype: Maven Central](https://central.sonatype.org/articles/2019/Apr/30/http-access-to-repo1mavenorg-and-repomavenapacheorg-is-being-deprecated/)
 - [JFrog: JCenter](https://jfrog.com/blog/secure-jcenter-with-https/)
 - [Pivotal: Spring](https://spring.io/blog/2019/09/16/goodbye-http-repo-spring-use-https)

The Gradle team will be making an announcement soon about use of HTTP with `services.gradle.org` and `plugins.gradle.org`.

### Signing Plugin now uses SHA512 instead of SHA1

A low severity security issue was reported in the Gradle signing plugin.

More information can be found below:

 - [Gradle GitHub Advisory](https://github.com/gradle/gradle/security/advisories/GHSA-mrm8-42q4-6rm7)
 - [CVE-2019-16370](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2019-16370)
 
This was contributed by [Vladimir Sitnikov](https://github.com/vlsi).

### Support for in-memory signing with subkeys

Gradle now supports [in-memory signing](userguide/signing_plugin.html#sec:in-memory-keys) with subkeys.

This was contributed by [szhem](https://github.com/szhem).

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
