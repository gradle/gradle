# Use cases

Gradle normally handles version conflicts by picking the highest version of the module.

In rare cases this may result in a project dependency being replaced by a module dependency, which
may be counter-intuitive.

Other use cases depend on no priority being given to project dependencies.

## Use case 1 (project priority required)

The actual use case involves a multi-repo system, where developers could choose a subset
of projects to work on, but here is a simple case:

We have 3 projects: `common`, `moduleA`, `moduleB`

* `moduleA` has a dependency on `common 1.1`
* `moduleB` depends on both `common` and `moduleA`
* `settings.gradle` only includes `common` and `moduleB`. `moduleA` is being pulled from a repository.
* The local version of `common` is `1.0`

What happens by default:
* For `moduleB` external dependency `common 1.1` replaces the direct project dependency.

What would happen with `preferProjectModules = true`:
* Project dependency will be picked over the external dependency.

## Use case 2 (project priority should be off)

We need to do performance testing to ensure that the current version of the code
isn't significantly slower than another version.

To that end we declare a dependency that references that other version, and we don't
want the current codebase to substitute it.

# The change

The proposed change adds an option to enforce higher priority of project dependencies, before version comparison even starts.
In other words prioritize by type first and by version second.

To that extent a new class `ProjectDependencyForcingResolver` is introduced. It implements `ModuleConflictResolver`,
and is chained in front of `LatestModuleConflictResolver` by `DefaultArtifactDependencyResolver`.

There is also a new method `ResolutionStrategy.preferProjectModules`, which allows toggling between project priority and version-only
conflict resolution.

# Test cases

`ProjectDependencyPreferenceIntegrationTest`, is added in order to test the various permutations of
versions, forced dependencies and `preferProjectModules` settings.
