## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

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

### Force rich or plain console with `org.gradle.console`

You may now force Gradle to use rich or plain [build output](userguide/console.html#sec:console_build_output) by setting [`org.gradle.console`](userguide/build_environment.html#sec:gradle_configuration_properties) in your `gradle.properties`.

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

## Potential breaking changes

### Changes to incubating native compile and link tasks

- `AbstractNativeCompileTask.compilerArgs` changed type to `ListProperty<String>` from `List<String>`.
- `AbstractNativeCompileTask.objectFileDir` changed type to `DirectoryVar` from `File`.
- `AbstractLinkTask.linkerArgs` changed type to `ListProperty<String>` from `List<String>`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Tomáš Polešovský](https://github.com/topolik) - Support for FindBugs JVM arguments (gradle/gradle#781)
- [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Support PMD's analysis cache (gradle/gradle#2223)
- [zosrothko](https://github.com/zosrothko) - Make the Gradle build import into Eclipse again (gradle/gradle#2899)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
