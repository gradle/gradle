## IDE models for composite have external dependencies substituted with project dependencies

### Overview

Tooling models requested from an 'integrated' composite build should include correct model references based on dependency substitution in the composite.

For an external dependency that is substituted with a project dependency in a composite:

- `EclipseProject.projectDependencies` should contain an entry for the dependency, referencing the correct target `EclipseProject`.
- `EclipseProject.classpath` should _not_ contain an entry for the dependency.
- `IdeaModule.dependencies` should contain an `IdeaModuleDependency` for the dependency.

### API

No API changes required.

### Implementation notes

### Test Coverage

All tests should verify both IDEA and Eclipse models.

- First-level external dependency is replaced by composite project dependency.
- Transitive external dependency is replaced by composite project dependency.
- Composite dependency substitution results in a dependency cycle between projects.

- Composite containing participants with same root directory name.
- Failure to configure one of the participants.
- Ambiguous project substitution.

### Documentation

### Open issues

