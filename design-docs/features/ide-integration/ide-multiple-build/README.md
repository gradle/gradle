# Developer uses projects from multiple Gradle builds from IDE

Projects might define a binary dependency on a library produced by another project. Usually, these projects are built separately from each other and live in distinct source code repositories.

The typical workflow for a developer that has to work on both projects would be to make a change to project A, publish its artifact and build project B with the changed dependency. If the library version of project A changes, project B needs to depend on the new artifact version. For developers this workflow is cumbersome and time-consuming. This feature allows a developer to work on multiple projects in a single IDE session that would normally be independent.

## Stories

### Story - Introduce the concept of a workspace to the Tooling API

Introduce the concept of a "workspace" to the Tooling API. This is simply a collection of Gradle projects that the IDE user is working on. These projects may come from different Gradle builds.

A Tooling API client will be able to define a workspace and query it as if it were a single, unified Gradle build containing all the projects in the workspace. Where possible, binary dependencies
would be replaced with source dependencies between IDE modules.

For example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on library B,
consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same workspace, however, application A would have a source dependency on
library B.

##### Estimate

-

##### Implementation

    package org.gradle.tooling

    public interface ProjectWorkspace {
        void addProject(BasicGradleProject gradleProject);
        void removeProject(BasicGradleProject gradleProject);
        DomainObjectSet<BasicGradleProject> getProjects();
    }

    public abstract class GradleConnector {
        public static ProjectWorkspace newWorkspace() {
            return ConnectorServices.createProjectWorkspace();
        }
    }

- Introduce a `ProjectWorkspace` interface that describes the collection of independent projects as a whole.
- Projects can be added or removed from the workspace. A workspace can return the list of projects that have been registered.
- A workspace does not depend on the Eclipse or Idea models.
- Expose a new method in `GradleConnector` for creating the workspace. The actual creation is done in `ConnectorServices`.

##### Test cases

- A new workspace can be created.
- Two workspaces can be compared by the list of projects they contain.
- If a project was added to the workspace, it can be retrieved from the list of projects.
- If a project already exists in the workspace, adding it again doesn't add a duplicate.
- If a project was removed from the workspace, the list of projects does not contain it anymore.
- If a project is requested to be removed from the workspace and doesn't exist, the list of projects isn't modified.
- The order of projects in the list is maintained.

##### Open issues

- We could potentially merge this story with the next one and just make it Eclipse-specific? Maybe we should just deal with the concrete IDE-specific `GradleProject` implementations
 e.g. `EclipseProject`.
- Should a workspace have a unique name that needs to be provided upcon creation?

### Story - Expose workspace concept for Eclipse

The generic workspace concept should be usable from the Eclipse Tooling model. Expose ways to match project coordinates to coordinates of a module
dependency.

#### Estimate

-

#### Implementation

- Introduce the interface `ModelAware` that allows a consumer to ask for the model.
- `ProjectConnection` and `ProjectWorkspace` share the same interface. Remove the methods `model(Class)`, `getModel(Class)` and getModel(Class, ResultHandler)` from `ProjectConnection`
 as they have been pulled up into the common interface.
- Document which models can be retrieved for each of the implementations of `ModelAware`.

<!-- -->

    public interface ModelAware {
        <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException;
        <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;
        <T> ModelBuilder<T> model(Class<T> modelType);
    }

    public interface ProjectConnection extends ModelAware {
        ...
    }

    public interface ProjectWorkspace extends ModelAware {
        ...
    }

- To build a workspace a consumer would retrieve the model for at least two projects via the Tooling API.
- The consumer matches based on the publication and classpath library GAVs for each project.
- If at least one match is found, a workspace can be created via `GradleConnector.newWorkspace()`. The matching projects can be added to the workspace.

The following code snippet shows some of the moving pieces in practice:

<!-- -->

    // create project connections
    ProjectConnection connection1 = GradleConnector.newConnector().forProjectDirectory(new File("project1")).connect();
    ProjectConnection connection2 = GradleConnector.newConnector().forProjectDirectory(new File("project2")).connect();

    // match based on publication and classpath library GAVs for each project
    EclipseProject eclipseProject1 = connection1.getModel(EclipseProject.class);
    GradlePublication publication2 = connection2.getModel(GradlePublication.class);

    for (ExternalDependency externalDependency : eclipseProject1.getClasspath()) {
        if(externalDependency.getGradleModuleVersion().equals(publication2.getId())) {
            ...
        }
    }

    // add projects with a match
    ProjectWorkspace projectWorkspace = GradleConnector.newWorkspace();
    projectWorkspace.addProject(connection1.getModel(BasicGradleProject.class));
    projectWorkspace.addProject(connection2.getModel(BasicGradleProject.class));

#### Test cases

- The `ProjectConnection` can be queried and resolve the requested model as usual.
- The `ProjectWorkspace` can be queried for the `EclipseProject`
- A project and an external dependency can be matched based on the module version.

#### Open issues

- Support for IDEA is out-of-scope.

### Story - Eclipse model for a workspace does not include duplicate project names

Selected projects in a workspace might have the same project name. This story implements a du-duping mechanism for the Eclipse model.

#### Estimate

-

#### Implementation

- If a workspace encounters two projects with the same project name, an algorithm will de-dedupe the project names. De-duped project names are only logic references to the
original projects. The actual project name stays unchanged.
- Gradle core implements a similar algorithm for the IDE plugins. Reuse it if possible.

#### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the names. De-duped project names still reference the original project. The original and de-duped project names
should be rendered in Eclipse's project view section.
- De-duping may be required for more that one duplicate project name.
- Multi-project builds can contain duplicate project names in any leaf of the project hierarchy.

### Story - Define a "workspace" in Buildship

From Buildship, a developer should be able to point to one or many projects in the local file system to form a workspace.
If any of the module dependencies matches on the coordinates of one of the selected projects, they'll be able to form
 a workspace. Buildship will treat the matching projects as source dependencies instead of binary dependencies.

#### Estimate

-

#### Implementation

- Expose a new dialog in Buildship (maybe some new type of import?) that allows for selecting projects that should form a workspace.
    - At least two projects have to be selected.
    - Projects can live in any directory on the local filesystem.
    - The project has to be a valid Gradle project and define the properties `group`, `name` and `version`.
- Buildship will indicate which projects are substitutable based on their coordinates (potentially with a preview).
    - Iterate over all dependencies of a project accessible via `DefaultEclipseProject.getClasspath()`.
    - Each dependency provides its coordinates through `DefaultEclipseExternalDependency.getGradleModuleVersion()`.
    - Compare the dependency coordinates with the coordinates of the project through `DefaultEclipseProject.getGradleModuleVersion()`.
    - If a match is determined, use the project path. If duplicate project paths are found, render a error message and disallow import.
    - Give a visual indication (e.g. icon) that a substitution was performed for a project.
- Allow for a context menu that brings up the original substitution dialog in case the user wants to modify the project selection.
- After selecting the projects the exposed Tooling API is consumed to form the workspace.
- Buildship renders the workspace in the project view as a multi-project build.
- The developer can make changes to any project's build script. Newly established (and substitutable) dependencies between projects are resolved as project dependencies.
- A workspace needs to be persistable.

#### Test cases

- Buildship doesn't allow creating a workspace if only 0 or 1 projects were selected.
- Project that do not define the proper coordinates cannot be used to form a workspace.
- If no substitutable module dependency can be determined, the dialog will render a error message.
- Projects that are part of a workspace can be built together based on established project dependencies.
- If the coordinates of a substituted module dependency is changed, Buildship needs to react properly.
    - If coordinates don't match up anymore, Buildship will depend on the module dependency.
    - If coordinates do match up, Buildship will re-establish the project dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

#### Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or from
the command-line. This would be an additional feature.
- De-duping of duplicate project names is out-of-scope for this story. It's going to be addressed in a separate story.
- The [concept of workspace exists in Eclipse](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.user/concepts/cworkset.htm?cp=0_2_1_6)
 which could be used to define a [custom extension point](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workingSets.html?cp=2_1_1_202).
 However, investigation is needed if and how can we use them.

