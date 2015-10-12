This spec describes changes to Gradle to support and take advantage of the major features introduced by Java 9.

# Milestone 1: Assist teams migrating to a modular architecture

The Java module system is a deep, disruptive change to the Java ecosystem. The following Gradle features will help
teams migrate to a modular architecture running on the modular JVM, so that the migration can be made in a controlled and
incremental way.

Useful links:

- [Jigsaw overview](http://openjdk.java.net/projects/jigsaw/doc/quickstart.html)
- [Explanation of source layout for Jigsaw](http://openjdk.java.net/projects/jigsaw/doc/ModulesAndJavac.pdf)
- [The module descriptor syntax](http://cr.openjdk.java.net/~mr/jigsaw/spec/lang-vm.html)

# Feature: Java library author declares library API

Given a description of the API of a library, Gradle will prevent at compile time the consumers of a library from using classes that are not
part of the API of the library.
This is intended to help teams materialize and describe the APIs of and dependencies between the various components of their software
stack, and enforce the boundaries between them, ready for the Java module system.

No runtime enforcement will be done - this is the job of the modular JVM. Gradle will simply approximate the behaviour of the
modular JVM at compile time, but in a way that should be sufficient for many teams to make significant progress towards a modular
architecture.

## Story: Java library author declares packages that make up the API of the library

- Add a DSL to declare packages of the API. Proposed DSL:

```
model {
    main(JvmLibrarySpec) {
        api {
            exports 'com.acme'
        }
    }
}
```

- Default to all packages: if no `api` section is found, we assume that all public elements of the library are exported.
- This story is about implementing the DSL, not use it.
- It is not expected to see the public API as part of the model report.

### Test cases

- Can have several `exports` clauses
- Multiple `exports` clauses do not overwrite themselves
- Can omit the `api` section
- Validates that the package string is valid with regards to the JVM specs

## Story: Generate an API jar using the public API declaration

- All JVM libraries should produce an API and an implementation jar.
- Build a jar containing those packages from compiled classes for each variant of the library.
- Not used yet by consumers, this story is simply to produce the API artifact.

### Implementation details

For this story, it is expected that the API jar is built after the implementation jar. It is not
in the scope of this story to make the API jar buildable without building the implementation jar.
Therefore, it is acceptable that the API jar task depends on the implementation jar if it helps.

### Test cases

- API jar can be built for each variant of the library and contains only API classes.
- `assemble` task builds the API jar and implementation jar for each buildable variant.
- API jar contains exported packages
- API jar does not contain non exported packages
- If no API section is declared, API jar and implementation jars are identical (same contents)
- Building the API jar should trigger compilation of classes
- Changes to the specification of the public APIs should not trigger recompilation of the classes
- Changes to the specification of the public APIs should not trigger repackaging of the implementation jar
- Changes to the specification of the public APIs should trigger regeneration of the API jar
- If the specification of the public APIs do not change, API jar is not rebuilt

## Story: Extract a buildable element to represent the compiled classes of the library variant

The next 3 stories form a larger effort to decouple the generation of the API jar from the generation of the implementation jar.
A new binary spec type should likely be introduced, supporting only classes found in a directory.
Replace the configuration of compile tasks from `JarBinarySpec` to the new binary type.
Compose `JarBinarySpec` with that new specification.

The idea is to move from this model:

<img src="img/classesspec-before.png"/>

to something closer to:

<img src="img/classesspec-after.png"/>


The first step involves the creation of a buildable element to represent the compiled classes of a variant.

### Implementation

- Add a `JvmClassesSpec` buildable element type.
- Add `classes` property to `JvmBinarySpec` that points to an instance of `JvmClassesSpec`.
- Remove `resourcesDir` from `JvmBinarySpec`. Generate resources into the classes dir.
- With `JvmClassesSpec`:
    - Move `classesDir` from `JvmBinarySpec`.
    - Add `inputs` property, a set of `LanguageSourceSet`. Defaults to library variant's input source sets.
    - Build task for this element should compile input sources to classes and process input resources.
    - Replace the Play plugin's `JvmClasses` with this.

### Test cases

- Can compile the classes of a component without having to build the jar
- Building the jar should depend on the classes
- Components report should show details of the `classes` element for a library variant

## Story: Extract a buildable element to represent the implementation Jar of the library variant

The second step in order to separate API jars from implementation jars involves the creation of a separate
buildable element to represent the implementation Jar of a variant.

### Implementation

- Add a `JarSpec` buildable element type.
- Add `jar` property to `JarBinarySpec` that points to an instance of `JarSpec`.
- With `JarSpec`:
    - Move `jarFile` from `JarBinarySpec`.
    - Add `inputs` property, a set of `JvmClassesSpec`. Defaults to the `classes` of the library variant.
    - Build task for this element should build the runtime Jar from input classes/resources.

### Test cases

- Components report should show details of the `jar` element for a library variant.

## Story: Add a buildable element to represent the API of the library variant

The last step of separating API from implementation involves the creation of a buildable element to represent
the API of a library. Once this story is implemented it must be possible to reuse the same directory of classes
for building both the API and the implementation jars of a variant.

### Implementation

- Add `api` property that points to an instance of `JarSpec`.
- Inputs should be the `classes` of the library variant.
- Build task for this element should build the API Jar.
- Components report should show details of the `api` element for a library variant.
- Rename `JarBinarySpec` to `JvmLibraryVariantSpec`.

### Test cases

- Building the API jar should not depend on the implementation jar
- Building the implementation jar should not depend on the API jar
- Building the implementation jar and the API jar should depend on the same compilation tasks

## Story: Implementation classes of library are not visible when compiling consuming Java source

- When compiling Java source against a library use the API jar of the selected variant.
- Applies to local libraries only. A later story adds support for libraries from a binary repository.

### Test cases

- Compilation fails when consuming source references an implementation class.
- Consuming source is not recompiled when implementation class does not change.
- Consuming source is recompiled when API class is changed.
- Consuming source is not recompiled when implementation class changes in an incompatible way.

## Story: Allow generation of public API jars

Given a set of source classes and a list of packages, generate a jar that only contains the public members
of the source classes that belong to those packages.A stub class contains only the public members of the source class required at compile time.

### Implementation

- Should take `.class` files as input, **not** source files
- Process classes using the ASM library
- Method bodies should throw an `UnsupportedOperationException`.
- Should consider `java.*`, `javax.*` as allowed packages, considering they will map later to the `java-base` module.
- Conversion of the implementation to an API jar should be done through a task that takes a classes directory as input and will output another class directory.

### Test cases

- Output contains:
    - public or protected elements, including nested classes
    - annotations (we don't need to deal with source-retention annotations because they are not present in the original binary)
- Output must not contain:
    - debug attributes
    - source location annotations
    - package private classes
- Trying to call a method of the API jar at runtime throws `UnsupportedOperationException`
- Public constant types should be initialized to `null` or their default JVM value if of a primitive type (do not use `UnsupportedOperationException` here because it would imply the
creation of a static initializer that we want to avoid).
- Java bytecode compatibility level of the classes must be the same as the original class compatibility level
- Throws an error if a public member references a class which is not part of the public API. For example:
    Given:
    ```
    package p1;
    public class A {
       public B foo()
    }
    ```
    and:
    ```
    package p2;
    public class B {
    }
    ```
    Then if only `p1` is declared as the public API package, `foo` violates the contract and we should throw an error.

### Out of scope

This doesn't have to use the `PublicAPISpec` or whatever it is called, if it is not available when work
on this story is started: a list of packages is enough.

## Story: Consuming Java source is not recompiled when API of library has not changed

AKA API classes can reference implementation classes of the same library.

- Replace the public API jar generator input from a list of packages to the public API specification class
- Generate stub classes in the API jar using the public API jar generator.
- Generate stub API jar for all libraries, regardless of whether the library declares its API or not (a library always has an API).
- Generation task should be incremental.

### Test cases

- A method body in an API class can reference implementation classes of the same library.
- A private method signature can reference implementation classes of the same library.
- Consuming source is not recompiled when API method body of is changed.
- Consuming source is not recompiled when package private class is added to API.
- Consuming source is not recompiled when comment is changed in API source file.
- Consuming source is recompiled when signature of public API method is changed.

## Story: Java library API references the APIs of other libraries

- Extend dependency DSL to allow a dependency of a library to be exported.
- When library A is exported by library B, then the API of B includes the API of A, and so the API of both A and B is visible to consumers at compile time.
- Resolve compile time graph transitively.
- A library may have no API classes of its own, and may simply export other libraries. For example, some library that implements an API defined somewhere else.
- A library must have a non-empty API (otherwise it is not a library - it is some other kind of component)

TBD - Add a dependency set at the component level, to be used as the default for all its source sets.

### Test cases

- Consuming source can use API class that is transitively included in the compile time dependency graph.
- Consuming source cannot use implementation class of library that is transitively included in the compile time dependency graph.
- A library may include no API classes.

## Story: Dependencies report shows compile time dependency graph of a Java library

- Dependency report shows all JVM components for the project, and the resolved compile time graphs for each variant.

## Backlog

- Validate dependencies of API classes at build time to verify all API dependencies are exported.
- Complain about exported dependencies that are not referenced by the API.
- Show details of API binary in component report.
- Generate stub API jar for Groovy and Scala libraries, use for compilation.
- Discovery of annotation processor implementations.

# Feature: Java library is compiled against the API of Java libraries in binary repository

## Story: Java library sources are compiled against library Jar resolved from Maven repository

- Extend the dependency DSL to reference external libraries:

```
model {
    components {
        main(JvmLibrarySpec) {
            dependencies {
                library group: 'com.acme', name: 'artifact', version: '1.0'
                library 'com.acme:artifact:1.0'
            }
        }
    }
}
```

TODO: Need a better DSL.

- Reuse existing repositories DSL, bridging into model space.
- Main Jar artifact of maven module is included in compile classpath.
- Main Jar artifact of any compile-scoped dependencies are included transitively in the compile classpath.

### Test cases

- For maven module dependencies
    - Main Jar artifact of module is included in compile classpath.
    - Main Jar artifact of compile-scoped transitive dependencies are included in the compile classpath.
    - Artifacts from runtime-scoped (and other scoped) transitive dependencies are _not_ included in the compile classpath.
- For local component dependencies:
    - Artifacts from transitive external dependencies that are non part of component API are _not_ included in the compile classpath.
- Displays a reasonable error message if the external dependency cannot be found in a declared repository

### Open issues

- Should we use a single `dependencies` block to define API and compile dependencies, or use 2 separate `dependencies` blocks?
- Need to provide support for `ResolutionStrategy`: forced versions, dependency substitution, etc

## Story: Resolve external dependencies from Ivy repositories

- Use artifacts and dependencies from some conventional configuration (eg `compile`, or `default` if not present) an for Ivy module.

## Story: Generate an API jar for external dependencies

- Generate API stub for external Jar and use this for compilation. Cache the generated API jar.
- Verify library is not recompiled when API of external library has not changed (eg method body change, add private element).
- Dependencies report shows external libraries in compile time dependency graph.

### Implementation

Should reuse the "stub generator" that is used to create an API jar for local projects.

### Test cases

- Stubs only contain public members of the external dependency
- Trying to use a stub at runtime should throw an `UnsupportedOperationException`

## Feature: Development team migrates Java library to Java 9

Allow a Java library to build for Java 9, and produce both modular and non-modular variants that can be used by different consumers.

## Story: Build author declares installed Java toolchain

- Add mechanism to declare Java toolchain resolvers.
- Add resolver that uses an specified install dir to locate toolchain.
- Resolver probes the version of the installed toolchain (reusing existing logic to do this).
- Implementation forks `javac`
- To compile Java source, select the closest compatible toolchain to compile the variant.

TBD - alternatively, select the toolchain with the highest version and use bootstrap classpath or `-release` to cross compile.
TBD - fail or warn when source code is not compiled against exactly the target Java API. Currently, toolchain selection is lenient.

## Story: Modular Java library is compiled using modular Java 9

- Install modular Java 9 on CI build VMs.
- Sample and test coverage for building modular library.

## Story: Modular consumer is compiled against Java library modular Jar

- When building for Java 9, produce a single modular Jar artifact instead of separate API and implementation jars.
- Use this when compiling consuming modular source.

## Story: Build non-modular variant of Java library

- Include `module-info.jar` when building for modular Java 9, exclude when not.
- Add conventional source sets or naming scheme for version specific source files.

### Test cases

- Java 9 specific source files in conventional location are not compiled when building for Java 8.
- Modular consumer compiles against modular Jar.
- Non-modular consumer should use non-modular API Jar.

## Backlog

- Generate the module descriptor from the Gradle model.
- Use Java 9 `-release` flag for compiling against older versions.
- Use bootstrap classpath for cross compilation against older versions.
- Add a toolchain resolve that reuses JVM discovery code from test fixtures to locate installed JVMs.

# Milestone 2: Gradle is self hosted on Java 9

# Feature: Run all Gradle tests against non modular Java 9

Goal: Successfully run all Gradle tests in a CI build on Java 8, running tests against Java 9. At completion, Gradle will be fully working on Java 9.
At this stage, however, there will be no special support for Java 9 specific features.

It is a non-goal of this feature to be able to build Gradle on Java 9. This is a later feature.

## Cannot fork test worker processes

See:
- https://discuss.gradle.org/t/classcastexception-from-org-gradle-process-internal-child-bootstrapsecuritymanager/2443
- https://issues.gradle.org/browse/GRADLE-3287
- http://download.java.net/jdk9/docs/api/index.html

As of b80, an `@argsfile` command-line option is available for the `java` command, change the worker process launcher to use this on Java 9.
Reuse handling for `javac` from java compiler infrastructure.

## Fix test fixtures and test assumptions

- `tools.jar` no longer exists as part of the JDK so `org.gradle.internal.jvm.JdkTools`(and others) need an alternative way to
get a SystemJavaCompiler which does rely on the JavaCompiler coming from an isolated, non-system `ClassLoader`. One approach would be:
    - Isolate the Gradle classes from the application `ClassLoader`
    - Load things, targeted for compilation, into an isolated `ClassLoader` as opposed to the JVM's application application `ClassLoader`.
    - `org.gradle.internal.jvm.JdkTools` could use `ToolProvider.getSystemJavaCompiler()` to get a java compiler

- JDK 9 is not java 1.5 compatible. `sourceCompatibility = 1.5` and `targetCompatibility = 1.5` will no longer work.
- Some tests use `tools.jar` as a "big jar", they need to be refactored to use something else.
- JDK 9 has completely changed the JVM and `org.gradle.internal.jvm.Jvm` is no longer an accurate model:
    - No longer a distinction between JRE and SDK, it's all rolled into one.
    - Files or jars under `lib/` should not be referenced: [http://openjdk.java.net/jeps/220](http://openjdk.java.net/jeps/220)
    _All other files and directories in the lib directory must be treated as private implementation details of the run-time system_

- Some tests which garbage collect(`System.gc()`) are failing. See: `ModelRuleExtractorTest`. There would need to be some exploration
to figure out how (or if) garbage collection is different on JDK9.

## Scala compilation is broken

## Update linux jdk9 installation

### open issues

- convenient update of jdk9 early access releases

## Add windows jdk9 coverage to Gradle CI pipeline

A 64 bit windows installer is now available (as of b80).

### implementation

- add jdk9 installation to the windows vm boxes
    - update salt-master win-repo setup to download jdk9 from java.net
    - update windows build vms to install jdk9
- setup  `Windows - Java 1.9 - Quick test` build configuration on teamcity
    - running `clean quickTest`
    - for `master` pipeline
    - for `release` pipeline

# Feature: Self hosted on Java 9

Goal: Run a coverage CI build on Java 9. At completion, it will be possible to build and test Gradle using Java 9.

## Initial JDK9 support in Gradle's own build

[gradle/java9.gradle](gradle/java9.gradle) adds both unit and integration test tasks executing on JDK 9.
 Once JDK 9 has been fully supported, jdk9 specific test tasks should be removed along with `[gradle/java9.gradle]`

# Milestone: Support Java 9 language and runtime features

Goal: full support of the Java 9 module system, and its build and runtime features

- [Jigsaw JSR-376](http://openjdk.java.net/projects/jigsaw/spec/)

In no particular order:

- Make further use of `@argfile` when supported
    - `JavaExec` task
    - daemon launcher
    - generated application start scripts
- Use `-release` javac flag for JVM binary that target older Java platform versions
- Extract or validate module dependencies declared in `module-info.java`
    - Infer API and runtime requirements based on required and exported modules
- Extract or validate platform dependencies declared in `module-info.java`
- Map module namespace to GAV namespace to resolve classpaths
- Resolve modules for compilation module path
    - Locate modules that provide required services.
    - Resolve conflicts when multiple components provide the same module
- Resolve libraries that are packaged as:
    - modular jar
    - jar
    - multi-version jar
    - any combination of the above
- Invoke compiler with module path and other args
- Deal with single and multi-module source tree layouts
- Resolve modules for runtime, packaged in various forms.
    - Locate modules that provide required services.
- Invoke java for module at runtime (eg for test execution)
- Build modular jar file
    - May need to build multiple jars for a given project, one module per jar
- Build runtime image for application
    - Operating specific formats
- Build multiple variants of a Java component
    - Any combination of (modular) jar, multi-version jar, runtime image
- Publish multiple variants of a Java component
- Capture module identifier and dependencies in publication meta-data
- Improve JVM platform definition and toolchains to understand and invoke modular JVMs
- Use module layers rather than filtering when running under Java 9 to enforce isolation.

Some migration/bridging options:

- Generate modules for non-modular libraries, based on dependency information.
    - Will require dependencies to also be converted to modules.
    - Might not be reliable, as semantics of module and jar are somewhat different.
- Support other JVM languages, generating modules based on dependency information.
- Allow non-module consumers to consume modules, applying some validation at compile and runtime.
    - This is possible already. Consumers loaded via classpath are part of an unnamed module and can read every other module.
- Support Gradle plugins packaged as modules

Abstractly:

- jar is a packaging with a single target JVM platform
- multi-version jar is a packaging with multiple target JVM platforms
- modular jar is a packaging with a single target JVM platform and single module
- runtime image is an executable with a single target native platform
- runtime image is a bundle of modules
- modular JVM is a JVM platform with a single target native platform that can host jars, multi-version jars, modular jars
