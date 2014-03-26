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

This story allows an IDE to map dependencies between different Gradle builds and and between Gradle and non-Gradle builds.
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
