## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

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

In this release of Gradle, the `ObjectFactory` service, which is part of the public API, now includes a method to create `SourceDirectorySet` instances. Plugins can now use this method instead of the internal types.

### Added Provider.flatMap() method

TBD - why this is useful

### Provider implementations track their producer task

TBD - More provider implementations track the task that produces the value of the provider:
- Any provider returned by `TaskContainer`
- Any property marked with `@OutputFile` or `@OutputDirectory`
- Any provider returned by `Provider.map()` that matches these criteria (including this one)

### Changes to file and directory property construction

`ObjectFactory` is now used to create file and directory `Property` instances, similar to other `Property` types. Previously, this was done using either the methods on `DefaulTask`, which was available only for `DefaultTask` subclasses, or using `ProjectLayout`, only available for projects. Now a single type `ObjectFactory` can be used to create all property instances in a Gradle model object.

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

### Task timeouts

You can now specify a timeout for a task, after which it will be interrupted.
See the user guide section on “[Task timeouts](userguide/more_about_tasks.html#task_timeouts)” for more information.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

### Reporting of TestNG classes/methods

When using a recent version of TestNG (6.9.13.3 or newer), classes were reported to `TestListeners` as sibling `TestDescriptors` of test method `TestDescriptors`.
Now, `TestDescriptors` of classes are parents of their enclosing method `TestDescriptors`.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 6.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### StartParameter.interactive flag

The `interactive` flag is deprecated and will be removed in Gradle 6.0.

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

### Incubating factory methods for creating properties

TBD - The methods on `DefaultTask` and `ProjectLayout` that create file and directory `Property` instances have been deprecated and replaced by methods on `ObjectFactory`. These deprecated methods will be removed in Gradle 6.0.

TBD - The `ObjectFactory.property(type)`, `listProperty(type)` and `setProperty(type)` methods no longer set an initial value for the property. Instead, you can use the `value()` or `empty()` methods, or any other mutation method, on the property instances to set an initial value, if required.

### The property `append` on `JacocoTaskExtension` has been deprecated

See [above](#jacoco-plugin-now-works-with-the-build-cache-and-parallel-test-execution) for details.

### Deprecated announce plugins

The [announce](userguide/announce_plugin.html) and [build announcements](userguide/build_announcements_plugin.html) plugins have been deprecated.

### Deprecated OSGi plugin

The [osgi](userguide/osgi_plugin.html) plugin has been deprecated. Builds should migrate to the [biz.aQute.bnd plugin](https://github.com/bndtools/bnd/blob/master/biz.aQute.bnd.gradle/README.md).

### Deprecated code quality plugins

- The FindBugs plugin has been deprecated because the project is unmaintained and does not work with bytecode compiled for Java 9 and above.
  Please consider using the [SpotBugs plugin](https://plugins.gradle.org/plugin/com.github.spotbugs) instead.
- The JDepend plugin has been deprecated because the project is unmaintained and does not work with bytecode compiled for Java 8 and above.

## Potential breaking changes

### Fixes to dependency resolution

Dependency resolutions fixes have been included in this release.
By definition this could impact the set of resolved dependencies of your build.
However the fixed issues are mostly about corner cases and combination with recent features and thus should have a limited impact.

When a dependency constraint matched a real dependency, it was made part of the graph.
However if for some reason the dependency was later evicted from the graph, the constraint remained present.
Now when the last non-constraint edge to a dependency disappears, all constraints for that dependency will be properly removed from the graph.

### Gradle 5.0 requires Java 8

Gradle can no longer be run on Java 7, but requires Java 8 as the minimum build JVM version.
However, you can still use forked compilation and testing to build and test software for Java 6 and above.

### Configuration Avoidance API disallows common configuration errors

The [configuration avoidance API](userguide/task_configuration_avoidance.html) introduced in Gradle 4.9 allows you to avoid creating and configuring tasks that are never used.

With the existing API, this example adds two tasks (`foo` and `bar`):
```
tasks.create("foo") {
    tasks.create("bar")
}
```

When converting this to use the new API, something surprising happens: `bar` doesn't exist.  The new API only executes configuration actions when necessary, 
so the `register()` for task `bar` only executes when `foo` is configured. 

```
tasks.register("foo") {
    tasks.register("bar") // WRONG
}
```

To avoid this, Gradle now detects this and prevents modification to the underlying container (through `create` or `register`) when using the new API. 

### Java Library Distribution Plugin utilizes Java Library Plugin

The [Java Library Distribution Plugin](userguide/java_library_distribution_plugin.html) is now based on the
[Java Library Plugin](userguide/java_library_plugin.html) instead of the [Java Plugin](userguide/java_plugin.html).
Additionally the created distribution will contain all artifacts of the `runtimeClasspath` configuration instead of the deprecated `runtime` configuration.

### Removed support for Play Framework 2.2

The previously deprecated support for Play Framework 2.2 has been removed.

### JaCoCo plugin deletes execution data on task execution

See [above](#jacoco-plugin-now-works-with-the-build-cache-and-parallel-test-execution) for details.

### Checkstyle plugin config directory in multi-project builds

Gradle will now, by convention, only look for Checkstyle configuration files in the root project's _config/checkstyle_ directory.
Checkstyle configuration files in subprojects — the old by-convention location — will be ignored unless you explicitly configure their path
via [`checkstyle.configDir`](dsl/org.gradle.api.plugins.quality.CheckstyleExtension.html#org.gradle.api.plugins.quality.CheckstyleExtension:configDir)
or [`checkstyle.config`](dsl/org.gradle.api.plugins.quality.CheckstyleExtension.html#org.gradle.api.plugins.quality.CheckstyleExtension:config).

### Updated default tool versions

The default tool versions of the following code quality plugins have been updated:

- The Checkstyle plugin now uses 8.12 instead of 6.19 by default.
- The CodeNarc plugin now uses 1.2.1 instead of 1.1 by default.
- The JaCoCo plugin now uses 0.8.2 instead of 0.8.1 by default.
- The PMD plugin now uses 6.7.0 instead of 5.6.1 by default.
  In addition, the default ruleset was changed from the now deprecated `java-basic` to `category/java/errorprone.xml`.
  We recommend configuring a ruleset explicitly, though.

### Library upgrades

Several libraries that are used by Gradle have been upgraded:

- Groovy was upgraded from 2.4.15 to 2.5.2 (see http://groovy-lang.org/releasenotes/groovy-2.5.html about the changes in this release).
- Ant has been upgraded from 1.9.11 to 1.9.13.
- The AWS SDK used to access S3 backed Maven/Ivy repositories has been upgraded from 1.11.267 to 1.11.407.
- The BND library used by the OSGi plugin has been upgraded from 3.4.0 to 4.0.0.
- The Google Cloud Storage JSON API Client Library used to access Google Cloud Storage backed Maven/Ivy repositories has been upgraded from v1-rev116-1.23.0 to v1-rev136-1.25.0.
- Ivy has been upgraded from 2.2.0 to 2.3.0.
- The JUnit Platform libraries used by the `Test` task have been upgraded from 1.0.3 to 1.3.1.
- The Maven Wagon libraries used to access Maven repositories have been upgraded from 2.4 to 3.0.0.
- SLF4J has been upgraded from 1.7.16 to 1.7.25.

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

### Source task `source` field access

In previous Gradle versions the `source` filed in `SourceTask` was accessible from subclasses.
This is not the case anymore as the `source` filed is now declared as `private`.

### The left shift operator on the Task interface is no longer supported

The left shift (`<<`) operator acted as an alias for adding a `doLast` action to an existing task. It was deprecated since Gradle 3.2 and has now been removed.

### Invalid project and domain object names are no longer supported

Previously, it was deprecated for project and domain object names to be empty, start or end with `.` or contain any of the following characters: `/\:<>"?*|`.
The use of such names now causes the build to fail.

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
- Removed the method `leftShift` from `Task`.
- Removed the type `RegularFileVar`.
- Removed the type `DirectoryVar`.
- Removed the type `PropertyState`.
- Removed the method `configureForSourceSet` from `JavaBasePlugin`
- Removed the property `classesDir` from `JDepend`.
- Removed the property `testClassesDir` from `Test`.
- Removed the property `classesDir` from `SourceSetOutput`.
- Removed `IdeaPlugin.performPostEvaluationActions` and `EclipsePlugin.performPostEvaluationActions`
- Removed `ConfigurableReport.setDestination(Object)`
- Removed the internal `@Option` and `@OptionValues` annotations from the `org.gradle.api.internal.tasks.options` package.
- Forbid passing `null` as configuration action to the methods `from` and `to` on `CopySpec`.
- Removed the property `bootClasspath` from `CompileOptions`.
- Validation problems for inputs or outputs registered via the runtime API now fail the build.
- Chaining calls to the methods `file`, `files`, and `dir` on `TaskInputs` is now impossible.
- Chaining calls to the methods `file`, `files`, and `dir` on `TaskOutputs` is now impossible.
- Chaining calls to the method `property` and `properties` on `TaskInputs` is now an error.
- `JavaPluginConvention` is now abstract.
- `ApplicationPluginConvention` is now abstract.
- `WarPluginConvention` is now abstract.
- `EarPluginConvention` is now abstract.
- `BasePluginConvention` is now abstract.
- `ProjectReportsPluginConvention` is now abstract.

### Implicit imports for internal classes have been removed

Classes in the internal `org.gradle.util` package are no longer implicitly imported by default.
Please either stop using internal classes (recommended) or import them explicitly at the top of your build file.

### Removed system properties

- The `test.single` filter mechanism has been removed. You must select tests from the command-line with [`--tests`](userguide/java_testing.html#simple_name_pattern).
- The `test.debug` mechanism to enable debugging of JVM tests from the command-line has been removed. You must use [`--debug-jvm`](userguide/java_testing.html#sec:debugging_java_tests) to enable debugging of test execution.
- The `org.gradle.readLoggingConfigFile` system property no longer does anything — please update affected tests to work with your `java.util.logging` settings.

### Replacing built-in tasks

In earlier versions of Gradle, builds were allowed to replace tasks that may be automatically created. This was deprecated in [Gradle 4.8](https://docs.gradle.org/4.8/release-notes.html#overwriting-gradle's-built-in-tasks) and has now been turned into an error.

Attempting to replace a built-in task will produce an error similar to the following:

> Cannot add task 'wrapper' as a task with that name already exists.

The full list of built-in tasks that cannot be replaced:

`wrapper`, `init`, `help`, `tasks`, `projects`, `buildEnvironment`, `components`, `dependencies`, `dependencyInsight`, `dependentComponents`, `model`, `properties`

### Changes to internal APIs

- Removed the internal class `SimpleFileCollection`.
- Removed the internal class `SimpleWorkResult`.
- Removed the internal method `getAddAction` from `BroadcastingCollectionEventRegister`.

### Gradle TestKit will search upwards for `settings.gradle`

When invoking a build, Gradle TestKit now behaves like a regular Gradle invocation, and will search upwards for a `settings.gradle` file that defines the build. 
Please ensure that all builds being executed with Gradle TestKit define `settings.gradle`, even if this is an empty file.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch Jacoco plugin to use configuration avoidance APIs (gradle/gradle#6245)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Switch build-dashboard plugin to use configuration avoidance APIs (gradle/gradle#6247)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Fix nullability of the CreateStartScripts task properties (gradle/gradle#6704)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Make Settings implement ExtensionAware (gradle/gradle#6685)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Remove `@Incubating` from LifecycleBasePlugin (gradle/gradle#6901)
- [Ben McCann](https://github.com/benmccann) - Remove Play 2.2 support (gradle/gradle#3353)
- [Björn Kautler](https://github.com/Vampire) - No Deprecated Configurations in Build Init (gradle/gradle#6208)
- [Georg Friedrich](https://github.com/GFriedrich) - Base Java Library Distribution Plugin on Java Library Plugin (gradle/gradle#5695)
- [Stefan M.](https://github.com/StefMa) — Include Kotlin DSL samples in Gradle Wrapper, Java Gradle Plugin, OSGI Plugin and Organizing Gradle Projects user manual chapters (gradle/gradle#5923, gradle/gradle#6485, gradle/gradle#6539, gradle/gradle#6621)
- [Stefan M.](https://github.com/StefMa) - Fix incoherent task name in the Authoring Tasks user manual chapter (gradle/gradle#6581)
- [Jean-Baptiste Nizet](https://github.com/jnizet) — Include Kotlin DSL samples in Announcements, ANTLR, Base, EAR, Java Library Plugins, JaCoCo Plugins, Building Java Projects, Declaring Repositories, Dependency Locking, Dependency Types, Java Library, Java Testing, Artifact Management, IDEA Plugin, Application Plugin, Build Cache, Build Lifecycle, Declaring Dependencies, Inspecting Dependencies, Dependency Management for Java Projects, Working With Files, Working With Dependencies, Building Java Projects, Java Quickstart, Eclipse Plugin, Custom Tasks, Java Plugin, Signing Plugin, Composite Builds, TestKit, Multi Projects Builds, Managing Transitive Dependencies, Custom Plugins, Init Scripts, Scala Plugin, Managing Dependency Configurations, Groovy Plugin, Groovy Quickstart and Customizing Dependency Resolution Behavior user manual chapters (gradle/gradle#6488, gradle/gradle#6500, gradle/gradle#6514, gradle/gradle#6518, gradle/gradle#6521, gradle/gradle#6540, gradle/gradle#6560, gradle/gradle#6559, gradle/gradle#6569, gradle/gradle#6556, gradle/gradle#6512, gradle/gradle#6501, gradle/gradle#6497, gradle/gradle#6571, gradle/gradle#6575, gradle/gradle#6586, gradle/gradle#6590, gradle/gradle#6591, gradle/gradle#6593, gradle/gradle#6597, gradle/gradle#6598, gradle/gradle#6602, gradle/gradle#6613, gradle/gradle#6618, gradle/gradle#6578, gradle/gradle#6660, gradle/gradle#6663, gradle/gradle#6678, gradle/gradle#6687, gradle/gradle#6588, gradle/gradle#6633, gradle/gradle#6637, gradle/gradle#6689, gradle/gradle#6509, gradle/gradle#6645, gradle/gradle#6596)
- [Jean-Baptiste Nizet](https://github.com/jnizet) — Use proper subtype for useTestNG() (gradle/gradle#6520)
- [Jean-Baptiste Nizet](https://github.com/jnizet) — Add documentation how to make a task cacheable using the runtime api (gradle/gradle#6691)
- [Xiang Li](https://github.com/lixiangconan) and [Theodore Ni](https://github.com/tjni) - Make FileUtils#calculateRoots more efficient (gradle/gradle#6455)
- [James Justinic](https://github.com/jjustinic) Include Kotlin DSL samples in Ant, WAR Plugin, Checkstyle plugin, CodeNarc plugin, FindBugs plugin, JDepend plugin, PMD plugin user manual chapters (gradle/gradle#6492, gradle/gradle#6510, gradle/gradle#6522)
- [James Justinic](https://github.com/jjustinic) Support type-safe configuration for Checkstyle/FindBugs HTML report stylesheet (gradle/gradle#6551)
- [Mike Kobit](https://github.com/mkobit) - Include Kotlin DSL samples in Lazy Configuration user manual chapter (gradle/gradle#6528)
- [Kevin Macksamie](https://github.com/k-mack) - Switch distribution plugin to use configuration avoidance APIs (gradle/gradle#6443)
- [Cliffred van Velzen](https://github.com/cliffred) - Allow logging null value (gradle/gradle#6665)
- [Artem Zinnatullin](https://github.com/artem-zinnatullin) - Update HttpCore from 4.4.9 to 4.4.10 and HttpClient from 4.5.5 to 4.5.6 (gradle/gradle#6709)
- [Jakub Strzyżewski](https://github.com/shindouj) - Improve exception message for missing repository credentials when publishing (gradle/gradle#6379)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
