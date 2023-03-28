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

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

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

### Wrapper task validates distribution url

The wrapper task now validates the configured distribution url before writing it to the `gradle-wrapper.properties` file.
This surfaces invalid urls early and can prevent IO exceptions at execution time.

More details can be found in the dedicated section of the [Gradle Wrapper](gradle_wrapper.html#[adding_the_gradle_wrapper](sec:adding_wrapper)) user manual chapter.

### Java toolchains discovery progress display

Progress is now displayed during [Java toolchains discovery](userguide/jvm/toochains.html#auto_detection).
This can be useful during a cold-start of Gradle for users who have environments with a lot of JVM installations in them.

### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the Groovy DSL with an enhanced editing experience in supported IDEs — superior content assistance, refactoring documentation, and more.

#### Gradle `init` defaults to the Kotlin DSL

Starting with this release running `gradle init` now defaults to generating new builds using the Kotlin DSL.

In interactive mode you can choose which DSL to use and the Kotlin one is now listed first:

```text
Select build script DSL:
  1: Kotlin
  2: Groovy
Enter selection (default: Kotlin) [1..2]
```

See the [build init](userguide/build_init.html#sec:what_to_set_up) user manual chapter for more information.

#### Fail on script compilation warnings

Gradle [Kotlin DSL scripts](userguide/kotlin_dsl.html#sec:scripts) are compiled by Gradle during the configuration phase of your build.
Deprecation warnings found by the Kotlin compiler are reported on the console when compiling the scripts.

It is now possible to configure your build to fail on any warning emitted during script compilation by setting the `org.gradle.kotlin.dsl.allWarningsAsErrors` Gradle property to `true`:

```properties
# gradle.properties
org.gradle.kotlin.dsl.allWarningsAsErrors=true
```

More details can be found in the dedicated section of the [Kotlin DSL](userguide/kotlin_dsl.html#sec:compilation_warnings) user manual chapter.

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
