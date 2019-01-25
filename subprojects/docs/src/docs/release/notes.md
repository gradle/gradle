The Gradle team is excited to announce Gradle 5.2.

This release features a new [Java Platform plugin](#the-java-platform-plugin), [improved C++ plugins](userguide/cpp_plugins.html), [new C++ project types for `gradle init`](userguide/build_init_plugin.html#sec:cppapplication_), [service injection into plugins and project extensions](#service-injection-into-plugins-and-project-extensions), [Kotlin DSL 1.1.3](https://github.com/gradle/kotlin-dsl/releases/tag/v1.1.3) and more.

Read the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.0.
If upgrading from Gradle 4.x, please read [upgrading from Gradle 4.x to 5.0](userguide/upgrading_version_4.html) first.
Users upgrading from 5.1 should not have to worry about breaking changes.

We would like to thank the following community contributors to this release of Gradle:

[Thomas Broyer](https://github.com/tbroyer), [Szczepan Faber](https://github.com/mockitoguy), [Stefan M.](https://github.com/StefMa), 
[Kim Brouer](https://github.com/brouer), [Roberto Perez Alcolea](https://github.com/rpalcolea), [Ian Kerins](https://github.com/isker),
[Josh Soref](https://github.com/jsoref), [Andrew Nessin](https://github.com/andrewnessinjim), [NFM](https://github.com/not-for-me),
[Jean-Michel Fayard](https://github.com/jmfayard), [Arun Ponniah Sethuramalingam](https://github.com/saponniah), [Victor Jacobs](https://github.com/victorjacobs),
[Theodore Ni](https://github.com/tjni), [Sam Holmes](https://github.com/sbholmes), [Artem Zinnatullin ](https://github.com/artem-zinnatullin),
[Ronald Brindl](https://github.com/rbrindl),
and [Richard Newton](https://github.com/ricnewton).

## Upgrade Instructions

Switch your build to use Gradle 5.2 RC1 by updating your wrapper properties:

`./gradlew wrapper --gradle-version=5.2-rc-1`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## The Java Platform plugin

This release features the [Java Platform plugin](userguide/java_platform_plugin.html), which allows you to declare a platform.
Like Maven BOMs, platforms can be used to define a set of versions for dependencies that are known to work together.
These versions can be published and consumed elsewhere as dependency recommendations.

Read the [Java Platform plugin section of the userguide](userguide/java_platform_plugin.html) for more details.

## Building native software with Gradle 

See more information about the [Gradle native project](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-52).

### Recommend C++ projects use new C++ plugins

We introduced [new C++ plugins](https://blog.gradle.org/introducing-the-new-cpp-plugins) last year that are more feature rich and familiar to Gradle users.

In this release, we are recommending new C++ projects use these plugins over [the existing software model plugins](userguide/native_software.html). The new plugins do not currently support C, Objective-C or Objective-C++ out of the box, but they have [a lot of other things to offer](userguide/cpp_plugins.html#cpp:features).

### Build Init can now generate C++ sample projects

If you want to get started quickly with a C++ project, try running `gradle init` and following the instructions to generate a sample C++ application or library. You'll notice that the projects will be generated with the `cpp-application` or `cpp-library` plugins instead of the `cpp` plugin. This is now the recommended approach when starting new projects.

### Support for additional Windows native toolchains

In previous versions of Gradle, native builds using GCC with [cygwin64](https://www.cygwin.com/) or [mingw64](https://mingw-w64.org/doku.php) was reported to work, but was not officially supported.
These toolchains are now officially supported by Gradle. See [the userguide](userguide/cpp_plugins.html#cpp:tool-chain-support) for more information about supported native toolchains.

### Support for testing applications on Windows

Gradle now automatically hides the `main` symbol when building native applications, so applications can be tested on Windows like they already were on macOS and Linux.

Contributed by [Richard Newton](https://github.com/ricnewton). 

## Improvements for custom task authors 

### Service injection into plugins and project extensions

There are several [useful services](userguide/custom_tasks.html#service_injection) that Gradle makes available for task and plugin implementations to use.
Previously, these were available for injection into task and plugin instances.
In this Gradle release these services are also available for injection directly into project extensions.
Services are also now available for injection into the elements of a container created using the `project.container(Class)` method.
Using this feature can help simplify your plugin implementation.

Services can be injected into an instance either as constructor parameters or using a property getter method.
In this release both options are available for all types for which service injection is available.
In previous Gradle versions, using a property getter method was not supported for plugin types.

See the user manual sections on [service injection for plugins](userguide/custom_plugins.html#service_injection) and [service injection into tasks](userguide/custom_tasks.html#service_injection) for details.

### Support for setting environment variables when using Gradle TestKit
  
Gradle [TestKit](userguide/test_kit.html) based tests can now specify environment variables via [`GradleRunner`](javadoc/org/gradle/testkit/runner/GradleRunner.html). These environment variables will be visible to the build under test.

Contributed by [Szczepan Faber](https://github.com/mockitoguy).

## Maven publication can expose resolved versions

When using the [`maven-publish` plugin](userguide/publishing_maven.html), you can now opt-in to publish the _resolved_ dependency versions instead of the _declared_ ones.
For details, have a look at the [dedicated section](userguide/publishing_maven.html#publishing_maven:resolved_dependencies) in the plugin documentation.

## Rich console improvements on Windows

This release includes some improvements to Gradle's console integration on Windows. Gradle now detects when it is running from Mintty on Windows and enables the rich console. Mintty is a popular terminal emulator used by projects such as Cygwin and Git for Windows. 

The implementation of the rich console has been improved to remove some distracting visual artifacts on Windows.

## Annotation processor improvements

Sources generated by annotation processors are now put in a separate directory by default, which means they will no longer pollute your production jar file.

Due to a bug in javac, users would often get warnings about unrecognized processor options during incremental compilation, because the corresponding processor did not need to run. This warning is now fixed and will only appear if an option is really unrecognized.

Contributed by [Thomas Broyer](https://github.com/tbroyer).

## `JavaExec` tasks track the version of Java used

Tasks that use `JavaExec` now track the version of Java used instead of the absolute path to the `java` executable.

Contributed by [Theodore Ni](https://github.com/tjni).

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

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

### `ProjectBuilder` constructor

The default constructor of `ProjectBuilder` is now deprecated. You should always use `ProjectBuilder#builder()` to create instances.

### Breaking changes

<!-- summary and links -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.x.

## External contributions
 
We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
