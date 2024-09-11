The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [Wrapper](userguide/gradle_wrapper.html) in your project:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

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
> HIGHLIGHT the use case or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

<a name="config-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of the configuration phase. Gradle uses the configuration cache to skip the configuration phase entirely when nothing that affects the build configuration has changed.

#### Parallel caching for faster loading times

Storing and loading of the configuration cache can now be performed in parallel, resulting in better performance for cache misses and hits. 
To enable the feature in `gradle.properties`:

```text
// gradle.properties
org.gradle.configuration-cache.parallel=true
```

Note that this is an incubating feature and may expose concurrency issues in some builds. 

See the [configuration cache](userguide/configuration_cache.html#config_cache:usage:parallel) documentation for more details.

<a name="java-compiler-error-rendering"></a>
### Java compiler errors in the failure report

In previous Gradle versions, finding the reason of a failed compilation was a suboptimal experience: the only pointer we could give to the user was to scroll back, and look for the failed task's output, containing the compiler failure.

Build logs can be extremely long, and support for multiple failures when using the `--continue` flag further complicates discovery, making the identification of the exact failure challenging.

Gradle now gained the ability to collect, and report per-task failures in the bottom "What went wrong" segment.
This report supplements the usual task output, and aims to give a new way to identify problems quicker and easier.

A simple failure at the bottom of the log will show up as:
```
* What went wrong:
Execution failed for task ':project1:compileJava'.
> Compilation failed; see the compiler output below.

Java compilation error (compilation:java:java-compilation-error)
  sample-project/src/main/java/Problem.java:6: error: incompatible types: int cannot be converted to String
          String a = 1;
                     ^
```

If any warning happens during the compilation, it will also be included in the report:
```
* What went wrong:
Execution failed for task ':project1:compileJava'.
> Compilation failed; see the compiler output below.

Java compilation warning (compilation:java:java-compilation-warning)
  sample-project/src/main/java/Problem1.java:6: warning: [cast] redundant cast to String
          var warning = (String)"warning";
                        ^
Java compilation error (compilation:java:java-compilation-error)
  sample-project/src/main/java/Problem2.java:6: error: incompatible types: int cannot be converted to String
          String a = 1;
                     ^
```

Note that the current solution reports upon _failures_. If only warnings happen (and no `-Werror` is set), this report will not be visible.

The feature also works with the [`--continue`](userguide/command_line_interface.html#sec:continue_build_on_failure) flag, and the bottom report will contain a per-task report of all the compilation failures.

<a name="native-plugin-improvements"></a>
### Core plugin improvements

Gradle provides core plugins for build authors, offering essential tools to simplify project setup and configuration across various languages and platforms.

#### Configuration cache compatibility for Swift and C++ plugins

The following Swift and C++ plugins are now [configuration cache](userguide/performance.html#enable_configuration_cache) compatible: 
- [Swift application](userguide/swift_application_plugin.html)
- [Swift library](userguide/swift_library_plugin.html)
- [XCTest](userguide/xctest_plugin.html)
- [C++ application](userguide/cpp_application_plugin.html)
- [C++ library](userguide/cpp_library_plugin.html)
- [CppUnit](userguide/cpp_unit_test_plugin.html)
- [GoogleTest](userguide/cpp_testing.html)
- [Visual Studio](userguide/visual_studio_plugin.html)

The [`xcode`](userguide/xcode_plugin.html) is not yet compatible.


<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Stable Build Features API

The [`BuildFeatures`](javadoc/org/gradle/api/configuration/BuildFeatures.html) API is now stable.
It allows checking the status of Gradle features such as [`configurationCache`](javadoc/org/gradle/api/configuration/BuildFeatures.html#getConfigurationCache())
and [`isolatedProjects`](javadoc/org/gradle/api/configuration/BuildFeatures.html#getIsolatedProjects()).

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
