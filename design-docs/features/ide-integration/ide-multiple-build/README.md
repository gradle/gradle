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

Introduce `EclipseWorkspace` to the Tooling API. This is a 'live' collection of eclipse projects based on the Gradle projects that the IDE user is working on. For this story, all Gradle projects for an `EclipseWorkspace` will be sourced from a single Gradle build.

The `EclipseWorkspace` provides a lightweight mechanism to query the set of all eclipse projects for an imported Gradle build, as well as a mechanism to get the fully configured set of eclipse projects.

##### API

```
    interface EclipseProjectIdentifier {
        File getRootProjectDirectory()
        String getPath()
    }

    interface EclipseWorkspace {
        /**
         * A set of lightweight project references for every Eclipse project that is built for the set of connected Gradle builds.
         * This set should not be expensive to calculate.
         */
        Set<EclipseProjectIdentifier> getAvailableProjects();

        /**
         * A flattened set of all projects in the Eclipse workspace.
         * These project models are fully configured, and may be expensive to calculate.
         * Note that not all projects necessarily share the same root.
         */
        Map<EclipseProjectIdentifier, EclipseProject> getOpenProjects();
    }

    ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(new File("myProject")).connect();
    GradleCompositeBuild compositeBuild = GradleConnector.newComposite().add(connection1).build()
    EclipseWorkspace eclipseWorkspace = compositeBuild.model(EclipseWorkspace.class)
```

##### Test cases

TBD

### Story - Buildship queries `EclipseWorkspace` to determine set of Eclipse projects for an imported Gradle build

By switching to use the new `EclipseWorkspace` model from the Tooling API, Buildship (and tooling-commons) should be simplified so that less knowledge is required of the mapping of Gradle projects to Eclipse projects. This change will also enable Buildship to later take advantage of project substitution for composite builds.

The `EclipseWorkspace` API should also make the synchronization of existing Eclipse projects simpler, as the `EclipseProjectIdentifier` can be easily used to match an `EclipseProject` instance with a local Eclipse project.

##### API

The API for this story is unchanged. Example usage is demonstrated below:


```
    // Create a project connection for each connected Gradle build, and combine them into a composite
    ProjectConnection connection1 = GradleConnector.newConnector().forProjectDirectory(new File("project1")).connect();
    GradleCompositeBuild compositeBuild = GradleConnector.newComposite().add(connection1).add(connection2).build()
    EclipseWorkspace eclipseWorkspace = compositeBuild.model(EclipseWorkspace.class)

    // Lightweight operation to get all of the eclipse projects that are mapped from the included Gradle builds
    Set<EclipseProjectIdentifier> candidateProjects = eclipseWorkspace.getAvailableProjects()

    // On import, create a project in eclipse for each candidate. Later we'll configure these projects
    candidateProjectIds.each {
        createEclipseProject(id)
    }

    // On startup, automatically import any newly discovered projects (later we might automatically create then as 'closed')
    // Later, we'll configure the open projects based on model from Gradle.
    candidateProjectIds.each {
        if (!eclipseProjectExists(id) {
            createEclipseProject(id)
        }
    }

    // Configure all open eclipse projects with configuration from model.
    // This is an expensive operation.
    Map<EclipseProjectIdentifier, EclipseProject> workspaceProjects = eclipseWorkspace.getOpenProjects()
    workspaceProjects.each { id, projectModel ->
        configureEclipseProjectFromModel(id, projectModel)
    }
```

##### Test cases

TBD

### Story - Tooling API provides EclipseProject model for a workspace containing projects for multiple Gradle builds

This story will enhance the implementation of `EclipseWorkspace` to support multiple `ProjectConnection` instances being added to the workspace.

##### API

The API for this story is unchanged. Example usage is demonstrated below:

```
    ProjectConnection connection1 = GradleConnector.newConnector().forProjectDirectory(new File("myProject1")).connect();
    ProjectConnection connection2 = GradleConnector.newConnector().forProjectDirectory(new File("myProject2")).connect();
    GradleCompositeBuild compositeBuild = GradleConnector.newComposite().add(connection1).add(connection2).build()
    EclipseWorkspace eclipseWorkspace = compositeBuild.model(EclipseWorkspace.class)
```

##### Test cases

TBD

### Story - Buildship queries `EclipseWorkspace` to determine set of Eclipse projects for multiple imported Gradle builds

This story builds on the previous by converting Buildship to create and use a single `EclipseWorkspace` instance to get the set of `EclipseProject` instances where multiple Gradle builds have been imported into Eclipse.

### Story - Eclipse model for a workspace does not include duplicate project names

Selected projects in a workspace might have the same project name. This story implements a de-duping mechanism for the Eclipse model.

##### Implementation

- If a workspace encounters two projects with the same project name, an algorithm will de-duplicate the project names. De-duped project names are only logic references to the original projects. The actual project name stays unchanged.
- Gradle core implements a similar algorithm for the IDE plugins. This will be reused.

##### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the names. De-duped project names still reference the original project. The original and de-duped project names
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

