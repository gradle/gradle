The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[altrisi](https://github.com/altrisi)
[Frosty-J](https://github.com/Frosty-J)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

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

### Improvements for IDE integrators

#### Tooling API progress events expose difference between test assertion failures and test framework failures

Gradle 7.6 introduces new failure types for the `Failure` interface returned by [FailureResult.getFailures()](javadoc/org/gradle/tooling/events/FailureResult.html#getFailures--): TestAssertionFailure and TestFrameworkFailure. 
IDEs can now easily distinguish between different failures using standard progress event listeners. 
Moreover, `TestAssertionFailure` exposes the expected and actual values if the used test framework supply such information.

### Improved Maven Conversion

The `init` task now adds compile-time Maven dependencies to Gradle's `api` configuration when converting a Maven project. This sharply reduces the number of compilation errors resulting from the automatic conversion utility. See the [Build Init Plugin](userguide/build_init_plugin.html#sec:pom_maven_conversion) for more information.

<a name="configuration-cache-improvements"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds.

#### New compatible tasks

The `dependencies`, `buildEnvironment`, `projects` and `properties` tasks are now compatible with the configuration cache.


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

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
