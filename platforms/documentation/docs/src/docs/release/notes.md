The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [Public Roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

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
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

### Configuration cache

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### Supporting standard streams as task property values

The standard streams (`System.in`, `System.out`, and `System.err`) can now be used as
`standardInput`, `standardOutput`, and `errorOutput` of [`Exec`](javadoc/org/gradle/api/tasks/Exec.html) and [`JavaExec`](javadoc/org/gradle/api/tasks/JavaExec.html) tasks.
Other tasks with properties of types `java.io.InputStream` and `java.io.OutputStream` can also use the standard streams as property values.
Setting up custom standard streams with `System.setIn`, `System.setOut` and `System.setErr` isn't supported.

### Documentation

#### Kotlin DSL Reference

Content derived from Javadoc contains a "Since" section now, specifying the Gradle version when the particular functionality has been introduced.
The information comes from `@since` tags in the Javadoc, but which weren't displayed until now. 

### Other improvements

#### Generating tidy projects with Gradle init

Using the new `--no-comments` option allows generating projects that contain only code without clarifying comments.
The resulting build files and source files are smaller and less noisy.

```
gradle init --use-defaults --type kotlin-library --no-comments
```

It is possible to persist the preference by setting the `org.gradle.buildinit.comments` property to `false`.


<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
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
