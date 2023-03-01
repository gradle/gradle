The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:
[Attila Király](https://github.com/akiraly),
[Björn Kautler](https://github.com/Vampire),
[DJtheRedstoner](https://github.com/DJtheRedstoner),
[JayaKrishnan Nair K](https://github.com/jknair0),
[kackey0-1](https://github.com/kackey0-1),
[Martin Bonnin](https://github.com/martinbonnin),
[Martin Kealey](https://github.com/kurahaupo),
[modmuss50](https://github.com/modmuss50),
[Sebastian Schuberth](https://github.com/sschuberth),
[valery1707](https://github.com/valery1707),
[Xin Wang](https://github.com/scaventz),
[Yanshun Li](https://github.com/Chaoba),
[Thrillpool](https://github.com/Thrillpool)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features, performance and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

### Gradle Wrapper

#### Introduced labels for selecting the version

The [`--gradle-version`](userguide/gradle_wrapper.html#sec:adding_wrapper) parameter for the wrapper plugin
now supports using predefined labels to select a version.

The allowed labels are:

- `latest`
- `release-candidate`
- `nightly`
- `release-nightly`

More details can be found in the [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) section.

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

### Configuration cache improvements

TODO - Java lambdas are supported, and unsupported captured values are reported.
TODO - File collections queried at configuration time are treated as configuration inputs.
TODO - File system repositories are fully supported including dynamic versions in Maven, Maven local, and Ivy repositories

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
If you're not sure that you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
