The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Attix Zhang](https://github.com/attix-zhang),
[anatawa12](https://github.com/anatawa12),
[Anil Kumar Myla](https://github.com/anilkumarmyla),
[Marcono1234](https://github.com/Marcono1234),
[Nicola Corti](https://github.com/cortinico),
[Scott Palmer](https://github.com/swpalmer),
[Marcin Zajączkowski](https://github.com/szpak),
[Alex Landau](https://github.com/AlexLandau),
[Stefan Oehme](https://github.com/oehme),
[yinghao niu](https://github.com/towith),
[Björn Kautler](https://github.com/Vampire),
[Alexis Tual](https://github.com/alextu),
[Tomasz Godzik](https://github.com/tgodzik)

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
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv

<a name="tooling-api"></a>
### Tooling API improvements

TBD - The tooling API allows applications to embed Gradle. This API is used by IDEs such as IDEA, Android Studio
and Buildship to integrate Gradle into the IDE.

#### File download progress events

TBD - some build may download many files or very large files, for example when resolving many dependencies, and this 
may cause Gradle builds to be slow. This release adds new events that notify the embedding IDE as files such as 
dependency artifacts are downloaded. This allows the IDE to show better progress information Gradle is running. 

<a name="scala"></a>
### Scala plugin improvements

Scala plugin allows users to compile their Scala code using Gradle and the Zinc incremental compiler underneath.

#### Scala 3 support

Newest version of Scala 3 brings about numerous features while keeping compatibility with most of the existing 
Scala 2 code. Due to some large changes in the compiler it was neccessary to adjust the Scala plugin
to be able to compile Scala 3 code. All existing configuration options should still be usable with the newest 
language version. To see more about the language features go to 
[overview of the new features in Scala 3](https://docs.scala-lang.org/scala3/new-in-scala3.html).


^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
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
