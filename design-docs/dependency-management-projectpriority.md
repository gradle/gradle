# Use case

Gradle normally handles version conflicts by picking the highest version of the module.

In rare cases this may result in a project dependency being replaced by a module dependency, which
may be counter-intuitive.

The actual use case involved a a multi-repo system, where developers could choose a subset
of projects to work on, but here is a simple case:

We have 3 projects: `common`, `moduleA`, `moduleB`

* `moduleA` has a dependency on `common`
* `moduleB` depends on both `common` and `moduleA`
* `settings.gradle` only includes `common` and `moduleB`. `moduleA` is being pulled from a repository.

What happens:

1. `moduleB` depends on `common` directly and also transitively through `moduleA`.
2. The version of `common` referenced by `moduleA` happens to be higher.
3. The higher version of `common` replaces the direct project dependency.

# The change

The proposed change will give a higher priority project dependencies, before version comparison even starts.
In other words prioritize by type first and by version second.

To that extent a new class `ProjectDependencyForcingResolver` is introduced. It implements `ModuleConflictResolver`,
and is chained in front of `LatestModuleConflictResolver` by `DefaultArtifactDependencyResolver`.

# Unit tests

A change to `DependencySubstitutionRulesIntegrationTest` is made to account for the new behavior.

A new test is added to `DependencyResolveRulesIntegrationTest` in order to test the various permutations of
versions, and forced dependencies settings.
