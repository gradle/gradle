This spec defines a number of stories to improve IDE integration in Gradle

# Candidates

Below is a list of candidates to work on, in approximate priority order:

## GRADLE-2434 - IDE visualises and runs aggregate tasks

On the command-line I can run `gradle test` and this will find and execute all tasks with name `test` in the current project
and all its subprojects.

Expose some information to allow the IDE to visualise this and execute builds given a task name.

## IDE hides implementation tasks

On the command-line I can run `gradle tasks` and see the 'main' tasks for the build, and `gradle tasks --all` to see all the tasks.

Expose some information to allow the IDE to visual this.

## Tooling API client cancels an operation

Add some way for a tooling API client to request that an operation be cancelled.

The implementation will do the same thing as if the daemon client is disconnected, which is to drop the daemon process.
Later stories incrementally add more graceful cancellation handling.

## Expose build script compilation details

As per [tooling-api-improvements.md](tooling-api-improvements.md):

Expose some details to allow some basic content assistance for build scripts:

- Expose the classpath each build script.
- Expose the default imports for each build script.
- Expose the Groovy version for each build script.

## Tooling API stability tests

Introduce some stress and stability tests for the tooling API and daemon to the performance test suite. Does not include
fixing any leaks or stability problems exposed by these tests. Additional stories will be added to take care of such issues.

## Buildscript classpath can contain a changing jar

Fix ClassLoader caching to detect when a buildscript classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.

## Can clean after compiling on Windows

Fix GRADLE-2275.

## Expose the publications of a project

As per [tooling-api-improvements.md](tooling-api-improvements.md):

Expose the publications of a project so that the IDE can wire together Gradle and Maven builds.

## Expose Java components to the IDE

As per [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Java language level and other details about a Java component.

## Expose Groovy components to the IDE

As per [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Groovy language level and other details about a Groovy component.

## Expose Scala components to the IDE

Expose Scala language level and other details about a Scala component.

## Expose Web components to the IDE

Expose Web content, servlet API version and other details about a web application.

## Handle more immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, `file.encoding` is supported.

Add support for the following properites:

- The jmxremote system properties (GRADLE-2629)
- The SSL system properties (GRADLE-2367)

## Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the surviour and the others expire quickly (say, as soon as they become idle).

## Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

## Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

## Reduce the default daemon maximum heap and permgen sizes

Needs to be done in a backwards compatible way.

## Support interactive builds from the command-line

Provide a mechanism that build logic can use to prompt the user, when running from the command-line.

## Support interactive builds from the IDE

Extend the above mechanism to support prompting the user, when running via the tooling API.

## Cancelled build is gracefully stopped

Improve cancellation so that the build is given an opportunity to finish up cleanly.

# More candidates

Some more features to mix into the above plan:

- Expose unresolved dependencies.
- Expose dependency graph.
- Provide notifications when model has changed.
- Provide some way to search repositories, to offer content assistance with dependency notations.
- Don't configure the projects when `GradleBuild` model is requested.
- Configure projects as required when using configure-on-demand.
- Don't configure the tasks when they are not requested.
- Deal with non-standard wrapper meta-data location.
- More accurate content assistance.
- Allow editing of dependencies via some UI.
- Allow Gradle upgrades via some UI.
