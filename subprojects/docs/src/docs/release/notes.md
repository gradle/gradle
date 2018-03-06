## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Support for mapped and named nested inputs

When dealing with task inputs, it may be that not all values are known upfront.
For example, for the `findbugs` plugin, it is not clear at configuration time which reports will be enabled by the time the task executes.
For each report itself it is easy to declare the inputs and outputs, this is just a matter of annotating the concrete report class.
Now it is also easy to [declare the map of enabled reports](https://github.com/gradle/gradle/blob/2376cd3824ea683c1af122f8a582ceb6ef51ec3b/subprojects/reporting/src/main/java/org/gradle/api/reporting/internal/DefaultReportContainer.java#L121-L124) as an input:
    
    @Nested
    public Map<String, Report> getEnabledReports() {
        return getEnabled().getAsMap();
    }            
    
This causes each report to be added as a [nested input](userguide/more_about_tasks.html#sec:task_input_nested_inputs) with the key as a name.
For example, the output directory of the FindBugs html report is added as `reports.html.destination` by the above declaration.

When annotating an iterable with [`@Nested`](javadoc/org/gradle/api/tasks/Nested.html), Gradle already treats each element as a separate nested input.
In addition, if the element implements `Named`, the `name` is now used as property name.
This allows for declaring nice names when adding `CommandLineArgumentProviders`, as for example done by [`JacocoAgent`](https://github.com/gradle/gradle/blob/1c6fa2d1fa794456d48a5268f6c2dfb85ff30cbf/subprojects/jacoco/src/main/java/org/gradle/testing/jacoco/plugins/JacocoPluginExtension.java#L139-L163).
    
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

## Potential breaking changes

### Changes in the caching of missing versions

Previously, Gradle would refresh the version list when dynamic version cache expires for repositories that did not contain a version at all.
From now on, Gradle [will cache forever that a `group:name` was absent](https://github.com/gradle/gradle/issues/4436) from a repository.
This has a positive performance on dependency resolution when multiple repositories are defined and dynamic versions are used.
As before, using `--refresh-dependencies` will force a refresh, bypassing all caching.

### Changes to native compilation, linking and installation tasks

To follow idiomatic [Provider API](userguide/lazy_configuration.html) practices, many tasks related to compiling and linking native libraries and applications have been converted to use the Provider API.

Tasks extending `org.gradle.nativeplatform.tasks.AbstractLinkTask`, which include `org.gradle.nativeplatform.tasks.LinkExecutable` and `org.gradle.nativeplatform.tasks.LinkSharedLibrary`.

- `getDestinationDir()` was replaced by `getDestinationDirectory()`.
- `getBinaryFile()`, `getOutputFile()` was replaced by `getLinkedFile()`.
- `setOutputFile(File)` was removed. Use `Property.set()` instead.
- `setOutputFile(Provider)` was removed. Use `Property.set()` instead.
- `getTargetPlatform()` was changed to return a `Property`.
- `setTargetPlatform(NativePlatform)` was removed. Use `Property.set()` instead.
- `getToolChain()` was changed to return a `Property`.
- `setToolChain(NativeToolChain)` was removed. Use `Property.set()` instead.

Task type `org.gradle.nativeplatform.tasks.CreateStaticLibrary`

- `getOutputFile()` was changed to return a `Property`.
- `setOutputFile(File)` was removed. Use `Property.set()` instead.
- `setOutputFile(Provider)` was removed. Use `Property.set()` instead.
- `getTargetPlatform()` was changed to return a `Property`.
- `setTargetPlatform(NativePlatform)` was removed. Use `Property.set()` instead.
- `getToolChain()` was changed to return a `Property`.
- `setToolChain(NativeToolChain)` was removed. Use `Property.set()` instead.
- `getStaticLibArgs()` was changed to return a `ListProperty`.
- `setStaticLibArgs(List)` was removed. Use `ListProperty.set()` instead.

Task type `org.gradle.nativeplatform.tasks.InstallExecutable`

- `getPlatform()` replaced by `getTargetPlatform()`.
- `setTargetPlatform(NativePlatform)` was removed. Use `Property.set()` instead.
- `getToolChain()` was changed to return a `Property`.
- `setToolChain(NativeToolChain)` was removed. Use `Property.set()` instead.

Task types `org.gradle.language.assembler.tasks.Assemble`, `org.gradle.language.rc.tasks.WindowsResourceCompile`, `org.gradle.nativeplatform.tasks.StripSymbols`, `org.gradle.nativeplatform.tasks.ExtractSymbols`, `org.gradle.language.swift.tasks.SwiftCompile`, and `org.gradle.nativeplatform.tasks.LinkMachOBundle` were changed in similar ways.

### Removed incubating method `BuildIdentifier.isCurrentBuild()`

TBD - This method is not longer available, so that a `BuildIdentifier` instance may be used to represent a build from anywhere in the Gradle invocation.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
