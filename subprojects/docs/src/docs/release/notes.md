The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

### Faster Java compilation

When compiling Java code, Gradle uses worker processes to run the Java compiler as a compiler daemon. 
Compiler daemons are reused within a single build invocation. 
This allows the Java compiler to "warm-up" and compile faster after a few uses. 

In previous Gradle releases, the compiler daemons were always shut down at the end of the build.
This caused every build to incur the cost of starting the compiler daemon and warming up the compiler.
These start-up costs contributed to a large portion of overall build time when incrementally compiling a few source files.

Starting in this release, Gradle attempts to keep Java compiler daemons around after the end of the build, so that subsequent builds are faster.
Gradle will stop compiler daemons when too many workers are running. 
Compiler daemons may also stop during a build if other worker processes are started and Gradle needs to free up resources.

No configuration changes are required to enable this feature. 

<!-- TODO: Link to blog post that details the measurements -->

Persistent compiler daemons for Groovy, Scala or Kotlin will be evaluated in the future.

<a name="SSL"></a>
### SSL improvements

Gradle had multiple issues when non-standard keystores and truststores were used.
This affected users on Linux systems with FIPS enabled and also Windows users who were storing certificates in the Trusted Root Certification Authorities store.
SSL context creation has been improved to be more aligned with the default implementation and to support these cases.
Also, error messages related to SSL have been improved, and they should be more visible.

### Reduced memory consumption

TODO - dependency resolution uses less heap

### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an enhanced editing experience in supported IDEs compared to the traditional Groovy DSL — auto-completion, smart content assist, quick access to documentation, navigation to source, and context-aware refactoring.

Kotlin DSL has received substantial improvements in the recent releases, leading to the announcement that [Kotlin DSL is Now the Default for New Gradle Builds](https://blog.gradle.org/kotlin-dsl-is-now-the-default-for-new-gradle-builds).
This release brings another series of improvements.

#### Request plugin with the embedded Kotlin version

It is now easier to request a plugin with the embedded Kotlin version in the builds of your Gradle plugins implemented in Kotlin.

Instead of using [kotlin()](kotlin-dsl/gradle/org.gradle.kotlin.dsl/kotlin.html) that requires a version declaration you can now use [embeddedKotlin()](kotlin-dsl/gradle/org.gradle.kotlin.dsl/embedded-kotlin.html) instead:

```kotlin
plugins {
    embeddedKotlin("plugin.serialization")
}
```

#### Build scripts now accept dependencies compiled with Kotlin K2 compiler

The Kotlin team continues to stabilize the [K2 compiler](https://blog.jetbrains.com/kotlin/2023/02/k2-kotlin-2-0/).
Starting with Kotlin 1.9.0-RC and until the release of Kotlin 2.0, you can easily test the K2 compiler in your projects.
Add `-Pkotlin.experimental.tryK2=true` to your command line invocations or add it to your `gradle.properties` file:

```properties
kotlin.experimental.tryK2=true
```

Setting this Gradle property also sets the language version to 2.0.

Starting with this version of Gradle, the compilation of `.gradle.kts` build scripts accepts dependencies compiled with Kotlin K2 compiler.
This makes it possible to try out K2 in builds that use Kotlin 1.9 and have Kotlin code in `buildSrc` or in included builds for build logic.

Note that if you use the [`kotlin-dsl` plugin](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) in your build logic, you will also need to explicitly set the Kotlin language version to 2.0:

```kotlin
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}
```

Moreover, at this stage, K2 doesn't support scripting, so it will not work with [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins).

#### Specify file permissions conveniently, even from Kotlin

Since Kotlin doesn't support octal numeric literals, setting file permissions as UNIX mode values has been awkward.
There is now a [better API](userguide/working_with_files.html#sec:setting_file_permissions) for setting file permissions and extra convenience methods for UNIX-style values.

```kotlin
tasks.register<Copy>("copy") {
    // details omitted
    filePermissions {
        user {
            read = true
            execute = true
        }
        other.execute = false
    }
    dirPermissions {
        unix("r-xr-x---")
    }
}
```

### Improved CodeNarc output

The `CodeNarc` plugin produces IDE-clickable links when reporting failures to the console.

The `CodeNarc` task also produces clickable links in failure messages to human-readable reports when multiple reports are enabled.

The HTML report generated by `CodeNarc` has been updated to produce violation reports with sortable columns. 

### Group opposite boolean build and task options together

The console output of the `help` task renders options in alphabetical order.

For better readability, this has been changed to group opposite boolean options together.

Disable options are now sorted after their enable option, for example the options `--daemon`, `--no-daemon`, `--no-parallel`, `--parallel` are now rendered in the following order:

```console
--daemon
--no-daemon
--parallel
--no-parallel
```

See the [task options](userguide/custom_tasks.html#sec:listing_task_options) user manual section for more information.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
