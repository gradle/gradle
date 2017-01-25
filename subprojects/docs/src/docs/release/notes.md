The Gradle team is pleased to announce Gradle 3.4.

We're excited to bring you 3 incredible performance features in this release.

First up: **Compile Avoidance**. We've introduced a new mechanism for up-to-date checking of Java compilation that is sensitive to public API changes only. This means that if you change a comment or even a private API in a downstream project, Java compilation for upstream projects will be `UP-TO-DATE`.

Next: A **stable incremental Java compiler**. We've smartened the handling of constants, backed it with in-memory caches, and fixed many bugs. It is now production-ready for your build and has been promoted out of `@Incubating`. 

Finally: A brand **new Java _Library_ Plugin**. Use this when building a component intended to be used as a dependency from another project. It provides a strong separation between public (exported) and private code which not only gives great performance benefits (because consumers' compile classpaths are smaller) but also enforces architectural boundaries on the _build_ level.   

Put these together on our [perf-enterprise-large benchmark Java project](https://github.com/gradle/perf-enterprise-large), and compilation time after a method body change is reduced from [2.5 minutes](https://scans.gradle.com/s/tojo2cxznjuko) to [9 seconds](https://scans.gradle.com/s/g7i3vjskudfps)! Let's put that in perspective:
 
![perf-enterprise-large assemble](data:image/png;base64,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)

It is not just large projects that reap the benefits, for example the same use case for [Apache Polygene](https://github.com/apache/polygene-java) was reduced from [14 seconds](https://scans.gradle.com/s/pxeuv4ujnvxgi) to [7 seconds](https://scans.gradle.com/s/akggvs5dajuyi).

Now, with your help and guidance we've been able to made a couple of highly-requested code-quality plugins improvements:

- The JaCoCo plugin now allows you to [enforce code coverage metrics](https://github.com/gradle/gradle/issues/824) and fail the build if they're not met.
- The default version of JaCoCo used by the JaCoCo plugin has been raised and the plugin is now [Java 9-ready](https://github.com/gradle/gradle/issues/1006).
- The Checkstyle plugin now allows a maximum number of warnings or errors to be configured.

A special thank you to those who voted and contributed to these issues.

Last but not least, we've made it more convenient to let Gradle know when you want a [build scan](https://gradle.com) — just use `--scan` (or `--no-scan` if not). No need for the "magic" system property `-Dscan` anymore.

We hope you're able to build happiness with Gradle 3.4, and we look forward to your feedback [via Twitter](https://twitter.com/gradle) or [on GitHub](https://github.com/gradle/gradle). Happy new year from the Gradle team! 

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

In addition, the incremental compiler is more efficient and backed by in-memory caches, which avoids a lot of disk I/O that slowed it down in previous versions.

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

With the Gradle 3.3 release we have accidentally left out the name of one of our contributors. With this 3.4 release we would just like to recognize [Sebastien Requiem](https://github.com/kiddouk) for his contribution - S3 repository can be configured to authenticate using AWS EC2 instance metadata ([gradle/gradle#690](https://github.com/gradle/gradle/pull/690)).
