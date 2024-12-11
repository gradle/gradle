The Gradle team is excited to announce Gradle @version@.

This release improves [error and warning reporting](#error-warning) by summarizing duplicate entries in the Problems API's generated problems report for better readability. The [console output](#build-authoring) is also enhanced when the Problems API is used to fail the build.

Gradle @version@ introduces [platform enhancements](#platform), including file-system watching support on the Alpine Linux distribution and support for building and testing Swift 6 applications.

Additionally, artifact transform ambiguities now produce a [deprecation warning](#error-warning) with clearer, more actionable information and [new methods](#build-authoring) are available in the DependencyConstraint API.

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

<a name="error-warning"></a>
### Error and warning reporting improvements

Gradle provides a rich set of [error and warning messages](userguide/logging.html) to help you understand and resolve problems in your build.

#### Summarization in the HTML report for problems

The [Problems API](userguide/reporting_problems.html) provides structured feedback on build issues, helping developers and tools like IDEs identify and resolve warnings, errors, or deprecations during configuration or runtime.

This release introduces a new problem summarization mechanism that reduces redundancy in the generated HTML Problems Report.

The feature limits the number of identical problems reported and provides a summarized count of additional occurrences in the summary report:

#### Ambiguous Artifact Transformation chains are detected and reported

Previously, when two or more equal-length chains of [artifact transforms](userguide/artifact_transforms.html) produced compatible variants to satisfy a resolution request, Gradle would arbitrarily and silently select one.
Gradle now emits a warning for this case.

This deprecation warning is the same failure message that now appears when multiple equal-length chains are available, producing incompatible variants that could each satisfy a resolution request.
In this case, resolution fails with an ambiguity failure, and Gradle emits a message like this:

```text
FAILURE: Build failed with an exception.

* What went wrong:
Could not determine the dependencies of task ':forceResolution'.
> Could not resolve all dependencies for configuration ':resolveMe'.
   > Found multiple transformation chains that produce a variant of 'root project :' with requested attributes:
       - color 'red'
       - matter 'liquid'
       - shape 'round'
     Found the following transformation chains:
       - From configuration ':squareBlueLiquidElements':
           - With source attributes:
               - artifactType 'txt'
               - color 'blue'
               - matter 'liquid'
               - shape 'square'
               - texture 'smooth'
           - Candidate transformation chains:
               - Transformation chain: 'ColorTransform' -> 'ShapeTransform':
                   - 'BrokenColorTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'smooth'
                       - To attributes:
                           - color 'red'
                           - texture 'bumpy'
                   - 'BrokenShapeTransform':
                       - Converts from attributes:
                           - shape 'square'
                           - texture 'bumpy'
                       - To attributes:
                           - shape 'round'
               - Transformation chain: 'ColorTransform' -> 'ShapeTransform':
                   - 'BrokenColorTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'smooth'
                       - To attributes:
                           - color 'red'
                           - texture 'rough'
                   - 'BrokenShapeTransform':
                       - Converts from attributes:
                           - shape 'square'
                           - texture 'rough'
                       - To attributes:
                           - shape 'round'
```

The formatting of this message has been improved to comprehensively display information about each complete chain of transformations that produces the candidates that would satisfy the request.
This allows authors to better analyze and understand their builds, allowing them to remove ambiguity.

<a name="platform"></a>
### Platform enhancements

Gradle provides many features for specific platforms and languages.

#### File-system watching and continuous mode support on Alpine Linux

[File-system watching](userguide/file_system_watching.html) is now supported on [Alpine Linux](https://alpinelinux.org), a popular choice for container-based images and the default distribution for Docker.

The feature is enabled by default, as is the case with all other supported platforms.

Additionally, it is now possible to [run builds in continuous mode](userguide/continuous_builds.html) on Alpine Linux.

<a name="swift-support"></a>
#### Swift 6 support

Gradle’s [Swift support](userguide/building_swift_projects.html) allows you to build and test native Swift libraries and applications.

Gradle now supports [Swift 6](https://www.swift.org/blog/announcing-swift-6/), introduced with [Xcode 16.0](https://developer.apple.com/documentation/xcode-release-notes/xcode-16-release-notes), extending its capabilities to the latest major version of Swift.

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for plugin authors and build engineers to develop custom build logic.

#### Richer console output for failures using the Problems API

The [Problems API](userguide/reporting_problems.html) provides structured feedback on build issues, helping developers and tools like IDEs identify and resolve warnings, errors, or deprecations during configuration or runtime.

With this release, problems that are the source of a build failure have all of their information displayed on the console output at the end of the build:

```text
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':sample-project:myFailingTask'.
> Message from runtime exception
    This happened because ProblemReporter.throwing() was called
      This is a demonstration of how to add
      detailed information to a build failure

* Try:
> Remove the Problems.throwing() method call from the task action
> Run with --scan to get full insights.

BUILD FAILED in 10s
```

This example output was obtained by using the Problems API as shown below:

```java
public abstract class FailingTask extends DefaultTask {


    @Inject public abstract Problems getProblems();


    @TaskAction public void run() {
        throw getProblems().getReporter().throwing(problemSpec -> {
            problemSpec.contextualLabel("This happened because ProblemReporter.throwing() was called");
            problemSpec.details("This is a demonstration of how to add\ndetailed information to a build failure");
            problemSpec.withException(new RuntimeException("Message from runtime exception"));
            problemSpec.solution("Remove the Problems.throwing() method call from the task action");
        });
    }
}
```

Check out our [sample project](samples/sample_problems_api_usage.html) for the complete code.

This will enable plugin authors to fully leverage the Problems API to enhance any error with additional details, documentation links, and possible resolution steps.

See the [Problems API](userguide/reporting_problems.html#command_line_interface) for more information.

#### `DependencyConstraintHandler` now has `addProvider` methods

The [`DependencyConstraintHandler`](javadoc/org/gradle/api/artifacts/dsl/DependencyConstraintHandler.html) now has `addProvider` methods, similar to the
[`DependencyHandler`](javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html).

These are useful in plugin code to bring attention to where inputs should and should not be lazily evaluated by preventing eager results from being passed in.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Service reference properties are now stable

Service references are task properties meant to facilitate the consumption of [shared build services](userguide/build_services.html#sec:service_references).

[`ServiceReference`](javadoc/org/gradle/api/services/ServiceReference.html) is now stable.

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
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
