The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[SheliakLyr](https://github.com/SheliakLyr),
and [Patrick Koenig](https://github.com/pkoenig10).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

## Security Improvements

During our investigation of a recent security vulnerability in the [Plugin Portal Publish Plugin](https://blog.gradle.org/plugin-portal-update) we became aware of how much potentially sensitive information is logged when Gradle is executed at `--debug` level.
This information can be publicly exposed when Gradle builds are executed on public CI like Travis CI, CircleCI, & GitHub Actions where build logs are publicly visible.
This information may include sensitive credentials and authentication tokens.
Much of this sensitive information logging occurs deep in components of the JVM and other libraries outside the control of Gradle.
While debugging, this information may be inherently useful for build maintainers.

To strike a balance between the security risks of logging sensitive information and the needs of build maintainers who may find this information useful,
this version of Gradle now warns users about the risks of using `--debug` at the beginning and end of the log on every build.

We recommend plugin maintainers avoid logging sensitive information if possible, and if it's not possible, that all sensitive information be logged exclusively at `--debug` level and no higher.

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
