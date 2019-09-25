The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

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

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

<!-- 
## Cancellable custom tasks

When a build is cancelled (e.g. using CTRL+C), the threads executing each task are interrupted.
Task authors only need to make their tasks respond to interrupts in order for the task to be cancellable.

details of 1

## 2

details of 2

## n
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

### Compatibility Notes

* Java version between 8 and 13 is required to execute Gradle.
Java 6 and 7 are also supported for forked test execution.
Java 14 and later versions are not supported.
* This version of Gradle is tested with Android Gradle Plugin versions 3.4, 3.5 and 3.6.
Earlier AGP versions are not supported.
* Kotlin versions between 1.3.21 and 1.3.50 are tested.
Earlier Kotlin versions are not supported.

## Endorse strict versions from platforms by default

When depending on a platform component, Gradle will automatically _endorse_ strict versions from the platform.
This means that all _strict_ constraints defined in the platform will automatically be added to your dependency graph _as if they were first level constraints_.
This behavior can be opted out by calling the `doNotEndorseStrictVersions()` method:

```
dependencies {
    implementation(platform(project(':platform'))) {
       doNotEndorseStrictVersions()
    }
    ...
}
```

More information about [strict version constraints](userguide/rich_versions.adoc#rich-version-constraints) can be found in the documentation.

## Support for Java 13 EA

Gradle now supports running with Java 13 EA (tested with OpenJDK build 13-ea+32).

## Javadoc and sources packaging and publishing is now a built-in feature of the Java plugins 

You can now activate Javadoc and sources publishing for a Java Library or Java project:

```
java {
    publishJavadoc()
    publishSources()
}
```

Using the `maven-publish` or `ivy-publish` plugin, this will not only automatically create and publish a `-javadoc.jar` and `-sources.jar`, but also publish the information that these exist as variants in Gradle Module Metadata.
This means that you can query for the Javadoc or sources _variant_ of a module and also retrieve the Javadoc (or sources) of its dependencies.
This also works in multi-projects.
If activated, a Java and Java Library project automatically provides the `javadocJar` and `sourcesJar` tasks.

## Problems with task definitions called out during build

Tasks that define their inputs or outputs incorrectly can cause problems when running incremental builds or when using the the build cache.
As part of an ongoing effort to bring these problems to light Gradle now displays these problems as deprecation warnings during the build.
When such problems are encountered, Gradle will show warnings like these on the console:

```
> Task :myTask
Property 'inputDirectory' is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.
Property 'outputFile' is not annotated with an input or output annotation. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.
```

Note that you need to run the build with `--warning-mode=all` to see the individual problems.

The warnings also show up in build scans.

## More robust file deletion on Windows

Deleting complex file hierarchies on Windows can sometimes be tricky, and errors like `Unable to delete directory ...` can happen at times.
To avoid these errors, Gradle has been employing workarounds in some but not all cases when it had to remove files.
From now on Gradle uses these workarounds every time it removes file hierarchies.
The two most important cases that are now covered are cleaning stale output files of a task, and removing previous outputs before loading fresh ones from the build cache.

## Update to latest Zinc compiler

The Zinc compiler has been upgraded to version 1.2.5. Gradle no longer supports building for Scala 2.9. 

This fixes some Scala incremental compilation bugs and improves performance. 

The minimum Zinc compiler supported by Gradle is 1.2.0 and the maximum version is 1.2.5.

## Automatic shortening of long classpaths on Windows

When Gradle detects that a Java process command-line will exceed Windows's 32,768 character limit, Gradle will now attempt to shorten the command-line by passing the classpath of the Java application via a "classpath jar". 
The classpath jar contains a manifest with the full classpath of the application and the command-line's classpath will only consist of the classpath jar.  If this doesn't shorten the command-line enough, the Java process will still fail to start.

If the command-line is not long enough to require shortening, Gradle will not change the command-line arguments for the Java process.

## `gradle init` generates `.gitattributes` file

To ensure Windows batch scripts retain the appropriate line endings, `gradle init` now generates a `.gitattributes` file.

This was contributed by [Tom Eyckmans](https://github.com/teyckmans).

## Improved Java/Groovy compilation avoidance

The class analysis used as part of the incremental compilation will now exclude any classes that are an implementation details.
It will help Gradle narrow the number of classes to recompile implementation detail changes.

## Features for plugin authors

### ConfigurableFileTree managed property methods

Gradle 5.5 introduced the concept of a _managed property_ for tasks and other types. A managed property is a property whose getters and setters are abstract, and for these properties
Gradle provides an implementation of the getters and setters. This simplifies plugin implementation by removing a bunch of boilerplate.

In this release, it is possible for a task or other custom types to have an abstract read-only property of type `ConfigurableFileTree`.  

### NamedDomainObjectContainer<T> managed property methods

In this release, it is also possible for a task or other custom types to have an abstract read-only property of type `NamedDomainObjectContainer<T>`. 

### File and directory property methods

TBD - Added `fileValue()` and `fileProvider()` methods.

### New `ConfigurableFileTree` and `FileCollection` factories

Previously, it was only possible to create a `ConfigurableFileTree` or a fixed `FileCollection` by using the APIs provided by a `Project`.
However, a `Project` object is not always available, for example in a project extension object or a [worker action](userguide/custom_tasks.html#worker_api).

The `ObjectFactory` service now has a [fileTree()](javadoc/org/gradle/api/model/ObjectFactory.html#fileTree--) method for creating `ConfigurableFileTree` instances.
The `Directory` and `DirectoryProperty` types now both have a `files(Object...)` method, respectively [`Directory.files(Object...)`](javadoc/org/gradle/api/file/Directory.html#files-java.lang.Object++...++-) and [`DirectoryProperty.files(Object...)`](javadoc/org/gradle/api/file/DirectoryProperty.html#files-java.lang.Object++...++-), for creating fixed `FileCollection` instances resolving files relatively to the referenced directory.

See the user manual for how to [inject services]((userguide/custom_gradle_types.html#service_injection)) and how to [work with files in lazy properties](userguide/lazy_configuration.html#sec:working_with_files_in_lazy_properties). 

### Injected `FileSystemOperations` and `ExecOperations` services

In the same vein, doing file system operations such as `copy()`, `sync()` and `delete()` or running external processes via `exec()` and `javaexec()` was only possible by using the APIs provided by a `Project`. Two new injectable services now allow to do all that when a `Project` is not available.

See the [user manual](userguide/custom_gradle_types.html#service_injection) for how to inject services and the [`FileSystemOperations`](javadoc/org/gradle/api/file/FileSystemOperations.html) and [`ExecOperations`](javadoc/org/gradle/process/ExecOperations.html) api documentation for more details and examples.

### Services available to Worker API actions

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

## Security

### Improving integrity of builds

Gradle will now warn when resolving dependencies, text resources, and script plugins with the insecure HTTP protocol.

We encourage all users to switch to using HTTPS instead of HTTP.
Free HTTPS certificates for your artifact server can be acquired from [Lets Encrypt](https://letsencrypt.org/).
The use of HTTPS is important for [protecting your supply chain and the entire JVM ecosystem](https://medium.com/bugbountywriteup/want-to-take-over-the-java-ecosystem-all-you-need-is-a-mitm-1fc329d898fb?source=friends_link&sk=3c99970c55a899ad9ef41f126efcde0e).

For users that require the use of HTTP, Gradle has several new APIs to continue to allow HTTP.

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

```groovy
apply from: resources.text.fromInsecureUri("http://my-company.example/external.gradle")
```

On January 13th, 2020 through January 15th, 2020, some of the most widely used artifact servers in the JVM ecosystem
will drop support for HTTP and will only support HTTPS. Their announcements can be found below:

 - [Sonatype: Maven Central](https://central.sonatype.org/articles/2019/Apr/30/http-access-to-repo1mavenorg-and-repomavenapacheorg-is-being-deprecated/)
 - [JFrog: JCenter](https://jfrog.com/blog/secure-jcenter-with-https/)
 - [Pivotal: Spring](https://spring.io/blog/2019/09/16/goodbye-http-repo-spring-use-https)

The Gradle team will be making an announcement soon about use of HTTP with `services.gradle.org` and `plugins.gradle.org`.

### Signing Plugin now uses SHA512 instead of SHA1

This was contributed by [Vladimir Sitnikov](https://github.com/vlsi).

A low severity security issue was reported in the Gradle signing plugin.

More information can be found below:

 - [Gradle GitHub Advisory](https://github.com/gradle/gradle/security/advisories/GHSA-mrm8-42q4-6rm7)
 - [CVE-2019-16370](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2019-16370)
 
### Support for in-memory signing with subkeys

Gradle now supports [in-memory signing](userguide/signing_plugin.html#sec:in-memory-keys) with subkeys.

This was contributed by [szhem](https://github.com/szhem).

## Wrapper reports download progress

Gradle now reports the progress of the distribution downloaded. 

Initially contributed by [Artur Dryomov](https://github.com/ming13).

## Features for native developers

### IntelliSense support for C++17 and latest C++ standard within Visual Studio

Gradle will now generate IDE solution honoring the C++17 `/std:cpp17` and latest C++ standard `/std:cpplatest` compiler flag.
The Visual Studio IntelliSense will help you write great code with those new standard.

### Support for Visual Studio 2019

Gradle now officially supports building application and libraries with Visual Studio 2019.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### C++ and Swift support

We promoted all the new native plugins (i.e. `cpp-application`, `cpp-library`, `cpp-unit-test`, `swift-application`, `swift-library`, `xctest`, `visual-studio` and `xcode`).
Note that all [software model plugins are still incubating and will be phased out](https://blog.gradle.org/state-and-future-of-the-gradle-software-model) instead of being promoted.

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
