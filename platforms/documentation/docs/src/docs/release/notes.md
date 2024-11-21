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


<a name="Problems API"></a>

### Problems API improvements

#### Enhanced Problem Summarization for Improved Usability

This release introduces a new problem summarization mechanism that reduces redundancy in problem reporting during builds. The feature limits the number of identical problems reported for each group (
default threshold: 15) and provides a summarized count of additional occurrences at the end of the build.

##### Key Improvements

- **Optimized Event Reporting**: Summarized problems minimize the data sent to clients, improving performance and reducing resource consumption.
- **Simplified Developer Experience**: Cleaner problem reports with less noise, making it easier to identify and address critical issues.
- **Enhanced Reporting**: Summarized problem details are reflected in the Messages, Group, and Locations tabs, maintaining clarity while reducing verbosity.

This change ensures a smoother experience when using Gradle with repetitive problems.

To learn more, check out our [sample project](samples/sample_problems_api_usage.html)
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

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides rich APIs for plugin authors and build engineers to develop custom build logic.

#### `DependencyConstraintHandler` now has `addProvider` methods

The [`DependencyConstraintHandler`](javadoc/org/gradle/api/artifacts/dsl/DependencyConstraintHandler.html) now has `addProvider` methods, similar to the 
[`DependencyHandler`](javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html).

```kotlin
dependencies {
    constraints {
        // Existing API:
        add("implementation", provider { "org.foo:bar:1.0" })
        add("implementation", provider { "org.foo:bar:1.0" }) {
            because("newer versions have bugs")
        }
        // New methods:
        addProvider("implementation", provider { "org.foo:bar:1.0" })
        addProvider("implementation", provider { "org.foo:bar:1.0" }) {
            because("newer versions have bugs")
        }
    }
}
```

This clarifies that adding a provider is possible, and that there is no immediately usable return value. The ability to pass a provider to `DependencyConstraintHandler.add` is unaffected.

### Other improvements

#### File-system watching and continuous mode support on Alpine Linux

[File-system watching](userguide/file_system_watching.html) is now supported on Alpine Linux.
The feature is enabled by default, as on all other supported platforms.

It is now also possible to [run builds in continuous mode](userguide/continuous_builds.html) on Alpine.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Service reference properties are now stable

Service references are task properties meant for easier consumption of [shared build services](userguide/build_services.html#sec:service_references).
[`ServiceReference`](/javadoc/org/gradle/api/services/ServiceReference.html) is now stable.

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
