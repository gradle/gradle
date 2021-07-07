The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
 [Peter Runge](https://github.com/causalnet)
 [Konstantin Gribov](https://github.com/grossws)
 [Zoroark](https://github.com/utybo)
 [Stefan Oehme](https://github.com/oehme)
 [KotlinIsland](https://github.com/KotlinIsland)

## Upgrade instructions
Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<a name="new-features-and-usability-improvements"></a>
## New features and usability improvements

<a name="VERSION-CATALOG-IMPROVEMENTS"></a>
### Version catalog improvements

In previous Gradle releases, it wasn't possible to declare a [version catalog](userguide/platforms.html#sub:version-catalog) where an alias would also contain sub-aliases.
For example, it wasn't possible to declare both an alias `jackson` and `jackson.xml`, you would have had to create aliases `jackson.core` and `jackson.xml`.
This limitation is now lifted.

### Improved credentials handling for HTTP Header-based authentication

It is now possible to provide credentials for HTTP header-based authentication [via properties](userguide/declaring_repositories.html#sec:handling_credentials) without additional configuration in the
build script.

## Support name abbreviation when specifying configuration for `dependencies` and `dependencyInsight`

When selecting configuration name using `--configuration` parameter from command line you can use camelCase notation like in subproject and task selection. This way `gradle dependencies --configuration tRC` could be used instead of `gradle dependencies --configuration testRuntimeClasspath` if `tRC` resolves to unique configuration within project where task is running.

<a name="http-build-cache-improvements"></a>
## HTTP build cache usage improvements

### Automatic retry of uploads on temporary network error

Previously, only load (i.e. GET) requests that failed during request transmission, after having established a TCP connection, would be automatically retried.
Now, store (i.e. PUT) requests are also retried.

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

Consult the [User manual](userguide/build_cache.html#sec:build_cache_expect_continue) for more on use of expect-continue.

## Performance Improvements

### More cache hits when switching platforms
For [up-to-date](userguide/more_about_tasks.html#sec:up_to_date_checks) checks and the [build cache](userguide/build_cache.html), Gradle needs to determine if two directory structures contain the same contents.  When line endings in text files differ (e.g. when checking out source code on different platforms) this can appear like the inputs of a task are different, even though the task may not actually produce different outputs.  This difference can cause tasks to re-execute unnecessarily, producing identical outputs that could otherwise have been retrieved from the build cache.

A new annotation has been introduced that allows task authors to specify that an input should not be sensitive to differences in line endings.  Inputs annotated with [@InputFiles](javadoc/org/gradle/api/tasks/InputFiles.html), [@InputDirectory](javadoc/org/gradle/api/tasks/InputDirectory.html) or [@Classpath](javadoc/org/gradle/api/tasks/Classpath.html) can additionally be annotated with [@NormalizeLineEndings](javadoc/org/gradle/work/NormalizeLineEndings.html) to specify that line endings in text files should be normalized during build cache and up-to-date checks so that files that only differ by line endings will be considered identical.  Binary files, on the other hand, will not be affected by this annotation.  Note that line ending normalization only applies to text files encoded with the ASCII character set or one of its supersets (e.g. UTF-8).  Text files encoded in a non-ASCII character set (e.g. UTF-16) will be treated as binary files and will not be subject to line ending normalization.

```groovy
class MyTask extends DefaultTask {
    @InputFiles
    @PathSensitive(@PathSensitivity.RELATIVE)
    @NormalizeLineEndings
    FileCollection inputFiles;
}
```

Similarly, there is a corresponding runtime API equivalent:

```groovy
tasks.register("myTask") {
    ext.inputFiles = files()
    inputs.files(inputFiles)
          .withPropertyName('inputFiles')
          .withPathSensitivity(PathSensitivity.RELATIVE)
          .normalizeLineEndings()
}
```

The [JavaCompile](javadoc/org/gradle/api/tasks/compile/JavaCompile.html) task has been updated to now normalize line endings in source files when doing up-to-date checks and build cache key calculations.

See the [User manual](userguide/more_about_tasks.html#sec:up_to_date_checks) for more information.

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
