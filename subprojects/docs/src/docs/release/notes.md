The Gradle team is excited to announce Gradle @version@.

This release [introduces a declarative test suite API](#test-suites) for JVM projects, adds [support for building projects with Java 17](#java17),  and updates the Scala plugin to support [Scala 3](#scala).

There are also changes to make builds [more reliable](#reliability), provide [additional details to IDEs when downloading dependencies](#tooling-api), improve [untracked files in custom plugins](#untracked), several [bug fixes](#fixed-issues) and more.

We would like to thank the following community members for their contributions to this release of Gradle:

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
[Raphael Fuchs](https://github.com/REPLicated),
[Sebastian Schuberth](https://github.com/sschuberth),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Xin Wang](https://github.com/scaventz)


## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

NOTE: Gradle 7.3 has had *one* patch release, which fixes several issues from the original release.
We recommend always using the latest patch release.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="java17"></a>
### Support for Java 17

Gradle now supports running on and building with [Java 17](https://openjdk.java.net/projects/jdk/17/).

In previous Gradle versions, running Gradle itself on Java 17 resulted in an error. JVM projects could have been built with Java 17 [using toolchains](userguide/toolchains.html).

As of Gradle 7.3, both running Gradle itself and building JVM projects with Java 17 is fully supported.

<a name="test-suites"></a>
### Declarative test suites in JVM projects

When [testing Java & JVM projects](userguide/java_testing.html), you often need to group tests classes together to organize them into manageable chunks, so that you can run them with different frequencies or at distinct points in your build pipeline. For example, you may want to define groups of _unit tests_, _integration tests_, and _functional tests_.

Previously, grouping tests correctly required thorough knowledge of how to modify and connect various domain objects in Gradle, like SourceSets, configurations and tasks.  If you wanted to divide tests into different groups, you needed to understand how these separate parts interact with one another.

With Gradle 7.3, the [JVM Test Suite Plugin](userguide/jvm_test_suite_plugin.html) simplifies the creation of such groups of tests. We refer to these groups as **Test Suites**.  Note that this is not to be confused with testing framework suites, like [JUnit4’s Suite](https://junit.org/junit4/javadoc/4.13/org/junit/runners/Suite.html).

Test Suites are a high-level concept which can be referred to directly and consistently in build scripts. You can configure dependencies, sources and the testing framework used by the tests without having to worry about the low-level details.

For example, you can create an _integration testing_, test suite by adding the following snippet to a Java project:

```
testing {
    suites {
        // Add a new test suite
        integrationTest(JvmTestSuite) {
            // Use JUnit Jupiter as a testing framework
            useJUnitJupiter('5.7.1')

            // depend on the production code for tests
            dependencies {
                implementation project
            }
        }
    }
}

// Run integration tests as part of check
tasks.named('check') {
    dependsOn(testing.suites.integrationTest)
}
```

This functionality is available automatically for all JVM-based projects that apply the `java` plugin. The built-in `test` task has been re-implemented on top of test suites. See more in the [user manual](userguide/jvm_test_suite_plugin.html).

This API is [incubating](userguide/feature_lifecycle.html) and will likely change in future releases as more functionality is added.

<a name="scala"></a>
### Scala 3 support

The Scala plugin allows users to compile their Scala code using Gradle and the Zinc incremental compiler underneath.

The Scala plugin is now able to compile Scala 3 code. All existing configuration options should still be usable with the newest language version.

The newest version of Scala 3 brings about numerous features while keeping compatibility with most of the existing Scala 2 code. To see more about the language features go to [overview of the new features in Scala 3](https://docs.scala-lang.org/scala3/new-in-scala3.html).

### Explore new behavior with `gradle init`

When you initialize a new Gradle project using [`gradle init`](userguide/build_init_plugin.html#build_init_plugin), Gradle will now ask if you want to try new but unstable features in the build. This will allow you to try out new features before they become stable. You can always ask for this behavior by running `gradle init --incubating` when generating a new project.

Currently, builds generated with this option will only enable [Test Suites](#test-suites), but other new APIs or behaviors may be added as they are introduced.

### Version catalog improvements

[Version catalog](userguide/platforms.html#sub:version-catalog-declaration) is a [feature preview](userguide/feature_lifecycle.html#feature_preview) that provides a convenient API for referencing dependencies and their versions. It received the following improvement in this release.

#### Lifted restrictions for alias names

In previous Gradle releases it was not possible to declare aliases with the suffix `plugin`, `version` and other restricted keywords. With this release these restrictions are now lifted. Check the [documentation](userguide/platforms.html#sub:mapping-aliases-to-accessors) for details.

#### Version catalog type unsafe API changes

When using the type unsafe API, all methods accepting [alias references](userguide/platforms.html#sub:mapping-aliases-to-accessors) now can use the exact same string as the alias definition. This means that you can declare and reference `groovy-json` instead of being forced to use `groovy.json` in the type unsafe API.

Note that access to the type unsafe API has changed, please see the [upgrade guide](userguide/upgrading_version_7.html#changes_7.3).

#### Consistent version catalog accessors support in more scenarios

With more possibilities for declaring aliases, some accessors were not supported in specific APIs related to plugin or dependency declarations. This release fixes those issues and accessors can be used consistently in more contexts.

For plugins, if you have `kotlin.js` and `kotlin.js.action` plugins, both can be used in the `plugins` block.

Declarations of dependencies with `platform`, `enforcedPlatform`, `testFixtures` and `force` support all accessor types.


<a name="reliability"></a>
## Reliability improvements

### More robust file system watching

When running an incremental build, Gradle needs to understand what has changed since the previous build on the file system. To do this it relies on the operating system's [file system events](userguide/gradle_daemon.html#sec:daemon_watch_fs) whenever possible.

In some rare environments these events can be unreliable, and would cause Gradle to ignore some changes.
To prevent this, Gradle now verifies that file system events are delivered in a timely fashion before enabling optimization based on them.

### Allow copying single files into directories which contain unreadable files.

Sometimes you want to copy files into a directory that contains unreadable files or into one that is not exclusively owned by the build.
For example when you are deploying single files into application servers or installing executables.

Doing so may fail or be slow because Gradle tries to track all the content in the destination directory.

In order to work around such issues, you can now use the method [`Task.doNotTrackState()`](dsl/org.gradle.api.Task.html#org.gradle.api.Task:doNotTrackState(java.lang.String)) on `Copy` tasks that forces Gradle to ignore content in the destination directory.

See the samples in the user manual about [Deploying single files into application servers](userguide/working_with_files.html#sec:copy_deploy) and [Installing executables](userguide/working_with_files.html#sec:install_executable).

### Input normalization support in configuration cache

The [input normalization](userguide/more_about_tasks.html#sec:configure_input_normalization) is now correctly tracked by the experimental [configuration cache](userguide/configuration_cache.html). Task up-to-date checks now consider normalization rules when the configuration cache is enabled, leading to faster builds.

## Plugin development improvements

Initializing new plugin projects using the [Build Init Plugin](userguide/build_init_plugin.html#build_init_plugin) can also benefit from [the `--incubating` option](#explore-new-behavior-with-gradle-init).

<a name="untracked"></a>
### Allow plugin authors to declare tasks as untracked

For up-to-date checks and the build cache, Gradle needs to track the state of the inputs and outputs of a task.
It is not always desirable or possible for Gradle to fully track the state of the input and output files.

For example:
- The input or output locations contain unreadable files like pipes where Gradle cannot track the content.
- The input or output is stored remotely, for example in a database, and its state cannot be tracked.
- Another tool like Git already takes care of keeping the state, so it doesn't make sense for Gradle to do additional bookkeeping.
- The build does not own the output location exclusively and Gradle would need to track the state of a potentially large amount of content.

Gradle 7.3 introduces the annotation [`@UntrackedTask`](javadoc/org/gradle/api/tasks/UntrackedTask.html) and the method [`Task.doNotTrackState()`](dsl/org.gradle.api.Task.html#org.gradle.api.Task:doNotTrackState(java.lang.String)) to declare that Gradle should not track the state of a task.
This allows tasks to implement the above use-cases.

If a task is untracked, then Gradle does not do any optimizations when running the task.
For example, such a task will always be out of date and never come from the build cache.

`@UntrackedTask` and `Task.doNotTrackState` are a replacement for `Task.outputs.upToDateWhen { false }` if you want your task to never be up-to-date.
It has the advantage that it is faster since `Task.outputs.upToDateWhen { false }` still spends time on capturing task state.

See the samples in the user manual about [Integrating an external tool which does its own up-to-date checking](userguide/more_about_tasks.html#sec:untracked_external_tool).

<a name="tooling-api"></a>
## Improvements for tooling providers

The Tooling API allows applications to embed Gradle. This API is used by IDEs such as IDEA, Android Studio
and Buildship to integrate Gradle into the IDE.

### File download progress events

When a build downloads many files or very large files, for example when resolving dependencies, Gradle may appear to be unresponsive due to the lack of any logging or console output.

This release adds new events that notify the IDE as files are downloaded. This allows IDEs to show better progress information while Gradle is running and during IDE import/sync.

## Security improvements

Both `ant` and `common-compress` bundled libraries have been updated to resolve reported vulnerabilities.
Head over to [the upgrade guide](userguide/upgrading_version_7.html#changes_7.3) for version and resolved vulnerabilities.

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
