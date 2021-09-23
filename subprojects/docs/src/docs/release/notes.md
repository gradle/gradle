The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Attix Zhang](https://github.com/attix-zhang),
[anatawa12](https://github.com/anatawa12),
[Anil Kumar Myla](https://github.com/anilkumarmyla),
[Marcono1234](https://github.com/Marcono1234),
[Nicola Corti](https://github.com/cortinico),
[Scott Palmer](https://github.com/swpalmer),
[Marcin Zajączkowski](https://github.com/szpak),
[Alex Landau](https://github.com/AlexLandau),
[Stefan Oehme](https://github.com/oehme),
[yinghao niu](https://github.com/towith),
[Björn Kautler](https://github.com/Vampire),
[Tomasz Godzik](https://github.com/tgodzik),
[Kristian Kraljic](https://github.com/kristian),
[Matthew Haughton](https://github.com/3flex),
[Xin Wang](https://github.com/scaventz)


## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

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
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv

--->

## New features and usability improvements

### Support for Java 17

Gradle 7.3 supports compiling, testing and running on Java 17.

<a name="tooling-api"></a>
### Tooling API improvements

TBD - The tooling API allows applications to embed Gradle. This API is used by IDEs such as IDEA, Android Studio
and Buildship to integrate Gradle into the IDE.

#### File download progress events

TBD - some build may download many files or very large files, for example when resolving many dependencies, and this
may cause Gradle builds to be slow. This release adds new events that notify the embedding IDE as files such as
dependency artifacts are downloaded. This allows the IDE to show better progress information Gradle is running.

<a name="scala"></a>
### Scala plugin improvements

Scala plugin allows users to compile their Scala code using Gradle and the Zinc incremental compiler underneath.

#### Scala 3 support

Newest version of Scala 3 brings about numerous features while keeping compatibility with most of the existing
Scala 2 code. Due to some large changes in the compiler it was neccessary to adjust the Scala plugin
to be able to compile Scala 3 code. All existing configuration options should still be usable with the newest
language version. To see more about the language features go to
[overview of the new features in Scala 3](https://docs.scala-lang.org/scala3/new-in-scala3.html).

### Allow copying single files into directories which contain unreadable files.

Sometimes you want to copy files into a directory which contains unreadable files or which is not exclusively owned by the build.
For example when you are deploying single files into application servers or installing executables.
This may fail or be slow since Gradle tries to track all the content in the destination directory.

The `Copy` task now has a method [`Copy.ignoreExistingContentInDestinationDir()`](dsl/org.gradle.api.tasks.Copy.html#org.gradle.api.tasks.Copy:ignoreExistingContentInDestinationDir()) that forces Gradle to ignore content in the destination directory.
This uses the new [untracked](#untracked) feature under the hood.
See the samples in the user manual about [Deploying single files into application servers](userguide/working_with_files.html#sec:copy_deploy) and [Installing executables](userguide/working_with_files.html#sec:install_executable).

### More robust file system watching

When running an incremental build, Gradle needs to understand what has changed since the previous build on the file system.
To do this it tries to rely on the operating system's [file system events](userguide/gradle_daemon.html#sec:daemon_watch_fs) whenever possible.
However, these events can be unreliable in some environments, which could cause Gradle to ignore some changes.
To prevent this, Gradle now makes a change to the file system whenever it first starts watching something.
If a file system event for this change does not arrive by the start of the next build, Gradle concludes that file system events are unreliable, and will fall back to checking the file system for files involved in the build for changes.  

<a name="plugin-development-improvements"></a>
## Plugin development improvements

<a name="untracked"></a>
### Allow plugin authors to declare inputs or outputs as untracked

For up-to-date checks and the build cache, Gradle needs to track the state of the inputs and outputs of a task.
Though it is not always desirable or possible for Gradle to fully track the state of the input and output files.
For example:
- The location contains unreadable files like pipes where Gradle cannot track the content.
- Another tool like Git already takes care of keeping the state, so it doesn't make sense for Gradle to do additional bookkeeping.
- The build does not own the output location exclusively and Gradle would need to track the state of a potentially large amount of content.

Gradle 7.3 introduces the annotation [`@Untracked`](javadoc/org/gradle/api/tasks/Untracked.html) and the method [TaskFilePropertyBuilder.untracked()](javadoc/org/gradle/api/tasks/TaskFilePropertyBuilder.html##untracked--) to declare that Gradle should not track the state of the input or output property.
This allows implementing the above use-cases.
If a task has any untracked properties, then Gradle does not do any optimizations for running the task.
For example, such a task will always be out of date and never from the build cache.

See the samples in the user manual about [Integrating an external tool which does its own up-to-date checking](userguide/more_about_tasks.html#sec:untracked_external_tool).

<!--

^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Disabling caching by default

The [`@DisableCachingByDefault` annotation](userguide/build_cache.html#sec:task_output_caching_disabled_by_default) is now a stable feature.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
