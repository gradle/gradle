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

### Story - Tooling client can query EclipseProject model for a workspace where all projects come from the same Gradle build.
### Story - Tooling client can query EclipseProject model for a workspace where projects come from different Gradle builds.

Introduce `GradleWorkspace` to the Tooling API. This is simply a collection of Gradle projects that the IDE user is working on. These projects may come from different Gradle builds.

A Tooling API client will construct a `GradleWorkspace` and add a `GradleBuild` instance for each imported Gradle build. The client will then query the workspace as if it were a single, unified Gradle build containing all the projects in the workspace. Where possible, binary dependencies would be replaced with source dependencies between IDE modules.

For example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on library B, consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same workspace, however, application A would have a source dependency on library B.

##### Estimate

-

##### API changes

- `GradleConnector.newWorkspaceBuilder()` creates an empty `GradleWorkspaceBuilder`
- `GradleWorkspaceBuilder` and `GradleWorkspace` are used to construct and query the collection of independent projects as a whole.
    - Should `GradleWorkspace` be called `GradleWorkspaceConnection`?
    - Is the separate `GradleWorkspaceBuilder` helpful?
- `EclipseWorkspace` represents the set of projects that are open in Eclipse: this is the only model that can currently be requested for a `GradleWorkspace`

```java

    interface GradleWorkspaceBuilder {
        /**
         * Add all projects in the build to this workspace.
         */
        GradleWorkspaceBuilder addProjects(GradleBuild build);

        /**
         * Remove all projects in the build from this workspace.
         */
        GradleWorkspaceBuilder removeProjects(GradleBuild build);

        /**
         * Builds a workspace containing the configured builds.
         */
        GradleWorkspace build();
    }

    interface GradleWorkspace {
        /**
         * Query the model for this workspace.
         * At first, the only supported type is {@link EclipseWorkspace}.
         */
        ModelBuilder<T> model(Class<T> modelType);

        // Do we want/need the getModel() convenience methods from `ProjectConnection`?
    }

    interface EclipseWorkspace {
        /**
         * The root project of each Gradle build that is included in this workspace.
         */
        <T extends HierarchicalEclipseProject> Set<T> getRootProjects(Class<T> type);

        /**
         * A flattened set of all projects in the Eclipse workspace.
         * Note that not all projects necessarily share the same root
         */
        <T extends HierarchicalEclipseProject> Set<T> getAllProjects(Class<T> type);
    }

    public abstract class GradleConnector {
        public static GradleWorkspaceBuilder newWorkspaceBuilder() {
            return ConnectorServices.createGradleWorkspaceBuilder();
        }
    }
```

##### Test cases

- When using a `GradleWorkspaceBuilder` to compose a `GradleWorkspace` and query the `EclipseWorkspace` model:
  - An empty workspace can be created and queried: the set of eclipse projects is empty
  - Adding a single `GradleBuild` results in a `HierarchicalEclipseProject` for each `BasicGradleProject` in the `GradleBuild`
  - Adding multiple `GradleBuild` instances results in a `HierarchicalEclipseProject` for each `BasicGradleProject` in each `GradleBuild`
  - Readding the same `GradleBuild` does not result in additional `HierarchicalEclipseProject` being added
  - Adding 2 `GradleBuild` instances that have `BasicGradleProject`s that share a project directory results in separate eclipse projects for each `BasicGradleProject`
  - Removing a `GradleBuild` results in the removal of all `HierarchicalEclipseProject` projects added for that build
- Can use the same `GradleWorkspaceBuilder` instance to generate different `GradleWorkspace` instances by adding and removing `GradleBuild`s.
- Exception is thrown when adding multiple `GradleBuild` instances would result in duplicated eclipse project names.
    - A later story will deal with de-duplication

##### Open issues

- The `HierarchicalEclipseProject` api will become difficult to fulfill when we have selective removal of gradle projects from the hierarchy.
- Should be able to query an `IdeaProject` from a `GradleWorkspace`.

### Story - Eclipse model for a workspace does not include duplicate project names

Selected projects in a workspace might have the same project name. This story implements a du-duping mechanism for the Eclipse model.

##### Estimate

-

##### Implementation

- If a workspace encounters two projects with the same project name, an algorithm will de-duplicate the project names. De-duped project names are only logic references to the original projects. The actual project name stays unchanged.
- Gradle core implements a similar algorithm for the IDE plugins. This will be reused.

##### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the names. De-duped project names still reference the original project. The original and de-duped project names
should be rendered in Eclipse's project view section.
- De-duping may be required for more that one duplicate project name.
- Multi-project builds can contain duplicate project names in any leaf of the project hierarchy.

### Story - Eclipse model for a workspace uses source dependency instead of binary dependency between the projects of that workspace.

If any of the module dependencies matches on the coordinates of one of the selected projects, they'll be able to form
 a workspace. Buildship will treat the matching projects as source dependencies instead of binary dependencies.

### Story - Buildship uses Gradle workspace to query EclipseProject model for all imported Gradle builds.

Once the `GradleWorkspace` API is available, Buildship should be updated to use this for constructing the `EclipseProject` model for all imported Gradle projects. When multiple gradle projects are imported and configured via the `GradleWorkspace`, Buildship will get the advantages of project name deduplication as well as dependency substitution.

##### API Usage

```
    // Create a project connection and get the `GradleBuild` instance for each imported Gradle project
    ProjectConnection connection1 = GradleConnector.newConnector().forProjectDirectory(new File("project1")).connect();
    GradleBuild build1 = connection1.getModel(GradleBuild.class);
    ProjectConnection connection2 = GradleConnector.newConnector().forProjectDirectory(new File("project2")).connect();
    GradleBuild build2 = connection2.getModel(GradleBuild.class);

    // Construct a `GradleWorkspace` for all imported Gradle projects, and get the `EclipseWorkspace` model for this
    GradleWorkspace gradleWorkspace = GradleConnector.newWorkspaceBuilder().addProjects(build1).addProjects(build2).build()
    EclipseWorkspace eclipseWorkspace = gradleWorkspace.model(EclipseWorkspace.class)

    // Iterate over all of the `EclipseProject` instances and configure the Eclipse projects accordingly
    for (EclipseProject project  : eclipseWorkspace.getAllProjects(EclipseProject.class)) {

        // These 2 dependency sets already have the correct dependency substitution in place
        Set<EclipseProjectDependency> projectDependencies = project.getProjectDependencies();
        Set<ExternalDependency> externalDependencies = project.getClasspath();
    }
    
```

##### Test cases

- Projects that are part of a workspace can be built together based on established project dependencies.
- When the coordinates of a substituted module dependency are changed, Buildship can refresh and recieve the updated model:
    - If coordinates don't match up anymore, Buildship will depend on the module dependency.
    - If coordinates do match up, Buildship will re-establish the project dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

## Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or from
the command-line. This would be an additional feature.
- The [concept of workspace exists in Eclipse](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.user/concepts/cworkset.htm?cp=0_2_1_6) which could be used to define a [custom extension point](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workingSets.html?cp=2_1_1_202).
 However, investigation is needed if and how can we use them.

