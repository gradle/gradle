## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Convenient use of build scan plugin

This version of Gradle makes it even easier to gain deep insights into your build. By using the command line option `--scan`, the latest [build scan plugin](https://scans.gradle.com/get-started) is applied automatically. You will not have to explicitly declare the plugin in your build script or an init script.

### Timeouts for HTTP/HTTPS requests

Previous versions of Gradle did not define a timeout for any HTTP/HTTPS requests. Under certain conditions e.g. network problems, unresponsive or overloaded servers this behavior could lead to hanging connections.
Gradle now defines connection and socket timeouts for all HTTP/HTTPS requests.

The timeouts are also effective for connections to an [HTTP build cache](dsl/org.gradle.caching.http.HttpBuildCache.html#org.gradle.caching.http.HttpBuildCache).
If connections to the build cache time out then it will be disabled for the rest of the build.

    :compileJava
    Could not load entry 2b308a0ad9cbd0ad048d4ea84c186f71 for task ':compileJava' from remote build cache: Unable to load entry from 'https://example.com/cache/2b308a0ad9cbd0ad048d4ea84c186f71': Read timed out
    
    BUILD SUCCESSFUL in 4s
    1 actionable task: 1 executed
    The remote build cache was disabled during the build due to errors.

### Improvements for plugin authors

In Gradle 4.1, we added APIs that allow a specific task output directory or output file to be wired in as an input for another task, in a way that allows the task dependencies to be inferred and that deals with later changes to the configured locations of those outputs. It is intended to be a more robust, performant and descriptive alternative to using `File` property types and calls to `Task.dependsOn`.

It added factory methods on `DefaultTask` - i.e. `newInputFile()`, `newOutputFile()`, and `newOutputDirectory()` - to allow a task implementation class to create `DirectoryVar` instances that represent an output directory, and `RegularFileVar` instances that represent an output file or directory. When used as an output directory or file property, these instances carry dependency information about the producing task. When used as an input file property, the producing task is tracked as a dependency of the consuming task. Similar support for input files is done using `ConfigurableFileCollection` and friends, as has been possible for quite a while.

In Gradle 4.3, we added a new factory method on `DefaultTask` - i.e. `newInputDirectory()` - to allow a task implementation class to create `DirectoryVar` instances that represent an input directory.

TBD: `Provider<T>` and `PropertyState<T>` can be used with `@Input` properties.
TBD: `ListProperty<T>`
TBD: `Provider.map()`
TBD: `PropertyState<Directory>` and `PropertyState<RegularFile>` can be set using `File` in DSL.

### Task input/output annotations and runtime API

Gradle 4.3 introduces some changes that bring task inputs and outputs registered via task annotations (e.g. `@InputFile` and `@OutputDirectory` etc.) and the runtime API (think `task.inputs.file(...)` and `task.outputs.dir(...)` etc.) closer to each other.

#### Output directory creation

For task outputs declared via annotations like `@OutputDirectory` and `@OutputFile`, Gradle always ensured that the necessary directory exists before executing the task. Starting with version 4.3 Gradle will also create directories for outputs that were registered via the runtime API (e.g. by calling methods like `task.outputs.file()` and `dir()`).

```
task customTask {
    inputs.file "input.txt"
    outputs.file "output-dir/output.txt"
    doLast {
        mkdir "output-dir" // <-- This is now unnecessary
        file("output-dir/output.txt") << file("input.txt")
    }
}
```

#### Input/output validation

Task inputs and outputs declared via task property annotations have always been validated by Gradle. If a task declared a non-optional (`@Optional`) input that was `null`, Gradle would fail the build with the message:

```text
* What went wrong:
Some problems were found with the configuration of task ':test'.
> No value has been specified for property 'inputDirectory'.
```

Gradle would also fail the build if an input file or directory did not exist, and also if it expected an output directory, but found a file (or vice versa).

Starting with Gradle 4.3, these validations also happen for properties registered via the runtime API. For backwards compatibility, Gradle will not fail the build if the new validation fails, but produce a warning similar to this:

```text
A problem was found with the configuration of task ':test'. Registering invalid inputs and outputs via TaskInputs and TaskOutputs methods has been deprecated and is scheduled to be removed in Gradle 5.0.
 - No value has been specified for property 'inputDirectory'.
```

#### Declaring classpath properties

The `@Classpath` annotation was introduced in Gradle 3.2 to mark task input properties that should represent a runtime classpath. Gradle 3.4 added `@CompileClasspath`. However, it was not possible to declare a similar property via the runtime API. With Gradle 4.3 this is now possible. The following examples declare equivalent inputs for `customTask`.

Using the annotations API:

```
class CustomTask {
    @Classpath FileCollection classpath

    // ...
}

task customTask(type: CustomTask) {
    classpath = files("lib1.jar", "lib2.jar")
}
```

Using the runtime API:

```
task customTask {
    inputs.files("lib1.jar", "lib2.jar")
        .withNormalizer(ClasspathNormalizer)
        .withPropertyName("classpath")
}
```

### Force console type with `org.gradle.console`

You may now force Gradle to use specific console type in [build output](userguide/console.html#sec:console_build_output) by setting [`org.gradle.console`](userguide/build_environment.html#sec:gradle_configuration_properties) in your `gradle.properties`.

### New `verbose` console type

Since Gradle 4.0, task header and outcome won't be displayed by default, which may be confusing. Now you can use `--console=verbose` command line argument 
or set [`org.gradle.console`](userguide/build_environment.html#sec:gradle_configuration_properties) in your `gradle.properties` to enable task header and outcome output.

### Specify output directory for source files generated by annotation processor 

When using annotation processors, Java source may be generated into a directory specified by the `-s` compiler argument. When the directory is specified via the new `compileJava.options.annotationProcessorGeneratedSourcesDirectory` property, the generated sources are also stored in and loaded from the build cache.

### Plugin library upgrades

The JaCoCo plugin has been upgraded to use [JaCoCo version 0.7.9](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

### Eclipse plugin separates output folders

The `eclipse` plugin now defines separate output directories for each source folders. Also, each source folder and dependency defines an additional `gradle_source_sets` classpath attribute. The attribute specifies to which sourceSet the entry belonged. Future [Buildship](http://eclipse.org/buildship) versions will use this information to separate source sets when launching Java applications within Eclipse.

### Support for PMD incremental analysis

TBD

The default version of PMD is now [PMD 5.8.1](https://pmd.github.io/2017/07/01/PMD-5.8.1/).

### More use cases supported using the `plugins {}` block

Non-core plugins already requested using the `plugins {}` block on a parent project can now be requested in child projects:

```
// root/settings.gradle
include("subproject")

// root/build.gradle
plugins {
    id("com.example.plugin") version "1.0"
}

// root/subproject/build.gradle
plugins {
    id("com.example.plugin")
}
```

Plugins from `buildSrc` can now be requested in child projects:

```
// root/buildSrc/src/main/groovy/my/MyPlugin.gradle
package my

import org.gradle.api.*

class MyPlugin implement Plugin<Project> {
    @Override
    void apply(Project project) {
        // ...
    }
}

// root/buildSrc/build.gradle
plugins {
    id("groovy")
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        myPlugins {
            id = "my-plugin"
            implementationClass = "my.MyPlugin
        }
    }
}

dependencies {
    compileOnly(gradleApi())
}

// root/build.gradle
plugins {
    id("my-plugin")
}
```

See the user guide section on the [`plugins {}` block](userguide/plugins.html#sec:plugins_block) for more information.

<!--
### Example new and noteworthy
-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Disabled equivalents for existing command line options

All Command line options that allow to enable a feature now also have an equivalent for disabling the feature. For example the command line option `--build-scan` supports `--no-build-scan` as well. Some of the existing command line options did not follow the same pattern. With this version of Gradle every boolean-based command line option also expose a "disabled" option. For more information please review the list of [command line options](userguide/gradle_command_line.html) in the user guide.

## Fixed issues

### Version ranges are now handled properly

Gradle will now honor version ranges correctly when multiple ranges are intersecting. For example, if a dependency on `some-module` is found with a range of versions `[3,6]` and that the same dependency is found transitively with a range of `[4,8]`, Gradle now selects version `6`, which is the highest version within both ranges. Previous releases of Gradle used to select version `8`.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Deprecation of old Tooling API version 

The following supports are deprecated now and will be removed in Gradle 5.0. You should avoid using them:

- Running Gradle older than 2.6 via Tooling API 
- Running Gradle via Tooling API older than 3.0

Please see [Gradle version and Java version compatibility](userguide/embedding.html#sec:embedding_compatibility) for more details.

### Chaining calls on `TaskInputs`

Chaining calls to `TaskInputs.property()` and `TaskInputs.properties()` is now deprecated, similar to how calls to `TaskInputs.file()` and `TaskOutputs.dir()` should not be chained since Gradle 3.0.

Don't do this:

```
task myTask {
    // Chaining all calls on `TaskInputs` is now deprecated
    inputs.property("title", title).property("version", "1.0")
}
```

Do this instead:

```
task myTask {
    inputs.property("title", title)
    inputs.property("version", "1.0")
}
```

### Deprecation of `TaskInternal.execute()`

In this release we deprecate calling `TaskInternal.execute()`. Calling `task.execute()` should never be necessary.
There are better ways for re-using task logic, for example by using [task dependencies](userguide/more_about_tasks.html#sec:adding_dependencies_to_tasks), [task rules](userguide/more_about_tasks.html#sec:task_rules), extracting a re-usable piece of logic from your task which can be called on its own (e.g. `project.copy` vs. the `Copy` task) or using the [worker API](userguide/custom_tasks.html#worker_api).

### Other deprecations

* `CompileOptions.bootClasspath` is deprecated in favor of the new `bootstrapClasspath` property.

## Potential breaking changes

### Changes to incubating native compile and link tasks

- `AbstractNativeCompileTask.compilerArgs` changed type to `ListProperty<String>` from `List<String>`.
- `AbstractNativeCompileTask.objectFileDir` changed type to `DirectoryVar` from `File`.
- `AbstractLinkTask.linkerArgs` changed type to `ListProperty<String>` from `List<String>`.

### Changes in the `eclipse` plugin

The default output location in [EclipseClasspath](dsl/org.gradle.plugins.ide.eclipse.model.EclipseClasspath.html#org.gradle.plugins.ide.eclipse.model.EclipseClasspath:defaultOutputDir) changed from `${project.projectDir}/bin` to `${project.projectDir}/bin/default`.

### Incremental build respects order of declared output files

For output properties annotated with [`@OutputFiles`](javadoc/org/gradle/api/tasks/OutputFiles.html) or [`@OutputDirectories`](javadoc/org/gradle/api/tasks/OutputDirectories.html) that evaluate to an `Iterable`, the order of the declared files is now important.
In other words, if the property changes from `[file1, file2]` to `[file2, file1]` the task will not be up-to-date. 
Prefer annotating individual properties with [`@OutputFile`](javadoc/org/gradle/api/tasks/OutputFile.html) and [`@OutputDirectory`](javadoc/org/gradle/api/tasks/OutputDirectory.html) if you can, or return a `Map` annotated with [`@OutputFiles`](javadoc/org/gradle/api/tasks/OutputFiles.html) or [`@OutputDirectories`](javadoc/org/gradle/api/tasks/OutputDirectories.html).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Tomáš Polešovský](https://github.com/topolik) - Support for FindBugs JVM arguments (gradle/gradle#781)
- [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Support PMD's analysis cache (gradle/gradle#2223)
- [zosrothko](https://github.com/zosrothko) - Make the Gradle build import into Eclipse again (gradle/gradle#2899)
- [Evgeny Mandrikov](https://github.com/Godin) - JaCoCo plugin uses version 0.7.9 by default (gradle/gradle#2892)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
