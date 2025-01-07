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

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for plugin authors and build engineers to develop custom build logic.

### ProjectLayout API improvement

The [`ProjectLayout`](org/gradle/api/file/ProjectLayout.html) class provides access to directories and files within a project.

Starting with this version of Gradle, it can also access the settings directory (the location of the `settings.gradle(.kts)` file). 
While the settings directory is not specific to any project, some use cases require resolving file paths relative to it.

Previously, accessing the settings directory required using `rootProject.layout.projectDirectory`. 
This approach involved accessing the `rootProject` object, which is discouraged, and then manually resolving paths to the settings directory.

The new capability addresses a common scenario: resolving files shared across all projects in a build, such as linting configurations or `version.txt` files in the root folder.
For example, all subprojects in a build might need access to a shared file, such as a `version.txt` file in the root folder. 
This use case is common in the build logic of the `gradle/gradle` project (e.g., instances of `rootProject.layout.projectDirectory` in `buildSrc`).

Refer to [`ProjectLayout.getSettingsDirectory()`](org/gradle/api/file/ProjectLayout.html#getSettingsDirectory()) for additional details.

<a name="error-warning"></a>
### Error and warning reporting improvements

Gradle provides a rich set of [error and warning messages](userguide/logging.html) to help you understand and resolve problems in your build.

#### New test report support for Custom Test Tasks

Gradle provides an HTML test report at the end of the console output when tests fail.

In this release, there are a number of updates to this report including support for:

- It allows for infinite nesting of test groups! 
- It renders metadata!
- It supports Custom Test tasks?

Metadata keys are printed at the group and test event levels. Metadata events with multiple keys are rendered together. Known value types are rendered. URIs are rendered as clickable links.

PIC


TODO: JVM Team should finish this placeholder

#### Corrected deprecation warnings that enable the full stack trace flag

The instructions printed under a deprecation warning now correctly indicate how to enable full stack traces for deprecation warnings.

The console properly prints out:

```text
Run with -Dorg.gradle.deprecation.trace=true to print the full stack trace for this deprecation warning.
```

Previously, the console printed the incorrect suggestion: 

```text
Run with --stacktrace to get the full stack trace of this deprecation warning.
```

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

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
