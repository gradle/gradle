The Gradle team is excited to announce Gradle @version@.

This release features the support for [persistent Java compiler daemons](#faster-java-compilation) to speed up Java compilation.
Gradle will also use [less memory for dependency resolution](#reduced-memory-consumption).
The effect is significant, particularly for large builds such as Android builds.

Gradle now supports running on Java 20.

For Kotlin DSL, build authors can [try out the Kotlin K2 compiler](#kotlin_k2) for build logic with some limitations.
See the [Kotlin DSL](#kotlin-dsl-improvements) dedicated section for more information.

This release also brings several usability improvements, including better [CodeNarc output](#improved-codenarc-output), a [dry run mode](#dry-run-mode-for-test-execution) for test execution,
improved [output for task options](#group-opposite-boolean-build-and-task-options-together), and upgraded [SSL support](#ssl).

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[Adam](https://github.com/aSemy),
[Ahmed Ehab](https://github.com/ahmedehabb),
[Aurimas](https://github.com/liutikas),
[Baptiste Decroix](https://github.com/bdecroix-spiria),
[Björn Kautler](https://github.com/Vampire),
[Borewit](https://github.com/Borewit),
[Korov](https://github.com/Korov),
[Mohammed Thavaf](https://github.com/mthavaf),
[Patrick Brückner](https://github.com/madmuffin1),
[Philip Wedemann](https://github.com/hfhbd),
[Róbert Papp](https://github.com/TWiStErRob),
[Shi Chen](https://github.com/CsCherrYY),
[Tony Robalik](https://github.com/autonomousapps)

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


### Maven toolchain declarations with environment variables are now supported

Using [toolchains](userguide/toolchains.html) is the recommended way of specifying Java versions for JVM projects.
By default, Gradle automatically detects local JRE/JDK installations so no further configuration is required by the user.
One of the auto-detection formats that Gradle supports is Maven Toolchain specification.

With this Gradle release, if you rely on the integration with Maven toolchain definitions, Gradle now supports the use of environment variables placeholders inside `toolchain.xml` files.
The placeholder will be resolved by looking at environment variables known to the Gradle build.

<a name="ssl"></a>
### SSL improvements for non-standard keystores and truststores

Previously, Gradle exhibited limitations when interfacing with non-standard keystores and truststores.
This affected users on Linux systems with FIPS enabled and also Windows users who were storing certificates in the Trusted Root Certification Authorities store.

SSL context creation has been improved to be more aligned with the default implementation and to support these cases.


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
