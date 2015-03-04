# Use cases

In order to permit Gradle projects to be assembled in more flexible ways, the difference between an external dependency and a project dependency
needs to be less fixed. By allowing an external dependency to be substituted with a project dependency (and vice-versa) a particular project
can more easily be incorporated into an ad-hoc assembly of projects.

Prezi Pride builds on top of Gradle to make it easy to assemble a multi-project build from separate single project builds. However, these builds
must use a special dependency syntax that can be used to generate an external or project dependency by the Pride plugin, depending on the 
projects included in the 'Pride'. One goal is to make it possible for Pride to be able to include regular single-project Gradle builds into a Pride,
where no special dependency syntax is required.

# Feature: Dependency substitution rules handle project dependencies

## Story: Use dependency substitution rule to replace external dependency with project dependency

Extend the existing dependency substitution mechanism to permit replacing an external dependency with a project dependency.
Note that adding a project dependency in this way will not result in the correct tasks being added to the task execution graph.
This is similar to the current situation when a project dependency is added to a configuration during task execution.

### User visible changes

```
class DependencySubstitution<T extends ComponentSelector> {
    T getRequested()
    void useTarget(Object notation)
}
    
configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            all { DependencySubstitution dependency ->
                if (dependency.requested instanceof ModuleComponentSelector) {
                    if (dependency.requested.group == 'org.gradle' && dependency.requested.name == 'mylib') {
                        dependency.useTarget project(":foo")
                    }
                }
            }
            withModule("org.gradle:mylib") { DependencySubstitution<ModuleComponentSelector> dependency ->
                dependency.useTarget project(":foo")
            }
            withProject(":bar") { DependencySubstitution<ProjectComponentSelector> dependency ->
                dependency.useTarget "org.gradle:another:1.+"
            }
        }
    }
}
```

### Implementation

A description of how we plan to implement this.

A few notes:

- See the poorly named `VersionForcingDependencyToModuleResolver` to see where substitution rules are evaluated
- Will need to change `DependencyMetaData.withRequestedVersion(ModuleVersionSelector)` to take a `ComponentSelector`
    - Probably rename to `withTarget(ComponentSelector)`
    - Will need to change to produce a `ProjectDependencyMetaData` for a `ProjectComponentSelector`
    - Unfortunately, `DependencyMetaData` still requires an Ivy `DependencyDescriptor` under the covers...

### Test coverage

- Resolved graph includes correct (substituted) dependencies
    - Replacement of top-level dependency
    - Replacement of transitive dependency
    - Replacement of Client module dependency
- Interaction with forced versions and other resolve rules
- Conflict resolution where 2 external dependency versions are in graph, but only one version is replaced by project dependency
- Additional dependency details (configuration, transitive, etc) are retained in substituted dependency
- Real resolution of dependency files: pre-build the substituted project. (This test would later be modified to remove the pre-build step).
- Dependency reports provide information on dependency substitution

### Open issues

This section is to keep track of assumptions and things we haven't figured out yet.

## Story: Option to re-resolve Configuration when modified after resolution

- Detect and track _all_ changes made to a Configuration after resolution
- After resolution, the configuration should be in a 'closed' state.
    - Warning emitted on any subsequent mutation
    - Changes made after this point will be ignored
- Provide an internal mechanism, set configuration to a 'mutable' state.
    - No warnings on subsequent mutation.
    - When resolved configuration is next required, the configuration is re-resolved, and state set to 'closed'
    
### Test coverage

- Warning emitted and change ignored when Configuration mutated after resolution:
   - Dependency added/replaced/removed
   - Set of parent configurations changes
   - ResolutionStrategy modified
   - Set of exclude rules changes
   - Calls to `ResolvableDependencies.beforeResolve` and `afterResolve`
   - Any mutation to parent configuration (even if that configuration has not been explicitly resolved)
- When a previously-resolved Configuration is set to a 'mutable' state and not mutated:
   - Use of `FileCollection` API uses previous resolution result
   - New and existing `ResolvedConfiguration` instances use previous resolution result
   - New and existing `ResolvableDependencies` instances use previous resolution result
- When a previously-resolved Configuration is set to a 'mutable' state and is mutated:
   - Use of `FileCollection` API forces re-resolve and uses new resolution result
   - Use of new and existing `ResolvedConfiguration` instances forces re-resolve and uses new resolution result
   - Use of new and existing `ResolvableDependencies` instances forces re-resolve and uses new resolution result

### Open issues

- Permit a user to set a configuration back to 'mutable' state, to avoid warnings and permit re-resolve?

## Task graph includes correct tasks for replaced project dependencies

Update the algorithm for building the `TaskExecutionGraph` such that if a project dependency is substituted for an
external dependency, the correct tasks are included for execution:

### User visible changes

- Dependent project _is not built_ when project dependency is replaced with external dependency
- Dependent project _is built_ when external dependency is replaced with project dependency

### Implementation

- Perform full resolution of task input configurations when building the task graph
- For now, assume that all configurations are modified during task execution, 
  and must be re-resolved when preparing the inputs for a particular task execution

### Test coverage

### Open issues

## Story: Use dependency substitution rule to replace project dependency with external dependency

- Dependency substitution rules already apply when resolving project dependencies as well as external dependencies.
- `DependencyResolveDetails.getRequested()` should return `ComponentSelector`

### Test coverage

- TBD
- Dependency reports provide information on dependency substitution

## Story: IDE plugins include correct set of projects based on dependency substitution

# Feature: Improve the dependency substitution rule DSL

## Story: Declare substitution rules that apply to all resolution for a project

### User visible changes

```
resolution {
    dependencies {
        all { DependencyResolveDetails details -> ... }
        withModule('org.gradle:foo') { DependencyResolveDetails  details -> ... }
    }
}
```

### Open issues

- Should allow component selection rules to be configured here
- Should allow cache timeouts to be configured here


