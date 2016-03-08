# Gradle implementation dependencies are not visible to plugins at development time

## The problem

To build a Gradle plugin currently, you declare the following dependencies:

```
dependencies {
    compile gradleApi()
    testCompile gradleTestKit()
}
```

In the case of `gradleApi()`, this actually encompasses the entire Gradle runtime including Gradle's third party dependencies (e.g. Guava).
This defect is capture by [GRADLE-1715](https://issues.gradle.org/browse/GRADLE-1715).

What's more is that it does this in such a way that the dependencies are outside of the scope of conflict resolution.
This defect is captured by [GRADLE-2516](https://issues.gradle.org/browse/GRADLE-2516).

This means that given the following:

```
dependencies {
    compile gradleApi()
    compile 'com.google.guava:guava:19.0'
}
```

The user will actually end up with two addressable copies of Guava on the compile classpath and in the test runtime.
However, when the plugin is actually used in real Gradle runtime, Gradle's Guava dependency is not visible to the plugin due to classloader based access control.

The current implementation of `gradleTestKit()`, which relies on a Gradle runtime, attempts to address this problem via a mechanism that introduces its own issues.
This dependency notation exposing a single fat JAR that encompasses the test kit classes, along with the Gradle runtime (incl. Tooling API) but shaded/relocated.
This effectively makes use of `gradleApi()` and `gradleTestKit()` together unreliable as the result is classes of duplicate name but of different content.

## The solution

`gradleApi()` and `gradleTestKit()` must be complimentary and only expose non `org.gradle.**` classes that are part of the Gradle public API.
That is, non `org.gradle.**` classes required by Gradle that are not part of the public API must be relocated to live under `org.gradle`.

This is effectively the strategy employed by the fat TAPI JAR.

## Terminology & definitions

- `impl-dep`: classes that are outside of the `org.gradle` namespace and are not part Gradle's public API (e.g. Guava)
- `api-dep`: classes that are outside of the `org.gradle` namespace and are part of Gradle's public API (e.g. Groovy/Ant)
- `gradle-api`: all of the `org.gradle` namespace (with exceptions) and all `api-dep` classes
- `gradle-public-api`: the `org.gradle` namespace (with exceptions) that are nominated as public (e.g. classes not in an `internal` package) and all `api-dep` classes

The following is the definition of classes that are part of the `gradle-api`:

- `org.gradle.**` (with some exceptions)
- `java.**`
- `groovy.**`
- `org.codehaus.groovy.**`
- `groovyjarjarantlr.**`
- `org.apache.tools.ant.**`
- `org.slf4j.**`
- `org.apache.commons.logging.**`
- `org.apache.log4j.**`
- `javax.inject.*`

This set is codified [here](https://github.com/gradle/gradle/blob/f6ec32e5acc2d4892ef786897e2f64d3a55acb81/subprojects/core/src/main/groovy/org/gradle/initialization/DefaultClassLoaderRegistry.java#L46-L46).

The `org.gradle` classes that make up the Gradle API are restricted to those currently exposed via the Gradle runtime.
This is codified [here](https://github.com/gradle/gradle/blob/4f9a5740772c240f5686004a3bbd0a08ed800779/subprojects/core/src/main/groovy/org/gradle/api/internal/DynamicModulesClassPathProvider.java#L37-L37).
However, the actual set currently exposed by `gradleApi()` is codified [here](https://github.com/gradle/gradle/blob/d23f16d4f20012d784775b4c6453d70b2671a2b7/subprojects/core/src/main/groovy/org/gradle/api/internal/DependencyClassPathProvider.java#L39-L39)

This excludes “modules” like `gradle-launcher` and others, and notably `gradle-test-kit`.

## Implementation

The `gradleApi()` dependency notation will effectively be a use-time generated fat JAR that is logically equivalent to the current version,
but with all `impl-dep` classes relocated to under `org.gradle.internal.impldep`.

The `gradleTestKit()` dependency notation will be similar except that the use-time generated JAR will only be for the `gradle-test-kit` JAR and its `impl-deps` that are not exposed by `gradleApi()`.
This notation will include all of the files included by `gradleApi()`, plus the `gradleTestKit()` specific generated JAR.

The generated JARs will be persistently cached as part of the Gradle versioned caches stored in the GRADLE_USER_HOME.
For each Gradle _version_, users should typically only pay the cost of generating these JARs once.
An exception to this will be scenarios where users frequently build with a “fresh” GRADLE_USER_HOME, as some people do for CI builds.

Currently, `GradleRunner` supports “auto discovery” of the Gradle installation for the purpose of testing.
This works by using the code source location of the GradleRunner class to find the gradle-test-kit JAR, which resides within the Gradle installation.
The proposed changes would have the `GradleRunner` class residing outside of the Gradle installation (i.e. it would reside withing GRADLE_USER_HOME caches).
To remedy this, the “auto discovery” will be updated to use a new “installation beacon” mechanism.
A new JAR will be added to the Gradle distribution called `gradle-installation-beacon`.
This will contain a single marker interface with no functionality.
The `gradleApi()` (and by `gradleTestKit()` by extension) will include this JAR, from the installation, in their file sets.
The runtime installation locator mechanism will be updated to look for this marker class.


### Requirements

- Installation discovery uses “installation beacon”
    - Distribution includes `gradle-installation-beacon` JAR in lib directory
    - Contains a single (empty) marker interface `org.gradle.internal.installation.beacon.InstallationBeacon`.
    - `GradleDistributionLocator` is updated to find the installation based on this class.
- `gradleApi()` presents a `SelfResolvingDependency` that generates the fat JAR on demand via its `resolve()` method.
    - JAR is generated via new code that creates a single fat JAR and relocates impl-dep classes using ASM
        - Service descriptors referring to relocated classes must also be transformed
    - JAR is written to `GRADLE_USER_HOME/«gradle-version»/gradle-shading-jars/gradle-runtime-«gradle-version».jar`
    - JAR is written to file with .tmp suffix during generation, and moved upon successful completion, deleted otherwise
    - `generated-jars` directory is locked for exclusive access (using existing multi process safe cache support) for write operation, no lock for read (i.e. JAR already generated/exists)
    - Progress logging is emitted during JAR generation, with percentage based on number of input JARs processed
    - `groovy-all` JAR is _not_ inlined into the fat JAR (i.e. is included in the file set as is), in order to not break `org.gradle.api.tasks.GroovyRuntime` use with `gradleApi()`
    - The returned file set includes the installation beacon, from the installation
    - Includes the (non inlined) installation beacon JAR in the file set
- `gradleTestKit()` presents a similar `SelfResolvingDependency` and is a superset of what is returned by `gradleApi()`
    - JAR is generated in a similar manner, with progress logging, multi process safety, caching etc.
    - JAR is written to `GRADLE_USER_HOME/«gradle-version»/gradle-shading-jars/gradle-test-kit-«gradle-version».jar`
- Fat TAPI JAR is updated to relocate `impl-deps` to the same location at `gradleApi()` etc. (i.e. `org.gradle.internal.impldep`).

### Acceptance criteria

- User can compile a typical Gradle plugin using `gradleApi()`
- User can compile a typical Gradle plugin implemented in Groovy, using just `compile gradleApi()` - i.e. Groovy provided by Gradle is detected
- User can use `ProjectBuilder` to unit test a plugin
- User can reliably compile and unit test a plugin that depends on a conflicting version off an `impl-dep` (e.g. newer Guava)
- User cannot compile against an `impl-dep` (non-relocated) class
- Module metadata (e.g. POM file) for published plugins do not contain reference to Gradle modules
- Appropriate user feedback is issued while JARs are being generated
- `gradleApi()` and `gradleTestKit()` can be “resolved” by concurrent Gradle builds and tasks within one build
- `gradleApi()` and `gradleTestKit()` are not duplicative
- User can compile plugin in IDEA and Buildship that uses `gradleApi()`
- User can unit test plugin in IDEA and Buildship via `gradleApi()`
- User can functionally test plugin in IDEA and Buildship via `gradleApi()`
- Gradle installation inference performed by `GradleRunner` continues to work
- Files exposed by `gradleApi()` have meaningful names (e.g. when viewed in IDE classpath dialog)
- `gradleApi()`, `gradleTestKit()` and fat TAPI JAR are classpath compatible regardless of order

### Non goals

- Preventing people from compiling against internal Gradle classes (including relocated `impl-deps`).
- Preventing people from accessing internal Gradle classes (including relocated `impl-deps`) at runtime (e.g. unit test).
- Surfacing `gradleApi()` and `gradleTestKit()` JARs via dependency reports (i.e. no change)
- Exposing source code for `gradleApi()` or `gradleTestKit()` dependencies to IDE users

### Spike

The implementation has already been spiked as part of [this branch](https://github.com/gradle/gradle/tree/ld-gradle-impldep-shading).
Parts of this should be taken as the starting point.

### Documentation

- Mention potential breaking change in release notes that `impl-deps` classes that previously were “visible” at compile and test time no longer are


