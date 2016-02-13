## IDE models have external dependencies directly replaced with dependencies on composite project publications

### Overview

- Naive implementation of dependency substitution: metadata is not considered
- Can use the `ProjectPublications` instance for every `EclipseProject` in the composite
- Adapt the returned set of `EclipseProject` instances by replacing Classpath entries with project dependencies
- Matching is done only on group and name (version numbers are ignored).

### API

No user facing API changes.

### Implementation notes

- tbd

### Test Coverage

**Notations**:

    Capital letters are identifiers for participants (think of it as the root project directory path).
    A::x - project x in participant A.
    org;x:1.0 - external dependency with group org, artifactId x, version 1.0
    [org;x:1.0] - publication of org;x:1.0
    org;y:1.0 <- [org;x:1.0] - org;x:1.0 has dependency on external artifact org;y:1.0
    project(A::y) <- [org;x:1.0] - org;x:1.0 has dependency on project ':y' in A

- Test composite of participants with no publications.  Shouldn't blow up, but doesn't do anything interesting.
- For participants using <Gradle 2.12, substitutions are not performed.
    - (Gradle 1.0-2.11) A::x publishes [org;x:1.0] 
    - (Gradle 1.8-2.12) B::y publishes [org;y:1.0] 
    - org;y:1.0 <- [org;x:1.0]
    - result: No substitutions are made.
- For participants using <Gradle 1.8, publications are not available.
    - (Gradle 2.12) A::x publishes [org;x:1.0] 
    - (Gradle 1.8-2.12) B::y publishes [org;y:1.0] 
    - org;y:1.0 <- [org;x:1.0]
    - result: A::x has dependency on B::y.
    - (Gradle 2.12) A::x publishes [org;x:1.0] 
    - (Gradle 1.0-1.7) B::y publishes [org;y:1.0] 
    - org;y:1.0 <- [org;x:1.0]
    - result: No substitutions are made.
- For the tests below, when an external dependency is replaced by a project dependency, check that the project dependency is visible when walking the hierarchy as well.
- For a single participant (A), multi-project build (x, y), where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:1.0]
    - org;y:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is _not_ replaced by A::y project dependency.
    - situation: Multi-project that uses external artifacts instead of project dependencies. We shouldn't replace external dependencies with project dependencies when the projects come from the same participant.
- For a multi-participant (A, B), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - org;y:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is replaced by B::y project dependency.
    - situation: Simple case of one participant replacing external dependencies in another.
- For a multi-participant (A, B), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::y produces [org;:y:1.0]
    - org;z:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is _not_ replaced by B::y project dependency.
    - situation: Simple case of no substitutions performed.
- For a single participant (A), multi-project build (x, y), where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:1.0]
    - org;y:1.0 <- [org;x:1.0]
    - Dependency substitution rule replaces org;y with project(A::y)
    - result: External dependency in A::x is replaced by A::y project dependency.
    - situation: Eclipse project substitution with dependency substitution. (may be covered by existing coverage)
- For a single participant (A), multi-project build (x, y), where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;x:1.0]
    - result: Fail? due to conflicting publications
    - situation: Multi-project that produces the same publications in two projects. How would we decide which publication wins?
- For a multi-participant (A, B), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::x produces [org;x:2.0]
    - result: Fail? due to conflicting publications
    - situation: Multiple participants that produce the same publications. How would we decide which publication wins?
- For a multi-participant (A, B), multi-project participants, where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:1.0]
    - B::y produces [org;y:1.0]
    - project(A::y) <- [org;x:1.0]
    - result: Fail? due to conflicting publications
    - situation: Multiple participants that produce the same publications, but one participant already has a project dependency. How would we decide which publication wins?
- For a multi-participant (A, B), multi-project participants, where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:1.0]
    - B::z produces [org;z:1.0]
    - org;z:1.0 <- [org;x:1.0]
    - Dependency substitution rule in A::x replaces org;z:1.0 with project(A::y)
    - result: A::x has dependency on A::y
    - situation: One participant replaces the dependency in another, but that dependency has already been replaced by a "local" project (may be moot if dependency substitution happens earlier).
- For a multi-participant (A, B, C), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - C::y produces [oldorg;y:1.0]
    - oldorg;y:1.0 <- [org;x:1.0]
    - Dependency substitution rule in A::x replaces oldorg;y:1.0 with org;y:1.0
    - result: A::x has dependency on B::y
    - situation: One participant has external dependency that has been substituted for another. Both old and new dependencies could be replaced (maybe unnecessary, since this should look like 'simple' case with substitution happening before we need to replace anything).
- For a multi-participant (A, B), multi-project participants, where 
    - A::x produces [org;:x:1.0]
    - A::y produces [org;:y:1.0]
    - B::x produces [org;:x:1.0]
    - B::y produces [org;:y:1.0]
    - project(A::y) <- [org;:x:1.0]
    - project(B::y) <- [org;:x:1.0]
    - result: A::x has dependency on A::y.  B::x has dependency on B::y.
    - situation: no substitutions with multiple participants/multi-project.
- For a multi-participant (A, B), multi-project participants, where 
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - B::z produces [org;z:1.0]
    - org;z:1.0 <- org;y:1.0 <- [org;x:1.0]
    - project(B::z) <- [org;y:1.0]
    - result: A::x has dependency on B::y and B::z.
    - situation: Transitive dependencies are replaced by project dependencies too.
- For a multi-participant (A, B), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::z produces [org;z:1.0]
    - org;z:1.0 <- org;y:1.0 <- [org;x:1.0]
    - result: A::x has dependency on B::z.
    - situation: Transitive dependencies are replaced by project dependencies when going through external dependency ('y' is not a project).
- For a multi-participant (A, B), multi-project participants, where 
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:2.0]
    - B::z produces [org;z:1.0]
    - org;y:2.0 <- org;z:2.0 <- [org;x:1.0]
    - org;y:1.0 <- [org;z:1.0]
    - result: A::x has dependency on B::z.  B::z has dependency on A::y.  Both versions of 'y' are on classpath.
    - Real implementation would need to also have A::x have a dependency on A::y.
    - situation: One participant publishes two artifacts, but the participant does not use project dependencies. Through an external dependency, one project has a dependency on the other.
- For a multi-participant (A, B), single-project participants, where 
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - org;z:1.0 <- org;y:1.0 <- [org;x:1.0]
    - org;z:2.0 <- [org;y:1.0]
    - result: A::x has dependency on B::y. B::y has dependency on org;z:2.0. A::x still has dependency for org;z:1.0. 
    - We do not want to maintain this behavior -- mark as @NotYetImplemented with correct behavior?
    - situation: Project dependencies in one participant would change the resolved dependencies in the other participant. For now, we're doing simple replacement, so this doesn't happen.
- Performance tests:
    - Compare multi-project with multi-participant equivalents:
        - A multi-project build with a "large" number of subprojects with dependencies between each other.
        - A multi-participant build with single-projects with dependencies matching the project above.
        - Do we generate the EclipseProject model with a composite faster or the same as a multi-project build?
    - Compare impact of substitution
        - A multi-participant with multi-project builds with a deep chain of dependencies.
        - A::(a..m) where a depends on b depends on c...
        - B::(n..z) where n depends on o depends on p...
        - For scenario 1, composite substitution is disabled, so the participants generated eclipse projects are independent.
        - For scenario 2, composite substitution is enabled, so the participants generated eclipse projects should have a long chain of dependencies (a depends on b ... y depends on z).

### Documentation

- Need to describe this behavior in context of overall composite feature.

### Open issues

- Should tests prefer one form of publication plugins over the other?
- If we publish [org;x:1.0] via ivy and maven in one project, is that one publication or two?
- Handling multiple publications from one project with different classifiers (e.g., a jar and a war) or different artifact names?  How do we map from publication -> eclipse project?
- Should participant project dependencies show up in `ComponentSelectionRules`?
- Should this consider version numbers (even in the naïve implementation)?

