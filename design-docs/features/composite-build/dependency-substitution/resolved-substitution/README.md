## IDE model has external dependency substituted with project dependency

- Need full metadata for each project publication: artifacts, dependencies, excludes
- Implement real dependency substitution for composite build (all participants must have the same Gradle version)
- Provide a "Composite Context" (containing all project publication information) when requesting `EclipseProject` model from each build
