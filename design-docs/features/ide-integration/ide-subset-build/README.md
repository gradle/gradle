# Developer uses subset of a multi-project Gradle build from IDE

This features allows a developer to work in a single IDE session on a subset of projects that would normally be part of the same build.

This allows a developer to efficiently work on part of a large code base with many projects, by focusing on only a certain set of projects and reducing the amount of work the IDE must do in indexing and compilation.

Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or the command-line.

## Project substitution

Large multi-project builds may consist of hundreds of projects. If a developer only works on a subset of these projects, the developer pays a penalty in terms of build execution performance as project dependencies have to be rebuilt (even though up-to-date checks might kick in). Another factor that slows down development is the re-indexing of changed files in the IDE. This feature allows for selectively substituting project dependencies with binary dependencies in Buildship.

Building on the workspace concept from the previous feature, Gradle will replace source dependencies with binary dependencies where appropriate.

For example, application A and library B might normally be built together as part of the same build. Application A would have a source dependency on library B. When application A is imported in a workspace and library B is not, application A would have a binary dependency on library B, either using a jar downloaded from a repository or built locally.

## Stories

### Story - Buildship user opens and closes projects within EclipseWorkspace

This story allows buildship to add and remove Eclipse projects from the set of open projects for a workspace.

When a project is closed:

- The closed project will not be included in the set of projects returned by `EclipseWorkspace.getOpenProjects()`
- No project returned by `EclipseWorkspace.getOpenProjects()` will reference the closed project as an `EclipseProjectDependency`
  - Instead, the artifacts of the closed project will be referenced directly as `ExternalDependency` instances
- The closed project will still participate in project name de-duplication, so the names of Eclipse projects will not change

##### API

```
    interface EclipseWorkspace {
        // New API
        /**
         * Remove the project from the set of open projects.
         * If the project is already closed, then this is a no-op.
         * @throws Exception if the supplied id is not part of getAvailableProjects()
         */
        void closeProject(EclipseProjectIdentifier id)

        /**
         * Add the project to the set of open projects.
         * It is a no-op to open a project that has not been closed.
         * @throws Exception if the supplied id is not part of getAvailableProjects()
         */
        void openProject(EclipseProjectIdentifier id)

        // Existing API
        Set<EclipseProjectIdentifier> getAvailableProjects();
        Map<EclipseProjectIdentifier, EclipseProject> getOpenProjects();
    }
```

##### Implementation notes

- When a project dependency is replaced by an artifact dependency, the necessary tasks will be executed to build the artifact. This will happen when the `EclipseProject` models are built as part of `EclipseWorkspace.getOpenProjects()`.
