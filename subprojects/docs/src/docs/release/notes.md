## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Origin of deprecation warning within build script is rendered on command line

For each deprecation warning Gradle now prints its location in the
build file to the console. When passing the command line option `-s` or `-S`
to Gradle then the whole stack trace is printed out.
The improved log message should make it much easier to spot and fix those warnings.

    > gradle tasks
    The Jetty plugin has been deprecated and is scheduled to be removed in Gradle 4.0. Consider using the Gretty (https://github.com/akhikhl/gretty) plugin instead.
            at build_dhrhtn4oo56t198zc6nkf59c4.run(/home/someuser/project-dir/build.gradle:3)

    ...

### The Wrapper can now use HTTP Basic Authentication to download distributions

The Gradle Wrapper can now download Gradle distributions from a server requiring authentication.
This allows you to host the Gradle distribution on a private server protected with HTTP Basic Authentication.

See the User guide section on “[authenticated distribution download](userguide/gradle_wrapper.html#sec:authenticated_download)“ for more information.

As stated in the User guide, please note that this shouldn't be used over insecure connections.

### Ctrl-c no longer stops the Daemon

In Gradle 3.1 we made a number of improvements to allow the daemon to cancel a running build when a client disconnects unexpectedly, but there were situations where pressing ctrl-c during a build could still cause the Daemon to exit.  With this release, any time ctrl-c is sent, the Daemon will attempt to cancel the running build.  As long as the build cancels in a timely manner, the Daemon will then be available for reuse and subsequent builds will reap the performance benefits of a warmed up Daemon.

### Continuous build usability improvements

Continuous build now ignores changes in the root project's `.gradle` directory and in all `build` directories. 
This change is important for projects that have the project directory as an input in some task in the build. 
Furthermore changes are now ignored to files or directories matching the default excludes that Gradle uses. 
Some of the default excludes patterns are `.git`, `.hg`, `*~`, `#*#`, `.DS_Store`, `.#*` , `._*`.

### New preferProjectModules() conflict resolution strategy for multi-project builds

The `preferProjectModules()` configuration option can now be used in multi-project builds.

    configurations.all.resolutionStrategy.preferProjectModules()

With this option it is possible to tell Gradle to always resolve a project dependency to a subproject,
if the corresponding subproject exists in the build. Without this option, other dependencies to a higher
version of the same module cause the replacement of the subproject by the other version in the dependency tree.

### Incremental build improvements

#### Java compilation tracks Java version used

If you change the Java version used to compile your sources, the compile task will now become out-of-date, and the sources will be recompiled.

#### Better change tracking in copy and archive tasks

When changing the destination for a copy spec in a `Copy`, `Zip` or `Jar` etc. task, the task now becomes out-of-date. Previously Gradle only tracked changes to case-sensitivity, duplication strategy and file- and dir modes on the main spec; now it tracks it for child specs, too.

#### Classpath tracking

Input properties that should be treated as Java classpaths can now be annotated with `@Classpath`. This allows the task to ignore irrelevant changes to the property, such as changing the name of the files on the classpath. It is similar to annotating the the property with `@OrderSensitive` and `@PathSensitive(RELATIVE)`, but it will also ignore the names of JAR files directly added to the classpath.

#### Removing all sources will delete outputs

For a long time Gradle supported skipping the execution of a task entirely if it didn't have any _sources._ This feature can be enabled by annotating an input file property with `@SkipWhenEmpty`. In previous versions of Gradle however, when all the sources of the task were removed since the last build, the previous outputs were left in place. This is now fixed, and in such cases the stale outputs are properly removed.

### Build Dependents for Native Binaries

Sometimes, you may need to *assemble* (compile and link) or *build* (compile, link and test) a component or binary and its *dependents* (things that depend upon the component or binary). The native software model now provides tasks that enable this capability.

First, the *dependent components* report gives insight about the relationships between each component.
Second, the *build and assemble dependents* tasks allow you to assemble or build a component and its dependents in one step.

See the User guide section on “[Assembling or building dependents](userguide/native_binaries.html#sec:dependents)“ in the “[Building native software](userguide/native_binaries.html)“ chapter for more information.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

### Fixed a performance problem in the Tooling API

The dependency resolution caches were not being filled when building Tooling API models. 
As a result, IDE import was very slow when the caches were cold. This especially affected
builds with many dynamic dependencies and low cache timeouts. One large enterprise project
saw import times drop by a factor of 100.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### The left shift operator on the Task interface

The left shift (`<<`) operator acts as alias for adding a `doLast` action for an existing task. For newcomers to Gradle, the meaning of the operator is not immediately apparent and
leads to mixing configuration code with action code. Consequently, mis-configured task lead to unexpected runtime behavior. Let's consider the following two examples to illustrate common
mistakes.

_Definition of a default task that configures the `description` property and defines an action using the left shift operator:_ As a result, the task would not configure the task's description.

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

_Definition of an enhanced task using the left shift operator:_ As a result, the task is always `UP-TO-DATE` as the inputs and outputs of the `Copy` task are configured during the execution
phase of the Gradle build lifecycle which is to late for Gradle to pick up the configuration.

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

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
