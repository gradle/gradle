The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:

[Stefan Neuhaus](https://github.com/stefanneuhaus),
[EthanLozano](https://github.com/EthanLozano),
[Pavlos-Petros Tournaris](https://github.com/pavlospt),
[Márton Braun](https://github.com/zsmb13),
[Thomas Iguchi](https://github.com/tiguchi),
[Vladimir Sitnikov](https://github.com/vlsi/),
[Peter Stöckli](https://github.com/p-),
and [Sebastian Schuberth](https://github.com/sschuberth).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<a name="dependency-verification"></a>
## Dependency verification

Gradle 6.2 ships with [dependency verification](userguide/dependency_verification.html).
Dependency verification is a major step towards a safer ecosystem by making it possible to verify both the checksums and the signatures of dependencies and plugins used during a build.

Please refer to the [userguide](userguide/dependency_verification.html) to figure out how to enable dependency verification.

We would like to give special thanks to [Vladimir Sitnikov](https://github.com/vlsi) for his feedback and inspiration.
A lot of the work on this feature is, in particular, available to previous versions of Gradle via his [Checksum Dependency Plugin](https://github.com/vlsi/vlsi-release-plugins/tree/master/plugins/checksum-dependency-plugin).

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

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
