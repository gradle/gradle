## Dependencies report for composite shows external dependencies substituted with project dependencies

### Overview

- No publishing is required in project: resolved using the same algorithm as a project dependency
- Will substitute external dependencies exact match:
    - 'group' matching attribute of project
    - 'module' matching `project.name`
    - any version
- No consideration will be given to explicit configuration of publication coordinates:
    - Via `PomFilter` with 'maven' plugin
    - Via 'maven-publish' or 'ivy-publish' plugins
    - Via other publishing plugins (e.g. 'artifactory-publish')
    - `archivesBaseName`
- No substitution will be performed for external dependency on configuration other than default.

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


##### Tests for GradleConnection API

- When executing 'dependencies' task for a composite using the Tooling API:
    - For composite containing participants using Gradle <= 2.13, no substitutions are performed.
    - For composite containing participants using Gradle >= 2.14, substitutions are performed.

##### Tests for composite build

For these tests, verify dependency substitution directly via the resolved dependency graph.

- Composite of participants with no publications.  Should not blow up, but does not do anything interesting.
- No available substitutions in composite
    - Multi-participant (A, B), single-project participants:
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - org;z:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is _not_ replaced by B::y project dependency.
- Single dependency substitution with single-project builds.
    - Multi-participant (A, B), single-project participants:
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:1.0]
    - org;y:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is replaced by B::y project dependency.
- Multiple dependency substitution with multi-project builds.
    - Multi-participant (A, B), multi-project (x, y):
    - A::x produces [org;ax:1.0]
    - A::y produces [org;ay:1.0]
    - B::x produces [org;bx:1.0]
    - B::y produces [org;by:1.0]
    - org;bx:1.0 <- [org;ax:1.0]
    - org;by:1.0 <- [org;ay:1.0]
    - result: External dependencies in A are replaced by B project dependencies
- Substitution of external dependencies with project dependencies from the same participant
    - Single-participant (A, B), multi-project (x, y):
    - A::x produces [org;x:1.0]
    - A::y produces [org;y:1.0]
    - org;y:1.0 <- [org;x:1.0]
    - result: External dependency in A::x is replaced by A::y project dependency.
- Full dependency metadata is substituted, resulting in different transitive dependencies
    - Multi-participant (A, B), single-project participants
    - A::x produces [org;x:1.0]
    - B::y produces [org;y:2.0]
    - org;z1:1.0, org:z2:1.0 <- org;y:1.0 <- [org;x:1.0]
    - org;z1:2.0, org:z3:1.0 <- [org;y:2.0]
    - result: A::x has dependency on B::y, org:z1:2.0, org:z3:1.0
- Transitive dependencies are substituted by project dependencies too.
    - Multi-participant (A, B, C), single-project participants
    - A::x produces [org;ax:1.0]
    - B::x produces [org;bx:1.0]
    - C::x produces [org;cx:1.0]
    - org;cx:1.0 <- org;bx:1.0 <- [org;ax:1.0]
    - result: A::x has dependency on B::x and C::x.
- Transitive dependencies are replaced by project dependencies when going through external dependency ('y' is not a project).
    - Multi-participant (A, B), single-project participants
    - A::x produces [org;x:1.0]
    - B::z produces [org;z:1.0]
    - org;z:1.0 <- org;y:1.0 <- [org;x:1.0]
    - result: A::x has dependency on B::z.

Failure conditions:

- Failure: External dependency can be replaced by two different projects in multi-project build.
    - Single participant (A, B), multi-project build (x, y):
    - A::x produces [org;x:1.0]
    - A::y produces [org;x:1.0]
    - B::z produces [org;z:1.0]
    - org;x:1.0 <- [org;z:1.0]
    - result: Fail - multiple projects can satisfy dependency
- Failure: External dependency can be replaced by two different participants in multi-participant build.
    - Multi-participant (A, B, C), single-project participants:
    - A::x produces [org;x:1.0]
    - B::x produces [org;x:2.0]
    - C::y produces [org;y:1.0]
    - org;x:1.0 <- [org;y:1.0]
    - result: Fail - multiple participants can satisfy dependency
- Dependency cycle within multi-project build
- Dependency cycle within multi-participant composite
- Composite containing participants with same root directory name
- One of the participants has no configurations defined
- One of the participants cannot be configured

Interaction with dependency rules:

- Dependency resolve rule switches in external dependency, that is then substituted in composite
    - Multi participant (A, B), sing-project build:
    - A produces [org;x:1.0]
    - B produces [org;y:1.0]
    - org;z:1.0 <- [org;x:1.0]
    - Dependency resolve rule replaces org;z:1.0 with org;y:1.0
    - result: External dependency in A::x is replaced by B::y project dependency.
- Dependency substitution rule replaces one external dependency for another, that is then substituted in composite
    - Should dependency substitution rule win over composite substitution?
- Dependency substitution rule replaces a project dependency with an external dependency, that is then substituted in composite
    - Should dependency substitution rule win over composite substitution?

Other improvements:

- Test selection reason for substitution (should be a new 'reason' that mentions composite build)
- Test cancellation of the builds that create the composite context

### Documentation

- Need to describe this behavior in context of overall composite feature.

### Open issues

- Consideration of publication config for project to determine what should be replaced
- Consider `archivesBaseName`
- Dependency on non-default configuration
- Dependency with classifier or artifact specified
- Should participant project dependencies show up in `ComponentSelectionRules`?
- Could determine the artifactId of a project using the [same mechanism as used for publishing project dependencies](https://github.com/gradle/gradle/blob/master/subprojects/maven/src/main/groovy/org/gradle/api/publication/maven/internal/pom/ProjectDependencyArtifactIdExtractorHack.java#L52-L69).
    - Will attempt to use, in order:
        - `MavenDeployer` configuration
        - `archivesBaseName`
        - `project.name`
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
- Provide way of detecting feature set of composite build?
- Automatically enable integrated composite for suitable participants
    - Consider JVM args and java home
    - Consider version of Gradle configured
    - Possibly permit override to force composite

