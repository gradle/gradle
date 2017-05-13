# Java annotation processing improvements

This is a proposal to improve Gradle's modeling and handling of annotation processors.

## Use case

* A user wants to configure their IDE (Eclipse or IntelliJ) to run annotation processors fetched from a repository.

## Implementation plan

1. Add a `processorpath` property to CompileOptions (like the existing `sourcepath`)
2. Add a `processorpath` property to SourceSet (like existing `compileClasspath`)
3. Update the Java plugin to wire #2 to #1
4. Create default/conventional dependency configurations that are wired to #3
5. Update the Eclipse plugin to generate `.factorypath` in addition to `.classpath` and update `.prefs` files
6. Update the IntelliJ plugin suitably
