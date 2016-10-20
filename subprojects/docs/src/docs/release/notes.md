The Gradle team is pleased to announce Gradle 3.2.

It's a relatively quiet release this time around, but there are still plenty of reasons to upgrade.

Perhaps the most significant improvements are in the **incremental build support**, which now has **better up-to-date checking** for Java compilation, copying, and archiving. You can also have Gradle treat any task input as a classpath with the new `@Classpath` annotation.

Users of Gradle's **native build support** gain an important tool in this release. Many of you will be familiar with the `buildDependents` task for classic multi-project builds. This is now available in native builds as well via new `assembleDependents` and `buildDependents` tasks. These are incredibly useful for determining whether your changes have adversely impacted anything that depends on them.

If you use an IDE and have a lot of dependencies in your build—particular dynamic ones—you may have experienced long import times. The underlying issue has been fixed in this release, resulting in significantly improved import times. One example enterprise build showed **a 100-fold improvement**!

Our users trialling the **Kotlin build script support** will be glad to hear that progress continues apace with support for **multi-project builds**. And it's easier to try this feature on Windows now that a bug in compiling scripts on that platform has been fixed.

The last change we want to bring to your attention has been a long time coming and will affect a large number of builds: the shortcut syntax for declaring tasks (via `<<`) has **now been deprecated**. The eagle-eyed among you will notice that the user guide examples have been updated to use `doLast()` and we strongly recommend that you follow suit. This feature will be removed in Gradle 5.0! See the [Deprecations section](#deprecations) for more details.

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Incremental build improvements

Gradle's incremental build support continues to see improvements, particularly in the area of more accurate up-to-date checks.

#### Java compilation tracks Java version used

Gradle's Java plugin has taken account of the `sourceCompatibility` and `targetCompatibility` versions for a while now with respect to working out whether compiled class files are up to date or not. But the version of the Java compiler used was ignored.

This release fixes that, which means changing the Java compiler version—say from Java 7 to Java 8—will now result in recompilation.

#### Better change tracking in copy and archive tasks

A long-standing issue with copy-based tasks has now been fixed: the destination directory is finally taken into account for the tasks' up-to-date checks. So changing the `into` value will result in the task running again.

In addition, Gradle now tracks changes to case-sensitivity, duplication strategy, file mode and dir mode on child specs, not just the main one.

#### Classpath tracking

Input properties that should be treated as Java classpaths can now be annotated with `@Classpath`. This allows the task to ignore irrelevant changes to the property, such as different names for the same files. It is similar to annotating the property with `@OrderSensitive` and `@PathSensitive(RELATIVE)`, but it will also ignore the names of JAR files directly added to the classpath.

#### Removing all sources will delete outputs

For a long time Gradle supported skipping the execution of a task entirely if it didn't have any _sources._ This feature can be enabled by annotating an input file or file collection property with `@SkipWhenEmpty`. In previous versions of Gradle, removing all of a task's input files would leave the previous build execution's outputs in place on subsequent runs. This is now fixed: stale outputs are properly removed.

### Build Dependents for Native Binaries

Sometimes, you may need to *assemble* (compile and link) or *build* (compile, link and test) a component or binary and its *dependents* (things that depend upon the component or binary). The native software model now provides tasks that enable this capability.

First, the `dependentComponents` task generates a report that gives insight about the relationships between each component.

Second, the `buildDependents*` and `assembleDependents*` tasks allow you to assemble or build a component and its dependents in one step.

See the User guide section on “[Assembling or building dependents](userguide/native_binaries.html#sec:dependents)“ in the “[Building native software](userguide/native_binaries.html)“ chapter for more information.

### New `preferProjectModules()` conflict resolution strategy for multi-project builds

The `preferProjectModules()` configuration option can now be used in multi-project builds.

    configurations.all.resolutionStrategy.preferProjectModules()

With this option it is possible to tell Gradle to always resolve a project dependency to a subproject,
if the corresponding subproject exists in the build. Without this option, other dependencies to a higher
version of the same module cause the replacement of the subproject by the other version in the dependency tree.

### Improved deprecation warnings

When a line in a build script triggers a deprecation warning, Gradle now prints its location to the console. You can also pass the command line option `-s` or `-S` to get the whole stack trace.

The improved log message should make it much easier to spot and fix those warnings, as demonstrated by this sample output:

    > gradle tasks
    The Jetty plugin has been deprecated and is scheduled to be removed in Gradle 4.0. Consider using the Gretty (https://github.com/akhikhl/gretty) plugin instead.
            at build_dhrhtn4oo56t198zc6nkf59c4.run(/home/someuser/project-dir/build.gradle:3)
    ...

This is particularly important now that the shortcut syntax for task definitions (`<<`) has been deprecated!

### HTTP Basic Authentication for wrapper distributions

The Gradle Wrapper can now download Gradle distributions from a server that requires authentication. This allows you to host the Gradle distribution on a private server protected with HTTP Basic Authentication.

See the User guide section on “[authenticated distribution download](userguide/gradle_wrapper.html#sec:authenticated_download)“ for more information.

As stated in the User guide, please note that this shouldn't be used over insecure connections.

### Ctrl-C no longer stops the Daemon

We made a number of improvements in Gradle 3.1 that allow the Daemon to cancel a running build when a client disconnects unexpectedly. This included many scenarios in which the user pressed Ctrl-C, but not all. In other words, a Ctrl-C would still sometimes kill the Daemon. 

This release resolves the remaining issues so that Ctrl-C will reliably _not_ kill the Daemon. As long as the build cancels in a timely manner, the Daemon will then be available for reuse and subsequent builds will reap the performance benefits of a warmed up Daemon.

### Multi-project builds with Kotlin scripting

This release ships with Gradle Script Kotlin v0.4.0.

v0.4.0 improves support for multi-project builds of Kotlin based projects, enables the usage of Gradle Script Kotlin extensions to the Gradle API in `buildSrc` and fixes Kotlin build script compilation on Windows.

See the [Gradle Script Kotlin v0.4.0 release notes](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.4.0) for the details.

## Fixed issues

### Fixed a performance problem in the Tooling API

The dependency resolution caches were not being filled when building Tooling API models. As a result, IDE import was very slow when the caches were cold. This especially affected builds with many dynamic dependencies and low cache timeouts. One large enterprise project saw import times drop by a factor of 100.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### The left shift operator on the Task interface

The left shift (`<<`) operator acts as an alias for adding a `doLast` action to an existing task. For newcomers to Gradle, the meaning of the operator is not immediately apparent and
leads to mixing configuration code with action code. Such misconfigured tasks lead to unexpected runtime behavior.

Let's consider the following two examples to illustrate some common mistakes:

 - A build configures the `description` property and defines an action using the left shift operator. As a result, the task would not configure the task's description.

        // WRONG: Description assigned in execution phase
        task helloWorld << {
            description = 'Prints out a message.'
            println 'Hello world!'
        }

        // CORRECT: Description assigned in configuration phase
        task helloWorld {
            description = 'Prints out a message.'
            doLast {
                println 'Hello world!'
            }
        }

 - A build defines a typed task using the left shift operator. As a result, the task is always `UP-TO-DATE` as the inputs and outputs of the task are configured during the execution phase.

        // WRONG: Configuring task in execution phase
        task copy(type: Copy) << {
            from 'source'
            into "$buildDir/output"
        }

        // CORRECT: Configuring task in configuration phase
        task copy(type: Copy) {
            from 'source'
            into "$buildDir/output"
        }

With this version of Gradle, the left shift operator on the `Task` interface is deprecated and is scheduled to be removed with the next major release. There's no direct replacement
for the left shift operation. Please use the existing methods `doFirst` and `doLast` to define task actions.

## Potential breaking changes

### Tooling API builders run before buildFinished listeners

Tooling API model builders are now executed before any `buildFinished` listeners have been notified.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Shintaro Katafuchi](https://github.com/hotchemi) - Fixed typo in `ShadedJar.java` under `buildSrc`
- [Jörn Huxhorn](https://github.com/huxi) - Show location in build file for deprecation warning, remove output files when task loses all its source files
- [Jeff Baranski](https://github.com/jbaranski) - Fix doc bug with turning off daemon in a .bat file
- [Justin Sievenpiper](https://github.com/jsievenpiper) - Prevent navigating down to JDK classes when detecting the parent test class
- [Alex Proca](https://github.com/alexproca) - Limit Unix Start Scripts to use POSIX standard sh
- [Spencer Allain](https://github.com/merscwog) - Do not require a password when using custom `javax.net.ssl.trustStore` for HTTP resource access over TLS 
- [Sandu Turcan](https://github.com/idlsoft) - Added `preferProjectModules()` option to dependency resolution strategy
- [Oliver Trosien](https://github.com/otrosien) - Wrong location of test resources in documentation
- [Andreas Schmidt](https://github.com/remigius42) - Fixed grammatical errors in documentation
- [Janito Vaqueiro Ferreira Filho](https://github.com/jvff) - Describe relationship between sources and binaries in native build documentation
- [Daniel Thomas](https://github.com/DanielThomas) - Added ability to revalidate external resource requests

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
