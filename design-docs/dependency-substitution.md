# Use cases

In order to permit Gradle projects to be assembled in more flexible ways, the difference between an external dependency and a project dependency
needs to be less fixed. By allowing an external dependency to be substituted with a project dependency (and vice-versa) a particular project
can more easily be incorporated into an ad-hoc assembly of projects.

Prezi Pride builds on top of Gradle to make it easy to assemble a multi-project build from separate single project builds. However, these builds
must use a special dependency syntax that can be used to generate an external or project dependency by the Pride plugin, depending on the 
projects included in the 'Pride'. One goal is to make it possible for Pride to be able to include regular single-project Gradle builds into a Pride,
where no special dependency syntax is required.

# Feature: Dependency substitution rules handle project dependencies

## Story: Use dependency substitution rule to substitute external and project dependencies

Extend the existing dependency substitution mechanism to permit replacing an external dependency with a project dependency and vice-versa.
Note that adding a project dependency in this way may not result in the correct tasks being added to the task execution graph, and replacing a project dependency with an external dependency may still result in the project being built when resolving the dependencies.
This is similar to the current situation when a project dependency is added to a configuration during task execution.

### User visible changes

```
interface DependencySubstitution {
    ComponentSelector getRequested()
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
            substitute module("org.gradle:mylib:1.0") with project(":foo")
            substitute project(":bar") with module("org.gradle:another:1.+")
        }
    }
}
```

### Implementation

Introduce a new DSL that allows applying substitution rules on all dependencies, or only on modules or projects.
Ability to substitute a single module or project should be provided as well.

This new DSL supersedes `ResolutionStrategy.eachDependency()`, which in turn is deprecated. `DependencyResolveDetails` is
deprecated, too, and replaced by the `DependencySubstitution` interface family.

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
- Deprecation warnings are shown when `ResolutionStrategy.eachDependency` is used
- New DSL works together with `ResolutionStrategy.force()` and the deprecated `ResolutionStrategy.eachDependency`

### Open issues

- Should generate `Closure` accepting API methods based on `Action` accepting API methods
    - Should also allow RuleSource input, and generate `Action` and `Closure` accepting methods based on `RuleSource` methods
- DSL documentation
    - More usage examples on `DependencySubstitutions`
    - Ensure that new classes are included in DSL reference
- Maybe don't _yet_ deprecate the existing API (and replace in the docs).
    - Depends on release strategy (do we announce in 2.4), and how confident we feel about the new DSL
    - Don't want to encourage users to switch if we think we might change DSL in 2.5
- Some of the userguide examples could use `withModule` for convenience

## Story: Option to re-resolve Configuration when modified after resolution

- Finer-grained tracking of state of Configuration
    - After inclusion in a result: state == 'Observed'
    - After task dependencies calculated: state == 'TaskDependenciesResolved'
    - After complete resolution: state == 'Resolved'
- Detect _all_ changes made to a Configuration after resolution. Changes are of 2 types:
    - Changes to the `Strategy` (how the configuration is resolved): ResolutionStrategy, Caching
    - Changes to the `Content` (actual dependencies included in the configuration): Dependencies, Artifacts
- When in 'Observed' state:
    - Changes to the Strategy are OK
    - Changes to the Content are deprecated
- When in 'TaskDependenciesResolved' state
    - Changes to the Strategy are deprecated
    - Changes to the Content are deprecated
    - Requesting the resolve result without an intervening change: re-use the previous result
    - Requesting the resolve result after an intervening change: recalculate the result
- When in 'Resolved' state
    - Changes to the Strategy are deprecated
    - Changes to the Content will fail
    - Any changes to the strategy after resolve will be ignored
    
### Test coverage

- Warning emitted and change honoured when configuration _content_ is mutated after configuration is 'observed'
    - No warning for changes to configuration _strategy_
- Warning emitted and change honoured when configuration _content_ or _strategy_ is mutated after configuration has it's task dependencies resolved
- Attempting to change the _content_ of a 'resolved' configuration will fail
- Warning emitted and change ignored for change to _strategy_ of a 'resolved' configuration

- When the task dependencies of a Configuration are calculated, and the configuration is resolved without modification
   - Use of `FileCollection` API uses previous resolution result
   - New and existing `ResolvedConfiguration` instances use previous resolution result
   - New and existing `ResolvableDependencies` instances use previous resolution result
- When the task dependencies of a Configuration are calculated, and the configuration is resolved after modification
   - Use of `FileCollection` API forces re-resolve and uses new resolution result
   - Use of new and existing `ResolvedConfiguration` instances forces re-resolve and uses new resolution result
   - Use of new and existing `ResolvableDependencies` instances forces re-resolve and uses new resolution result

## Task graph includes correct tasks for replaced project dependencies

Update the algorithm for building the `TaskExecutionGraph` such that if a project dependency is substituted for an
external dependency, the correct tasks are included for execution:

### User visible changes

- Dependent project _is not built_ when project dependency is replaced with external dependency
- Dependent project _is built_ when external dependency is replaced with project dependency

### Implementation

### Test coverage

- TBD

### Open issues

## Story: IDE plugins include correct set of projects based on dependency substitution

Newly introduced dependency substitutions should work in both IntelliJ and Eclipse. The IDEs
should be able to recognize when an external dependency is replaced with a local project or
vice versa.

### Implementation

No need to add new functionality, this should already work due to the changes in other stories.

### Test coverage

- For both IDEA and Eclipse plugins:
    - external dependency replaced with subproject is shown as project dependency in IDE
    - transitive external dependency replaced with subproject is shown as project dependency in IDE
    - subproject replaced with external dependency is shown as external dependency in IDE

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


