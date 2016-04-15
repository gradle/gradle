## GradleConnection performs validation when building composite

### Overview

Perform validation of the composite on `GradleConnection.build()` where this validation is reasonably cheap to perform.

### Implementation notes

- Fail on modifications to builder (`GradleConnectionBuilder` or `GradleConnectionBuilder.ParticipantBuilder`) after `GradleConnectionBuilder.build()`
- Verify root directories:
    - Duplicate root directory
    - Root directory does not exist
    - Root directory is a file

### Test coverage

- TBD

##### Additional test coverage for composites:
- Multi-project participant with a ‘master’ directory
- Unusual project layouts (overlapping multi-projects, etc)

### Out of Scope

- TBD
