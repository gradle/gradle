This document describes use cases often associated with Maven "provided" scope. This is also meant to address [GRADLE-784](https://issues.gradle.org/browse/GRADLE-784).
While the Maven "provided" behavior is well known, the uses cases it addresses are a bit muddled. Our intention is to break down the use cases and potentially provide
separate solutions for each.

# Stories

## User can declare compile only dependencies for a source set

### Description

User can declare one or more dependencies to be used during source compilation only. That is to say that these are local, non-exported dependencies that are either not needed at
runtime or potentially provided by some other dependency at runtime. These dependencies will not be included on the project's runtime classpath nor inherited by any consuming
projects. This means should these dependencies leak through the project's API that consumers will need to explicitly declare the dependency. For dependencies that require an
implementation at runtime the consuming project (or deployment) will need to explicitly provide that implementation.

Compile only dependencies should be made visible to the IDE (as they are needed for the IDE to compile). Publications should either omit compile only dependencies from published
metadata or include them in such a way as they are non-transitive and ignored by consuming projects.

    dependencies {
        compileOnly 'javax.servlet:servlet-api:2.5'
    }

When using the 'war' plugin, 'compileOnly' behaves similarly to 'providedCompile'. Compile only dependencies should not be included in the packaged WAR file.

A source set's compile classpath will no longer be accurately described by `configurations.compile`. For getting the compile classpath of a given source set
`SourceSet.getCompileClasspath()` should be used instead. Additionally, the resolved graph of dependencies used at compile time should be accessible via
`SourceSet.getCompileDependencies()`.

### Implementation

* Introduce a new configuration for each `SourceSet` named 'compileOnly'.
* The 'compileOnly' configuration should extend from 'compile'. The 'runtime' configuration will continue to extend from 'compile'.
* `SourceSet.compileClasspath` should now become `configurations.compileOnly`.
* Add method `getCompileDependencies()` to `SourceSet` which returns a `ResolutionResult` representing resolve dependency graph for compile time dependencies.
* Compile only dependencies should be visible to IDEs.
    * For IntelliJ this means mapping to 'provided' scope
    * For Eclipse this means not exporting 'compileOnly' dependencies
* Maven publishing should ignore 'compileOnly' dependencies (potentially address this in another story?)
* Since Ivy publishing includes all configurations by default 'compileOnly' should be marked private and 'runtime' should *not* extend from it

### Test Cases

* Can compile source against a 'compileOnly' dependency
* A 'compileOnly' dependency is not available on runtime classpath
* Compile classpath, include 'compileOnly' dependencies, are queryable via `SourceSet.compileClasspath`
* Compile dependency graph is queryable via `SourceSet.compileDependencies`
* Conflicts between 'compile' and 'compileOnly' dependencies are resolved the same as conflicts between 'compile' and 'runtime'
* Declaring a dependency on a project with 'compileOnly' dependencies does not include 'compileOnly' dependencies
* When using 'java' plugin, 'main' sourceset 'compileOnly' dependencies are *not* available on the test compile classpath
* When using 'war' plugin, 'compileOnly' dependencies are not included in WAR file.
* 'compileOnly' dependencies should not be included in EAR (using 'ear' plugin) or application distribution (using 'application' plugin)
* 'compileOnly' dependencies mapped to 'provided' scope in IDEA model
* The 'eclipse' plugin includes 'compileOnly' dependencies on project classpath
* The 'eclipse' plugin does not include 'compileOnly' dependencies on dependent projects' classpath
* Both 'maven' and 'maven-publish' plugins ignore 'compileOnly' dependencies
* The old and new Ivy publishing include 'compileOnly' as a _private_ configuration not inherited by any other public configuration
