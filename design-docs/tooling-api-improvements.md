
This specification defines a number of improvements to the tooling API.

# Use cases

## Tooling can be developed for Gradle plugins

Many plugins have a model of some kind that declares something about the project. In order to build tooling for these
plugins, such as IDE integration, it is necessary to make these models available outside the build process.

This must be done in a way such that the tooling and the build logic can be versioned separately, and that a given
version of the tooling can work with more than one version of the build logic, and vice versa.

## Replace or override the built-in Gradle IDE plugins

The Gradle IDE plugins currently implement a certain mapping from a Gradle model to a particular IDE model. It should be
possible for a plugin to completely replace the built-in plugins and assemble their own mapping to the IDE model.

Note that this is not the same thing as making the IDE plugins more flexible (although there is a good case to be made there
too). It is about allowing a completely different implementation of the Java domain - such as the Android plugins - to provide
their own opinion of how the project should be represented in the IDE.

## Built-in Gradle plugins should be less priviledged

Currently, the built-in Gradle plugins are priviledged in several ways, such that only built-in plugins can use
certain features. One such feature is exposing their models to tooling. The IDE and build comparison models
are baked into the tooling API. This means that these plugins, and only these plugins, can contribute models to tooling.

This hard-coding has a number of downsides beyond the obvious lack of flexibility:

* Causes awkward cycles in the Gradle source tree.
* Responsibilities live in the wrong projects, so that, for example, the IDE project knows how to assemble the
  build comparison project. This means that the IDE plugin must be available for the tooling API provider to work.
* Cannot replace the implementation of a model, as discussed above.
* A tooling model cannot be 'private' to a plugin. For example, the build comparison model is really a private cross-version
  model that allows different versions of the build comparison plugin to communicate with each other.
* A tooling model must be distributed as part of the core Gradle runtime. This makes it difficult to bust up the
  Gradle distribution.

Over time, we want to make the built-in plugins less priviledged so that the difference between a 'built-in' plugin and
a 'custom' is gradually reduced. Allowing custom plugins to contribute tooling models and changing the build-in plugins
to use this same mechanism is one step in this direction.

# Implementation plan

## Story: Expose the publications of a project

1. Add a `getPublications()` method to `GradleProject` that should return a `DomainObjectSet<GradleModuleVersion>`. Include an `@since` javadoc tag and
   an `@Incubating` annotation on this method.
2. Change `GradleProjectBuilder` to:
    1. When the `PublishingPlugin` is applied to the project, then add a value for each publication defined in the `publishing.publications`
       container. For an instance of type `IvyPublicationInternal`, use the publication's `identity` property to determine the values to use.
       For an instance of type `MavenPublicationInternal`, use the publication's `mavenProjectIdentity` property.
    2. Add a value for each `MavenResolver` defined for the `uploadArchives` task. Use the resolver's `pom` property to determine the values to use.
       Will need to remove duplicate values.
    3. When the `uploadArchives` task has any other type of repository defined, then use the `uploadArchives.configuration.module` property
       to determine the values to use.

### Test coverage.

- Add a new `ToolingApiSpecification` integration test class that covers:
    - For a project that does not configure `uploadArchives` or use the publishing plugins, verify that the tooling model does not include any publications.
    - A project that uses the `ivy-publish` plugin and defines a single Ivy publication.
    - A project that uses the `maven-publish` plugin and defines a single Maven publication.
    - A project that uses the `maven` plugin and defines a single remote `mavenDeployer` repository on the `uploadArchives` task.
    - A project that defines a single Ivy repository on the `uploadArchives` task.

## Story: Built-in Gradle plugin can register a tooling model

This story adds a public mechanism that allows a plugin to register a tooling model to make available to tooling API
clients. This will replace the hardcoded registry in the IDE project.

However, this story does not address classloading issues in the client process, so only models from built-in plugins
will actually be usable from the tooling API. The following stories address this issue.

## Story: Tooling model implementations live with their plugin implementations

Currently, the tooling model implementations must live in the tooling API project, and are distributed as part of the
core Gradle runtime. This story adds a mechanism to allow the tooling model implementations to live with the plugins
that contribute the model, and the tooling API mechanism can dynamically provision the appropriate classes at runtime.

## Story: Custom Gradle plugin can register a tooling model

This story builds on the previous stories to allow a custom plugin to expose a tooling model to any tooling API client
that shares compatible model classes.

## Story: Tooling API client can query the available tooling models

## Story: Add a convenience dependency for obtaining the tooling API JARs

Similar to `gradleApi()`

## Story: Add ability to launch tests in debug mode

Need to allow a debug port to be specified, as hard-coded port 5005 can conflict with IDEA.

# Open issues

* Discovery or registration?
* Per-build or per-project?
