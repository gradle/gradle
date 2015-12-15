# Developer uses subset of a multi-project Gradle build from IDE

This features allows a developer to work in a single IDE session on a subset of projects that would normally be part of the same build.

This allows a developer to efficiently work on part of a large code base with many projects, by focusing on only a certain set of projects and reducing the amount of work the IDE must do in indexing and compilation.

Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or the command-line.

## Project substitution

Large multi-project builds may consist of hundreds of projects. If a developer only works on a subset of these projects, the developer pays a penalty in terms of build execution performance as project dependencies have to be rebuilt (even though up-to-date checks might kick in). Another factor that slows down development is the re-indexing of changed files in the IDE. This feature allows for selectively substituting project dependencies with binary dependencies in Buildship.

Building on the workspace concept from the previous feature, Gradle will replace source dependencies with binary dependencies where appropriate.

For example, application A and library B might normally be built together as part of the same build. Application A would have a source dependency on library B. When application A is imported in a workspace and library B is not, application A would have a binary dependency on library B, either using a jar downloaded from a repository or built locally.


For example, application A and library B might normally be built together as part of the same build. Application A would have a source dependency on library B. When application A is imported in a workspace and library B is not, application A would have a binary dependency on library B, either using a jar downloaded from a repository or built locally.

## Stories

### Story - Select subset of project for a "workspace" in Buildship

Build on the workspace concept from the previous feature, to replace source dependencies with binary dependencies.

##### Estimate

-

##### Implementation

- In the import screen of Buildship, allow the user to select projects that define project dependencies. For these projects try to resolve the binary dependency.
    - The user can decide to substitute none or (any number of projects - 1). This assumes that the developer wants to work on the source code of at least on project.
    - The project selected substitution has to define the properties `group`, `name` and `version` in order to build the module coordinates for external lookup.
- Buildship will indicate which projects are substitutable based on their coordinates (potentially with a preview).
    - Iterate over all project dependencies of a project accessible via `DefaultEclipseProject.getProjectDependencies()`.
    - Each dependency provides its coordinates through `DefaultEclipseProjectDependency.getTarget().getGradleModuleVersion()`.
    - Check for resolvable binary dependency available in any of the repositories defined for the coordinates.
    - If a match is determined, use the module coordinates. Replace the source dependency with a binary dependency.
    - Give a visual indication (e.g. icon) that a substitution was performed for a project.
- Allow for a context menu that brings up the original substitution dialog in case the user wants to modify the project selection.
- A workspace needs to be persistable.

##### Test cases

- A source dependency can only be replaced if the binary dependency exists in any of the declared binary repositories. Otherwise, don't allow substitution.
- Substituted projects are not built as part of the multi-project build (e.g. no tasks are invoked to compile the code and create the JAR file).
- The workspace as a whole is buildable. That means all dependencies can be resolved, no compilation issues occur.
- If the list of repositories was changed in the build script (e.g. by editing the file), BuildShip will need to check if substituted dependencies are still valid.
- If the coordinates of a substituted module dependency are changed, Buildship needs to react properly.
    - If coordinates don't match up anymore, Buildship will depend on the project dependency.
    - If coordinates do match up, Buildship will re-establish the binary dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

##### Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or the command-line.
- The import screen is only one entry point for this functionality. Should we have other dialogs/screen to allow for substitution? What about changing a workspace after it has been created?
