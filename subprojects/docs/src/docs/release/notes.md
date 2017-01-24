The Gradle team is pleased to announce Gradle 3.4.

This release improves incremental build performance for Java-based projects thanks to three major features: Java **compile-avoidance**, a **more efficient incremental Java compiler** and the new **Java Library plugin** that allows you to separate your API and implementation dependencies. In our large Java [test project](https://github.com/gradle/perf-enterprise-large), compilation time after a method body change was reduced from [2.5 minutes](https://scans.gradle.com/s/tojo2cxznjuko) to [9 seconds](https://scans.gradle.com/s/g7i3vjskudfps).

The community's voice clearly indicated the need for [verifying JaCoCo code coverage metrics](https://github.com/gradle/gradle/issues/824). The JaCoCo plugin in Gradle 3.4 verifies code coverage metrics and will fail the build if code coverage falls below a configurable threshold. The plugin is also [fully prepared to run on Java 9](https://github.com/gradle/gradle/issues/1006).

Finally, [Gradle build scans](https://gradle.com) has become more convenient to use with the command-line options `--scan` and `--no-scan`. Builds no longer have the hassle of setting a "magic" system property `-Dscan`.

Enjoy this new version and let us know what you think! We are looking forward to your strong involvement in making Gradle even better.

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Compile-avoidance for Java

This version of Gradle introduces a new mechanism for up-to-date checking of Java compilation that is sensitive to public API changes only. 

If a dependent project has changed in an [ABI](https://en.wikipedia.org/wiki/Application_binary_interface)-compatible way (only its private API has changed), then Java compilation tasks will now be up-to-date.

This means that if project `A` depends on project `B` and a class in `B` is changed in an ABI-compatible way
(typically, changing only the body of a method), then Gradle won't recompile `A`. Even finer-grained compile-avoidance can be achieved by enabling incremental Java compilation, as explained below.

Some of the types of changes that do not affect the public API and are ignored: 

- Changing a method body.
- Changing a comment.
- Adding, removing or changing private methods, fields, or inner classes.
- Adding, removing or changing a resource.
- Changing the name of jars or directories in the classpath.

Compile-avoidance can greatly improve incremental build time, as Gradle now avoids recompiling source files that will produce the same bytecode as the last time.

### Compile-avoidance in the presence of annotation processors

Compile-avoidance is deactivated if annotation processors are found on the compile classpath, because for annotation processors the implementation details matter. To better separate these concerns, the `CompileOptions` for the `JavaCompile` task type now defines a `annotationProcessorPath` property.

If you are using annotation processors and want optimal performance, make sure to separate them from your compile classpath.

    configurations {
      apt
    }
    dependencies {
      apt 'some.cool:annotation.processor:1.0'
    }
    tasks.withType(JavaCompile) {
      options.annotationProcessorPath = configurations.apt
    }

### Faster Java incremental compilation

The Java incremental compiler has been improved to deal with constants in a smarter way. 

Due to the way constants are inlined by the Java compiler, previous Gradle releases have taken a conservative approach and recompiled all sources when a constant has changed. Now Gradle avoids recompiling under the following conditions:

- if a constant is found in a dependency, but that constant isn't used in your code
- if a constant is changed in a dependency, but that constant isn't used in your code
- if a change is made in a class containing a constant, but the value of the constant didn't change

The incremental compiler will recompile only a small subset of the potentially affected classes now.

In addition, the incremental compiler is more efficient and backed by in-memory caches, which avoids a lot of disk I/O that slowed it down before.

### Stable Java incremental compilation

The Java incremental compiler is no longer incubating and is now considered stable. This Gradle release includes many bug fixes and improved performance for incremental Java compilation.

Note that incremental Java compilation is not enabled by default. It needs to be [activated explicitly](userguide/java_plugin.html#sec:incremental_compile). We encourage all Gradle users to give it a try in their projects.

### The Java Library plugin

The new [Java Library plugin](userguide/java_library_plugin.html) is the next step towards improved modeling of the Java ecosystem, and should be
used whenever you are building a Java component aimed at being consumed by other components. This is typically called a library, and it has many
advantages:

- a clean [separation of the API and implementation](userguide/java_library_plugin.html#sec:java_library_separation) of the component
- avoiding leaking the [compile classpath](userguide/java_library_plugin.html#sec:java_library_configurations_graph) of the library to consumers
- faster compilation thanks to the clean separation of API and implementation

We strongly encourage users to migrate to this plugin, instead of the `java` plugin, whenever they are building a library. Some
of the new configurations of this plugin are available to the `java` plugin too, and others are just deprecated.

- instead of `compile`, you should use one of `implementation` or `api`
- instead of `runtime`, you should use `runtimeOnly`

### @CompileClasspath annotation for task properties

Java compile-avoidance is implemented using a new [@CompileClasspath](javadoc/org/gradle/api/tasks/CompileClasspath.html) annotation that can be attached to a task property, similar to the `@InputFiles` or `@Classpath` annotations. 

This new annotation is also available for use in your own tasks as well, for those tasks that take a Java compile classpath. For example, you may have a task that performs static analysis using the signatures of classes. You can use the `@CompileClasspath` annotation for this task instead of `@InputFiles` or `@Classpath`, to avoid running the task when the class signatures have not changed.
   
### Task for enforcing JaCoCo code coverage metrics

Gradle introduces a feature for the JaCoCo plugin strongly requested by the community: enforcing code coverage metrics. The JaCoCo plugin now provides a new task of type `JacocoCoverageVerification` enabling the user to define and enforce violation rules. Coverage verification does not automatically run as part of the `check` task. Please see the relevant user guide section on the “[JaCoCo plugin](userguide/jacoco_plugin.html#sec:jacoco_report_violation_rules)” for more information.
 
    tasks.withType(JacocoCoverageVerification) {
        violationRules {
            rule {
                limit {
                    minimum = 0.5
                }
            }
        }
    }

### Gradle removes source set output directories on upgrade

Gradle keeps information about each task's inputs and outputs in your project's `.gradle` directory. If this information is lost or cannot be read, your build directory can be in an inconsistent state with stale files from previous builds. [GitHub issue #1018](https://github.com/gradle/gradle/issues/1018) is an example of the problems this can cause.

Gradle now removes the output directories for source sets when this situation is detected. Most often, this occurs when performing a Gradle upgrade because the information kept in `.gradle` is not backwards compatible.

There are other situations where output files are not cleaned up, such as removing a sub project or a task from the build. You can follow the progress on [GitHub issue #821](https://github.com/gradle/gradle/issues/821).

### Plugin library upgrades

The JaCoCo plugin has been upgraded to use [JaCoCo version 0.7.8](http://www.eclemma.org/jacoco/trunk/doc/changes.html) by default.

### Command line options for creating build scans

You can now create a [build scan](https://gradle.com) by using the `--scan` command line option.
To explicitly disable creating a build scan, use the `--no-scan` command line option.

For more information about build scans, see [https://gradle.com](https://gradle.com).

### Improved feedback when skipping tasks with no source input 

It is relatively common to have tasks within a build that can be skipped because they have no input source.
For example, the standard `java` plugin creates a `compileTestJava` task to compile all java source at `src/test/java`.
If at build time there are no source files in this directory the task can be skipped early, without invoking a Java compiler.

Previously in such scenarios Gradle would emit:

<pre class="tt"><tt>:compileTestJava UP-TO-DATE</tt></pre>

This is now communicated as:

<pre class="tt"><tt>:compileTestJava NO-SOURCE</tt></pre>

A task is said to have no source if all of its input file properties that are annotated with [`@SkipWhenEmpty`](javadoc/org/gradle/api/tasks/SkipWhenEmpty.html) are _empty_ (i.e. no value or an empty set of files).

APIs that communicate that outcome of a task have been updated to accommodate this new outcome.  
The [`TaskSkippedResult.getSkipMessage()`](javadoc/org/gradle/tooling/events/task/TaskSkippedResult.html#getSkipMessage\(\)) of the [Tooling API](userguide/embedding.html) now returns `NO-SOURCE` for such tasks, where it previously returned `UP-TO-DATE`.  
The [`TaskOutcome.NO_SOURCE`](javadoc/org/gradle/testkit/runner/TaskOutcome.html#NO_SOURCE) enum value of [TestKit](userguide/test_kit.html) is now returned for such tasks, where it previously returned `TaskOutcome.UP_TO_DATE`.   

### Deferred evaluation for WriteProperties task

The `WriteProperties` task that was introduced in Gradle 3.3 now supports deferred evaluation for properties:

- `WriteProperties.property(String, Object)` can be used to add a property with a `Callable` or `Object` that can be coerced into a `String`.
- `WriteProperties.properties(Map<String, Object>)` can be used to add multiple properties as above. 

### Initial support for reproducible archives

When Gradle creates an archive, the order of the files in the archive is based on the order that Gradle visits each file. This means that even archive tasks with identical inputs can produce archives with different checksums. We have added initial support for reproducible archives, which tries to create an archive in a byte-for-byte equivalent manner.

For more information visit the [section in the user guide about reproducible archives](userguide/working_with_files.html#sec:reproducible_archives).

We would love to get feedback from you about this incubating feature!

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Incremental Java compilation

With the improvements made to the incremental Java compiler in this release, this is great time to promote this feature. If you want to make use of it, please keep in mind that it needs to be [activated explicitly](userguide/java_plugin.html#sec:incremental_compile).

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Javadoc options should not be overwritten

`Javadoc.setOptions(MinimalJavadocOptions)` is now deprecated.

### JaCoCo class dump directory property renamed

`JacocoTaskExtension.classDumpFile` is now called `classDumpDir` and the old property is deprecated.

<!--
### Example deprecation
-->

## Potential breaking changes

### WriteProperties task API

- `WriteProperties.getProperties()` returns an immutable collection
- `WriteProperties.setProperties()` generics have been added. 

### Skipping tasks with no source

- New `NO-SOURCE` skip message when observing task execution via Tooling API
- New `NO_SOURCE` task outcome when testing with GradleRunner

Please see <a href="#improved-feedback-when-skipping-tasks-with-no-source-input">Improved feedback when skipping tasks with no source input</a>.

### Setting Javadoc options

When the now deprecated `Javadoc.setOptions(MinimalJavadocOptions)` method is called with a `StandardJavadocDocletOptions`, it replaces the task's own `options` value. However, calling the method with a parameter that is not a `StandardJavadocDocletOptions` will only copy the values declared by the object, but won't replace the `options` object itself.

### compileOnly no longer extends compile

The fact that `compileOnly` extends the `compile` configuration was an oversight. It made it very hard for users to query for the dependencies that were actually "only used for compilation". With this release of Gradle, `compileOnly` no longer extends the `compile` configuration.

### IDEA mapping has been simplified

The mapping from Gradle's configurations to IntelliJ IDEA's scopes has been drastically simplified. There used to be a lot of hardcoded mappings and pseudo-scopes in order to reduce the number of dependency declarations in the .iml files.
These hardcoded mappings were intransparent to the user and added a lot of complexity to the codebase. Thus we decided to reimplement IDEA mapping with a very simple scheme:

- The core Gradle plugins now use `idea.module.scopes.SCOPENAME.plus/minus` just like a user would
- Only `COMPILE`, `PROVIDED`, `RUNTIME` and `TEST` are valid scope names. The (undocumented) pseudo-scopes like `RUNTIME_TEST` no longer have any effect
- the following default mappings apply when using the `java` plugin
    - the `COMPILE` scope is empty
    - the `PROVIDED` scope contains the `compileClasspath` configuration
    - the `RUNTIME` scope contains the `runtimeClasspath` configuration
    - the `TEST` scope contains the `testCompileClasspath` and `testRuntimeClasspath` configurations

This means that some `runtime` dependencies might be visible when using auto-completion in test classes. This felt like a small price to pay, since the same was already true for `testRuntime` dependencies.

We have thoroughly tested these new mappings and found them to work well. Nevertheless, if you encounter any problems importing projects into IDEA, please let us know.

### runtimeClasspath used instead of runtime

When resolving the runtime classpath for Java applications, Gradle will now use the `runtimeClasspath` configuration instead of the `runtime` configuration. If you previously attached resolution rules to `runtime`, you should apply them to `runtimeClasspath` instead or apply them to all configurations.

Tooling providers should try not to depend on configurations directly, but use `sourceSet.runtimeClasspath` where applicable. This always contains the correct classpath for the current Gradle version and has been changed to return the `runtimeClasspath` configuration in this release. If you are directly resolving the `runtime` configuration, your tool will not work with the `java-library` plugin.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Bo Zhang](https://github.com/blindpirate) - Fixed a typo in Tooling API Javadoc ([gradle/gradle#1034](https://github.com/gradle/gradle/pull/1034))
 - [Anne Stellingwerf](https://github.com/astellingwerf) - Fixed final fields being excluded from the API jar ([gradle/gradle#819](https://github.com/gradle/gradle/issues/819))
 - [Francis Andre](https://github.com/zosrothko) - Added a chapter about running and debugging Gradle under Eclipse ([gradle/gradle#880](https://github.com/gradle/gradle/pull/880))
 - [Alex Arana](https://github.com/alex-arana) - Added max allowed violations to checkstyle plugin ([gradle/gradle#780](https://github.com/gradle/gradle/pull/780))
 - [Marco Vermeulen](https://github.com/marc0der) - Made Scala sample projects more idiomatic ([gradle/gradle#744](https://github.com/gradle/gradle/pull/744))
 - [Paul Balogh](https://github.com/javaducky) - Fix missed build.gradle files in user guide chapter on multi-project builds ([gradle/gradle#915](https://github.com/gradle/gradle/pull/915))
 - [Alex McAusland](https://github.com/banderous) - Fixed README link for contributing to Gradle ([gradle/gradle#915](https://github.com/gradle/gradle/pull/1047))
 - [Andrew Oberstar](https://github.com/ajoberstar) - Initial design doc for JUnit Platform support ([gradle/gradle#946](https://github.com/gradle/gradle/pull/946))
 - [Ingo Kegel](https://github.com/ingokegel) - Support for `jdkName` from idea module model ([gradle/gradle#989](https://github.com/gradle/gradle/pull/989))

<!--
 - [Some person](https://github.com/some-person) - fixed some issue ([gradle/gradle#1234](https://github.com/gradle/gradle/issues/1234))
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## Erratum

With the Gradle 3.3 release we have accidentaly left out the name of one of our contributors. With this 3.4 release we would just like to recognise [Sebastien Requiem](https://github.com/kiddouk) for his contribution - S3 repository can be configured to authenticate using AWS EC2 instance metadata ([gradle/gradle#690](https://github.com/gradle/gradle/pull/690)).
