## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Build init plugin improvements

This release includes a number of improvements to The [Build Init plugin](userguide/build_init_plugin.html).

#### Interactive mode

If you run the `init` task from an interactive console, it will prompt you for details of the Gradle build that you'd like to generate.

#### Kotlin library and applications

The `init` task can generate a Kotlin library or application, using the `kotlin-library` or `kotlin-application` setup type. This was one of our top 10 most voted issues.

#### Generated builds use recommended configurations

The `init` task generates build scripts that use the recommended `implementation`, `testImplementation`, and `testRuntimeOnly` configurations instead of `compile`, `testCompile`, and `testRuntime`, respectively, for all build setup types.

#### Configure project and source package names

The `init` task provides a `--project-name` option to allow you to adjust the name of the generated project, and a `--package` option to allow you to adjust the package for the generated source.
The task will also allow you to specify these if you run the task interactively.

#### Create resource directories

The `init` task creates empty resource directories.

#### Create a .gitignore file

While the `init` task does not automatically create a Git repository, the `init` task generates a simple `.gitignore` file to make it easier for you to set up a Git repository. This `.gitignore` file ignores Gradle's build outputs.

### Plugin authoring features

#### Public method to create SourceDirectorySet instances

The `SourceDirectorySet` type is often used by plugins to represent some set of source directories and files. Previously, it was only possible to create instances of `SourceDirectorySet` using internal types. This is problematic because when a plugin uses internal types it can often break when new versions of Gradle are released because internal types may change in breaking ways between releases.

In this release of Gradle, the `ObjectFactory` service, which is part of the public API, now includes a method to create `SourceDirectorySet` instances. A plugin can now use this method instead of the internal types.

### Changes to file and directory property construction

`ObjectFactory` is now used to create file and directory `Property` instances, similar to other `Property` types. Previously, this was done using either the methods on `DefaulTask`, which was available only for `DefaultTask` subclasses, or using `ProjectLayout`, only available for projects. Now a single type, `ObjectFactory` can be used to create all property instances in a Gradle model object.

These other methods have been deprecated and will be removed in Gradle 6.0.

### JaCoCo plugin now works with the build cache and parallel test execution

The [JaCoCo plugin](userguide/jacoco_plugin.html) plugin now works seamlessly with the build cache.
When applying the plugin with no extra configuration, the test task stays cacheable and parallel test execution can be used.  

In order to make the tasks cacheable when generating execution data with `append = true`, the tasks running with code coverage are configured to delete the execution data just before they starts executing.
In this way, stale execution data, which would cause non-repeatable task outputs, is removed.

Since Gradle now takes care of removing the execution data, the `JacocoPluginExtension.append` property has been deprecated.
The JaCoCo agent is always configured with `append = true`, so it can be used when running tests in parallel. 

### Plural task output properties don't disable caching anymore

When using `@OutputFiles` or `@OutputDirectories` with an `Iterable` type, Gradle used to disable caching for the task with the following message:

> Declares multiple output files for the single output property 'outputFiles' via @OutputFiles, @OutputDirectories or TaskOutputs.files()

This is no longer the case, and using such properties doesn't prevent the task from being cached.
The only remaining reason to disable caching for the task is if the output contains file trees.

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
in the next major Gradle version (Gradle 6.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### StartParameter.interactive flag 

The `interactive` flag is deprecated and will be removed in Gradle 6.0.

<!--
### Example deprecation
-->

### Removing tasks from TaskContainer

Removing tasks from the `TaskContainer` using the following methods has been deprecated and will be an error in Gradle 6.0.

- `remove(Object)`
- `removeAll(Collection)`
- `retainAll(Collection)`
- `clear()`
- `Iterator#remove()` via `TaskContainer#iterator()`

With the deprecation of every method for removing a task, registering a callback when an object is removed is also deprecated (`whenObjectRemoved(Closure/Action)`). These methods will be removed in Gradle 6.0

### Replacing tasks 

It is only safe to replace an unrealized tasks registered with the new Task API because this task has not been used by anything else.

In Gradle 6.0, these behaviors will be treated as errors.

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

### Factory methods for creating properties

TBD - The methods on `DefaultTask` and `ProjectLayout` that create file and directory `Property` instances have been deprecated and replaced by methods on `ObjectFactory`. These deprecated methods will be removed in Gradle 6.0.

TBD - The `ObjectFactory.property(type)` method no longer sets a default value for the property. There is an overload `property(type, initialValue)` that can be used instead.

### The property `append` on `JacocoTaskExtension` has been deprecated

See [above](#jacoco-plugin-now-works-with-the-build-cache-and-parallel-test-execution) for details.

### Deprecated code quality plugins

- The JDepend plugin has been deprecated because the project is unmaintained and does not work with bytecode compiled for Java 8 and above.

## Potential breaking changes

<!--
### Example breaking change
-->

### Gradle requires Java 8

Gradle can no longer be run on Java 7, but requires Java 8 as the minimum build JVM version. 
However, you can still use forked compilation and testing to build and test software for Java 6 and above.

### Java Library Distribution Plugin utilizes Java Library Plugin

The [Java Library Distribution Plugin](userguide/java_library_distribution_plugin.html) is now based on the
[Java Library Plugin](userguide/java_library_plugin.html) instead of the [Java Plugin](userguide/java_plugin.html).
Additionally the created distribution will contain all artifacts of the `runtimeClasspath` configuration instead of the deprecated `runtime` configuration.

### Removed support for Play Framework 2.2

The previously deprecated support for Play Framework 2.2 has been removed.

### JaCoCo plugin deletes execution data on task execution

See [above](#jacoco-plugin-now-works-with-the-build-cache-and-parallel-test-execution) for details.

### Updated default tool versions

The default tool versions of the following code quality plugins have been updated:

- The Checkstyle plugin now uses 8.12 instead of 6.19 by default.
- The CodeNarc plugin now uses 1.2.1 instead of 1.1 by default.
- The JaCoCo plugin now uses 0.8.2 instead of 0.8.1 by default.
- The PMD plugin now uses 6.7.0 instead of 5.6.1 by default.
  In addition, the default ruleset was changed from the now deprecated `java-basic` to `category/java/errorprone.xml`.
  We recommend configuring a ruleset explicitly, though.

### `CopySpec.duplicatesStrategy` is no longer nullable

For better compatibility with the Kotlin DSL, the property setter no longer accepts `null` as a way
to reset the property back to its default value. Use `DuplicatesStrategy.INHERIT` instead.

### `CheckstyleReports` and `FindbugsReports` `html` property now return `CustomizableHtmlReport`

For easier configurability from statically compiled languages such as Java or Kotlin.

### Javadoc and Groovydoc delete destination dir

The [`Javadoc`](dsl/org.gradle.api.tasks.javadoc.Javadoc.html) and [`Groovydoc`](dsl/org.gradle.api.tasks.javadoc.Groovydoc.html) tasks now delete the destination dir for the documentation before executing.
This has been added to remove stale output files from the last task execution.

### Changes to property factory methods on `DefaultTask`

#### Property factory methods on `DefaultTask` are final

The property factory methods such as `newInputFile()` are intended to be called from the constructor of a type that extends `DefaultTask`. These methods are now final to avoid subclasses overriding these methods and using state that is not initialized.

#### Inputs and outputs are not automatically registered

The `Property` instances that are returned by these methods are no longer automatically registered as inputs or outputs of the task. The `Property` instances need to be declared as inputs or outputs in the usual ways, such as attaching annotations such as `@OutputFile` or using the runtime API to register the property.

Previously:
```
class MyTask extends DefaultTask {
    // note: no annotation here
    final RegularFileProperty outputFile = newOutputFile()
}

task myOtherTask {
    def outputFile = newOutputFile()
    doLast { ... }
}
```

Now:
```
class MyTask extends DefaultTask {
    @OutputFile // property needs an annotation
    final RegularFileProperty outputFile = project.objects.fileProperty()
}

task myOtherTask {
    def outputFile = project.objects.fileProperty()
    outputs.file(outputFile) // or to be registered using the runtime API
    doLast { ... }
}
```

### Source and test source dirs in `IdeaModule` no longer contain resources

The `IdeaModule` Tooling API model element contains methods to retrieve resources and test resources so those elements were removed from the result of  `IdeaModule#getSourceDirs()` and `IdeaModule#getTestSourceDirs()`.

### Changes to previously deprecated APIs

- The `org.gradle.plugins.signing.Signature` methods `getToSignArtifact()` and `setFile(File)` are removed.
- Removed `DirectoryBuildCache.targetSizeInMB`.
- Removed the methods `dependsOnTaskDidWork` and `deleteAllActions` from `Task`.
- Removed the methods `execute`, `getExecuter`, `setExecuter`, `getValidators` and `addValidator` from `TaskInternal`.
- Removed the methods `stopExecutionIfEmpty` and `add` from `FileCollection`.
- Removed the ability to cast (Groovy `as`) `FileCollection` to `File[]` and `File`.
- Removed the method `getBuildDependencies` from `AbstractFileCollection`. 
- Removed the methods `file` and `files` from `TaskDestroyables`.
- Removed the property `styleSheet` from `ScalaDocOptions`.
- Removed the methods `newFileVar` and `newDirectoryVar` from `ProjectLayout`.
- Removed the method `property` from `ProviderFactory`.
- Removed the method `property` from `Project`.
- Removed the method `property` from `Script`.
- Removed the type `RegularFileVar`.
- Removed the type `DirectoryVar`.
- Removed the type `PropertyState`.
- Forbid passing `null` as configuration action to the methods `from` and `to` on `CopySpec`.
- Removed the property `bootClasspath` from `CompileOptions`.
- Validation problems for inputs or outputs registered via the runtime API now fail the build.
- Chaining calls to the methods `file`, `files`, and `dir` on `TaskInputs` is now impossible.
- Chaining calls to the methods `file`, `files`, and `dir` on `TaskOutputs` is now impossible.
- Chaining calls to the method `property` and `properties` on `TaskInputs` is now an error.

### Changes to internal APIs

- Removed the internal class `SimpleFileCollection`.
- Removed the internal class `SimpleWorkResult`.
- Removed the internal method `getAddAction` from `BroadcastingCollectionEventRegister`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch Jacoco plugin to use configuration avoidance APIs (gradle/gradle#6245)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch build-dashboard plugin to use configuration avoidance APIs (gradle/gradle#6247)
- [Ben McCann](https://github.com/benmccann) - Remove Play 2.2 support (gradle/gradle#3353)
- [Björn Kautler](https://github.com/Vampire) - No Deprecated Configurations in Build Init (gradle/gradle#6208)
- [Georg Friedrich](https://github.com/GFriedrich) - Base Java Library Distribution Plugin on Java Library Plugin (gradle/gradle#5695)
- [Stefan M.](https://github.com/StefMa) — Include Kotlin DSL samples in Gradle Wrapper, Java Gradle Plugin, and OSGI Plugin user manual chapters (gradle/gradle#5923, gradle/gradle#6485, gradle/gradle#6539)
- [Stefan M.](https://github.com/StefMa) - Fix incoherent task name in the Authoring Tasks user manual chapter (gradle/gradle#6581)
- [Jean-Baptiste Nizet](https://github.com/jnizet) — Include Kotlin DSL samples in Announcements, ANTLR, Base, EAR, Java Library Plugins, JaCoCo Plugins, Building Java Projects, Declaring Repositories, Dependency Locking, Dependency Types, Java Library, Java Testing, Artifact Management, IDEA Plugin, Application Plugin, Build Cache, Build Lifecycle, Declaring Dependencies, Inspecting Dependencies, Dependency Management for Java Projects, Working With Files, Working With Dependencies, Building Java Projects, Java Quickstart, Eclipse Plugin, Custom Tasks, Java Plugin, Signing Plugin, Composite Builds and TestKit user manual chapters (gradle/gradle#6488, gradle/gradle#6500, gradle/gradle#6514, gradle/gradle#6518, gradle/gradle#6521, gradle/gradle#6540, gradle/gradle#6560, gradle/gradle#6559, gradle/gradle#6569, gradle/gradle#6556, gradle/gradle#6512, gradle/gradle#6501, gradle/gradle#6497, gradle/gradle#6571, gradle/gradle#6575, gradle/gradle#6586, gradle/gradle#6590, gradle/gradle#6591, gradle/gradle#6593, gradle/gradle#6597, gradle/gradle#6598, gradle/gradle#6602, gradle/gradle#6613, gradle/gradle#6618, gradle/gradle#6578, gradle/gradle#6660, gradle/gradle#6663, gradle/gradle#6678)
- [Jean-Baptiste Nizet](https://github.com/jnizet) — Use proper subtype for useTestNG() (gradle/gradle#6520)
- [Xiang Li](https://github.com/lixiangconan) and [Theodore Ni](https://github.com/tjni) - Make FileUtils#calculateRoots more efficient (gradle/gradle#6455)
- [James Justinic](https://github.com/jjustinic) Include Kotlin DSL samples in Ant, WAR Plugin, Checkstyle plugin, CodeNarc plugin, FindBugs plugin, JDepend plugin, PMD plugin user manual chapters (gradle/gradle#6492, gradle/gradle#6510, gradle/gradle#6522)
- [James Justinic](https://github.com/jjustinic) Support type-safe configuration for Checkstyle/FindBugs HTML report stylesheet (gradle/gradle#6551)
- [Mike Kobit](https://github.com/mkobit) - Include Kotlin DSL samples in Lazy Configuration user manual chapter (gradle/gradle#6528)
- [Kevin Macksamie](https://github.com/k-mack) - Switch distribution plugin to use configuration avoidance APIs (gradle/gradle#6443)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
