# Developer executes tasks for a composite build from IDE

This feature allows a developer to execute tasks for a composite build from within an IDE, where the IDE has defined and configure a composite build via the Tooling API. During task execution, dependencies will be resolved within the context of the composite, with locally-built artifacts being substituted for external module dependencies where possible.

## Stories

- [ ] Story - Tooling API provides API for executing tasks in a composite build
    - This story does not change the way the tasks execute, but provides the appropriate task execution API
- [ ] Story - Buildship uses composite build API for task execution
    - Change Buildship to use the new API to take advantage of subsequently developed features
- [ ] Story - External module dependencies are replaced with already-built project artifacts during task execution on a composite
    - This story implements the appropriate dependency substitution for an executing project, assuming that the artifacts for the depended-on projects have already been built.
    - Substitution will only occur where all projects involved in the composite use the same, latest version of Gradle
    - The API will provide a means to determine when composite build task execution is not possible
- [ ] Story - Buildship provides visual indicator when composite build task execution is not possible (different Gradle versions)
- [ ] Story - All possible required project artifacts are built in a composite before task is executed
- [ ] Story - Directly dependent project artifacts are constructed and substituted when task is executed in a composite
- [ ] Story - Directly dependent project artifacts are constructed in parallel and on demand when task is executed in a composite
- [ ] Story - Transitive graph of project artifacts are constructed and substituted when task is executed in a composite

