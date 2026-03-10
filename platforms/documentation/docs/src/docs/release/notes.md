<meta property="og:image" content="https://gradle.org/images/releases/gradle-default.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:image" content="https://gradle.org/images/releases/gradle-default.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:
[Ujwal Suresh Vanjare](https://github.com/usv240),
[Suvrat Acharya](https://github.com/Suvrat1629)

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the use case or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

To embed videos, use the macros below. 
You can extract the URL from YouTube by clicking the "Share" button. 
For Wistia, contact Gradle's Video Team.
@youtube(Summary,6aRM8lAYyUA?si=qeXDSX8_8hpVmH01)@
@wistia(Summary,a5izazvgit)@

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->


## New features and usability improvements

### Type-safe Accessors for Precompiled Kotlin Settings Plugins

Gradle now generates type-safe Kotlin accessors for [precompiled convention Settings plugins](userguide/pre_compiled_script_plugin_advanced.html) (`*.settings.gradle.kts`).
Previously, when writing a convention plugin for `settings.gradle.kts`, you often had to use string-based APIs to configure extensions or plugins.
Now, as long as the `kotlin-dsl` plugin is applied, Gradle generates accessors that provide IDE autocompletion and compile-time checking for your settings scripts, matching the experience already available for Project-level convention plugins.

To enable these accessors, ensure your convention plugin build includes the `kotlin-dsl` plugin:

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}
```

### Improved `--help` output

The output of `gradle --help` has been refreshed to be clearer and more consistent. Changes include:
- Options are now grouped into labeled sections,
- Several options have moved to more fitting categories,
- Descriptions were adjusted to follow a more consistent phrasing, and
- The output now respects the width of the terminal (if present), wrapping text as needed to avoid horizontal scrolling.

The updated output printed to a terminal with 80 columns looks like this:

```
To see help contextual to the project, use gradle help

To see more detail about a task, run gradle help --task <task>

To see a list of available tasks, run gradle tasks

USAGE: gradle [option...] [task...]

--                                   Signals the end of built-in options. Parses
                                     subsequent parameters as tasks or task
                                     options only.

Help:
  --help, -?, -h                     Shows this help message.
  --show-version, -V                 Prints version information and continues.
  --version, -v                      Prints version information and exits.

Logging:
  --console                          Specifies which type of console output to
                                     generate. Supported values are 'plain',
                                     'colored', 'auto' (default), 'rich', or
                                     'verbose'.
  --console-unicode                  Specifies which character types are allowed
                                     in the console output. Supported values are
                                     'auto' (default), 'disable', or 'enable'.
  --debug, -d                        Sets log level to debug. Includes the
                                     normal stacktrace.
  --full-stacktrace, -S              Prints the full (very verbose) stacktrace
                                     for all exceptions.
  --info, -i                         Sets the log level to info.
  --quiet, -q                        Logs errors only.
  --stacktrace, -s                   Prints the stacktrace for all exceptions.
  --warn, -w                         Sets the log level to warn.

Configuration:
  --gradle-user-home, -g             Specifies the Gradle user home directory.
                                     Default is ~/.gradle.
  --include-build                    Includes the specified build in the
                                     composite.
  --init-script, -I                  Specifies an initialization script.
  --offline                          Runs the build without accessing network
                                     resources.
  --project-cache-dir                Specifies the project-specific cache
                                     directory. Default is .gradle in the root
                                     project directory.
  --project-dir, -p                  Specifies the start directory for Gradle.
                                     Default is the current directory.
  --project-prop, -P                 Sets a project property for the build
                                     script (for example, -Pmyprop=myvalue).
  --refresh-dependencies, -U         Refreshes the state of dependencies.
  --system-prop, -D                  Sets a JVM system property (for example,
                                     -Dmyprop=myvalue).

Execution:
  --continue                         Continues task execution after a task
                                     failure.
  --no-continue                      Stops task execution after a task failure.
  --continuous, -t                   Enables continuous build. Gradle does not
                                     exit and will re-execute tasks when task
                                     file inputs change.
  --dry-run, -m                      Runs the build with all task actions
                                     disabled.
  --exclude-task, -x                 Specifies a task to exclude from execution.
  --no-rebuild, -a                   Disables rebuilding of project
                                     dependencies.
  --rerun-tasks                      Ignores previously cached task results.

Performance:
  --build-cache                      Enables the Gradle build cache. Gradle will
                                     try to reuse outputs from previous builds.
  --no-build-cache                   Disables the Gradle build cache.
  --configuration-cache              Enables the configuration cache. Gradle
                                     will try to reuse the build configuration
                                     from previous builds.
  --no-configuration-cache           Disables the configuration cache.
  --configure-on-demand              Configures necessary projects only. Gradle
                                     will attempt to reduce configuration time
                                     for large multi-project builds.
                                     [incubating]
  --no-configure-on-demand           Disables the use of configuration on
                                     demand. [incubating]
  --max-workers                      Configures the maximum number of concurrent
                                     workers Gradle is allowed to use.
  --parallel                         Builds projects in parallel. Gradle will
                                     attempt to determine the optimal number of
                                     executor threads to use.
  --no-parallel                      Disables parallel project execution.
  --priority                         Specifies the scheduling priority for the
                                     Gradle daemon and all processes launched by
                                     it. Supported values are 'normal' (default)
                                     or 'low'.
  --watch-fs                         Enables file system watching. Reuses file
                                     system data for subsequent builds.
  --no-watch-fs                      Disables file system watching.

Security:
  --dependency-verification, -F      Configures the dependency verification
                                     mode. Supported values are 'strict',
                                     'lenient', or 'off'.
  --export-keys                      Exports the public keys used for dependency
                                     verification.
  --refresh-keys                     Refreshes the public keys used for
                                     dependency verification.
  --update-locks                     Performs a partial update of the dependency
                                     lock. Allows passed-in module notations to
                                     change version. [incubating]
  --write-locks                      Persists dependency resolution for locked
                                     configurations. Ignores existing locking
                                     information if it exists.
  --write-verification-metadata, -M  Generates checksums for dependencies used
                                     in the project. Accepts a comma-separated
                                     list.

Diagnostics:
  --configuration-cache-problems     Configures how the configuration cache
                                     handles problems (fail or warn). Supported
                                     values are 'warn', or 'fail' (default).
  --problems-report                  Enables the HTML problems report.
                                     [incubating]
  --no-problems-report               Disables the HTML problems report.
                                     [incubating]
  --profile                          Profiles build execution time. Generates a
                                     report in the <build_dir>/reports/profile
                                     directory.
  --property-upgrade-report          Runs the build with the experimental
                                     property upgrade report. [incubating]
  --task-graph                       Prints the task graph instead of executing
                                     tasks.
  --warning-mode                     Specifies which mode of warnings to
                                     generate. Supported values are 'all',
                                     'fail', 'summary' (default), or 'none'.

Daemon:
  --daemon                           Uses the Gradle daemon to run the build.
                                     Starts the daemon if it is not running.
  --no-daemon                        Runs the build without the Gradle daemon.
                                     Useful occasionally if you have configured
                                     Gradle to always run with the daemon by
                                     default.
  --foreground                       Starts the Gradle daemon in the foreground.
  --status                           Shows the status of running and recently
                                     stopped Gradle daemons.
  --stop                             Stops the Gradle daemon if it is running.

Develocity:
  --scan                             Generates a Build Scan (powered by
                                     Develocity).
  --no-scan                          Disables the creation of a Build Scan.
```

### Domain Object Collections can be made immutable

Plugin and build authors can now lock domain object collections to prevent further modifications using the new `disallowChanges()` method.
- Once `disallowChanges()` is called, elements can no longer be added to or removed from the collection.
- Invoking this method does not force the realization of lazy items previously added to the collection. 
- This lock applies only to the collection itself. Individual objects within the collection can still be modified.

```kotlin
val myCollection = objects.domainObjectContainer(MyType::class)
val main = MyType("main")

myCollection.add(main)
myCollection.add(MyType("test"))

myCollection.disallowChanges()    // the collection is now immutable
main.setFoo("bar")                // individual elements can still be modified
myCollection.add(MyType("other")) // this will fail
myCollection.remove(main)         // this will fail
```

## Tooling integration improvements

Tooling API clients can now directly access Gradle help and version information the same way as the Gradle CLI.
This allows IDEs and other tools to provide a more consistent user experience when interacting with Gradle.
For example, In IntelliJ IDEA users will be able to run `--help` and `--version` via the `Execute Gradle task` toolbar action.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

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
