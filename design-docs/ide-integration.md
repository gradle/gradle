This spec defines a number of stories to improve IDE experience with Gradle. It covers changes in Gradle to improve those features that
directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins
(i.e. the plugins that run inside Gradle to provide the IDE models).

This spec does not cover more general features such as improving dependency resolution or configuration performance.

# Implementation plan

Below is a list of stories, in approximate priority order:

## GRADLE-2434 - IDE visualises and runs aggregate tasks

On the command-line I can run `gradle test` and this will find and execute all tasks with name `test` in the current project
and all its subprojects.

Expose some information to allow the IDE to visualise this and execute builds in a similar way.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-gradle-2434---expose-the-aggregate-tasks-for-a-project)

## Tooling API client cancels an operation

Add some way for a tooling API client to request that an operation be cancelled.

The implementation will do the same thing as if the daemon client is disconnected, which is to drop the daemon process.
Later stories incrementally add more graceful cancellation handling.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-tooling-api-client-cancels-an-operation)

## Expose build script compilation details

See [tooling-api-improvements.md](tooling-api-improvements.md#story-expose-the-compile-details-of-a-build-script):

Expose some details to allow some basic content assistance for build scripts:

- Expose the classpath each build script.
- Expose the default imports for each build script.
- Expose the Groovy version for each build script.

## Tooling API stability tests

Introduce some stress and stability tests for the tooling API and daemon to the performance test suite. Does not include
fixing any leaks or stability problems exposed by these tests. Additional stories will be added to take care of such issues.

## Build script classpath can contain a changing jar

Fix ClassLoader caching to detect when a build script classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.

## Can clean after compiling on Windows

Fix GRADLE-2275.

## Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly (say, as soon as they become idle).

## Expose Java components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Java language level and other details about a Java component.

Expose the corresponding Eclipse and IDEA model.

## Expose Groovy components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Groovy language level and other details about a Groovy component.

Expose the corresponding Eclipse and IDEA model.

## Expose Scala components to the IDE

Expose Scala language level and other details about a Scala component.

Expose the corresponding Eclipse and IDEA model.

## Expose the publications of a project

See [tooling-api-improvements.md](tooling-api-improvements.md#story-expose-the-publications-of-a-project):

Expose the publications of a project so that the IDE can wire together Gradle and Maven builds.

## Expose Web components to the IDE

Expose Web content, servlet API version, web.xml descriptor, runtime and container classpaths, and other details about a web application.

Expose the corresponding Eclipse and IDEA model.

## Expose J2EE components to the IDE

Expose Ear content, J2EE API versions, deployment descriptor and other details about a J2EE application.

Expose the corresponding Eclipse and IDEA model.

## IDE hides implementation tasks

On the command-line I can run `gradle tasks` and see the 'main' tasks for the build, and `gradle tasks --all` to see all the tasks.

Expose some information to allow the IDE to visualise this.

## Expose artifacts to IDEA

Expose details to allow IDEA to build various artifacts: http://www.jetbrains.com/idea/webhelp/configuring-artifacts.html

## Daemon handles more immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, only `file.encoding` is treated as an immutable system property.

Add support for the following properties:

- The jmxremote system properties (GRADLE-2629)
- The SSL system properties (GRADLE-2367)

## Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

## Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

## Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

## Tooling API client listens for changes to a tooling model

Provide a subscription mechanism to allow a tooling API client to listen for changes to the model it is interested in.

## Tooling API client receives test execution events

Allow a tooling API client to be notified as tests are executed

## Support interactive builds from the command-line

Provide a mechanism that build logic can use to prompt the user, when running from the command-line.

## Support interactive builds from the IDE

Extend the above mechanism to support prompting the user, when running via the tooling API.

## Cancelled build is gracefully stopped

Improve cancellation so that the build is given an opportunity to finish up cleanly.

# More candidates

Some more features to mix into the above plan:

- Richer events during execution:
    - Task execution
    - Custom events
- Richer build results:
    - Test results
    - Custom results
    - Compilation and other verification failures
    - Task failures
    - Build configuration failures
- Expose unresolved dependencies.
- Expose dependency graph.
- Provide some way to search repositories, to offer content assistance with dependency notations.
- Don't configure the projects when `GradleBuild` model is requested.
- Configure projects as required when using configure-on-demand.
- Don't configure tasks when they are not requested.
- Deal with non-standard wrapper meta-data location.
- More accurate content assistance.
- User provides input to build execution.
- User edits dependencies via some UI.
- User upgrades Gradle version via some UI.
- User creates a new build via some UI.
- Provide some way to define additional JVM args and system properties (possibly via command-line args)
- Provide some way to locate Gradle builds, eg to allow the user to select which build to import.
