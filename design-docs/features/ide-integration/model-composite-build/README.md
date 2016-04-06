# Developer uses projects from multiple Gradle builds from IDE

This feature allows a developer to work in a single IDE session on multiple projects that would normally be independent.

A typical workflow for a developer that has to work on 2 independent projects would be to make a change to project A, publish its artifact and build project B with the
changed dependency. For developers this workflow is cumbersome and time-consuming. This feature allows a developer to work on multiple projects in a single IDE session
that would normally be independent.

## 'Gradle Composite'

The defined stories introduce the concept of a ‘Gradle composite build’ to the tooling API. This is simply a collection of Gradle projects that the IDE user is working on.
These projects may come from different Gradle builds.

A tooling API client will be able to define a composite and query it in a similar way to how a `ProjectConnection` can be queried. While the projects contained in a composite
may come from separate Gradle builds, where possible the composite will present as if it were a single, unified Gradle build containing all of the projects for each participating
Gradle build.

This will provide the developer with a view of all the projects in the composite, so that the developer can search for usages or make changes to any of these projects. When the
 developer compiles or runs tests from withing the IDE, these changes will be picked up for any dependent project. However, IDE actions that delegate to Gradle (such as task execution) will not operate on a composite build, as these actions will not (yet) be composite-aware.

In scope are changes to Buildship to define and use a composite build. Out of scope are changes to IDEA.

## Project substitution

Where possible, binary dependencies will be replaced with source dependencies between IDE modules.

So, for example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on
library B, consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same composite, however, application A would have
a source dependency on library B.

## Stories

### Story - Tooling Commons provides model result for a composite containing a single or multiple Gradle builds

Introduce `ModelResult` to the Tooling Commons. This represents a collection of eclipse projects based on the Gradle builds that the IDE user is working on.
For this story, all Gradle projects for an `ModelResult` will be sourced from a single or  multiple Gradle builds. As such, this story merely provides a convenience for
obtaining a flattened collection of `EclipseProject` instances.

On completion of this story, it will be possible to convert Buildship to use this new API for project import and refresh. Converting Buildship is the subject of the next story.

##### API

    /**
     * Indicates that a Gradle distribution can be used for executing the composite operation.
     */
    public interface GradleDistributionAware {
        void useInstallation(File gradleHome);
        void useGradleVersion(String gradleVersion);
        void useDistribution(URI location);
    }

    /**
     * Represents a participating build for a composite.
     */
    public interface CompositeParticipant extends GradleDistributionAware {
        File getRootProjectDirectory();
    }

    /**
     * The main entry point for create a composite build.
     */
    public abstract class CompositeBuildConnector {
         public static CompositeBuildConnector newComposite() { ... }
         public abstract CompositeParticipant addParticipant(File rootProjectDirectory) { ... }
         public abstract CompositeBuildConnection connect() throws GradleConnectionException { ... }
    }

    /**
     * Provides the response for a model request.
     */
    public interface ModelResult<T> {
        T getModel();
    }

    /**
     * Represents a long-lived connection to a composite of Gradle project(s).
     * For now, the only model type supported is EclipseProject.
     */
    public interface CompositeBuildConnection {
        <T> Set<ModelResult<T>> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException, IllegalArgumentException;
        <T> ModelBuilder<Set<ModelResult<T>>> models(Class<T> modelType) throws GradleConnectionException, IllegalStateException, IllegalArgumentException;
        void close();
    }

##### Usage

    CompositeBuildConnection connection = null;

    try {
        connection = CompositeBuildConnector.newComposite();
        connection.addParticipant("project-1");
        connection.addParticipant("project-2").useGradleVersion("2.8");
        connection.connect();
        Set<ModelResult<EclipseProject>> modelResult = connection.getModels(EclipseProject.class);

        for (ModelResult modelResult : modelResult) {
            EclipseProject eclipseProject = modelResult.getModel();
            System.out.println(eclipseProject.getName());
        }
    } finally {
        if (connection != null) {
            connection.close();
        }
    }

##### Implementation

- The `CompositeBuildConnector` provides a means to define a composite build via the Tooling Commons. Each `CompositeParticipant` added to the composite specifies a Gradle build that
participates in the composite.
    - Adding any `CompositeParticipant` effectively adds the Gradle build that _contains_ the referenced project to the composite.
    - For each `CompositeParticipant` a new `ProjectConnection` is created internally.
    - A `CompositeParticipant` can provide a Gradle distribution for use with the underlying `ProjectConnection`. If no Gradle distribution is provided, the Gradle distribution of the
    build is used.
- The only model type that can be requested for a `CompositeBuildConnection` is `EclipseProject`
    - On request for a `EclipseProject`, the underlying `ProjectConnection` will be queried for the `EclipseProject` model. This model represents the hierarchy of all eclipse projects
    for all participating Gradle builds of a composite.
    - The instance of `ModelResult` will be constructed directly by the `CompositeBuildConnection` instance, by traversing the hierarchy of the `EclipseProject` obtained.
    - The `EclipseProject`s still contain the information about their hierarchy, so Buildship can potentially display them in a hierarchical layout.
- The order in which participants are added doesn't not make any guarantees over the order of returned model results.
- The method on `CompositeBuildConnection` returning a `ModelBuilder` does only allow for providing a cancellation token or a progress listener. Any other method on the `ModelBuilder`
interface is not supported. The returned `ModelBuilder` can provide a `ResultHandler` when retrieving the model.

##### Test cases

- A composite cannot be built without assigning at least one participating project. If the `CompositeBuildConnector.connect()` method is called without assigning at least one participant
an `IllegalStateException` is thrown.
- A composite can add a one or many participating projects via `CompositeBuildConnector.withParticipant()`.
- Requesting a model by calling any of the methods in `CompositeBuildConnection` with a type that's not an interface will throw a `IllegalArgumentException`.
- A model can only be requested for the type `EclipseProject`. Providing any other type throws a `IllegalArgumentException`.
- A composite can be built with a single participating build if the hierarchy of the participating build only contains a single Gradle project. The requested `Set<ModelResult<EclipseProject>>` model
contains a single project of type `EclipseProject`. The `EclipseProject` properly populates the model (e.g. name, path, classpath and project dependencies).
- A composite can be built with a single participating build containing a hierarchy of Gradle projects. The requested `Set<ModelResult<EclipseProject>>` model
contains all projects of the hierarchy (including the root project) with type `EclipseProject`. The `EclipseProject` properly populates the model
(e.g. name, path, classpath and project dependencies).
- If the `ProjectConnection` points to a sub-project of a multi-project build hierarchy, the requested `Set<ModelResult<EclipseProject>>` model determines the root project and traverses the whole hierarchy.
The `Set<ModelResult<EclipseProject>>` contains all `EclipseProject`s of that hierarchy.
- If a composite contains at least two projects with the same name at the time of building it, an `IllegalStateException` is thrown.
- The `ProjectConnection` uses the Gradle distribution passed in from the `CompositeParticipant`.
- Adding a second `CompositeParticipant` instance for the same project directory is a no-op.
- Adding a second `CompositeParticipant` instance for a project within the same Gradle build is a no-op.
- A composite can be built for `ProjectConnection`s that resolve to
    - multiple single project builds
    - multiple multi-project builds
    - a combination of both
- A participant uses the provided Gradle distribution to resolve the model.
- An exception is thrown if any of the `ProjectConnection`s fail to properly resolve the model.
- An exception is thrown if any of the resolved `EclipseProject`s have the same name.
- When building a composite, a cancellation token can be provided. If the build is cancelled with the cancellation token, building the composite fails and `BuildCancelledException`
is thrown.
- When building a composite, a progress listener can be provided. The progress listener captures the relevant events.
- Unsupported methods of the `ModelBuilder` throw an `UnsupportedMethodException`.
- A result handler can be used to capture the result of the operation upon completion or the exception of the failed operation.

##### Open issues

- A composite can only contain a Gradle project. A future story will also need to address building a workspace with homogeneous project types (e.g. Maven, Ant or any other type of
project that does not have access to the Gradle API).

### Story - Buildship queries model result to determine set of Eclipse projects for multiple imported Gradle builds

By switching to use the new model result, Buildship (and tooling-commons) will no longer need to traverse the hierarchy of eclipse projects. This change will enable Buildship to later take advantage of project substitution and name de-duplication for composite builds. When importing a new Gradle build, projects for all previously imported Gradle builds will need to be refreshed. If implemented correctly, the development of project name deduplication and project dependency substitution support in Gradle will automatically be reflected in functionality within Buildship.

##### API

Most of the changes will be made in the tooling-commons layer that Buildship uses.

The `ToolingClient` will be expanded to provide methods for managing and querying a composite.

    public class ToolingClient {
        //existing methods...

        public abstract <T> CompositeModelRequest<T> newCompositeModelRequest(Class<T> modelType);
    }

The `CompositeModelRequest` clients can specify the composite participants in a write-only manner

     public interface CompositeModelRequest {
         CompositeRequest<T> participants(GradleBuildIdentifier... buildIdentifiers);
         CompositeRequest<T> addParticipants(GradleBuildIdentifier... buildIdentifiers);
     }

A new `CompositeModelRepository` will be added in order to query composites from Buildship

    public interface CompositeModelRepository {
        OmniEclipseWorkspace fetchEclipseWorkspace(TransientRequestAttributes requestAttributes, FetchStrategy fetchStrategy);
    }

    public class ModelRepositoryProvider {
        //existing methods...

        CompositeModelRepository getCompositeModelRepository(FixedRequestAttributes... projects);
    }

    public interface OmniEclipseWorkspace {
        Set<OmniEclipseProject> getOpenEclipseProjects();
    }

##### Implementation

 The current approach of synchronizing individual projects will be replaced with one `SynchronizeWorkspaceJob`. This job will make use of the new `CompositeModelRepository` to query for the `EclipseWorkspace` model. If the set of projects contained in the workspace change, the `ToolingClient` will rebuild the `GradleComposite` from scratch.

 The project synchronization methods will be changed to take an additional `EclipseWorkspace` argument. This especially affects the `ClasspathUpdater`, because to support the upcoming substitution story, it now needs to search for project dependencies across the whole workspace.

##### Test cases

tooling-commons:
- executing a `CompositeModelRequest` with no `GradleBuildIdentifier` throws an `IllegalArgumentException`
- a `CompositeModelRequest` can only request a `EclipseProject`s, requesting any other Model throws an `UnsupportedModelException`
- the `OmniEclipseWorkspace` for a single project build contains exactly one `OmniEclipseProject`
- the `OmniEclipseWorkspace` for a multi-project build contains all the `EclipseProject`s contained in that project
- the `OmniEclipseWorkspace` for multiple multi-project builds contains the union of all the `OmniEclipseProject`s contained in those projects.
- an exception is thrown if any of the `OmniEclipseProject`s cannot be obtained

Buildship:
- adding two projects to the workspace -> both are part of the composite afterwards
- removing a project from the workspace -> no longer contained in the composite
- refreshing/adding/removing a project -> all other projects are refreshed as well
- all other Buildship tests must still pass, as this story should not change the user facing behavior
- the model for one project cannot be built -> no project is synchronized

### Story - Model for a composite does not include duplicate eclipse project names

Individual projects in a composite might have the same project name. This story implements a de-duping mechanism for the Eclipse model, such that the generated eclipse projects are uniquely identified.

##### Implementation

- If an `Set<ModelResult<EclipseProject>>` would include two projects with the same project name, an algorithm will de-duplicate the Eclipse project names. The name of the referenced `GradleProject` stays unchanged.
- Gradle core implements a similar algorithm for the IDE plugins. This implementation will be reused. The current implementation would have to be refactored and moved to an internal
package in the tooling-api subproject. The de-duplication implementation should use Tooling API's `HierarchicalElement` interface to access the name and hierarchy of projects. The IDE
subproject already depends on tooling-api and it's easy to adapt the current code to use the `HierarchicalElement` interface in de-duplication so that the implementation can be shared.
  - The current implementation lacks de-duplication for root project names. This has to be added.
  - The current `ModuleNameDeduper` implementation is not functional style. The logic mutates state between steps and it makes it hard to understand the de-dup logic. Consider
  rewriting the implementation.
- the renaming algorithm will prepend the parent project names when a duplicate name is found. If prepending the all parents up to the root project name still does not yield a unique name, then it appends an increasing counter.
- the algorithm should be implemented completely in Java (the existing one is partially written in Groovy)
- the algorithm will rename all projects that are part of a name conflict so they all end up with the same number of prefixes
- if a subproject already has the correct prefix, it is not prefixed again
- parent projects are de-duped first to avoid unnecessary counters in child project

##### Examples

foo:sub and bar:sub become foo-sub and bar-sub (prefixed with parent)
foo and foo become foo1 and foo2 (suffixed with counter)
foo:sub and bar:bar-sub become foo-sub and bar-sub (not prefixed twice)
foo:sub and foo:sub become foo1-sub and foo2-sub (parent projects are de-duped first)

##### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the `EclipseProject` names. The names of the `GradleProject`s are unaffected
- De-duping may be required for more that one duplicate project name.
- Multi-project builds can contain duplicate project names in any leaf of the project hierarchy.
- De-dup the names of root projects that have the same project name.
- Buildship uses de-duplicated names for Eclipse projects when multiple Gradle builds are imported containing duplicate names.
- see examples above

### Story - Model for a composite does not contain project names that conflict with non-Gradle projects

The Eclipse workspace might contain existing projects that are not Gradle projects. Their names are taken and cannot be changed by Buildship. The composite build API needs to be enhanced to allow passing reserved names for the de-duplication algorithm.

##### API
TBD

##### Test cases
TBD

### Story - Model for a composite substitutes source project dependencies for external module dependencies

If a composite contains a projectA and projectB, where projectA has a binary (external) dependency on projectB, then the `EclipseProject` model for projectA should contain a
reference to projectB via `EclipseProject.getProjectDependencies()`. The `EclipseProject.getClasspath()` should not contain a reference to projectB.

The algorithm for which projects will substitute for which external dependencies will initially be deliberately simplistic:
 - Dependencies specifying classifier, extension or artifacts will not be substituted
 - Substituted project must match the group and module of the dependency exactly
 - Version will not be considered for substitution

##### Implementation

- For the initial story, dependency substitution will be performed within Tooling Commons: the remote Gradle processes will simply provide the separate EclipseProject
model for each connected build, and will have no involvement in the substitution.
- To determine the external modules that can be substituted, we will need a way to determine the `GradlePublication` associated with an `EclipseProject`, if any.
- The `GradlePublication`s for a project can be determined by querying the model `ProjectPublications`. If a matching coordinates are found, the substitution can only be performed
if the model of the project registered a publication.
- Use `DefaultEclipseProjectDependency` if possible or provide a custom implementation of `EclipseProjectDependency` if needed.

##### Test cases

- Projects that are part of a composite can be built together in the IDE based on established project dependencies.
- When the coordinates of a substituted module dependency are changed, Buildship can refresh and receive the updated model:
    - If coordinates don't match up anymore, Buildship will depend on the module dependency.
    - If coordinates do match up, Buildship will re-establish the project dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a composite.
- Substituting a dependency for a project that has been renamed because of de-duping.
- Substituting an unresolved dependency for a project in the workspace.
- The project dependency (as a result of a substituted module dependency) reflects the proper path even if it's multiple levels deep e.g. `:sub1:sub-sub1:sub-sub-sub1`.

##### Open issues

- Can an instance of `EclipseProject` be changed after querying for it so that no custom model is required?
    - Removing matching external dependency via `EclipseProject.getClasspath().remove(...)`
    - Adding matching project dependency via `EclipseProject.getProjectDependencies().add(...)`
- In Buildship show visual hints that a module was substituted e.g. icon, tooltip etc.

### Story - Composite returns information about build environment for each participant

Buildship heavily relies on information exposed by the Tooling API for rendering or enabling/disabling specific functionality. One example for functionality exposed by the
Tooling API is the task view. Not only does Buildship render the tasks available for a project, it also indicates whether they are "public" or not. "Public" is determined
as a task that is not assigned to a group. The Tooling API provides information through the method `org.gradle.tooling.model.Launchable.isPublic()`. This method was introduced
with Gradle 2.3 which means that older versions of the Tooling API won't be able to provide the information. So far the composite model does not return information about
the Gradle versions used to build a participating build. The goal of this story is to expose information about the build environment for any participant of a composite.

_Use cases:_

- Buildship uses the Gradle version for project preview and task visibility.
- Buildship uses the Java home directory for project preview.

##### API

    public class BuildEnvironment {
        GradleEnvironment getGradleEnvironment();
        JavaEnvironment getJavaEnvironment();
    }

    public class GradleEnvironment {
        String getGradleVersion();
    }

    public class JavaEnvironment {
        File getJavaHome();
        List<String> getJvmArguments();
    }

    public interface ModelResult<T> {
        BuildEnvironment getBuildEnvironment();
    }

##### Implementation

- Add a new method to `ModelResult` that exposes the build environment.
- The build environment will expose information about the Gradle and the Java runtime.
- The only information exposed is the Gradle versions used for a participant.
- When building the model of a participant, the Tooling API needs to be queried for `org.gradle.tooling.model.build.BuildEnvironment`. It then populates the `ModelResult`.
- The returned build environment information is used in Buildship for the use cases mentioned above.

##### Test cases

- The `ModelResult` of a participating build always provides `BuildEnvironment`.
- Gradle environment and Java environment are always populated.
- The Gradle version lines up with the version of the provided Gradle distribution or the Gradle version provided by the build.
- The Java environment represents the Java runtime used to build the participant.
- Manually verify that Buildship uses the Gradle version for project preview and task visibility.
- Manually verify that Buildship uses the Java home directory for project preview.

##### Open issues

- Do we render all task as "public" in the task view before implementing this story?
- Alternative implementations:
    - Buildship just query for this information with "new" `ProjectConnection`s based on the returned `EclipseProject`. This would come with a performance cost or the
      composite API would need to expose a method for retrieving the already open `ProjectConnection`s.
    - The composite API exposes another model that can query for the information.

### Story - Composite participants can specify operational parameters

A participant of a composite requires to specify the root project directory upon creation. It can also provide a Gradle distribution used to build that project. There are other
use cases that require additional parameter for creating the model of a build.

_Use cases:_

- A participating build contains a lot of sub-projects. The underlying `BuildLauncher` needs more memory defined via JVM args.
- A participating build requires arguments e.g. project properties. The underlying `BuildLauncher` needs use these argument.

##### API

    public interface BuildArguments {
        String getJvmArgs();
        String getArguments();
    }

    public interface CompositeParticipant extends GradleDistributionAware, BuildArguments {
        ...
    }

##### Implementation

- Introduce a new interface `BuildArguments`.
- `CompositeParticipant` extend the new interface.
- The `BuildLauncher` calls `setJvmArguments(Iterable<String>)` for JVM arguments provided for a participant.
- The `BuildLauncher` calls `withArguments(Iterable<String>)` for arguments provided for a participant.

##### Test cases

- A participant can be built without providing any JVM arguments or arguments.
- When building the model for a participant JVM arguments and/or arguments are taken into consideration.
- JVM arguments can be provided independent from arguments and vice versa.

### Story - Move composite implementation from Tooling Commons to Tooling API

##### Implementation

- Move most of the logic implemented in Tooling Commons into the Tooling API.
- Tooling Commons uses the implementation from Tooling API.
- There should be no change to Buildship as the majority of the implementation previously used to the live in Tooling Commons.

### Story - Buildship can rename projects when importing/refreshing

Buildship will need to react to name changes by renaming projects. One limitation is that Eclipse requires projects that are physically contained in the workspace location to have the
same name as their folder. Buildship cannot rename such projects and should warn the user if synchronization becomes impossible due to this problem. Another important corner case is
swapping the names of two projects (A->B, B->A). This might be solved by assigning temporary names to all projects before the synchronization.

##### Test cases

- renaming a project in Gradle (e.g. in `settings.gradle`) -> the project is renamed in Eclipse
- trying to rename a project that is physically contained in the workspace location -> inform the user that this is not possible.
- swapping the names of two projects in Gradle -> the corresponding Eclipse projects swap names
- importing an existing project which already has a .project file and a different name than the one assigned by the de-duper ->
the projects name is changed to match the one assigned by the deduper

### Story - Tooling API provides IdeaProject model for a composite containing multiple Gradle builds

This story provides an API that will allow the IDEA developers to define and model a build composite for multiple imported Gradle builds. The provided feature will be the exact
analogue of the model provided for Buildship.

## Open issues

- Out-of-scope for this feature would be the ability to run builds using the composite definition from the IDE or the command-line.
- How will transitive dependencies be handled? The whole composite needs to be taken into account for resolution, so we can no longer delegate to the individual `ProjectConnection`s. What if the Gradle version building the composite does not match the Gradle version of one of the composed projects?
- There might be non-Gradle projects in the workspace that conflict with names in the Gradle projects. How will we pass this information to the deduping algorithm?
- Malformed projects now affect the whole workspace. Should we try to synchronize as much as possible or not synchronize at all if the model for one project cannot be retrieved?
- What about projects that depend on an older version of themselves? Or more generally, a set of projects that have a cycle when not taking versions into account? Using the current logic (which ignores versions), these would lead to cyclic project dependencies.
- How will Buildship identify projects? Using rootDir + path allows moving projects without renaming. Using projectDir allows renaming projects without moving. Neither can handle both at the same time.
- What if two projects could be substituted for the same external dependency? Unlikely, but the behavior should at least be stable (e.g. always use the first one)
- The order of projects that the `Set<ModelResult<EclipseProject>>` returns should be stable (in the order the ProjectConnections were added)
- Since this is a big change and we probably won't get all the details right in the first iteration, it should be an opt-in feature in Buildship
- In the prototype the projects returned by the `Set<ModelResult<EclipseProject>>` are decorated (for de-duping and substitution),
but their `getParent()` and `getChildren()` methods return undecorated projects.
- There are a lot of details and test cases about the flattening, but it is not really helpful to Buildship.
A `getRootProjects()` method would need less explanation and work just as well for Buildship
- Refreshing all projects in the workspace might be too costly for some power users. Buildship could allow the user to define composites explicitly, in order to reduce the number of affected projects
