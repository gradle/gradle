# Debugging Gradle

This guide is intended to get you started on how to debug the integration tests and different parts of the Gradle runtime in the Gradle code base.

## TL:DR

To debug a unit test, just run it in the debug mode.

To debug an integration test, in most cases, all you need to do is to start listening debugger via the "Daemon debug" run configuration in IntelliJ IDEA and then run the test in debug mode with the `forkingIntegTest` task. 
The debugger will automatically connect to the Gradle Daemon, and you can start debugging it.

If you need to debug something besides the Daemon, read below.

## Overview

### IntelliJ IDEA setup

For most cases, use [listening debugger](https://www.jetbrains.com/help/idea/attaching-to-local-process.html#attach-to-remote]).

The build should create debugging run configurations for you [automatically](../build-logic/idea/src/main/kotlin/gradlebuild.ide.gradle.kts#L82), but you'll need to make sure to enable automatic restart of the debugging session once the debuggee is gone.
Unfortunately, setting this up [is not automated yet](https://github.com/JetBrains/gradle-idea-ext-plugin/issues/84).

![](./images/auto-restart-debugger.png)

Before you start debugging, start the "Daemon debug" or "Debug Launcher" configuration. 
It should be automatically re-used for subsequent test runs.

Be careful when you stop the test: you should stop the test, not the debugger.

### Gradle test executors

There are [several executors](../testing/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/executer/GradleContextualExecuter.java) available for running integration tests configured in Gradle.

Most of them are forking, and you should be able to debug a test with any of them by running the test in the debug mode.
The test detects that it is running in debug mode and provides all necessary information to the forked daemon to connect to the debugger.

There is also an embedded executor that runs the test in the same JVM as the test runner.
While it's convenient for debugging and sometimes may be faster, it's not recommended to use it because it may behave differently in some corner cases due to classloader issues.

In test code, you can access the current executer via `executer` and modify its options.

### Gradle debug options

You may also refer to these resources about debugging Gradle build in general:
* [Gradle Troubleshooting](https://docs.gradle.org/current/userguide/troubleshooting.html)
* [Debugging options](https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_debugging) 

The [blog series](https://blog.gradle.org/how-gradle-works-1) about how Gradle works can help understand why multiple JVMs are involved in the build process.

## Debugging daemon

Just starting the test in debug mode in IDE with the "Debug Daemon" configuration running should do the trick.

You can also explicitly enable debugging for the daemon by setting `debugDaemon` gradle property to any value or changing the test code with `executer.startBuildProcessInDebugger` method.

## Debugging launcher

You can enable debugging for the launcher by setting `debugLauncher` gradle property to any value or changing the test code with `executer.startLauncherInDebugger` method.

Note that by default, port 5006 is used for debugging the launcher, so you'll also need to start the "Debug Launcher" configuration.

## Debugging wrapper

The main obstacle to wrapper debugging is the minification of the wrapper jar. 
To mitigate this, minifying is disabled when the `debugLauncher` property is present.
Note that means that it wouldn't be 100% the same as in production and may behave differently in corner cases.

Otherwise, the debugging process is the same as for the launcher.

## Debugging compiler daemon

In order to debug compiler daemons, you'll need to edit the test code to pass the debug JVM args to the forked daemon process:

```groovy
tasks.withType(JavaCompile) {
    options.forkOptions.jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:5006")
}
```

This should also work with `GroovyCompile` and `ScalaCompile` tasks.

Make sure not to commit the test code changes you've made just for the debugging.

## Debugging worker actions with process isolation

To debug a worker, you'll need to edit the test code to enable debugging for the worker process.

To debug such an action, you have two options:

1. enable debugging via the fork options in the spec:

    ```java
    getWorkerExecutor()
        .processIsolation(spec -> {
            spec.getForkOptions().getDebugOptions().getEnabled().set(true);
            spec.getForkOptions().getDebugOptions().getPort().set(5005);
        })
    ```

   You can then run your integration test normally, and attach a remote debugger to the port.

2. If what you are trying to debug does not require process-level isolation, you can change the code to use classloader isolation instead:

    ```java
    getWorkerExecutor()
        .classloaderIsolation(spec -> {
            // ...
        })
        // ...
    ```

In either case, make sure not to commit the test code changes you've made just for the debugging.

## Debugging cross-version tests

Cross-version tests use the Tooling API to launch Gradle builds.

To debug a build in those tests, use [the same parameters](#gradle-debug-options) as if it was a regular Gradle build.

Add `-Dorg.gradle.debug=true` to the `LongRunningOperation` you want to debug with `LongRunningOperation.withArguments(...)`.

## Debugging sync (project import) in IntelliJ IDEA

This is useful if a problem with Gradle surfaces when IntelliJ is trying to (re-)import a Gradle build.

The project import (or sync as we often call it) works via a Tooling API client embedded in the IntelliJ,
and it talks to a Gradle daemon to fetch models required to update the IDE's UI.
Since the IDE runs a separate process, we need to use remote debugging.

TIP: Use different *versions* of IntelliJ for viewing Gradle-sources-to-be-debugged and the target build.
Since a locally built Gradle distribution is usually used to debug problems,
it might be required to entirely restart the IDE used for synching the target build.
In this case, it is handy to keep the IDE with Gradle sources independent.
For instance, use **IntelliJ Ultimate** to view Gradle sources
and **IntelliJ Community** to sync the target build.

We will use this terminology:

- **Target build IDE** -- the IDE which we'll be using to initiate the sync process
- **Gradle sources IDE** -- the IDE in which we'll have the debugger

### Setting up the target build IDE

If you want to debug sync with a patched version of Gradle,
[install it locally](../CONTRIBUTING.md#install-gradle-locally) first:

```bash
./gradlew install -Pgradle_installPath=~/.local/gradle_install
```

Now, we open the desired project in the target build IDE and make sure it uses the local installation for sync:

![](./images/local-installation-for-sync.jpg)

Configure the JVM args used by the Gradle daemon in the `gradle.properties` of the target build:

```properties
org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005,suspend=y
```

### Debugging the sync process

We can trigger sync by doing any of the following:

![](./images/trigger-sync.jpg)

Stop the Gradle daemon before doing a repeated sync if you disconnected the debugger. 
Or keep the current debugging session connected by letting the program run past finishing the current sync iteration you were debugging.
