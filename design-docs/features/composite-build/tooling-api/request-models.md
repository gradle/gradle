## Tooling Client requests models for all builds in a composite

### Overview

This story provides an API for retrieving a particular model type for all projects in a `GradleConnection`.

This will be useful both for a single multi-project build, as well as for a composite consisting of muliple builds.
(With the existing `ProjectConnection` API, getting all models for a multi-project build can involve opening a connection to each subproject.)

Tooling API supports retrieving these model types:
- hierarchical model, a single model contains models for all projects.
  - EclipseProject
  - HierarchicalEclipseProject
  - GradleProject
- hierarchical models where the model is returned for the root project. models for sub-projects are different type than the parent.
  - IdeaProject
  - BasicIdeaProject
- build models, a single model for the build
  - BuildEnvironment
  - GradleBuild
- project models, a model has to be requested separately for each project
  - BuildInvocations
  - ProjectPublications

A `GradleConnection` will make it easy to retrieve models for all of the Gradle projects the composite. It will be possible to correlate each model with the Build or Project that it is associated with.

For some projects, it may not be possible to determine the model, due to incompatible Gradle version, configuration error, etc. The result will include a failure for each Project, again correlated with the Build or Project that it is associated with.

Open Question: Is it important to always have a result for every `Project` in the connection? Or is it OK to have a result for each _Build_ when:
 - The user requests a build model, like `BuildEnvironment` or `GradleBuild`
 - The user requests an invalid model type, or a model type that is not supported by the target Gradle version
 - The target build cannot be loaded (in this case it's not really possible to determine the project structure)
 - The user requests `IdeaProject`, which is another example of a per-build model

### API

New types:

    public interface ModelResults<T> extends Iterable<ModelResult<T>> {}

    public interface ModelResult<T> {
        /**
         * @return the identity of the project this model was produced from, never null.
         */
        ProjectIdentity getProjectIdentity();

        /**
         * @return the model produced, never null.
         * @throws GradleConnectionException if the model could not be retrieved.
         */
        T getModel() throws GradleConnectionException;

        /**
         * @return the failure.
         */
        GradleConnectionException getFailure();
    }

    interface ProjectIdentity {
        BuildIdentity getBuild()
    }

    interface BuildIdentity {
    }

Example usage:

    GradleConnectionBuilder builder = GradleConnector.newGradleConnection();

    BuildIdentity idBuild1 = builder.newParticipant(dir1).create()
    BuildIdentity idBuild2 = builder.newParticipant(dir2).create()

    GradleConnection connection = builder.build()

    try {
        ModelResults<EclipseProject> eclipseResults = connection.getModels(EclipseProject.class);
        ModelResults<ProjectPublications> publicationResults = connection.getModels(ProjectPubications.class);

        // Get all eclipse model results for build1
        def eclipseResultsForBuild1 = eclipseResults.findAll({it.projectIdentity.build == idBuild1})

        // Find the ProjectPublication model matching a particular EclipseProject
        def selectedEclipseResult = eclipseResults.last()
        def matchingProjectResult = publicationResults.find({it.projectIdentity == selectedEclipseResult.projectIdentity})
    } finally {
        connection.close();
    }

### Implementation notes

- Create a separate `ProjectConnection` instance for each participant and delegate model requests
- A variety of strategies are used to retrieve all project models for a build:
    - For per-build models, retrieve the `GradleBuild` model to determine structure, and obtain a single model for the root project
    - For hierarchical models, obtain a single model for the root project and traverse to determine structure and get other project models
    - For Gradle versions that support custom model actions, use a custom action to get all models for all projects
    - Otherwise, use a brute force strategy that opens a separate `ProjectConnection` for each subproject
- Each participant in the composite will be used sequentially
- Order of participants added to a composite does not guarantee an order when operating on participants or returning results.  A composite with [A,B,C] will return the same results as a composite with [C,B,A], but results are not guaranteed to be in any particular order.

### Test coverage

- Given:
    - scenario #1, single participant:
        - 1 single-project build
    - scenario #2, single participant:
        - 1 multi-project build with 3 projects
    - scenario #3, 2 participants:
        - 1 single-project build
        - 1 multi-project builds with 3 projects
    - each project is a java project with publications for each project so that `ProjectPublications` can be tested.

- test that each supported model type can be retrieved
    - check that there is a model result for each project for project models and normal hierarchical models
    - some model types don't contain the project information so that the model can be correlated to a project.
        - In these cases, check that the number of returned models matches the number of projects
    - Build models and IdeaProject models are the same for each project in the build.
        - In these cases, check that the number of returned models matches the number of builds in the composite
    - Check `ProjectIdentity` and `BuildIdentity` attached to model results
- Test failures:
    - When all participants have bad project configuration, get a single failure result per participant
    - When one participant has bad project configuration, get model results for other participant(s)

- The same BuildIdentity and ProjectIdentity should be able to correlate across model types
    - Retrieve modelType X and modelType Y
    - ProjectIdentity for modelType-X should be able to locate modelType-Y results

- After making a successful model request, on a subsequent model request:
    - Changing the set of sub-projects changes the size of the `ModelResults` that is returned
    - Changing a single build into a multi-project build changes the number of `EclipseProject`s that are returned
    - Removing the project directory is causes a failure
    - Making one participant a subproject of another causes the request to fail

- Fail with `IllegalStateException` after connecting to a `GradleConnection`, closing the connection and trying to retrieve a model.
- Fail if participant is not a Gradle build (what does this look like for existing integration tests?)
- Fail if participant is a subproject of another participant.
- The correct Gradle distribution is used to connect to each participant
- Participant project directory is matches that used to define the composite participant
- When composite build connection is closed, all `ProjectConnection`s are closed.

### Documentation

- Add to the `GradleConnection` sample so that models are requested and reported.
    - Include reporting of project that matches each model
- Add to the 'Embedding Gradle' of the user guide.
