The Gradle team is excited to announce Gradle @version@.

This release adds [several usability improvements](#usability), such as toolchain support for Scala projects, and [improves build cache hits](#performance) between operating systems.

There are also changes to make the [remote HTTP build cache more resilient](#http-build-cache) when encountering problems and several [bug fixes](#fixed-issues) .

We would like to thank the following community members for their contributions to this release of Gradle:

[Ned Twigg](https://github.com/nedtwigg),
[Oliver Kopp](https://github.com/koppor),
[Björn Kautler](https://github.com/Vampire),
[naftalmm](https://github.com/naftalmm),
[Peter Runge](https://github.com/causalnet),
[Konstantin Gribov](https://github.com/grossws),
[Zoroark](https://github.com/utybo),
[Stefan Oehme](https://github.com/oehme),
[Martin Kealey](https://github.com/kurahaupo),
[KotlinIsland](https://github.com/KotlinIsland),
[Herbert von Broeuschmeul](https://github.com/HvB)

## Upgrade instructions
Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<a name="usability"></a>
## New features and usability improvements

### Java toolchain support for Scala projects

[Java toolchains](userguide/toolchains.html) provide an easy way to declare which Java version your project should be built with. By default, Gradle will [detect installed JDKs](userguide/toolchains.html#sec:auto_detection) or automatically download new toolchain versions.

With this release, toolchain support has been added to the [Scala plugin](userguide/scala_plugin.html).

### Preserving escape sequences when copying files

Previously, it was impossible to prevent Gradle from expanding escape sequences in a copied file when a `Copy` task also used [`expand(Map)`](dsl/org.gradle.api.tasks.Copy.html#org.gradle.api.tasks.Copy:expand(java.util.Map)). The default behavior is to convert each escape sequence into the corresponding character in the destination file. For example, the literal string `\t` becomes a tab character. This might be undesirable when escape sequences in processed files should be preserved as-is.

This release adds [`Copy.expand(Map,Action)`](dsl/org.gradle.api.tasks.Copy.html#org.gradle.api.tasks.Copy:expand(java.util.Map,%20org.gradle.api.Action)) that allows you to disable the automatic conversion of escape sequences.

```groovy
processResources {
    expand([myProp: "myValue"]) {
        // Do not replace \n or \t with characters
        escapeBackslash = true
    }
}
```

This method is available to all tasks and specs of type [`ContentFilterable`](javadoc/org/gradle/api/file/ContentFilterable.html).

### Improved credentials handling for HTTP header-based authentication

Like for password credentials and AWS credentials for repositories, Gradle now looks for credentials for repositories that use [HTTP header-based authentication](userguide/declaring_repositories.html#sec:handling_credentials) in Gradle properties.

If the name of your project's repository is `mySecureRepository`, Gradle will search for properties with the names `mySecureRepositoryAuthHeaderName` and `mySecureRepositoryAuthHeaderValue` once you've configured the repository to use [`HttpHeaderCredentials`](dsl/org.gradle.api.credentials.HttpHeaderCredentials.html#org.gradle.api.credentials.HttpHeaderCredentials):

```
repositories {
    maven {
        name = 'mySecureRepository'
        credentials(HttpHeaderCredentials)
        // url = uri(<<some repository url>>)
    }
}
```

### `dependencies` and `dependencyInsight` support configuration name abbreviation

The [dependencies task](userguide/viewing_debugging_dependencies.html#sec:listing_dependencies) and [depedencyInsight task](userguide/viewing_debugging_dependencies.html#sec:identifying_reason_dependency_selection) reports can be used to list the dependencies used by your project and to identify why a particular version of a dependency was selected.

When using those reports from the command line and selecting a configuration using the `--configuration` parameter, you can now use an abbreviated camelCase notation in the [same way as subproject and task names](userguide/command_line_interface.html#sec:name_abbreviation).

For example, the command-line `gradle dependencies --configuration tRC` can be used instead of `gradle dependencies --configuration testRuntimeClasspath` as long as the abbreviation `tRC` is unambiguous.

### Version catalog improvements

[Version catalog](userguide/platforms.html#sub:version-catalog-declaration) is a [feature preview](userguide/feature_lifecycle.html#feature_preview) that provides a convenient API for referencing dependencies and their versions.

#### Declaring sub-accessors

In previous Gradle releases, it wasn't possible to declare a [version catalog](userguide/platforms.html#sub:version-catalog) where an alias would also contain sub-aliases.
For example, it wasn't possible to declare both an alias `jackson` and `jackson.xml`, you would have had to create aliases `jackson.core` and `jackson.xml`.

This limitation is now lifted.

#### Declaring plugin versions

Version catalogs already supported declaring versions of your libraries, but they were not accessible to the `plugins` and `buildscript` blocks.
This limitation is now lifted, and it's possible to declare plugins, for example in the TOML file:
```toml
[versions]
jmh = "0.6.5"
[plugins]
jmh = { id = "me.champeau.jmh", version.ref="jmh" }
```
which allows using them in the plugins block like this:
```kotlin
plugins {
    alias(libs.plugins.jmh)
}
```

<a name="performance"></a>
## Performance improvements

### More cache hits between operating systems

For [up-to-date](userguide/more_about_tasks.html#sec:up_to_date_checks) checks and the [build cache](userguide/build_cache.html), Gradle needs to determine if two directory structures contain the same contents.  When line endings in text files differ (e.g. when checking out source code on different operating systems) this can appear like the inputs of a task are different, even though the task may not actually produce different outputs.  This difference can cause tasks to re-execute unnecessarily, producing identical outputs that could otherwise have been retrieved from the build cache.

A new annotation has been introduced that allows task authors to specify that an input should not be sensitive to differences in line endings.  Inputs annotated with [@InputFiles](javadoc/org/gradle/api/tasks/InputFiles.html), [@InputDirectory](javadoc/org/gradle/api/tasks/InputDirectory.html) or [@Classpath](javadoc/org/gradle/api/tasks/Classpath.html) can additionally be annotated with [@NormalizeLineEndings](javadoc/org/gradle/work/NormalizeLineEndings.html) to specify that line endings in text files should be normalized during build cache and up-to-date checks so that files that only differ by line endings will be considered identical.  Binary files, on the other hand, will not be affected by this annotation.

```groovy
abstract class MyTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @NormalizeLineEndings
    ConfigurableFileCollection getInputFiles();
}
```

The [JavaCompile](javadoc/org/gradle/api/tasks/compile/JavaCompile.html) task has been updated to normalize line endings in source files when doing up-to-date checks and build cache key calculations.

See the [User manual](userguide/more_about_tasks.html#sec:up_to_date_checks) for more information.

### Configuration cache support for Groovy and Scala projects

Projects that are written in Groovy or Scala can enable the experimental [configuration cache](userguide/configuration_cache.html) without generating errors from the built-in `groovy` and `scala` plugins. Configuration caching is a feature that reduces build times by caching the result of the configuration phase and reusing the result for subsequent builds.

See the full set of [supported plugins](userguide/configuration_cache.html#config_cache:plugins).

<a name="http-build-cache"></a>
## Remote build cache reliability improvements

The [Gradle build cache](userguide/build_cache.html) is a cache mechanism that aims to save time by reusing outputs produced by other builds. A remote build cache works by storing build outputs and allowing builds to fetch these outputs from the cache when it is determined that inputs have not changed, avoiding the expensive work of regenerating them.

This release improves the reliability of interactions with a remote build cache.

### Automatic retry of uploads on temporary network error

Previously, only load (i.e. GET) requests that failed during request transmission would be automatically retried. Now, store (i.e. PUT) requests are also retried.

This prevents temporary problems, such as connection drops, read or write timeouts, or low-level network failures, to cause cache operations to fail and disable the remote cache for the remainder of the build.

Requests will be retried up to 3 times. If the problem persists, the cache operation will fail and the remote cache will be disabled for the remainder of the build.

### Follow redirects by default

Redirect responses are now followed by default with no additional configuration needed.

This can be leveraged to gracefully migrate to new cache locations, utilize some form of request signing to read to and write from other systems, or reroute requests from certain users or geographies to other locations.

For more information on the effect of different types of redirects, consult the [User manual](userguide/build_cache.html#sec:build_cache_redirects).

### Use Expect-Continue to avoid redundant uploads

It is now possible to opt-in to the use of [Expect-Continue](https://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html#sec8.2.3) for upload requests.

This is useful when build cache upload requests are regularly rejected or redirected by the server,
as it avoids the overhead of transmitting the large file just to have it rejected or redirected.

Consult the [User manual](userguide/build_cache.html#sec:build_cache_expect_continue) for more on the use of expect-continue.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
