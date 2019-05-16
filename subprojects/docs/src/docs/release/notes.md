The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Martin d'Anjou](https://github.com/martinda),
[Ben Asher](https://github.com/benasher44),
[Mike Kobit](https://github.com/mkobit),
[Erhard Pointl](https://github.com/epeee),
[Sebastian Schuberth](https://github.com/sschuberth),
[Evgeny Mandrikov](https://github.com/Godin),
[Stefan M.](https://github.com/StefMa),
[Igor Melnichenko](https://github.com/Myllyenko),
[Roberto Perez Alcolea](https://github.com/rpalcolea) and
[Christian Fränkel](https://github.com/fraenkelc)

<!-- 
## 1

details of 1

## 2

details of 2

## n
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

## Build init plugin improvements

### Support for JUnit Jupiter

The `init` task now provides an option to use Junit Jupiter, instead of Junit 4, to test Java applications and libraries. You can select this test framework when you run the `init` task interactively, or use the `--test-framework` command-line option. See the [User manual](userguide/build_init_plugin.html) for more details.

Contributed by [Erhard Pointl](https://github.com/epeee)

### Generate Gradle plugin builds

The `init` task can now generate simple Gradle plugins. You can use these as a starting point for developing and testing a Gradle plugin. The `init` task provides an option to use either Java, Groovy or Kotlin for the plugin source. You can select a Gradle plugin when you run the `init` task interactively, or use the `--type` command-line option. 

See the [User manual](userguide/build_init_plugin.html) for more details.

## Define organization-wide properties with a custom Gradle Distribution

Gradle now looks for a `gradle.properties` file in the Gradle distribution used by the build.  This file has the [lowest precedence of any `gradle.properties`](userguide/build_environment.html#sec:gradle_configuration_properties) and properties defined in other locations will override values defined here.

By placing a `gradle.properties` file in a [custom Gradle distribution](userguide/organizing_gradle_projects.html#sec:custom_gradle_distribution), an organization can add default properties for the entire organization or tweak the default Gradle daemon memory parameters with `org.gradle.jvmargs`.

## Improved Eclipse project name deduplication in Buildship

When importing Gradle Eclipse projects into Buildship, the current Eclipse workpace state is taken into account. This allows Gradle to import/synchronize in Eclipse workspaces that include
non-Gradle projects that conflict with project names in the imported project.

The upcoming 3.1.1 version of Buildship is required to take advantage of this behavior.

Contributed by [Christian Fränkel](https://github.com/fraenkelc)

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

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 6.0). See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Deprecated plugins

#### Play

The built-in [Play plugin](userguide/play_plugin.html) has been deprecated and replaced by a new [Playframework plugin](https://gradle.github.io/playframework) available from the plugin portal.

#### Build Comparison

The [build comparison](userguide/comparing_builds.html) plugin has been deprecated without replacement.

## Breaking changes and potential breaking changes

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about breaking changes and considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes here! Add them to the upgrade guide instead. --> 

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
