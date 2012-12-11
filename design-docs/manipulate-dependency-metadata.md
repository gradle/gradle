Manipulate the dependency metadata.

# Primary use case

- A set of inter-dependent libraries, developed, tested and published together are intended to work with a consistent, same version. If they are forming a classpath in a java project and one of the libraries from a set has a different version than others, certain ill symptomps are likely to occurr (compilation errors, test failures, other build-time errors). Some options:

    - detect and blow up (implementable with the current API). Not very convenient because every detected conflict requires user intervention and declaring something in the build (for example a forced version).
    - detect and choose highest requested, consistent versions for all libraries from the releasable unit.
    - allow specifying forced version rule as oppose to specifying the module explicitly as it is now.
		
# Other related use cases

- One of the users developed a plugin that configures all direct dependencies as forced (via configuration.resolutionStrategy.forcedVersions). This apparently gives better conflict resolution results in their environment. Without this setting, the build often fails (compilation error) because the 'latest' conflict resolution strategy picks up incompatible versions of dependencies.
- The current build system uses ivy custom conflict resolvers that prefer the same versions of libraries from the same org / groupId. I would like to migrate to Gradle incrementally.

# Current implementation plan

## Story: allow specifying forced versions by 'rule'

This way libraries from a releasable unit can be forced to use a consistent version.

### User visible changes

- new api methods in the resolutionStrategy type.
- can specify an action that gets executed for each dependency, before resolution.
- Action receives a 'details' object that contains asked module (for example ModuleVersionSelector)
 and provides some way to force the version.
- the current implementation of forcedVersions should use this api
- the api should be designed in the way we can increment to a more robust solution. For example, forced version action can receive an information about current candidates, etc.

### Sad day cases

- contradicting rules
- rule that does not change the target module
- runtime exception when evaluating rule

### Test coverage

- sad day cases
- no op rule
- rule that affects a set of modules
- rule that affects multiple modules
- multiple rules
- ResolutionResult api must consider versions forced by rule
- Decent error message when a rule fails
- Using rule to replace the requested version with a dynamic version
    - project has a direct dependency on `lib-a:1.2`
    - repository contains `lib-a:1.4`
    - use a rule to replace `lib-a:1.2` with `lib-a:1.2+`
    - expect that `lib-a:1.4` is used with selection reason 'changed by action'
- Decent error message when a rule requests a version that does not exist
    - project has a direct dependency on `lib-a:1.2`
    - repository is empty
    - use a rule to replace `lib-a:1.2` with `lib-a:broken`
    - expect an error message that informs the user that `lib-a:broken` could not be resolved

## Story: allow a module version to be blacklisted by 'rule'

### Test coverage

- Using rule to blacklist a version:
    - project has a direct dependency on `lib-a:1.2`
    - project has a transitive dependency on `lib-a:1.3`
    - use a rule to replace `lib-a:1.2` with `lib-a-1.2.1`
    - expect that `lib-a:1.3` is used with selection reason 'conflict resolution'
- Using rule to blacklist a version:
    - project has a direct dependency on `lib-a:1.2`
    - project has a transitive dependency on `lib-a:1.3`
    - use a rule to replace `lib-a:1.2` with `lib-a-1.4`
    - expect that `lib-a:1.4` is used with selection reason 'conflict resolution' and 'changed by action'

## Story: allow custom dynamic versioning scheme

For example, allow `default` to be used as the version in dependency notation, and allow a rule to map this to some version selector.

### Test coverage

- Using a rule to provide a custom version scheme
    - project has a direct dependency on `lib-a:default`
    - use a rule to replace `lib-a:default` with `lib-a:1.2`
    - expect that `lib-a:1.2` is used with selection reason 'changed by action'
- As above, with a transitive dependency instead of a direct dependency.

## Story: allow substitution of group and module

For example, allow `groovy-all` to be replaced with `groovy`, or 'log4j' with 'log4j-over-slf4j'.

- Add a method to `DependencyResolveDetails` that accepts a module version selector notation (same as ResolutionStrategy.force() does) that can replace the (group, module, version).
- Change dependency reporting so that it does not assume that the requested and selected modules are the same.
- Change DependencyGraphBuilder so that does not assume that the requested and resolved modules are the same.

### Test coverage

- Using a rule to substitute the module:
    - project has a dependency on `lib-a:1.2`
    - project has a dependency on `lib-b:2.0`
    - use a rule to replace `lib-a:1.2` with `lib-b:2.1`
    - expect that `lib-a` does not appear in the result
    - expect that `lib-b:2.1` is used with selection reason 'conflict resolution' and 'changed by action'
- As above, and expect that the dependency report shows `lib-a:1.2 -> lib-b:2.0` followed by the dependencies for `lib-b:2.0`.
- As above, and expect that the dependency insight report shows `lib-a:1.2 -> lib-b:2.0` followed by the path to the `lib-a:1.2` dependency.

## Story: declarative substitution of group, module and version

Allow some substitutions to be expressed declaratively, rather than imperatively as a rule.
