## Tooling client can correlate model instances to projects in a composite

### Overview

- TBD

### API

    interface BuildIdentity {
        String getDisplayName()
    }

    interface ProjectIdentity {
        BuildIdentity getBuild()

        ProjectIdentity getParent()
        
        String getDisplayName()
    }

    GradleConnection connection = GradleConnector.newGradleCompositeBuilder().
        addBuild(participantA).
        addBuild(participantB).
        build()

    // ModelBuilder returned by GradleConnection becomes a ModelBuilder<Map<ProjectIdentifier, T>>

    Map<ProjectIdentity, EclipseProject> eclipseProjects = connection.getModels(EclipseProject)
    for (Map.Entry<ProjectIdentity, EclipseProject> e : eclipseProjects.entrySet()) {
        
    }

### Implementation notes

- TBD

### Test coverage

- TBD

### Documentation

### Open issues
