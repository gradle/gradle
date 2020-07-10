
The Gradle team is excited to announce Gradle @version@.

This release introduces a major performance optimization as an experimental opt-in. [Configuration caching](#configuration-caching) allows Gradle to skip the configuration phase of the build and start executing tasks as soon as possible. 

Other improvements in this release include [Java compilation --release flag support](#javacompile-release), [conventions for handling user-provided credentials](#credentials) and a number of smaller changes and [bug fixes](#fixed-issues). 

We would like to thank the following community contributors to this release of Gradle:

[SheliakLyr](https://github.com/SheliakLyr),
[Danny Thomas](https://github.com/DanielThomas),
[Daiki Hirabayashi](https://github.com/dhirabayashi),
[Sebastian Schuberth](https://github.com/sschuberth),
[Frieder Bluemle](https://github.com/friederbluemle),
[Brick Tamland](https://github.com/mleveill),
[Stefan Oehme](https://github.com/oehme),
[Yurii Serhiichuk](https://github.com/xSAVIKx),
[JunHyung Lim](https://github.com/EntryPointKR),
[Igor Dvorzhak](https://github.com/medb),
and [Leonid Gaiazov](https://github.com/gaiazov).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

## Performance improvements

Fast feedback for local incremental builds is crucial for developer productivity. This is especially true when your IDE uses Gradle to build and run tests for your project, which IntelliJ IDEA does by default. This scenario has been the primary focus of performance improvements since Gradle 6.5 and will continue for the next several Gradle releases.

<a name="configuration-caching"></a>
### Configuration caching

Before running any task, Gradle needs to run the [configuration phase](userguide/build_lifecycle.html#build_lifecycle). Currently, this is done on every build invocation and can incur a noticeable overhead, especially in large projects.

The configuration cache significantly improves build performance by caching the result of the configuration phase and reusing it for subsequent builds. Using the configuration cache, Gradle can skip the configuration phase entirely when nothing that affects the build configuration has changed.

Additionally, Gradle is able to optimize task execution when configuration caching is enabled and execute more tasks in parallel by default.

Note that configuration caching is different from the [build cache](userguide/build_cache.html#build_cache), which caches outputs produced by the build. The configuration cache captures only the state of the configuration phase.

IDE sync and import does not currently benefit from configuration caching.

This feature is considered *highly experimental* and not enabled by default or recommended for production use. Tasks and plugins usually require changes to meet the [requirements](userguide/configuration_cache.html#config_cache:requirements) to use configuration caching. Not all [core Gradle plugins](userguide/configuration_cache.html#config_cache:plugins:core) have been updated yet and some features are [not yet implemented](userguide/configuration_cache.html#config_cache:not_yet_implemented).
Your build may likely require changes.

You can enable this experimental feature by supplying the parameter `--configuration-cache` on the command-line or adding it to your run configuration. If your build is incompatible with configuration caching, Gradle will generate a report describing the problems found.

Learn more about this new feature and its impact in the [Configuration Cache](userguide/configuration_cache.html) documentation.

### Stability improvements for file-system watching

Gradle 6.5 introduced an [experimental opt-in](https://docs.gradle.org/6.5/release-notes.html#file-watching) that improves performance of local incremental builds by watching for file-system changes.

This release brings a number of stability improvements for file-system watching when used with composite builds or large projects on Windows and macOS. Gradle will now report better errors when you enable file-system watching on unsupported systems. 

### Improved cache hits with normalized runtime classpaths

For [up-to-date checks](userguide/more_about_tasks.html#sec:up_to_date_checks) and the [build cache](userguide/build_cache.html), Gradle needs to determine if two task input properties have the same value. In order to do so, Gradle first normalizes both inputs and then compares the result. 

Runtime classpath analysis can now inspect manifest and `META-INF` properties files, ignore changes to comments, and selectively ignore attributes or properties that don't impact the runtime classpath.

```groovy
normalization {
    runtimeClasspath {
        metaInf {
            ignoreAttribute("Implementation-Version")
            ignoreProperty("timestamp")
        }
    }
}
```

This improves the likelihood of build cache hits when any zip file on the classpath is regenerated and only differs by unimportant values or comments.  The most common case where this sort of normalization can be useful is with jar files, but it can be applied to any zip file on the classpath--such as aar, war, or apk files.

See the [user manual](userguide/more_about_tasks.html#sec:meta_inf_normalization) for further information.  Note that this API is incubating and will likely change in future releases as support is expanded for normalizing properties files outside of the `META-INF` directory.

## New features and usability improvements

<a name="credentials"></a>
### Conventions for handling user-provided credentials

Gradle builds sometimes require users to supply credentials that are used by tasks. For example, credentials might be required to authenticate with an artifact repository in order to publish an artifact. It's a good practice to keep credentials outside the build script.

This release includes a new API for credentials that makes working with credentials easier by establishing a convention to supply credentials using `gradle.properties`. It also introduces fail-fast behavior when Gradle knows that the build will need credentials at some point and the credentials are missing.

Starting from this release, you can easily externalize credentials used for authentication to an artifact repository:

```groovy
repositories {
    maven {
        name = 'mySecureRepository'
        credentials(PasswordCredentials)
        // url = uri(<<some repository url>>)
    }
}
```

The credentials for `mySecureRepository` will be searched for in Gradle properties with the names `mySecureRepositoryUsername` and `mySecureRepositoryPassword`.

For more details on using the new API to authenticate with artifact repositories, see the [user manual](userguide/declaring_repositories.html#sec:handling_credentials)
section as well as an updated [sample](samples/sample_publishing_credentials.html).

You can also use the new [provider API](javadoc/org/gradle/api/provider/ProviderFactory.html#credentials-java.lang.Class-java.lang.String-) directly to supply credentials to an external tool:

```groovy
tasks.register('login', Exec) {
    def loginProvider = 
providers.credentials(PasswordCredentials, 'login')
    inputs.property('credentials', loginProvider)
    doFirst {
       PasswordCredentials loginCredentials = loginProvider.get()
       // use credentials
    }
}
```

The credentials for above will be searched for with the names `loginUsername` and `loginPassword`.

See the updated [sample](samples/sample_publishing_credentials.html) for more details.

## Dependency management improvements

### Reproducible Gradle Module Metadata 

[Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html) is a format used to serialize the Gradle component model, similar to but more powerful than Maven’s POM. 
By default, the Gradle Module Metadata file contains a build identifier field which defaults to a unique ID generated during build execution. This behaviour can now be disabled at the publication level, allowing users to opt-in for a reproducible Gradle Module Metadata file. This enables downstream tasks to consider it up-to-date.

```groovy
main(MavenPublication) {
    from components.java
    withoutBuildIdentifier()
}
```

See the documentation for more information on [Gradle Module Metadata generation](userguide/publishing_gradle_module_metadata.html#sub:gmm-reproducible).

### Variant-aware dependency substitution rules

Previously, it wasn't possible for Gradle to substitute a dependency which uses a classifier with a dependency without a classifier, nor was it possible to substitute a dependency _without_ classifier with a classified dependency.
Similarly, dependencies with attributes (typically "platform" dependencies) or capabilities (typically "test fixtures" dependencies) could not be substituted.

Gradle now supports substitution of dependencies with classifiers, attributes or capabilities.
Gradle's dependency substitution API has been enriched to cover those cases.

See the documentation on [variant-aware substitution](userguide/resolution_rules.html#sec:variant_aware_substitutions) for details.

<a name="javacompile-release"></a>
### Support for the `--release` flag in Java compilation

Java 9 introduced cross compilation support with the `--release` flag on the Java compiler.
This option tells the compiler to produce bytecode for an earlier version of Java combined with the guarantee that the code does not use any APIs from later versions.

With this release, Gradle now supports the `--release` option for Java compilation on the `CompileOptions` of `JavaCompile` tasks.

See the section on [cross compilation](userguide/building_java_projects.html#sec:java_cross_compilation) for details.

Support for this flag on the Javadoc tasks and other JVM language compilation will be added in future releases.

## Improvements for plugin authors

### Injectable `ArchiveOperations` service

Previously, it was only possible to create a `FileTree` for a ZIP or TAR archive by using the APIs provided by a `Project`.

However, a `Project` object is not always available, for example in [worker actions](userguide/custom_tasks.html#worker_api) or when using the [configuration cache](userguide/configuration_cache.html#config_cache:requirements:use_project_during_execution).

The new `ArchiveOperations` service has [zipTree()](javadoc/org/gradle/api/file/ArchiveOperations.html#zipTree-java.lang.Object-) and [tarTree()](javadoc/org/gradle/api/file/ArchiveOperations.html#tarTree-java.lang.Object-) methods for creating read-only `FileTree` instances respectively for ZIP and TAR archives.

See the [user manual](userguide/custom_gradle_types.html#service_injection) for how to inject services and the [`ArchiveOperations`](javadoc/org/gradle/api/file/ArchiveOperations.html) API documentation for more details and examples.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
