## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Task for enforcing JaCoCo code coverage metrics

Gradle introduces a feature for the JaCoCo plugin strongly requested by the community: enforcing code coverage metrics. The JaCoCo plugin now provides a new task of type `JacocoCoverageVerification` enabling the user to define and enforce violation rules. Coverage verification does not automatically run as part of the `check` task. See the relevant user guide section on the “[JaCoCo plugin](userguide/jacoco_plugin.html#sec:jacoco_report_violation_rules)” for more information.
 
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

This version of Gradle introduces a new mechanism for up-to-date checking of Java compilation tasks, which is now sensitive to API changes only: if a
dependent project changed in an ABI compatible way (only its private API has changed), then the task is going to be up-to-date.
It means, for example, that if project `A` depends on project `B` and that a class in `B` is changed in an ABI-compatible way
(typically, changing only the body of a method), then we won't recompile `A`. Even finer-grained compile-avoidance can be achieved by
enabling incremental compilation, as explained below.
    
### Faster Java incremental compilation
    
The Java incremental compiler has been significantly improved. In particular, it's now capable of dealing with constants in a smarter way. It will avoid recompiling:

- if a constant is found in a dependency, but that constant isn't used in your code
- if a constant is changed in a dependency, but that constant wasn't used in your code
- if a change is made in a class containing a constant, but the value of the constant didn't change

For all those cases, the previous behavior was to recompile everything, because of the way the Java compiler inlines constants. The new incremental compiler will recompile only the small subset of potentially affected classes.
In addition, the incremental compiler is now backed by in-memory caches, avoiding a lot of disk I/O which slowed it down.

### Plugin library upgrades

Several libraries that are used by Gradle plugins have been upgraded:

- The Jacoco plugin has been upgraded to use Jacoco version 0.7.8 by default.

### Enable Gradle build scan using `--scan` Command line option

Publishing a Gradle build scan for a build can now be triggered using the `--scan` commandline option. 
This requires the Gradle build scan plugin being applied with the minimum version 1.6. 
For further information about using Gradle build scans see 

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
The [`TaskSkippedResult.getSkipMessage()`](javadoc/org/gradle/tooling/events/task/TaskSkippedResult.html#getSkipMessage\(\)) of the [Tooling API](userguide/embedding.html) now returns `"NO-SOURCE"` for such tasks, where it previously returned `"UP-TO-DATE"`.  
The [`TaskOutcome.NO_SOURCE`](javadoc/org/gradle/testkit/runner/TaskOutcome.html#NO_SOURCE) enum value of [TestKit](userguide/test_kit.html) is now returned for such tasks, where it previously returned `TaskOutcome.UP_TO_DATE`.   

### `WriteProperties` supports deferred properties

- `WriteProperties.property(String, Object)` can be used to add a property with a `Callable` or `Object` that can be coerced into a `String`.
- `WriteProperties.properties(Map<String, Object>)` can be used to add multiple properties as above. 

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
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Javadoc options should not be overwritten

`Javadoc.setOptions(MinimalJavadocOptions)` is now deprecated.

<!--
### Example deprecation
-->

## Potential breaking changes

### `WriteProperties` API for adding properties

- `WriteProperties.getProperties()` returns an immutable collection
- `WriteProperties.setProperties()` generics have been added. 

### New NO-SOURCE skip message when observing task execution via Tooling API

Please see <a href="#improved-feedback-when-skipping-tasks-with-no-source-input">Improved feedback when skipping tasks with no source input</a>.

### New NO_SOURCE task outcome when testing with GradleRunner

Please see <a href="#improved-feedback-when-skipping-tasks-with-no-source-input">Improved feedback when skipping tasks with no source input</a>.

### Setting Javadoc options

When the now deprecated `Javadoc.setOptions(MinimalJavadocOptions)` method is called with a `StandardJavadocDocletOptions`, it replaces the task's own `options` value. However, calling the method with a parameter that is not a `StandardJavadocDocletOptions` will only copy the values declared by the object, but won't replace the `options` object itself.

### compileOnly no longer extends compile

The fact that `compileOnly` extends the `compile configuration was on oversight. It made it very` hard for users to query for the dependencies that were actually "only used for compilation".
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

<!--
 - [Some person](https://github.com/some-person) - fixed some issue ([gradle/gradle#1234](https://github.com/gradle/gradle/issues/1234))
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
