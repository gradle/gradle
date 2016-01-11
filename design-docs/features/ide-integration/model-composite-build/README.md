# Developer uses projects from multiple Gradle builds from IDE

This feature allows a developer to work in a single IDE session on multiple projects that would normally be independent.

A typical workflow for a developer that has to work on 2 independent projects would be to make a change to project A, publish its artifact and build project B with the changed dependency. For developers this workflow is cumbersome and time-consuming. This feature allows a developer to work on multiple projects in a single IDE session that would normally be independent.

## 'Gradle Workspace'

The defined stories introduce the concept of a ‘Gradle workspace’ to the tooling API. This is simply a collection of Gradle projects that the IDE user is working on. These projects may come from different Gradle builds.

A tooling API client will be able to define a workspace and query it in the same way that a `ProjectConnection` can be queried. While the projects contained in a workspace may come from separate Gradle builds, the workspace will present as if it were a single, unified Gradle build containing all the projects in the workspace.

This will provide the developer with a view of all the projects in the workspace, so that the developer searches or uses or makes changes to any of these projects. When the developer compiles or runs tests from withing the IDE, these changes will be picked up for any dependent project. However, IDE actions that delegate to Gradle will not operate on a composite build, since these actions are not (yet) workspace-aware.

Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or from the command-line. This would be an additional feature.

In scope are changes to Buildship to use the workspace. Out of scope are changes to IDEA.

## Project substitution

Where possible, binary dependencies will be replaced with source dependencies between IDE modules.

So, for example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on library B, consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same workspace, however, application A would have a source dependency on library B.

## Stories

### Story - Tooling API provides EclipseProject model for a workspace where all projects come from the same Gradle build.

Introduce `EclipseWorkspace` to the Tooling API. This represents a collection of eclipse projects based on the Gradle builds that the IDE user is working on. For this story, all Gradle projects for an `EclipseWorkspace` will be sourced from a single Gradle build. As such, this story merely provides a convenience for obtaining a flattened collection of `EclipseProject` instances for a single Gradle build.

On completion of this story, it will be possible to convert Buildship to use this new API for project import and refresh, preparing for the next story which provides an `EclipseWorkspace` model for a composite of Gradle builds. Converting Buildship is the subject of the next story.

##### API

```
    abstract class GradleCompositeBuilder {
         static CompositeBuilder newComposite() { ... }
         CompositeBuilder withParticipant(ProjectConnection participant) { ... }
         CompositeBuild build(() { ... }
    }

    interface GradleConnection {
        // Extracted from ProjectConnection
        <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException;
        <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;
        <T> ModelBuilder<T> model(Class<T> modelType);
    }

    /**
     * EclipseWorkspace is not a supported model type.
     */
    interface ProjectConnection extends GradleConnection {
    }

    /**
     * For now, the only model type supported is EclipseWorkspace.
     */
    interface GradleComposite extends GradleConnection {
        // No other methods
    }

    interface EclipseWorkspace {
        /**
         * A flattened set of all projects in the Eclipse workspace.
         * These project models are fully configured, and may be expensive to calculate.
         * Note that not all projects necessarily share the same root.
         */
        Set<EclipseProject> getOpenProjects();
    }

    ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(new File("myProject")).connect();
    GradleComposite composite = GradleCompositeBuilder.newComposite().withParticipant(connection).build()
    EclipseWorkspace eclipseWorkspace = composite.model(EclipseWorkspace.class)
```

##### Implementation

- The `CompositeBuilder` provides a means to define a composite build via the Tooling API. Each `ProjectConnection` added to the composite specifies a Gradle build that participates in the composite.
    - Adding any `ProjectConnection` effectively adds the Gradle build that _contains_ the referenced project to the composite.
    - TODO: Should adding a `ProjectConnection` for a non-root project fail? Ideally, yes, but it may be expensive to calculate early.
    - For this story, adding multiple connections to the composite is not permitted.
- The only model type that can be requested for a `CompositeBuild` is `EclipseWorkspace`
    - On request for an `EclipseWorkspace`, the `ProjectConnection` will be queried for the `EclipseProject` model. This model represents the hierarchy of all eclipse projects for the Gradle build.
    - The instance of `EclipseWorkspace` will be constructed directly by the `CompositeBuild` instance, by traversing the hierarchy of the `EclipseProject` obtained.
    - A delegating implementation of `ModelBuilder` will be required

##### Test cases

TBD

### Story - Buildship queries `EclipseWorkspace` to determine set of Eclipse projects for an imported Gradle build

By switching to use the new `EclipseWorkspace` model from the Tooling API, Buildship (and tooling-commons) will no longer need to traverse the hierarchy of eclipse projects. This change will enable Buildship to later take advantage of project substitution and name de-duplication for composite builds.

##### API

The API for this story is unchanged.

##### Implementation

TBD

##### Test cases

TBD

### Story - Tooling API provides EclipseProject model for a workspace containing projects for multiple Gradle builds

This story will enhance the implementation of `EclipseWorkspace` to support multiple `ProjectConnection` instances being added to the workspace. The set of `EclipseProject` instances will be exactly the union of those returned by creating a separate `GradleComposite` per `ProjectConnection`. No name deduplication or substitution will occur.

##### API

The API for this story is unchanged. Example usage is demonstrated below:

```
    ProjectConnection connection1 = GradleConnector.newConnector().forProjectDirectory(new File("myProject1")).connect();
    ProjectConnection connection2 = GradleConnector.newConnector().forProjectDirectory(new File("myProject2")).connect();
    GradleComposite composite = GradleCompositeBuilder.newComposite().withParticipant(connection1).withParticipant(connection2).build()
    EclipseWorkspace eclipseWorkspace = composite.model(EclipseWorkspace.class)
```

##### Implementation

- Extends the delegating `ModelBuilder` implementation to perform a separate model request on each project connection, and to aggregate the full set of `EclipseProject` instances for these projects.

##### Test cases

- Adding a second `ProjectConnection` instance for the same project is a no-op.
- Adding a second `ProjectConnection` instance for a project within the same Gradle build is a no-op.

More TBD

### Story - Buildship queries `EclipseWorkspace` to determine set of Eclipse projects for multiple imported Gradle builds

This story builds on the previous by converting Buildship to create and use a single `GradleCompositeBuild` instance where multiple Gradle projects have been imported into Eclipse.

When importing a new Gradle build, projects for all previously imported Gradle builds will need to be refreshed.

If implemented correctly, the development of project name deduplication and project dependency substitution support in Gradle will automatically be reflected in functionality within Buildship.

##### Implementation

TBD

##### Test cases

TBD

### Story - Eclipse model for a workspace does not include duplicate project names

Selected projects in a workspace might have the same project name. This story implements a de-duping mechanism for the Eclipse model, such that the generated eclipse projects are uniquely identified.

##### Implementation

- If a workspace encounters two projects with the same project name, an algorithm will de-duplicate the project names. De-duped eclipse project names are only logic references to the original projects. The actual project name stays unchanged.
- Gradle core implements a similar algorithm for the IDE plugins. This implementation will be reused (shared).

##### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the names. De-duped project names still reference the original project.
should be rendered in Eclipse's project view section.
- De-duping may be required for more that one duplicate project name.
- Multi-project builds can contain duplicate project names in any leaf of the project hierarchy.
- Buildship uses de-duplicated names for Eclipse projects when multiple Gradle builds are imported containing duplicate names

### Story - Eclipse model for a workspace uses source dependency instead of binary dependency between the projects of that workspace.

If a workspace contains a projectA and projectB, where projectA has a binary (external) dependeny on projectB, then the `EclipseProject` model for projectA should contain a reference to projectB via `EclipseProject.getProjectDependencies()`. The `EclipseProject.getClasspath()` should not contain a reference to projectB.

The algorithm for which projects will substitute in for which external dependencies will initially be deliberately simplistic:
 - Dependencies specifying classifier, extension or artifacts will not be substituted
 - Substituted project must match the group and module of the dependency exactly
 - Version will not be considered for substitution

##### Implementation

- For the initial story, dependency substitution will be performed within the Tooling API client: the remote Gradle processes will simply provide the separate EclipseProject model for each connected build, and will have no involvement in the substitution.

##### Test cases

- Projects that are part of a workspace can be built together based on established project dependencies.
- When the coordinates of a substituted module dependency are changed, Buildship can refresh and recieve the updated model:
    - If coordinates don't match up anymore, Buildship will depend on the module dependency.
    - If coordinates do match up, Buildship will re-establish the project dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

More TBD

## Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or the command-line.
the command-line.
- The [concept of workspace exists in Eclipse](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.user/concepts/cworkset.htm?cp=0_2_1_6) which could be used to define a [custom extension point](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workingSets.html?cp=2_1_1_202).
 However, investigation is needed if and how can we use them.

