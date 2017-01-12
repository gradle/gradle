The Gradle team is pleased to announce Gradle 3.4 RC-1.

There is definitely a Java flavor to this release with many of they key release features focusing on the Java ecosystem.

In a further step in our commitment to continual performance improvement, this release of Gradle introduces **compile-avoidance** for Java as well as significant improvements **incremental compilation** for Java. 

We have made various improvements have been made to the **Jacoco plugin** and **delayed evaluation** can now be used for properties in the `WriteProperties` task.

Source tasks such as `JavaCompile` that are skipped due to nil source inputs will now provide this feedback, rather that just reporting as being up to date.

We have also fixed a previous oversight in the `compileOnly` configuration to limit dependencies to those only required for compilation.

Finally, we have also added a command-line option for **Gradle build scan**, eliminating the need for passing a system property and tasks

Enjoy the new version and let us know what you think!

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

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

### Compile-avoidance for Java

This version of Gradle introduces a new mechanism for up-to-date checking of Java compilation tasks, which is now sensitive to public API changes only: if a
dependent project changed in an ABI-compatible way (only its private API has changed), then the task is going to be up-to-date.
It means, for example, that if project `A` depends on project `B` and that a class in `B` is changed in an ABI-compatible way
(typically, changing only the body of a method), then we won't recompile `A`. Even finer-grained compile-avoidance can be achieved by
enabling incremental compilation, as explained below.
    
### Faster Java incremental compilation
    
The Java incremental compiler has been significantly improved. In particular, it's now capable of dealing with constants in a smarter way. Due to the way constants are inlined by the Java compiler, previsous Gradle releases have always taken the conservative approach and recompiled everything. Now it will avoid recompiling:

- if a constant is found in a dependency, but that constant isn't used in your code
- if a constant is changed in a dependency, but that constant wasn't used in your code
- if a change is made in a class containing a constant, but the value of the constant didn't change

The new incremental compiler will recompile only the small subset of potentially affected classes.

In addition, the incremental compiler is now backed by in-memory caches, avoiding a lot of disk I/O which slowed it down.

### Annotation processor path for Java compilation

The `CompileOptions` for the `JavaCompile` task type now defines a `annotationProcessorPath` property, which allows you to specify the annotation processor path to use for compilation. This path is treated as an input for the compilation task, meaning that the annotation processor path is built as required, and the contents is considered for incremental build.

### Remove stale outputs on Gradle upgrade

Gradle keeps information about each task's inputs and outputs in your project's `.gradle` directory. If this information is lost, your build can be in an inconsistent state.

Gradle now removes `buildDir` when this situation is detected. This new behavior is only enabled when a project applies the `base` plugin and works similarly to running `clean`.

### Plugin library upgrades

The Jacoco plugin has been upgraded to use Jacoco version 0.7.8 by default.

### Command line options for creating build scans

You can now create a [build scans](https://gradle.com) by using the `--scan` command line option.
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

### Jacoco class dump directory property renamed

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

The fact that `compileOnly` extends the `compile` configuration was on oversight. It made it very hard for users to query for the dependencies that were actually "only used for compilation".

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

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Bo Zhang](https://github.com/blindpirate) - Fixed a typo in Tooling API Javadoc ([gradle/gradle#1034](https://github.com/gradle/gradle/pull/1034))
 - [Anne Stellingwerf](https://github.com/astellingwerf) - Fixed final fields being excluded from the API jar ([gradle/gradle#819](https://github.com/gradle/gradle/issues/819))
 - [zosrothko](https://github.com/zosrothko) - Added a chapter about running and debugging Gradle under Eclipse ([gradle/gradle#880](https://github.com/gradle/gradle/pull/880))
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
