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
        // Parent of project (e.g., :foo:bar, :foo would be the parent)
        // null if this is a root project
        ProjectIdentity getParent()
        
        // pretty toString
    }

    // NEW: Interface to create identities based on a participant
    interface GradleBuild {
        // Get a BuildIdentity that can be used to correlate all results from a particular build
        BuildIdentity toBuildIdentity()

        // Get a ProjectIdentity that can be used to correlate a result from a particular project
        ProjectIdentity toProjectIdentity(String projectPath)
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

    // NEW: ModelBuilder returned by GradleConnection becomes a ModelBuilder<Map<ProjectIdentity, T>>
    // get() returns a Map<ProjectIdentity, T> 
    // get(ResultHandler) receives a Map<ProjectIdentity, T> 

    Map<ProjectIdentity, EclipseProject> eclipseProjects = connection.getModels(EclipseProject)
    for (Map.Entry<ProjectIdentity, EclipseProject> e : eclipseProjects.entrySet()) {
        // do something with all eclipse projects
    }

    // Participants are A::x, B::y, B::z
    ProjectIdentity projectYofB = participantB.toProjectIdentity(":y")
    eclipseProjects.get(projectYofB) // returns B::y EclipseProject

    // Find all EclipseProjects in the B participant
    final BuildIdentity buildB = participantB.toBuildIdentity()
    Map<ProjectIdentity, EclipseProject> eclipseProjectsOfB = CollectionUtils.
        filter(eclipseProjects, new Spec<Map.Entry<ProjectIdentity, EclipseProject>>() {
            public boolean isSatisfiedBy(Map.Entry<ProjectIdentity, EclipseProject> e) {
                return e.getKey().getBuild().equals(buildB)
            }
        })

### Implementation notes

- TBD

### Test coverage

- For a single project/single participant composite, getModels(X) returns a single entry Map with
    - ProjectIdentity for single project
    - BuildIdentity for single project
    - parent ProjectIdentity is null
- For a N-multi-project/single participant composite, getModels(X) returns a Map with N-entries that have
    - ProjectIdentity for each project
    - BuildIdentity for all results are equal
    - parent ProjectIdentity is the root project (except for the root project)
- For a single-project + N-multi-project multi-participant composite, getModels(X) returns a Map with (N+1)-entries that have
    - ProjectIdentity for each project
    - BuildIdentity for all results from the multi-project participant are equal
    - BuildIdentity from the single-project participant is not equal to the others
    - parent ProjectIdentity is the root project (except for the root project)

### Documentation

- Update sample to handle Map return type

### Open issues

- How do we name participants/builds? By the root project name?
    - Single project build in directory 'A' with rootProject.name = x would have 
        - participant x
        - project : 
        - "composite path" x::
    - Multi-project build in directory 'A' with rootProject.name = x and subprojects a, b, c would have 
        - participant x
        - projects :, :a, :b, :c
        - "composite paths" x::, x::a, x::b, x::c
- Alternatively, using the root project _directory_ name:
    - Single project build in directory 'A' with rootProject.name = x would have 
        - participant A
        - project : 
        - "composite path" A::
    - Multi-project build in directory 'A' with rootProject.name = x and subprojects a, b, c would have 
        - participant A
        - projects :, :a, :b, :c
        - "composite paths" A::, A::a, A::b, A::c