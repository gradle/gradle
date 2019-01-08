The Gradle team is excited to announce Gradle 5.2.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
[Thomas Broyer](https://github.com/tbroyer).

## Improvements for plugin authors 

### Service injection into project extensions

There are several [useful services](userguide/custom_tasks.html#service_injection) that Gradle makes available for task and plugin implementations to use. Previously, these were available for injection into task and plugin instances. In this Gradle release these services are also available for injection directly into project extensions. Using this can help simplify your plugin implementation.

See the [User Manual](userguide/custom_plugins.html#service_injection) for details.

TBD - can have abstract service getter
TBD - can use server getter on plugins
TBD - can use service injection on `project.container(Class)` elements 

## The Java Platform plugin

A new plugin, the [Java Platform plugin](userguide/java_platform_plugin.html) allows the declaration and publication of platforms for the Java ecosystem.
A platform is typically published as a bill-of-material (BOM) file, and can be used as a source of recommendations for versions, between projects or externally.
Read the [Java Platform plugin section of the userguide](userguide/java_platform_plugin.html) for more details.

## Maven publication: expose resolved versions

When using the [`maven-publish` plugin](userguide/publishing_maven.html), you can now opt-in to publish the _resolved_ dependency versions instead of the _declared_ ones.
For details, have a look at the [dedicated section](userguide/publishing_maven.html#publishing_maven:resolved_dependencies) in the plugin documentation.

## Support for additional Windows native toolchains

In previous versions of Gradle, native builds using GCC with [cygwin64](https://www.cygwin.com/) or [mingw64](https://mingw-w64.org/doku.php) was reported to work, but was not officially supported.
These toolchains are now officially supported by Gradle.  See [the userguide](userguide/native_software.html#native-binaries:tool-chain-support) for more information about supported native toolchains.

## Rich console improvements on Windows

This release includes some improvements to Gradle's console integration on Windows. Gradle now detects when it is running from Mintty on Windows and enables the rich console. Mintty is a popular terminal emulator used by projects such as Cygwin and Git for Windows. 

The implementation of the rich console has been improved to remove some distracting visual artifacts on Windows. 

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

The default constructor of `ProjectBuilder` is now deprecated.
You should always use `ProjectBuilder#builder()` to create instances.

### Breaking changes

<!-- summary and links -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.x.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Thomas Broyer](https://github.com/tbroyer) - Provide default value for annotationProcessorGeneratedSourcesDirectory (gradle/gradle#7551)
 - [Stefan M.](https://github.com/StefMa) - Deprecate ProjectBuilder constructor (gradle/gradle#7444)
 - [Roberto Perez Alcolea](https://github.com/rpalcolea) - Ignore ProjectBuilder deprecation warning when using builder (gradle/gradle#8067)
 - [Ian Kerins](https://github.com/isker) - Fix links to CreateStartScripts DSL docs in Application Plugin user manual chapter (gradle/gradle#7938)
 - [Kim Brouer](https://github.com/brouer) - Fix issue with leading zero in version numbers (gradle/gradle#7806)
 - [Richard Newton](https://github.com/ricnewton) - Enable hiding of main symbols for Windows executables (gradle/gradle#8127)
 - [Thomas Broyer](https://github.com/tbroyer) - Add annotation processor generated sources to SourceSetOutput (gradle/gradle#8134)
 - [Thomas Broyer](https://github.com/tbroyer) - Silence unrecognized annotation processor option during incremental processing (gradle/gradle#8135)
 
We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## Upgrade Instructions

Switch your build to use Gradle 5.2 by updating your wrapper properties:

`./gradlew wrapper --gradle-version=5.2`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
