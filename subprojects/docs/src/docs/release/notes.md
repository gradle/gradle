The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

[Martin d'Anjou](https://github.com/martinda)
[Till Krullmann](https://github.com/tkrullmann)
[Andreas Axelsson](https://github.com/judgeaxl)

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

## Build reliability improvements

Gradle employs a number of optimizations to ensure that builds are executed as fast as possible.
These optimizations rely on the inputs and outputs of tasks to be well-defined.
Gradle already applies some validation to tasks to check whether they are well-defined.
If a task is found to be invalid, Gradle will now execute it without the benefit of parallel execution, up-to-date checks and the build cache.
For more information See the [user manual on runtime validation](userguide/more_about_tasks.html#sec:task_input_validation).

## Plugin development improvements

### Included plugin builds

Developing plugins as part of a composite build was so far only possible for project plugins.
Settings plugins always had to be developed in isolation and published to a binary repository.

This release introduces a new DSL construct in the settings file for including plugin builds.
Build included like that can provide both project and settings plugins.
```
pluginManagement {
    includeBuild("../my-settings-plugin")
}
plugins {
    id("my.settings-plugin") 
}
```
The above example assumes that the included build defines a settings plugin with the id `my.settings-plugin`.

Library components produced by builds included though the `pluginManagement` block are not automatically visible to the including build.
However, the same build can be included as plugin build and normal library build:
```
pluginManagement {
    // contributes plugins
    includeBuild("../project-with-plugin-and-library") 
}
// contributes libraries
includeBuild("../project-with-plugin-and-library") 
```
This distinction reflects what Gradle offers for repository declarations - 
repositories are specified separately for plugin dependencies and for production dependencies.

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
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv



^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

In Gradle 7.0 we moved the following classes out of incubation phase.

- org.gradle.tooling.model.eclipse.EclipseRuntime
- org.gradle.tooling.model.eclipse.EclipseWorkspace
- org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
- org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies

- org.gradle.tooling.events.OperationType.TestOutput
- org.gradle.tooling.events.test.Destination
- org.gradle.tooling.events.test.TestOutputDescriptor
- org.gradle.tooling.events.test.TestOutputEvent

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
