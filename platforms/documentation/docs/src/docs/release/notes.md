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

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

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
> HIGHLIGHT the use case or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

<a name="config-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### More precise information about file changes that cause a configuration cache miss

Before this release, when a configuration cache entry could not be reused due to a change to some file, the console output would show this message:

> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' **has changed**.

even if the file was not actually changed, but removed, or replaced with a directory.

Starting with this release, you get additional information detailing the kind of change. For example:

> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' **has been removed**.

or

> Calculating task graph as configuration cache cannot be reused because file '.../some-file.txt' **has been replaced by a directory**.

### Error and warning reporting improvements

#### Better warning message for some common variant ambiguity issues

When Gradle attempts to resolve a dependency and finds multiple variants are available, all of which define attributes that would satisfy the resolution request, the resolution fails with a [variant ambiguity error](userguide/variant_model.html#sub:variant-ambiguity).

A common scenario is that the all these variants contain only a single, unrequested attribute with distinct values.
The addition of this attribute to the resolution request would resolve the ambiguity.
This is almost always the desired solution to this problem, but the previous generic error message did not provide any guidance in this regard.

The new message explicitly suggests adding this missing attribute, if such an attribute exists:

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

This also adds a suggestion to run the [`dependencyInsight` task](userguide/command_line_interface.html#sec:listing_project_dependencies) to view the full list of variants and attributes, as these are now omitted to make the message more clear.

```
* Try:
Use the dependencyInsight report with the --all-variants option to view all variants of the ambiguous dependency.  This report is described at https://docs.gradle.org/<VERSION>/userguide/viewing_debugging_dependencies.html#sec:identifying_reason_dependency_selection.
```

#### Explain when resolution fails due to lacking any resolvable variants

If a dependency is requested that declares no variants, dependency resolution will necessarily fail.

Previously, the error message was misleading, as it mentioned that none of the variants have attributes:

```
> No matching variant of project :subproject1 was found. The consumer was configured to find attribute 'attrA' with value 'value1' but:
    - None of the variants have attributes.
```

While technically true, this failed to emphasize the more important fact that there were no available variants found at all.

The new message now makes this clear:

```
> No matching variant of project :producer was found. The consumer was configured to find attribute 'color' with value 'green' but:
    - No variants exist.
```

### Changes to init task behavior

The [`init` task](userguide/build_init_plugin.html) will now ask the user to confirm before proceeding if there are any files in the project directory, including Gradle `settings.gradle(.kts)` and `build.gradle(.kts)` files.

This change is intended to prevent accidental overwriting of existing files in the project directory.

A new `--overwrite` option has been added to the init task to allow users to bypass this confirmation message.
This can be used if initialization is cancelled or fails, and the user wants to re-run the init task without being prompted to confirm.

If the user declines to overwrite files that exist, or if the `--no-overwrite` option is provided, initialization will fail with the message:

```Aborting build initialization due to existing files in the project directory: <PATH>```

The exception to this behavior is when Gradle detects an existing Maven build via the presence of a `pom.xml` file - these builds will be converted to Gradle builds without prompting.

### New TestNg Options supported

The [TestNGOptions](javadoc/org/gradle/api/tasks/testing/testng/TestNGOptions.html) class now supports configuring the following options:

`suiteThreadPoolSize`

More information about this option is available in the [TestNG documentation](https://testng.org/#_command_line_parameters).

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

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
