<meta property="og:image" content="https://gradle.org/assets/images/releases/gradle-default.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:image" content="https://gradle.org/assets/images/releases/gradle-default.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
[Aharnish Solanki](https://github.com/Ahar28),
[Benedikt Johannes](https://github.com/benediktjohannes),
[Devendra Reddy Pennabadi](https://github.com/devareddy05),
[Dmytro Rodionov](https://github.com/smplio),
[Dreeam](https://github.com/Dreeam-qwq),
[Elías Hernández Rodríguez](https://github.com/EliasHdzR),
[Eng Zer Jun](https://github.com/Juneezee),
[FinlayRJW](https://github.com/FinlayRJW),
[Kamal Kansal](https://github.com/kamalkansal27),
[Marcono1234](https://github.com/Marcono1234),
[Nelson Osacky](https://github.com/runningcode),
[Philip Wedemann](https://github.com/hfhbd),
[Ravi](https://github.com/rkdfx),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Ryan Schmitt](https://github.com/rschmitt),
[Sebastian Schuberth](https://github.com/sschuberth),
[seunghun.ham](https://github.com/seung-hun-h),
[sk-reddy17](https://github.com/sk-reddy17),
[Suvrat Acharya](https://github.com/Suvrat1629),
[Vedant Madane](https://github.com/VedantMadane).

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew :wrapper --gradle-version=@version@ && ./gradlew :wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

### Configuration Cache improvements
Gradle provides a [Configuration Cache](userguide/configuration_cache.html) that improves build time by caching the result of the configuration phase and reusing it for subsequent builds.

### Test reporting and execution
Gradle provides a [set of features and abstractions](userguide/java_testing.html) for testing JVM code, along with test reports to display results.

### CLI, logging, and problem reporting
Gradle provides an intuitive [command-line interface](userguide/command_line_interface.html), detailed [logs](userguide/logging.html), and a structured [problems report](userguide/reporting_problems.html#sec:generated_html_report) that helps developers quickly identify and resolve build issues.

#### Non-interactive mode

Gradle now supports a `--non-interactive` command-line option to disable all interactive console prompting.
This is useful for running Gradle in automated environments such as CI pipelines, scripts, and AI agents where no user input is available.

See the [Non-interactive mode](userguide/command_line_interface.html#sec:non_interactive) section in the Gradle User Manual for more information.

#### NO_COLOR support

Gradle now honors the `NO_COLOR` environment variable following the [no-color.org](https://no-color.org/) convention.
When `NO_COLOR` is set and non-empty, Gradle suppresses color output while preserving other styling (bold, underline) and rich features (progress bars, animations).

![NO-COLOR Screenshot](release-notes-assets/no-color-screenshot.png)

See the [Environment variables](userguide/build_environment.html#sec:gradle_environment_variables) section in the Gradle User Manual for more information.

### Build authoring improvements
Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

### Platform and toolchain management
Gradle provides comprehensive support for [Native development](userguide/building_cpp_projects.html) and [JVM languages](userguide/building_java_projects.html), featuring automated [Toolchains](userguide/toolchains.html) for seamless JDK management.

### Core plugin and plugin authoring enhancements
Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

### Security and infrastructure
Gradle provides robust [security features and underlying infrastructure](userguide/security.html) to ensure that builds are secure, reproducible, and easy to maintain.

### Tooling and IDE integration
Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

### General improvements
Gradle provides various incremental updates and performance optimizations to ensure the continued reliability of the build ecosystem.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

* [`getNetworkTimeout()`](javadoc/org/gradle/api/tasks/wrapper/Wrapper.html#getNetworkTimeout()) in `Wrapper`

## Documentation and training

## Fixed issues

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
