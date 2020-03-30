The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[SheliakLyr](https://github.com/SheliakLyr),
[James Baiera](https://github.com/jbaiera),
[Patrick Koenig](https://github.com/pkoenig10),
and [Matthew Duggan](https://github.com/mduggan).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

## New dependency locking file format

Gradle 6.4 introduces an experimental dependency locking file format.
This format uses a single lock file per project instead of a file per locked configuration.
The main benefit is a reduction in the total number of lock files in a given project.

In addition, when using this format, the lock file name can be configured.
This enables use cases where a given project may resolve different dependency graphs for the same configuration based on some project state.
A typical example in the JVM world are Scala projects where the Scala version is encoded in dependency names.

The format is experimental because it requires opt-in and a migration for existing dependency locking users.
It is however stable and expected to become the default format in Gradle 7.0.

Take a look at [the documentation](userguide/dependency_locking.html#single_lock_file_per_project) for more information and how to enable the feature.

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
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
