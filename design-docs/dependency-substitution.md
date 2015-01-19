A paragraph about what the feature is.

# Use cases

What are the use cases we are trying to solve with this feature? That is, why should the user care? What's out of scope?

# Feature: Dependency substitution rules handle project dependencies

## Story: Use dependency substitution rule to replace external dependency with project dependency

Extend the existing dependency substitution mechanism to permit replacing an external dependency with a project dependency.
Note that adding a project dependency in this way will not result in the correct tasks being added to the task execution graph.
This is similar to the current situation when a project dependency is added to a configuration during task execution.

### User visible changes

```
configurations.all {
    resolutionStrategy.eachDependency { DependencyResolveDetails details ->
        assert details.target instanceOf ModuleComponentSelector
        if (details.requested.group == 'org.gradle' && details.requested.name == 'mylib') {
            details.useTarget project: 'foo'
        }
        assert details.target instanceOf ProjectComponentSelector
    }
}
```

- `DependencyResolveDetails.getTarget()` now returns `ComponentSelector`
    - This is a breaking change and should be noted in the release notes.

### Implementation

A description of how we plan to implement this.

A few notes:

- See the poorly named `VersionForcingDependencyToModuleResolver` to see where substitution rules are evaluated
- Will need to change `DependencyMetaData.withRequestedVersion(ModuleVersionSelector)` to take a `ComponentSelector`
    - Probably rename to `withTarget(ComponentSelector)`
    - Will need to change to produce a `ProjectDependencyMetaData` for a `ProjectComponentSelector`
    - Unfortunately, `DependencyMetaData` still requires an Ivy `DependencyDescriptor` under the covers...

### Test coverage

How are we going to test this feature? How do we prove that the work is done?

### Open issues

This section is to keep track of assumptions and things we haven't figured out yet.

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

## Configuration is not re-resolved if not modified after task graph is built

Detect any changes made to a Configuration after resolution, and re-resolve a modified configuration when required.

- After a regular resolution, the configuration should be in an 'immutable' state.
    - Warning emitted on any subsequent mutation
    - Changes made after this point will be ignored
- After initial resolve to compose task graph, set configuration to a 'mutable' state.
    - No warnings on subsequent mutation.
    - When resolved configuration is next required, the configuration is re-resolved.
     
### Open issues

- Permit a user to set a configuration back to 'mutable' state?
- Force the user to explicitly set the configuration to 'mutable' if changes are made during task execution?

## Story: Use dependency substitution rule to replace project dependency with external dependency

- Dependency substitution rules already apply when resolving project dependencies as well as external dependencies.
- `DependencyResolveDetails.getRequested()` should return `ComponentSelector`

# Feature: Improve the dependency substitution rule DSL

## Story: Make the DSL for dependency substitution rules more consistent with component selection rules

### User visible changes

```
configurations.all {
    resolutionStrategy {
        dependencies {
            all { DependencyResolveDetails details -> ... }
            withModule('org.gradle:foo') { DependencyResolveDetails  details -> ... }
        }
    }
}
```

### Open issues

- Provide `ModuleDependencyResolveDetails` and `ProjectDependencyResolveDetails` subtypes that allow declaring rules that apply 
  to only external or project dependencies respectively
     - For these types, `getRequested()` would be typed for the correct `ComponentSelector` subtype.


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

## Dependency reports provide information on dependency substitution

