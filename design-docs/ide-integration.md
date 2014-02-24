
This spec defines a number of features to improve the developer IDE experience with Gradle. It covers changes in Gradle to improve those features
that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins
(i.e. the plugins that run inside Gradle to provide the IDE models).

This spec does not cover more general features such as improving dependency resolution or configuration performance.

# Implementation plan

Below is a list of stories, in approximate priority order:

## Feature - Tooling API parity with command-line for task visualisation and execution

This feature exposes via the tooling API some task execution and reporting features that are currently available on the command-line.

- Task selection by name, where I can run `gradle test` and this will find and execute all tasks with name `test` in the current project and its subprojects.
  However, this selection logic does have some special cases. For example, when I run `gradle help` or `gradle tasks`, then the task from the current project
  only is executed, and no subproject tasks are executed.
- Task reporting, where I can run `gradle tasks` and this will show me the things I can run from the current project. This report, by default, shows only the public
  interface tasks and hides the implementation tasks.

The first part of this feature involves extracting the logic for task selection and task reporting into some reusable service, that is used for command-line invocation
and exposed through the tooling API. This way, both tools and the command-line use the same consistent logic.

The second part of this feature involves some improvements to the task reporting, to simplify it, improve performance and to integrate with the component
model introduced by the new language plugins:

- Task reporting treats as public any task with a non-empty `group` attribute, or any task that is declared as a public task of a build element.
- All other tasks are treated as private.
- This is a breaking change. Previously, the reporting logic used to analyse the task dependency graph and treated any task which was not a dependency of some other task
  as a public task. This is very slow, requires every task in every project to be configured, and is not particularly accurate.

#### Open issues

- Split `gradle tasks` into 'what can I do with this build?' and a 'what are all the tasks in this project?'.

### GRADLE-2434 - IDE visualises and runs task selectors

On the command-line I can run `gradle test` and this will find and execute all tasks with name `test` in the current project
and all its subprojects.

Expose some information to allow the IDE to visualise this and execute builds in a similar way.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-gradle-2434---expose-the-aggregate-tasks-for-a-project)

### IDE hides implementation tasks

On the command-line I can run `gradle tasks` and see the public tasks for the build, and `gradle tasks --all` to see all the tasks.

Expose some information to allow the IDE to visualise this.

See [tooling-api-improvements.md](tooling-api-improvements.md#expose-information-about-the-visibility-of-a-task)

### Simplify task visibility logic

Change the task visibility logic introduced in the previous stories so that:

- A task is public if its `group` attribute is not empty.
- A task selector is public if any of the tasks that it selects are public.

### Test cases

- Tooling API and `gradle tasks` treats as public a task with a non-empty `group` attribute.
- Tooling API and `gradle tasks` treats as public a selector that selects a public task.
- Tooling API and `gradle tasks` treats as private a task with null `group` attribute.
- Tooling API and `gradle tasks` treats as private a selector that selects only private tasks.

### Expose the lifecycle tasks of build elements as public tasks

Change the task visibility logic so that the lifecycle task of all `BuildableModelElement` objects are public, regardless of their group attribute.

### Test cases

- Tooling API and `gradle tasks` treats as public the lifecycle task of a `BuildableModelElement` with a null `group` attribute.

#### Open issues

- Should other tasks for a `BuildableModelElement` be private, regardless of group attribute?

### GRADLE-2017 - Correctly map libraries to IDEA dependency scope

TBD

#### Test cases

- When using the tooling API to fetch the IDEA model or the `idea` task to generate the IDEA project files, verify that:
    - When a java project has a dependency declared for `testCompile` and `runtime`, the dependency appears with `test` and `runtime` scopes only.
    - When a java project has a dependency declared for `testRuntime` and `runtime`, the dependency appears with `runtime` scope only.
    - When a java project has a dependency declared for `compile` and `testCompile`, the dependency appears with `compile` scope only.
    - When a war project has a dependency declared for `providedCompile` and `compile`, the dependency appears with `provided` scope only.

## Feature - Tooling API client cancels an operation

Add some way for a tooling API client to request that an operation be cancelled.

The implementation will do the same thing as if the daemon client is disconnected, which is to drop the daemon process.
Later stories incrementally add more graceful cancellation handling.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-tooling-api-client-cancels-a-long-running-operation)

## Feature - Expose build script compilation details

See [tooling-api-improvements.md](tooling-api-improvements.md#story-expose-the-compile-details-of-a-build-script):

Expose some details to allow some basic content assistance for build scripts:

- Expose the classpath each build script.
- Expose the default imports for each build script.
- Expose the Groovy version for each build script.

## Story - Tooling API stability tests

Introduce some stress and stability tests for the tooling API and daemon to the performance test suite. Does not include
fixing any leaks or stability problems exposed by these tests. Additional stories will be added to take care of such issues.

## Feature - Daemon usability improvements

### Story - Build script classpath can contain a changing jar

Fix ClassLoader caching to detect when a build script classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.

### Story - Can clean after compiling on Windows

Fix GRADLE-2275.

### Story - Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly (say, as soon as they become idle).

## Feature - Expose project components to the IDE

### Story - Expose Java components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Java language level and other details about a Java component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Groovy components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Groovy language level and other details about a Groovy component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Scala components to the IDE

Expose Scala language level and other details about a Scala component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose the publications of a project

See [tooling-api-improvements.md](tooling-api-improvements.md#story-expose-the-publications-of-a-project):

Expose the publications of a project so that the IDE can wire together Gradle and Maven builds.

### Story - Expose Web components to the IDE

Expose Web content, servlet API version, web.xml descriptor, runtime and container classpaths, and other details about a web application.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose J2EE components to the IDE

Expose Ear content, J2EE API versions, deployment descriptor and other details about a J2EE application.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose artifacts to IDEA

Expose details to allow IDEA to build various artifacts: http://www.jetbrains.com/idea/webhelp/configuring-artifacts.html

## Feature - Daemon usability improvements

### Story - Daemon handles additional immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, only `file.encoding` is treated as an immutable system property.

Add support for the following properties:

- The jmxremote system properties (GRADLE-2629)
- The SSL system properties (GRADLE-2367)

### Story - Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

### Story - Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

### Story - Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

## Feature - Tooling API client listens for changes to a tooling model

Provide a subscription mechanism to allow a tooling API client to listen for changes to the model it is interested in.

## Feature - Tooling API client receives test execution events

Allow a tooling API client to be notified as tests are executed

## Feature - Interactive builds

### Story - Support interactive builds from the command-line

Provide a mechanism that build logic can use to prompt the user, when running from the command-line.

### Story - Support interactive builds from the IDE

Extend the above mechanism to support prompting the user, when running via the tooling API.

# More candidates

Some more features to mix into the above plan:

- Honour same environment variables as command-line `gradle` invocation.
- Cancelled build is gracefully stopped
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
- Expose component graph.
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
