This spec collects issues that prevent Gradle from working well, or at all, with JDK 9.

# Feature: Run all Gradle tests against Java 9

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

# Feature: Support Java 9 language and runtime features

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
