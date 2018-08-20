## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Incremental build uses less memory

Memory usage for up-to-date checking has been improved.
For the gradle/gradle build, heap usage dropped by 60 MB to 450 MB, that is a 12% reduction.

### Build Init plugin uses recommended configurations

The [Build Init plugin](userguide/build_init_plugin.html) now generates build scripts that use the recommended `implementation`, `testImplementation`, and `testRuntimeOnly` configurations instead of `compile`, `testCompile`, and `testRuntime`, respectively, for all build setup types.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Removing tasks from TaskContainer

Removing tasks from the `TaskContainer` using the following methods has been deprecated:

- `remove(Object)`
- `removeAll(Collection)`
- `retainAll(Collection)`
- `clear()`
- `Iterator#remove()` via `TaskContainer#iterator()`

With the deprecation of every method for removing a task, registering a callback when an object is removed is also deprecated (`whenObjectRemoved(Closure/Action)`).

### Replacing tasks 

It is only safe to replace an unrealized tasks registered with the new Task API because this task has not been used by anything else.

In the future, these behaviors will be treated as errors.

#### Replacing tasks that may still be used by other tasks 

Gradle now emits a deprecation warning when you attempt to replace a task that may have already been used by something else.  

#### Replacing tasks with a task of an incompatible type

Gradle now emits a deprecation warning when you attempt to replace a task with a type that's incompatible from the task being replaced. 

#### Replacing a task that does not exist

Gradle now emits a deprecation warning when you attempt to replace a task that does not already exist.

### Removing dependencies from a task

In the next major release (6.0), removing dependencies from a task will become an error.

Gradle will emit a deprecation warning for code such as `foo.dependsOn.remove(bar)`.  Removing dependencies in this way is error-prone and relies on the internal implementation details of how different tasks are wired together.
At the moment, we are not planning to provide an alternative. In most cases, task dependencies should be expressed via [task inputs](userguide/more_about_tasks.html#sec:task_inputs_outputs) instead of explicit `dependsOn` relationships.

## Potential breaking changes

<!--
### Example breaking change
-->

### Java Library Distribution Plugin utilizes Java Library Plugin

The [Java Library Distribution Plugin](userguide/java_library_distribution_plugin.html) is now based on the
[Java Library Plugin](userguide/java_library_plugin.html) instead of the [Java Plugin](userguide/java_plugin.html).
Additionally the created distribution will contain all artifacts of the `runtimeClasspath` configuration instead of the deprecated `runtime` configuration.

### Removed support for Play Framework 2.2

The previously deprecated support for Play Framework 2.2 has been removed.

### Changes to previously deprecated APIs

- The `org.gradle.plugins.signing.Signature` methods `getToSignArtifact()` and `setFile(File)` are removed.
- Removed `DirectoryBuildCache.targetSizeInMB`.
- Removed the methods `dependsOnTaskDidWork` and `deleteAllActions` from `Task`.
- Removed the methods `execute`, `getExecuter`, `setExecuter`, `getValidators` and `addValidator` from `TaskInternal`.
- Removed the methods `stopExecutionIfEmpty` and `add` from `FileCollection`.
- Removed the ability to cast (Groovy `as`) `FileCollection` to `File[]` and `File`.
- Removed the class `SimpleFileCollection`.
- Removed the method `getBuildDependencies` from `AbstractFileCollection`. 
- Removed the class `SimpleWorkResult`.
- Removed the methods `file` and `files` from `TaskDestroyables`.
- Removed the property `styleSheet` from `ScalaDocOptions`.
- Forbid passing `null` as configuration action to the methods `from` and `to` on `CopySpec`.
- Removed the property `bootClasspath` from `CompileOptions`.
- Registering invalid inputs or outputs via the runtime API is now an error.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch Jacoco plugin to use configuration avoidance APIs (gradle/gradle#6245)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch build-dashboard plugin to use configuration avoidance APIs (gradle/gradle#6247)
- [Ben McCann](https://github.com/benmccann) - Remove Play 2.2 support (gradle/gradle#3353)
- [Björn Kautler](https://github.com/Vampire) - No Deprecated Configurations in Build Init (gradle/gradle#6208)
- [Georg Friedrich](https://github.com/GFriedrich) - Base Java Library Distribution Plugin on Java Library Plugin (gradle/gradle#5695)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
