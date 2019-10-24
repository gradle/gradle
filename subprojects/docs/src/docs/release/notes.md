The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

[Mark Nordhoff](https://github.com/MarkNordhoff)

<!-- 
## 1

details of 1

## 2

details of 2

<<<<<<< HEAD
## n
-->
=======
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
[lingocoder](https://github.com/lingocoder),
and [Robert Stupp](https://github.com/snazy).
>>>>>>> release

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<<<<<<< HEAD
<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 
=======
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

### Publication of SHA256 and SHA512 checksums

If you use the `maven-publish` or `ivy-publish` plugins, Gradle will now automatically upload SHA256 and SHA512 signatures, in addition to the traditional but unsecure MD5 and SHA1 signatures.

Publication of SHA256 and SHA512 files is _not_ supported by the deprecated `maven` plugin but works with the legacy `uploadArchives` task for Ivy repositories.

In addition, the Gradle Module Metadata file also includes SHA256 and SHA512 checksums on referenced artifacts.

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

The `Directory` and `DirectoryProperty` types both have a new `files(Object...)` method to create fixed `FileCollection` instances resolving files relative to the referenced directory.
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
>>>>>>> release

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

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
