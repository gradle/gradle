This specification defines a number of improvements to the tooling API.

Stories relating specifically to usability from the IDE should go in the `ide-integration.md` spec.

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

## Built-in Gradle plugins should be less privileged

Currently, the built-in Gradle plugins are privileged in several ways, such that only built-in plugins can use
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

Over time, we want to make the built-in plugins less privileged so that the difference between a 'built-in' plugin and
a 'custom' is gradually reduced. Allowing custom plugins to contribute tooling models and changing the build-in plugins
to use this same mechanism is one step in this direction.

# Implementation plan

## Bugfix: allow concurrent usage of different gradle distributions of the same version

When using the tooling api to work with different gradle distributions of the same version (e.g a gradle "-bin" and a "-all" distribution)
an OverlappingFileException can be thrown in the current implementation.
This is caused by loading the same version of the Gradle provider loaded up in multiple ClassLoaders (from each of the different distributions for the 2 different builds).
The provider loading must be changed to deal with this.

### implementation
- Instead of using the version string as cache key in `CachingToolingImplementation` use a hash of the files of the provider's classpath as a key to the cache.
- pass the distribution(by file reference) from the consumer across to the provider
- for provider versions is >= this implementation can use cached provider, otherwise use new one.

### integration test coverage

- Can load and use multiple distributions of the same gradle version for doing running multiple build requests via tooling api.
    - using the new tooling-api tested against multiple (older) gradle versions

### Open issues
- cannot handle init scripts in distributions init.d folder if as distribution of cached provider is used for resolving init scripts

## Story - Tooling API stability tests

Introduce some stress and stability tests for the tooling API and daemon to the performance test suite. Does not include
fixing any leaks or stability problems exposed by these tests. Additional stories will be added to take care of such issues.

# Feature: Fetching models

## Story: Tooling API client builds a complex tooling model in a single batch operation (DONE)

This story adds support for a tooling API to query portions of the Gradle model in an efficient way.

Introduce the concept of a `BuildAction`. A build action is provided by the tooling API client and is serialized across to the process where
the build is running. It can then query the models as required and return a result. This result is serialized back to the tooling API client.

This approach allows the action to make decisions about which models to request and to request multiple models efficiently within a single
build invocation. It can also later provide the action with access to features which cannot be serialized to the tooling API client.

    public interface BuildAction<T> extends Serializable {
        T execute(BuildController controller);
    }

    public interface BuildController {
        <T> T getModel(Class<T> modelType);
        // Later stories will add more here
    }

    public interface BuildActionExecutor<T> extends LongRunningOperation {
        T get();
        void get(ResultHandler<? super T> handler);
    }

    <T> ConsumerActionExecutor<T> execute(ConsumerAction<T> action);

The `BuildController` interface provides a single method `getModel()` which returns the tooling model of the given type for the default project
associated with the `ProjectConnection`. This method will return the same result as `ProjectConnect.getModel()` would.

For this story, the build action is executed before the build has been configured. If any model is requested, the build is fully configured. Later stories
will introduce a more efficient implementation, where the work done by the build is driven on demand by what the action asks for, and some caching of
models is used.

### Test cases

- Action builds a result and this result is received by the client.
    - Returns a custom result type
    - Returns null
    - Returns a built-in tooling model (such as `IdeaProject`)
    - Returns a custom result type that references a built-in tooling model
    - Returns a custom result type that references multiple tooling models
    - Returns a custom result type that references a custom tooling model whose implementation uses classes that conflict with the client
    - Client changes action implementation to return a different result
- Verify environment that the action executes in:
    - Logging is received by the client as part of the build output.
    - Context ClassLoader is set appropriately.
    - No Gradle-version specific classes are visible to the action.
- Client receives a reasonable error message when:
    - Target Gradle version does not support consumer actions.
    - The action throws an exception.
    - A failure occurs configuring the build.
    - A failure occurs building a requested model.
    - The action cannot be serialized or deserialized.
    - The action result cannot be serialized or deserialized.
    - The action throws an exception that cannot be serialized or deserialized.
    - The Gradle process is killed before the action completes.
    - The action requests an unknown model.
    - The action is compiled for Java 6 but the build runs with Java 5.

## Story: Tooling API build action requests a tooling model for a Gradle project

1. Add a new `GradleBuild` model which contains information about which projects are included in the build.
2. Add a new `BasicGradleProject` type to provide basic structural information about a project.
3. Extend `BuildController` to add methods to query a model for a given project.
4. Change `ProjectConnection.getModel(type)` and `BuildController.getModel(type)` to return only build-level models.
5. Change `BuildController.getModel(project, type)` to return only project-level models.

Here are the above types:

    interface BasicGradleProject { }

    interface GradleBuild {
        BasicGradleProject getRootProject();
        Set<? extends BasicGradleProject> getProjects();
    }

    interface BuildController {
        <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;
    }

Note: there is a breaking change here.

### Test cases

- Can request the `GradleBuild` model via `ProjectConnection`.
- Can request the `GradleBuild` model via a build action.
- Can request a model for a given project:
    - A `BasicGradleProject` for the specified project, not the root.
    - A `GradleProject` for the specified project, not the root.
    - An `IdeaModule` for the specified project, not the root.
    - An `EclipseProject` for the specified project, not the root.
- Cannot request a build model for a project:
    - Cannot request a `BuildModel`.
    - Cannot request a `BuildEnvironment`.
- Client receives decent feedback when
    - Requests a model from an unknown project.
    - Requests an unknown model.

## Story: Gradle provider builds build model efficiently

When the `GradleBuild` model is requested, execute only the settings script, and don't configure any of the projects.

## Story: Tooling API client determines whether model is available (DONE)

This story adds support for conditionally requesting a model, if it is available

    interface BuildController {
        <T> T findModel(Class<T> type); // returns null when model not present
        <T> T findModel(Model target, Class<T> type); // returns null when model not present
    }

### Test cases

- Client receives null for unknown model, for all target Gradle versions.
- Build action receives null for unknown model, for all target Gradle versions >= 1.8

## Story: Tooling API client changes implementation of a build action

Fix the `ClassLoader` caching in the tooling API so that it can deal with changing implementations.

## Story: Allow options to be specified for tasks

For example, allow something similar to `gradle test --tests SomePattern`

## Story: Tooling API build action requests a tooling model for a Gradle build

This story adds support to build models that have a scope of a whole Gradle build (not just a project)

1. Add a new `GradleBuildToolingModelBuilder` similar to `ToolingModelBuilder`. Possibly an abstract class since this is an SPI.
2. Extend `ToolingModelBuilderRegistry` to allow registration of such a builder.
3. Change the way how models are queried from `ProjectConnection` to use this new builders (there is no project context passed).
   The only special case is the EclipseModel, which is actually built from the default project instead of the root project, so we'd need a specific implementation for that.
4. Extend `BuildController.getModel()` to support `GradleBuild` as model parameter or add `BuildController.getBuildModel(Class)`.
   Those would be using gradle model builder rather than project model builders

### Test cases

- Can register new model builder and
  - query it from client via `ProjectConnection`.
  - query it from client via build action.
- Can request a model via build action:
  - And get result from `GradleBuildToolingModelBuilder` for gradle build scope if passing `GradleBuild` as target.
  - And get result from `ToolingModelBuilder` for project scope if passing one of parameters describing project.
- Client receives decent feedback when requests an unknown model.

### Open issues

- A model builder should simply be a rule with some tooling model as output, and whatever it needs declared as inputs.

## Story: Add a convenience dependency for obtaining the tooling API JARs

Similar to `gradleApi()`

## Story: Run tasks before executing a BuildAction

This story adds support to fetch complex models that are not known upfront, but depend on the result of some tasks.

Some complex models generation might involve analyzing project's source tree, upstream dependencies, etc.
Thus it's necessary to make the Tooling API allow users to pass a list of tasks to be executed before the BuildAction
is executed (in the same connection to improve performance).

1. Modify the `BuildActionExecuter` interface to allow passing a list of tasks to be run:

```java
public interface BuildActionExecuter<T> extends ConfigurableLauncher<BuildActionExecuter<T>> {
    BuildActionExecuter<T> forTasks(String... tasks);
    BuildActionExecuter<T> forTasks(Iterable<String> tasks);
}
```

2. Implement the ability of running tasks in `ClientProvidedBuildActionRunner` based on `BuildModelActionRunner` implementation.

### Test cases

- `ProjectConnection.action(myAction).forTasks(myTasks)` is called.
  - Project is configured, tasks graph is calculated and tasks are run before `BuildAction.execute` is called.
- `forTasks` is called more than once.
  - Tasks defined in the last call override the previous
- Failure occurs while executing tasks.
  - Exception is thrown and the `BuildAction` is not executed

# Backlog

* Replace `LongRunningOperation.standardOutput` and `standardError` with overloads that take a `Writer`, and (later) deprecate the `OutputStream` variants.
* Change the tooling API protocol to allow the provider to inform the consumer that it is deprecated and/or no longer supported, and fix the exception
  handling in the consumer to deal with this.
* Test fixtures should stop daemons at end of test when custom user home dir is used.
* Introduce a `GradleExecutor` implementation backed by the tooling API and remove the in-process executor.

## Feature - Tooling API client listens for changes to a tooling model

Provide a subscription mechanism to allow a tooling API client to listen for changes to the model it is interested in.

## Feature - Interactive builds

### Story - Support interactive builds from the command-line

Provide a mechanism that build logic can use to prompt the user, when running from the command-line.

### Story - Support interactive builds from the IDE

Extend the above mechanism to support prompting the user, when running via the tooling API.
