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

- For a single project/single participant composite, getModels(X) returns a single entry Map with
    - ProjectIdentity for single project
    - BuildIdentity for single project
- For a N-multi-project/single participant composite, getModels(X) returns a Map with N-entries that have
    - ProjectIdentity for each project
    - BuildIdentity for all results are equal
- For a single-project + N-multi-project multi-participant composite, getModels(X) returns a Map with (N+1)-entries that have
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
