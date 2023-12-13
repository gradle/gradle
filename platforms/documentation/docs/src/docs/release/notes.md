The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

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

### Public API improvements

#### Enhanced name-based filtering on NamedDomainObject containers

A new [`named(Spec<String>)` method](javadoc/org/gradle/api/NamedDomainObjectCollection.html#named-org.gradle.api.specs.Spec-) has been added to all NamedDomainObject containers, which simplifies name-based filtering and eliminates the need to touch any of the values, may they be realized or unrealized.

#### Allow Providers to be used with capabilities

[`Providers`](javadoc/org/gradle/api/provider/Provider.html) can now be passed to capability methods
[`ConfigurationPublications#capability(Object)`](javadoc/org/gradle/api/artifacts/ConfigurationPublications.html#capability-java.lang.Object-),
[`ModuleDependencyCapabilitiesHandler#requireCapability(Object)`](javadoc/org/gradle/api/artifacts/ModuleDependencyCapabilitiesHandler.html#requireCapability-java.lang.Object-),
and [`CapabilitiesResolution#withCapability(Object, Action)`](javadoc/org/gradle/api/artifacts/CapabilitiesResolution.html#withCapability-java.lang.Object-org.gradle.api.Action-).

### Problems API

Gradle now has a new incubating API that allows build engineers and plugin authors to consume and report problems that occur during a build.

The [`Problems`](javadoc/org/gradle/api/problems/Problems.html) service can be used to describe problems with details (description, location information, link to documentation, etc.) and report them.
Reported problems are then exposed via the Tooling API, allowing Gradle IDE providers - IntelliJ IDEA, Visual Studio Code, Eclipse - to display details in the UI.
The reported problems carry location information; therefore, IDEs can easily integrate them into the developer experience, providing error markers, problem views, and more.

Gradle already emits problems from many components, including (but not limited to) deprecation warnings, dependency version catalog errors, task validation errors, and Java toolchain problems.

The current release focuses on reporting problems in the IDE. 
Users can expect further enhancements to the Problems API aimed at console reporting in future releases.

### Error and warning reporting improvements

Gradle provides a rich set of error and warning messages to help you understand and resolve problems in your build.

#### Dependency locking now separates the error from the possible action to try

[Dependency locking](userguide/dependency_locking.html) is a mechanism for ensuring reproducible builds when using dynamic dependency versions.

This release improves error messages by separating the error from the possible action to fix the issue in the console output.
Errors from invalid [lock file format](userguide/dependency_locking.html#lock_state_location_and_format) or [missing lock state when strict mode is enabled](userguide/dependency_locking.html#fine_tuning_dependency_locking_behaviour_with_lock_mode) are now displayed as illustrated below:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':dependencies'.
> Could not resolve all dependencies for configuration ':lockedConf'.
   > Invalid lock state for lock file specified in '<project>/lock.file'. Line: '<<<<<<< HEAD'

* Try:
> Verify the lockfile content. For more information on lock file format, please refer to https://docs.gradle.org/@version@/userguide/dependency_locking.html#lock_state_location_and_format in the Gradle documentation.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
```

### Gradle init

This update brings cleaner and more idiomatic projects generated by `gradle init`.

You no longer have to answer an interactive question about the source package.
Instead, a default value of `org.example` will be used.
You can override it using an existing option `--package` flag for the `init` task.
Additionally, you can set the default value by adding a new `org.gradle.buildinit.source.package` property in `gradle.properties` in the Gradle User Home.

```
// ~/.gradle/gradle.properties

org.gradle.buildinit.source.package=my.corp.domain
```

Names of the generated convention plugins now start with `buildlogic` instead of the package name, making them shorter and cleaner.

Projects generated with Kotlin DSL scripts now use [simple property assignment](/8.4/release-notes.html#assign-stable) syntax with the `=` operator.

<a name="other-improvements"></a>

### Other improvements

#### Gradle encryption key via an environment variable

You may now provide Gradle with the key used to encrypt cached configuration data via the `GRADLE_ENCRYPTION_KEY` environment variable.
By default, Gradle creates and manages the key automatically, storing it in a keystore under the Gradle User Home.
This may be inappropriate in some environments.

More details can be found in the dedicated section of the [configuration cache](userguide/configuration_cache.html#config_cache:secrets:configuring_encryption_key) user manual chapter.


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
