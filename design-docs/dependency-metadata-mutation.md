Manipulate the dependency metadata.

# Use cases

- A set of inter-dependent libraries, developed, tested and published together are intended to work with a consistent, same version. If they are forming a classpath in a java project and one of the libraries from a set has a different version than others, certain ill symptomps are likely to occurr (compilation errors, test failures, other build-time errors).
- One of the users developed a plugin that configures all direct dependencies as forced (via configuration.resolutionStrategy.forcedVersions). This apparently gives better conflict resolution results in their environment. Without this setting, the build often fails (compilation error) because the 'latest' conflict resolution strategy picks up incompatible versions of dependencies.
- The current build system uses ivy custom conflict resolvers that prefer the same versions of libraries from the same org / groupId. I would like to migrate to Gradle incrementally.

# Implementation plan

- model a set of libraries as a releasable unit with 'must-use' kind of dependencies.
- explore richer model of dependency declaration that the publisher can use so that the consumers can understand the 'releasable unit' and 'must-use' kind of dependencies. Custom dependency metadata format is most likely needed.
- client meta-data mutation - we might start from the consumer, this way:
		- we can work with legacy dependencies (not easily releasable with updated metadata)
		- we can work with libraries released by other build systems
		- we may defer investments into custom dependency metadata format