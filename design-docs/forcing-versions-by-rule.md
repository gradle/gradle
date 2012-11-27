Manipulate the dependency metadata.

# Primary use case

- A set of inter-dependent libraries, developed, tested and published together are intended to work with a consistent, same version. If they are forming a classpath in a java project and one of the libraries from a set has a different version than others, certain ill symptomps are likely to occurr (compilation errors, test failures, other build-time errors). Some options:
		- detect and blow up (implementable with the current API). Not very convenient because every detected conflict requires user intervention and declaring something in the build (for example a forced version).
		- detect and choose highest requested, consistent versions for all libraries from the releasable unit.
		- allow specifying forced version rule as oppose to specyfing the module explicitly as it is now.
		
# Other related use cases

- One of the users developed a plugin that configures all direct dependencies as forced (via configuration.resolutionStrategy.forcedVersions). This apparently gives better conflict resolution results in their environment. Without this setting, the build often fails (compilation error) because the 'latest' conflict resolution strategy picks up incompatible versions of dependencies.
- The current build system uses ivy custom conflict resolvers that prefer the same versions of libraries from the same org / groupId. I would like to migrate to Gradle incrementally.

# Current implementation plan

Story: allow specifying forced versions by 'rule'				

This way libraries from a releasable unit can be forced to use a consistent version.

## User visible changes

- new api methods in the resolutionStrategy type.
- can specify the forced versions by passing an Action object
- Action receives some context object that contains asked module (for example ModuleVersionSelector) and something kind of target module (for example ConfigurableModuleVersionSelector).
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