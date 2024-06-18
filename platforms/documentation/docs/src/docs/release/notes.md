The Gradle team is excited to announce Gradle @version@.

This release features enhanced [error and warning reporting](#error-warning) to better handle variant ambiguity issues and missing variants during dependency resolution.

Additionally, this release includes improvements for [ide integrators](#ide-integration) and [other improvements](#other) to Build Init and TestNG support.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="error-warning"></a>
### Error and warning reporting improvements

Gradle provides a rich set of error and warning messages to help you understand and resolve problems in your build.

#### Better message for common variant ambiguity issues

When Gradle attempts to resolve a dependency and finds multiple [variants](userguide/dependency_management_terminology.html#sub:terminology_variant) are available, all of which define attributes that would satisfy the resolution request, the resolution fails with a [variant ambiguity error](userguide/variant_model.html#sub:variant-ambiguity).

A common scenario is that all these variants contain only a single, unrequested attribute with distinct values.
The addition of this attribute to the resolution request would resolve the ambiguity.
This is almost always the desired solution to this problem, but the previous generic error message did not provide proper guidance in this regard.

The new message explicitly suggests adding this missing attribute if such an attribute exists:

```
> Could not resolve all files for configuration ':consumer'.
    > Could not resolve com.squareup.okhttp3:okhttp:4.4.0.
        Required by:
            project :
        > The consumer was configured to find attribute 'org.gradle.category' with value 'documentation'. There are several available matching variants of com.squareup.okhttp3:okhttp:4.4.0
          The only attribute distinguishing these variants is 'org.gradle.docstype'. Add this attribute to the consumer's configuration to resolve the ambiguity:
            - Value: 'javadoc' selects variant: 'javadocElements'
            - Value: 'sources' selects variant: 'sourcesElements'
            - Value: 'other' selects variant: 'additionalDocs'
```

The message also adds a suggestion to run the [`dependencyInsight` task](userguide/command_line_interface.html#sec:listing_project_dependencies) to view the full list of variants and attributes, as these are now omitted to make the message more clear:

```
* Try:
Use the dependencyInsight report with the --all-variants option to view all variants of the ambiguous dependency. This report is described at https://docs.gradle.org/<VERSION>/userguide/viewing_debugging_dependencies.html#sec:identifying_reason_dependency_selection.
```

#### Better message for resolution failures due to missing variants

If a dependency is requested that declares no variants, dependency resolution will fail.

The new message makes this clear:

```
> No matching variant of project :producer was found. The consumer was configured to find attribute 'color' with value 'green' but:
    - No variants exist.
```

Previously, the error message was misleading, as it mentioned that none of the variants had attributes:

```
> No matching variant of project :subproject1 was found. The consumer was configured to find attribute 'attrA' with value 'value1' but:
    - None of the variants have attributes.
```

While technically true, this did not emphasize the more important fact that no available variants were found.

#### Detailed information on file changes for configuration cache misses

Starting with this release, when a configuration cache entry cannot be reused due to a change to some file, the console output provides additional information detailing the change:

```
> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' has been removed.
```

or

```
> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' has been replaced by a directory.
```

Before this release, the console output provided a generic message. The message was shown even if the file was not changed but was removed or replaced with a directory:

```
> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' has changed.
```

<a name="ide-integration"></a>
### IDE integration improvements

Gradle is integrated into many IDEs using the [Tooling API](userguide/third_party_integration.html).
The following improvements are for IDE integrators.

#### Adoption of the Problems API with Java compilation

The Java compilation infrastructure has been updated to use the [Problems API](userguide/implementing_gradle_plugins.html#reporting_problems).
This change will supply the Tooling API clients with structured, rich information about compilation issues. IDEs such as IntelliJ IDEA and Visual Studio Code can adopt this feature to provide an accurate visual representation of Java compilation problems without relying on parsing text printed by the compiler.

<a name="other"></a>
### Other improvements

#### Changes to Build init behavior when Gradle build files are present

The [build `init` plugin](userguide/build_init_plugin.html) will now ask the user to confirm before proceeding if there are any files in the project directory, including Gradle `settings.gradle(.kts)` and `build.gradle(.kts)` files.

This change is intended to prevent accidental overwriting of existing files in the project directory.

Build init now has a new `--overwrite` option, which allows users to bypass this confirmation message.
This can be used if initialization is canceled or fails, and the user wants to re-run the init task without being prompted to confirm.

If the user declines to overwrite files that exist, or if the `--no-overwrite` option is provided, initialization will fail with the message:

```Aborting build initialization due to existing files in the project directory: <PATH>```

The exception to this behavior is when Gradle detects an existing Maven build via the presence of a `pom.xml` file - these builds will be converted to Gradle builds without prompting.

#### New TestNg options supported

The [TestNGOptions](javadoc/org/gradle/api/tasks/testing/testng/TestNGOptions.html) class now supports configuring the following options:

`suiteThreadPoolSize`

More information about this option is available in the [TestNG documentation](https://testng.org/#_command_line_parameters).

#### Daemon JVM information report

It is now possible to view information about the JVM used for the [Daemon](userguide/gradle_daemon.html) from the [command line](userguide/command_line_interface.html).

Running `gradle --version` provides a short output that highlights the potentially different JVM versions used by the Daemon (which executes the build process) and the Launcher (which initiates the Gradle build process):

```
[...]
Launcher JVM:  11.0.23 (Eclipse Adoptium 11.0.23+9)
Daemon JVM:    /Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home (no JDK specified, using current Java home)
```

This includes information about how Gradle chose the JVM, including the `org.gradle.java.home` [system property](userguide/build_environment.html) and the incubating [Daemon JVM criteria](userguide/gradle_daemon.html#sec:daemon_jvm_criteria).

More details can be seen by running `gradle buildEnvironment`:

```
Daemon JVM: Eclipse Temurin JDK 11.0.23+9
  | Location:           /Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
  | Language Version:   11
  | Vendor:             Eclipse Temurin
  | Architecture:       aarch64
  | Is JDK:             true
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
