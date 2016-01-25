## Tooling client provides model for "composite" with one multi-project participant

### Overview

- What models are we supporting?
- What about Gradle versions?

### API

### Implementation notes

- Implement `CompositeBuildConnection` on top of existing Tooling API client
- Create `ProjectConnection` instance for the participant
- Delegating all `getModels()` calls to the `ProjectConnection`
    - Optimize for `EclipseProject`: open a single connection and traverse hierarchy
    - Fail for any other model type
- Create a `ModelResult` for each `EclipseProject` instance

### Test coverage

### Documentation

### Open issues
