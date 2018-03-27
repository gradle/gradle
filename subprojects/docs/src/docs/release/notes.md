The Gradle team is pleased to announce Gradle 4.7.

First and foremost, Gradle's incremental Java compiler can now run _annotation processing incrementally_. No user-facing configuration is necessary, but processor authors need to opt-in. We request annotation processor authors read the [documentation for this feature](userguide/java_plugin.html#sec:incremental_annotation_processing) and contact the Gradle team via the [forum](https://discuss.gradle.org/c/help-discuss) for assistance.

Next, Gradle log output is now grouped under a header by project and task for non-interactive environments, making debugging parallel tasks much easier to debug on CI.

This release supports running Gradle on JDK 10.

User experience for running tests is further improved: _failed tests now run first_. This allows use of the [`--fail-fast` option](userguide/java_plugin.html#sec:test_execution) to provide the quickest possible feedback loop.

Last but not least, the _IDEA Gradle Plugin now automatically marks Java resources directories_ as resources in the IDEA module definitions. This behavior can be customized; see an example.

We hope you will build happiness with Gradle 4.7, and we look forward to your feedback [via Twitter](https://twitter.com/gradle) or [on GitHub](https://github.com/gradle).

## Upgrade instructions

Switch your build to use Gradle 4.7 RC1 quickly by updating your wrapper properties:

    gradle wrapper --gradle-version=4.7-rc-1

Standalone downloads are available at [gradle.org/releases](https://gradle.org/release-candidate). 

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

### Rerun failed tests first

Now, in the subsequent test, Gradle will execute the previous failed test class first. With [`--fail-fast`](userguide/java_plugin.html#sec:test_execution) option introduced in `4.6`, this can provide a much faster feedback loop for development.

### Support for resources and test resources in the IDEA plugin

The IDEA plugin now automatically marks your Java resource directories (e.g. `src/main/resources`) as resources in the IDEA project hierarchy. From now on it is also possible to mark additional directories as resources or test resources in the IDEA module:

    idea {

        module {
            //and some extra resource dirs
            resourceDirs += file('src/main/some-extra-resource-dir')

            //and some extra test resource dirs
            testResourceDirs += file('src/test/some-extra-test-resource-dir')
        }
    }

### Incremental annotation processing

Gradle's incremental Java compiler can now also run annotation processing incrementally. 
No user-facing configuration is necessary, but processor authors need to opt in.
If you are a processor author, have a look at the [user guide](userguide/java_plugin.html#sec:incremental_annotation_processing) to find out how to make your processor compatible.
    
### Gradle console improvements

Gradle has two basic console modes, which determine how Gradle formats the text output it generates: The 'plain' console mode is used by default when Gradle is running without without an associated console, for example from an IDE or a CI build agent, and the 'rich' console is used by default when Gradle is running with an associated console, for example when running from the command-line.

In previous releases, the rich console had some features that the plain console was missing. These are now available for the plain console as well. In this Gradle release, the plain console groups the output from each task is grouped with a header rather than interleaving the output. This makes diagnosing issues on CI using the log output much easier.

TBD - build scan task output grouping
    
### Default JaCoCo version upgraded to 0.8.1

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.1](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### De-incubation of Google repository shortcut method

The method `RepositoryHandler.google()` has been promoted.

### De-incubation of Project#findProperty

See [javadocs](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html#findProperty-java.lang.String-) for details

### De-incubation of several Groovy compile options

The following Groovy compile options have been promoted:

- [configurationScript](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/GroovyCompileOptions.html#getConfigurationScript--)
- [javaAnnotationProcessing](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/GroovyCompileOptions.html#isJavaAnnotationProcessing--)
- [fileExtensions](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/GroovyCompileOptions.html#getFileExtensions--)

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

* `Task.deleteAllActions()` is deprecated without replacement.

### Change of default Checkstyle configuration directory

With this release of Gradle, the Checkstyle configuration file is discovered in the directory `config/checkstyle` of the root project and automatically applies to all sub projects without having to set a new location for the `configDir` property.
The Checkstyle configuration file in a sub project takes precedence over the file provided in the root project to support backward compatibility.

### Special casts for `FileCollection` using the Groovy `as` operator

Previously it was possible to cast a `FileCollection` instance to some special types using the Groovy `as` keyword.
This is now discontinued.

- the `FileCollection.asType(Class)` method is deprecated
- casting `fileCollection as File` is deprecated, use `FileCollection.getSingleFile()` instead
- casting `fileCollection as File[]` is deprecated
- casting `fileCollection as FileTree` is deprecated, use `FileCollection.getAsFileTree()` instead

Using the `as` operator to cast `FileCollection` to `Object[]`, `Collection`, `Set` and `List` is still supported.

## Potential breaking changes

### Gradle console output changes

The plain console mode now formats output consistently with the rich console, which means that the output format has changed. This may break tools that scrape details from the console output.

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

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

 - [Piotr Kubowicz](https://github.com/pkubowicz) - Make CheckStyle plugin work by default for multi-project builds (gradle/gradle#2811)
 - [Philippe Agra](https://github.com/philippeagra) - Fix annotation-processors cache (gradle/gradle#4680)
 - [Jesper Utoft](https://github.com/jutoft) - Improve ear plugin (gradle/gradle#4381)
 - [Henrik Brautaset Aronsen](https://github.com/henrik242) - Upgrade ASM to `6.1` (gradle/gradle#4696)
 - [Evgeny Mandrikov](https://github.com/Godin) - Upgrade JaCoCo to `0.8.1` (gradle/gradle#4807)
 - [Oleksandr Kulychok](https://github.com/kool79) - Don't set parallel mode and threadCount for testng unless explicitly defined (gradle/gradle#4794)
 - [Peter Ledbrook](https://github.com/pledbrook) - Fix typo (gradle/gradle#4801)
 - [Brett Randall](https://github.com/javabrett) - Always report Checkstyle violations-summary (gradle/gradle#3901)
 - [Fred Deschenes](https://github.com/FredDeschenes) - Removed extraneous 'chmod' (gradle/gradle#4779)
 - [Frantisek Veverka](https://github.com/fanick1) - Add support for resources and test resources in IDEA module (gradle/gradle#3724)
 

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
