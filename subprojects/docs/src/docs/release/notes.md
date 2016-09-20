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

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

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

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Shintaro Katafuchi](https://github.com/hotchemi) - Fixed typo in `ShadedJar.java` under `buildSrc`
- [Jörn Huxhorn](https://github.com/huxi) - Show location in build file for deprecation warning
- [Jeff Baranski](https://github.com/jbaranski) - Fix doc bug with turning off daemon in a .bat file
- [Justin Sievenpiper](https://github.com/jsievenpiper) - Prevent navigating down to JDK classes when detecting the parent test class
- [Alex Proca](https://github.com/alexproca) - Limit Unix Start Scripts to use POSIX standard sh
- [Spencer Allain](https://github.com/merscwog) - Do not require password for truststore

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
