## IDE models have external dependencies directly replaced with dependencies on composite project publications

- Naive implementation of dependency substitution: metadata is not considered
- Can use the `ProjectPublications` instance for every `EclipseProject` in the composite
- Adapt the returned set of `EclipseProject` instances by replacing Classpath entries with project dependencies
