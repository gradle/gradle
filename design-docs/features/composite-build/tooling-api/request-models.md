## Tooling client requests arbitrary model type for every project in a composite

### Overview

Previous stories have implemented hard-coded support only for EclipseProject models. This story adds support for the other
standard models that are supported in the Tooling API.

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

### API

This story doesn't introduce any API changes.

### Implementation notes

The current implementation supports hierarchical models. Support for per-project models will be added.

Per-project models will be retrieved from each participant by using a custom BuildAction that traverses the build's project structure and uses the API method available on
`org.gradle.tooling.BuildController` to request per-project models:
```
<T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;
```
The `getBuildModel` method on `BuildController` will be used to traverse the build's project structure.

Build models will be handled in the same way as per-project models in the implementation.
The same applies to `IdeaProject` and `BasicIdeaProject`, they will be handled as per-project models, returning the same instance for each project in the build.
They are hierarchical models, but the children are different type than the parent.
It's really not a hierarchical structure since a build returns a `IdeaProject` with multiple `IdeaModule`s as children. The possible sub-project hierarchy is flattened.

### Test coverage

- given 4 scenarios of composites:
  - scenario #1, participant:
    - 1 single-project build
  - scenario #2, participant:
    - 1 multi-project build with 3 projects
  - scenario #3, participants:
    - 1 single-project build
    - 1 multi-project builds with 3 projects
- each sample project is a java project with publications for each project so that `ProjectPublications` can be tested.

- test that each model type can be retrieved
  - check that there is a model result for each project for project models and normal hierarchical models
  - some model types don't contain the project information so that the model can be correlated to a project.
    - In these cases, check that the number of returned models matches the number of projects
  - Build models and IdeaProject models are the same for each project in the build.
    - In these cases, check that the number of returned models matches the number of builds in the composite

### Open Issues

- Support for custom models

## Tooling client can correlate model instances to projects in a composite

### Overview

- Not all model types include information that allows a client TAPI to determine which project the model came from
- Composite API does not guarantee any particular order to the results returned
- All model results from all projects are always returned (no partial results or selective API yet)

### API

    // NEW: Interface to identify a build (a collection of 1+ projects)
    interface BuildIdentity {
        // pretty toString
    }

    // NEW: Interface to identify a project (a single project in a build)
    interface ProjectIdentity {
        // Build that contains this project
        BuildIdentity getBuild()

        // pretty toString
    }

    // NEW: Interface to create identities based on a participant
    interface GradleBuild {
        // Get a BuildIdentity that can be used to correlate all results from a particular build
        BuildIdentity toBuildIdentity()

        // Get a ProjectIdentity that can be used to correlate a result from a particular project
        ProjectIdentity toProjectIdentity(String projectPath)
    }

    // NEW: Interface for results returned
    interface ModelResult<T> {
        T getModel()
        ProjectIdentity getProjectIdentity()
    }

    // NEW: Method to get a GradleBuild which can be used to get identities
    GradleBuild participantA = GradleConnector.newParticipant(new File ("path/to/A")).
        useGradleDistribution("2.11").create()
    GradleBuild participantB = GradleConnector.newParticipant(new File ("path/to/B")).
        create()
    // or URI or File or don't specify to use the wrapper

    GradleConnection connection = GradleConnector.newGradleCompositeBuilder().
        addBuild(participantA).
        addBuild(participantB).
        build()

    // NEW: ModelBuilder returned by GradleConnection becomes a ModelBuilder<Iterable<ModelResult<T>>>
    // get() returns a Iterable<ModelResult<T>>
    // get(ResultHandler) receives a Iterable<ModelResult<T>>

    Iterable<ModelResult<EclipseProject>> eclipseProjects = connection.getModels(EclipseProject)
    for (ModelResult<EclipseProject> modelResult : eclipseProjects) {
        // do something with all eclipse projects
    }

    // Participants are A::x, B::y, B::z
    final ProjectIdentity projectYofB = participantB.toProjectIdentity(":y")
     // returns B::y EclipseProject
    eclipseProjects.find({ modelResult -> modelResult.getProjectIdentity().equals(projectYofB) })

    // Find all EclipseProjects in the B participant
    final BuildIdentity buildB = participantB.toBuildIdentity()
    Iterable<ModelResult<EclipseProject>> eclipseProjectsOfB = CollectionUtils.
        filter(eclipseProjects, new Spec<ModelResult<EclipseProject>>() {
            public boolean isSatisfiedBy(ModelResult<EclipseProject> modelResult) {
                return modelResult.getProjectIdentity().getBuild().equals(buildB)
            }
        })

### Implementation notes

- TBD

### Test coverage

- For a single project/single participant composite, getModels(X) returns a single entry with
    - ProjectIdentity for single project
    - BuildIdentity for single project
- For a N-multi-project/single participant composite, getModels(X) returns N-entries that have
    - ProjectIdentity for each project
    - BuildIdentity for all results are equal
- For a single-project + N-multi-project multi-participant composite, getModels(X) returns (N+1)-entries that have
    - ProjectIdentity for each project
    - BuildIdentity for all results from the multi-project participant are equal
    - BuildIdentity from the single-project participant is not equal to the others
- The same BuildIdentity and ProjectIdentity should be able to correlate across model types
    - Retrieve modelType X and modelType Y
    - ProjectIdentity for modelType-X should be able to locate modelType-Y results

### Documentation

- Update sample to handle complex return type

### Open issues

- n/a
