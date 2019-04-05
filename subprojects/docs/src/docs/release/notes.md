The Gradle team is excited to announce Gradle @version@.

This release features a [new API for Incremental Tasks](#incremental-tasks-api), updates to [building native software with Gradle](#native-support), [Swift 5 Support](#swift5-support), [running Gradle on JDK12](#jdk12-support) and more.

Read the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.0.
If upgrading from Gradle 4.x, please read [upgrading from Gradle 4.x to 5.0](userguide/upgrading_version_4.html) first.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

[Ian Kerins](https://github.com/isker),
[Rodolfo Forte](https://github.com/Tschis),
and [Stefan M.](https://github.com/StefMa).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper properties:

`./gradlew wrapper --gradle-version=@version@`

<a name="incremental-tasks-api"/>

## New API for Incremental Tasks

With Gradle, it's very simple to implement a task that is skipped when all of its inputs and outputs are up to date (see [Incremental Builds](userguide/more_about_tasks.html#sec:up_to_date_checks)).
However, there are times when only a few input files have changed since the last execution, and you'd like to avoid reprocessing all of the unchanged inputs.
When a task has an input that can represent multiple files and the task only processes those files that are out of date, it's called an incremental task.
Prior to Gradle 5.4, you had to use [`IncrementalTaskInputs`](dsl/org.gradle.api.tasks.incremental.IncrementalTaskInputs.html) to implement an incremental task.

Now you can use the new [`InputChanges`](dsl/org.gradle.work.InputChanges.html) API for implementing incremental tasks.
This API addresses some shortcomings of the old API, first and foremost that it is now possible to query for changes of individual input file properties, instead of receiving the changes for all input file properties at once.
Additionally, the file type and the normalized path can be queried for each change, and the old outputs of the task are automatically removed when Gradle is unable to determine which input files need to be processed.

See the [user manual](userguide/custom_tasks.html#incremental_tasks) for more information on how to implement incremental tasks using the new API.

```
inputChanges.getFileChanges(inputDir).forEach { change ->
    val targetFile = outputDir.file(change.normalizedPath).get().asFile
    if (change.changeType == ChangeType.REMOVED) {
        targetFile.delete()
    } else {
        targetFile.writeText(change.file.readText().reversed())
    }
}
```

<a name="native-support"/>

## Building native software with Gradle

Updates include relocating generated object files to separate directories per variant; usage of the new Incremental Changes API. See more information about the [Gradle native project](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-54).

<a name="swift5-support"/>

### Swift 5 Support

Gradle now supports [Swift 5](https://swift.org/blog/swift-5-released/) officially [release with the Xcode 10.2](https://developer.apple.com/documentation/xcode_release_notes/xcode_10_2_release_notes).
Specifying the source compatibility to Swift 5 instruct the compiler to expect Swift 5 compatible source files.
Have a look at the [Swift samples](https://github.com/gradle/native-samples) to learn more about common use cases.

<a name="jdk12-support"/>

## Support for JDK12

Gradle now supports running on [JDK12](https://jdk.java.net/12/). 

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

<!--
### Example deprecation
-->

### Using custom local build cache implementations

Using a custom build cache implementation for the local build cache is now deprecated.
The only allowed type will be `DirectoryBuildCache` going forward.
There is no change in the support for using custom build cache implementations as the remote build cache. 

### Breaking changes

<!-- summary and links -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about breaking changes and considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes here! Add them to the upgrade guide instead. --> 

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
