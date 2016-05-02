## Artifacts for substituted dependencies in a composite are constructed on demand

### Overview

In a composite build, projects dependencies are substituted in place of external dependencies where possible. Previous stories ensure that the resolved dependency graph was constructed correctly for these substitutions. This story adds the ability to obtain the artifacts for a resolved dependency graph, ensuring that tasks are executed to build these artifacts when they are required.

### API

No changes to the public API should be required.

### Implementation notes

### Test Coverage

- External dependency on default configuration replaced with project dependency:
    - Project configuration has single artifact
    - Project configuration has multiple artifacts
    - Project configuration has transitive external dependencies
    - Project configuration has transitive project dependencies
        - Default configuration of secondary project
        - Non-default configuration of secondary project
    - Project configuration has transitive external dependency that is replaced with project dependency
- External dependency replaced with project dependency:
    - Dependency on non-default configuration
    - Dependency with defined classifier
    - Dependency on specified artifact in configuration
    - Dependency has defined exclude rules

- Ensure that dependency artifacts are only built once:
    - A:: <- ('org:b1', 'org:b2') | B::b1 <- ('org:b2')
    - A:: <- ('org:b1', 'org:b2') | B::b1 <- (B::b2)

- Build failures
    - Graceful failure on dependency cycles
        - A:: <- ('org:b1') | B::b1 <- ('org:a')
        - B::b1 <- ('org:b2') | B::b2 <- ('org:b1') // Cycle within same multi-project build
        - A:: <- ('org:b1') | B::b1 <- ('org:c') | C:: <- ('org:a')
    - Depended-on build fails:
        - A:: <- ('org:b1') | B::b1 - execution fails
        - A:: <- ('org:b1') | B::b1 <- ('org:c') | C:: - execution fails

### Documentation

### Open issues

