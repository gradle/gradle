## Story: Tooling API client requests build model for old Gradle version (DONE)

This story adds support for the `GradleBuild` model for older target Gradle versions.

### Implementation

Change the implementations of `ConsumerConnection.run(type, parameters)` so that when asked for a `GradleBuild` model, they instead
request the `GradleProject` model and then convert it to a `DefaultGradleBuild` instance. See `ConnectionVersion4BackedConsumerConnection.doGetModel()`
for an example of this kind of thing.

For the `ModelBuilderBackedConsumerConnection` implementation, if the provider Gradle version supports the `GradleBuild` model (is >= 1.8-rc-1) then
forward to the provider, as it does now.

To implement this cleanly, one option might be to introduce some chain of model producers into the `ConsumerConnection` subclasses, so that each producer is
asked in turn whether it can produce the requested model. The last producer can delegate to the provider connection. Stop at the first producer that can
produce the model.

### Test cases

- For all Gradle versions, can request the `GradleBuild` model via `ProjectConnection`. This basically means removing the `@TargetGradleVersion` from
  the test case in `GradleBuildModelCrossVersionSpec`.

## Story: Deprecate support for Tooling API clients earlier than Gradle 1.2 (DONE)

When any of the following methods are called on the provider connection treat the client version as deprecated:

- `ConnectionVersion4.getModel()` and `executeBuild()`.
- `InternalConnection.getTheModel()`.
- `configureLogging(boolean)`.

Whenever an operation is invoked on the provider connection by a deprecated client version, the connection implementation should report to
the user that the client version is deprecated and support for it will be removed in Gradle 2.0.
The logging output should be received through the stream attached to `LongRunningOperation.setStandardOutput()`.

### Test cases

- Running a build generates a warning when using a client < 1.2, and does not generate a warning when using a client >= 1.2.
- Fetching a model generates a warning when using a client < 1.2, and does not generate a warning when using a client >= 1.2.

## Story: Deprecate support for Gradle versions earlier than Gradle 1.0-milestone-8 (DONE)

When the provider connection does not implement `InternalConnection` then treat the provider version as deprecated.

Whenever an operation is invoked on a deprecated provider version, the client implementation should report to the user that the provider
version is deprecated and support for it will be removed in Gradle 2.0.
The logging output should be received through the stream attached to `LongRunningOperation.setStandardOutput()`.

### Test cases

- Running a build generates a warning when using a provider version < 1.0-milestone-8, and does not generate a warning when using a provider version >= 1.0-milestone-8.
- Fetching a model generates a warning when using a provider version < 1.0-milestone-8, and does not generate a warning when using a provider version >= 1.0-milestone-8.

## Story: Expose the publications of a project (DONE)

This story allows an IDE to map dependencies between different Gradle builds and between Gradle and non-Gradle builds.
For incoming dependencies, the Gradle coordinates of a given library are already exposed through `ExternalDependency`. This
story exposes the outgoing publications of a Gradle project.

1. Add a `GradlePublication` type with the following properties:
    1. An `id` property with type `GradleModuleVersion`.
2. Add a `publications` property to `GradleProject` with type `DomainObjectSet<GradlePublication>`.
3. Include an `@since` javadoc tag and an `@Incubating` annotation on the new types and methods.
4. Introduce a project-scoped internal service which provides some detail about the publications of a project.
   This service will also be used during dependency resolution. See [dependency-management.md](dependency-management.md#story-dependency-resolution-result-exposes-local-component-instances-that-are-not-module-versions)
    1. The `publishing` plugin registers each publication defined in the `publishing.publications` container.
       For an instance of type `IvyPublicationInternal`, use the publication's `identity` property to determine the publication identifier to use.
       For an instance of type `MavenPublicationInternal`, use the publication's `mavenProjectIdentity` property.
    2. For each `MavenResolver` defined for the `uploadArchives` task, register a publication. Use the resolver's `pom` property to determine the
       publication identifier to use. Will need to deal with duplicate values.
    3. When the `uploadArchives` task has any other type of repository defined, then register a publication that uses the `uploadArchives.configuration.module`
       property to determine the publication identifier to use.
5. Change `GradleProjectBuilder` to use this service to populate the tooling model.

An example usage:

    GradleProject project = connection.getModel(GradleProject.class);
    for (GradlePublication publication: project.getPublications()) {
        System.out.println("project " + project.getPath() + " produces " + publication.getId());
    }

### Test coverage

- Add a new `ToolingApiSpecification` integration test class that covers:
    - For a project that does not configure `uploadArchives` or use the publishing plugins, verify that the tooling model does not include any publications.
    - A project that uses the `ivy-publish` plugin and defines a single Ivy publication.
    - A project that uses the `maven-publish` plugin and defines a single Maven publication.
    - A project that uses the `maven` plugin and defines a single remote `mavenDeployer` repository on the `uploadArchives` task.
    - A project that defines a single Ivy repository on the `uploadArchives` task.
- Verify that a decent error message is received when using a Gradle version that does not expose the publications.

## Story: Expose the build script of a project (DONE)

This story exposes via the tooling API some basic information about the build script of a project.

1. Add a `GradleScript` type with the following properties:
    1. A `file` property with type `File`.
2. Add a `buildScript` property to `GradleProject` with type `GradleScript`.
3. Include an `@since` javadoc tag and an `@Incubating` annotation on the new types and methods.
4. Change `GradleProjectBuilder` to populate the model.

An example usage:

    GradleProject project = connection.getModel(GradleProject.class);
    System.out.println("project " + project.getPath() + " uses script " + project.getBuildScript().getFile());

### Test coverage

- Add a new `ToolingApiSpecification` integration test class that covers:
    - A project with standard build script location
    - A project with customized build script location
- Verify that a decent error message is received when using a Gradle version that does not expose the build scripts.
    - Request `GradleProject` directly.
    - Using `GradleProject` via an `EclipseProject` or `IdeaModule`.

## Story: GRADLE-2434 - Expose the aggregate tasks for a project (DONE)

This story allows an IDE to implement a way to select the tasks to execute based on their name, similar to the Gradle command-line.

1. Add an `EntryPoint` model interface, which represents some arbitrary entry point to the build.
2. Add a `TaskSelector` model interface, which represents an entry point that uses a task name to select the tasks to execute.
3. Change `GradleTask` to extend `EntryPoint`, so that each task can be used as an entry point.
4. Add a method to `GradleProject` to expose the task selectors for the project.
    - For new target Gradle versions, delegate to the provider.
    - For older target Gradle versions, use a client-side mix-in that assembles the task selectors using the information available in `GradleProject`.
5. Add methods to `BuildLauncher` to allow a sequence of entry points to be used to specify what the build should execute.
6. Add `@since` and `@Incubating` to the new types and methods.

Here are the above types:

    interface EntryPoint {
    }

    interface TaskSelector extends EntryPoint {
        String getName(); // A display name
    }

    interface GradleTask extends EntryPoint {
        ...
    }

    interface GradleProject {
        DomainObjectSet<? extends TaskSelector> getTaskSelectors();
        ...
    }

    interface BuildLauncher {
        BuildLauncher forTasks(Iterable<? extends EntryPoint> tasks);
        BuildLauncher forTasks(EntryPoint... tasks);
        ...
    }

TBD - maybe don't change `forTasks()` but instead add an `execute(Iterable<? extends EntryPoint> tasks)` method.

### Test cases

- Can request the entry points for a given project hierarchy
    - Task is present in some subprojects but not the target project
    - Task is present in target project but no subprojects
    - Task is present in target project and some subprojects
- Executing a task selector when task is also present in subprojects runs all the matching tasks, for the above cases.
- Can execute a task selector from a child project. Verify the tasks from the child project are executed.
- Executing a task (as an `EntryPoint`) when task is also present in subprojects run the specified task only and nothing from subprojects.
- Can request the entry points for all target Gradle versions.

## Story: Tooling API exposes project's implicit tasks as launchable (DONE)

Change the building of the `BuildInvocations` model so that:

- `getTasks()` includes the implicit tasks of the project.
- `getTaskSelectors()` includes the implicit tasks of the project and all its subprojects.

### Test cases

- `BuildInvocations.getTasks()` includes `help` and other implicit tasks.
    - Launching a build using one of these task instances runs the appropriate task.
- `BuildInvocations.getTaskSelectors()` includes the `help` and other implicit tasks.
    - Launching a build using the `dependencies` selector runs the task in the default project only (this is the behaviour on the command-line).
- A project defines a task placeholder. This should be visible in the `BuildInvocations` model for the project and for the parent of the project.
    - Launching a build using the selector runs the task.

## Story: Expose information about the visibility of a task (DONE)

This story allows the IDE to hide those tasks that are part of the implementation details of a build.

- Add a `visibility` property to `Launchable`.
- A task is considered `public` when it has a non-empty `group` property, otherwise it is considered `private`.
- A task selector is considered `public` when any task it selects is `public`, otherwise it is considered `private`.

## Story: Expose the project root directory

Add a `projectDir` property to `GradleProject`

### Test coverage

- Verify that a decent error message is received when using a Gradle version that does not expose the project directory

## Story: Tooling API client launches a build using task selectors from different projects (DONE)

TBD

### Test cases

- Can execute task selectors from multiple projects, for all target Gradle versions
- Can execute overlapping task selectors.

### Test cases

- A project defines a public and private task.
    - The `BuildInvocations` model for the project includes task instances with the correct visibility.
    - The `BuildInvocations` model for the project includes task selectors with the correct visibility.
    - The `BuildInvocations` model for the parent project includes task selectors with the correct visibility.
