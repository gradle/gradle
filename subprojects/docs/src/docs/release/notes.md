The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Dan Sănduleac](https://github.com/dansanduleac),
[Andrew K.](https://github.com/miokowpak),
[Noa Resare](https://github.com/nresare),
[Juan Martín Sotuyo Dodero](https://github.com/jsotuyod),
[Semyon Levin](https://github.com/remal),
[wreulicke](https://github.com/wreulicke),
[John Rodriguez](https://github.com/jrodbx),
[mig4](https://github.com/mig4),
[Evgeny Mandrikov](https://github.com/Godin),
[Bjørn Mølgård Vester](https://github.com/bjornvester),
[Simon Legner](https://github.com/simon04),
[Sebastian Schuberth](https://github.com/sschuberth),
[Ian Kerins](https://github.com/isker),
[Ivo Anjo](https://github.com/ivoanjo),
[Stefan M.](https://github.com/StefMa),
[Nickolay Chameev](https://github.com/lukaville),
[Dominik Giger](https://github.com/gigerdo),
[Stephan Windmüller](https://github.com/stovocor),
[Zemian Deng](https://github.com/zemian),
[Robin Verduijn](https://github.com/robinverduijn),
[Sandu Turcan](https://github.com/idlsoft),
[Emmanuel Guérin](https://github.com/emmanuelguerin),
and [Christian Fränkel](https://github.com/fraenkelc).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<a name="test-fixtures"/>

## Test fixtures for Java projects

Gradle 5.6 introduces a new [Java test fixtures plugin](userguide/java_testing.html#sec:java_test_fixtures), which, when applied in combination with the `java` or `java-library` plugin, will create a conventional `testFixtures` source set.
Gradle will automatically perform the wiring so that the `test` compilation depends on test fixtures, but more importantly, it allows other projects to depend on the test fixtures of a library.
For example:

```groovy
dependencies {
   // this will add the test fixtures of "my-lib" on the compile classpath of the tests of _this_ project
   testImplementation(testFixtures(project(":my-lib")))
}
```

## Central management of plugin versions with settings script

Gradle 5.6 makes it easier to manage the versions of plugins used by your build. By configuring all plugin versions in a settings script within the new `pluginManagement.plugins {}` block, build scripts can apply plugins via the `plugins {}` block without specifying a version.

```groovy
pluginManagement {
    plugins {
        id 'org.my.plugin' version '1.1'
    }
}
```

One benefit of managing plugin versions in this way is that the `pluginManagement.plugins {}` block does not have the same constrained syntax as a build script `plugins {}` block. Plugin versions may be loaded from `gradle.properties`, or defined programmatically.

See [plugin version management](userguide/plugins.html#sec:plugin_version_management) for more details.

## Performance fix for using the Java library plugin in very large projects on Windows

Very large multi-projects can suffer from a significant performance decrease in Java compilation when switching from the `java` to the `java-library` plugin.
This is caused by the large amount of class files on the classpath, which is only an issue on Windows systems.
You can now tell the `java-library` plugin to [prefer jars over class folders on the compile classpath](userguide/java_library_plugin.html#sec:java_library_known_issues_windows_performance) by setting the `org.gradle.java.compile-classpath-packaging` system property to `true`.

## Improvements for plugin authors

### Task dependencies are honored for `@Input` properties of type `Provider`

Gradle can automatically calculate task dependencies based on the value of certain task input properties. 
For example, for a property that is annotated with `@InputFiles` and that has type `FileCollection` or `Provider<Set<RegularFile>>`, 
Gradle will inspect the value of the property and automatically add task dependencies for any task output files or directories in the collection. 

In this release, Gradle also performs this analysis on task properties that are annotated with `@Input` and that have type `Provider<T>` (which also includes types such as `Property<T>`).
This allow you to connect an output of a task to a non-file input parameter of another task.
For example, you might have a task that runs the `git` command to determine the name of the current branch, and another task that uses the branch name to produce an application bundle.
With this change you can connect the output of the first task as an input of the second task, and avoid running the `git` command at configuration time. 

See the [user manual](userguide/lazy_configuration.html#sec:working_with_task_dependencies_in_lazy_properties) for examples and more details.

### Convert a `FileCollection` to a `Provider`

A new method `FileCollection.getElements()` has been added to allow the contents of the file collection to be viewed as a `Provider`. This `Provider` tracks the elements of the file collection and tasks that
produce these files and can be connected to a `Property` instance.
 
### Finalize the value of a `ConfigurableFileCollection`

A new method `ConfigurableFileCollection.finalizeValue()` has been added. This method resolves deferred values, such as `Provider` instances or Groovy closures or Kotlin functions, that may be present in the collection 
to their final file locations and prevents further changes to the collection.

This method works similarly to other `finalizeValue()` methods, such as `Property.finalizeValue()`.

### Prevent changes to a `Property` or `ConfigurableFileCollection`

New methods `Property.disallowChanges()` and `ConfigurableFileCollection.disallowChanges()` have been added. These methods disallow further changes to the property or collection.

### `Provider` methods

New methods `Provider.orElse(T)` and `Provider.orElse(Provider<T>)` has been added. These allow you to perform an 'or' operation on a provider and some other value.

### Worker API improvements

TBD

## Improved handling of ZIP archives on classpaths

Compile classpath and runtime classpath analysis will now detect the most common zip extension instead of only supporting `.jar`.
It will inspect nested zip archives as well instead of treating them as blobs. This improves the likelihood of cache hits for tasks
that take such nested zips as an input, e.g. when testing applications packaged as a fat jar.

The ZIP analysis now also avoids unpacking entries that are irrelevant, e.g. resource files on a compile classpath. 
This improves performance for projects with a large amount of resource files.

## Support for PMD incremental analysis

TBD

This was contributed by [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod).

## Incubating support for Groovy compilation avoidance

Gradle now supports experimental compilation avoidance for Groovy. 
This accelerates Groovy compilation by avoiding re-compiling dependent projects if only non-ABI changes are detected.
See [Groovy compilation avoidance](userguide/groovy_plugin.html#sec:groovy_compilation_avoidance) for more details.

## Experimental incremental Groovy compilation

Gradle now supports experimental incremental compilation for Groovy.
If only a small set of Groovy source files are changed, only the affected source files will be recompiled.
For example, if you only change a few Groovy test classes, you don't need to recompile all Groovy test source files - only the changed ones need to be recompiled.
See [Incremental Groovy compilation](userguide/groovy_plugin.html#sec:incremental_groovy_compilation) in the user manual for more details.

## Closed Eclipse Buildship projects

Closed gradle projects in an eclipse workspace can now be substituted for their respective jar files. In addition to this 
those jars can now be built during Buildship eclipse model synchronization.

The upcoming version of Buildship is required to take advantage of this behavior.

This was contributed by [Christian Fränkel](https://github.com/fraenkelc).

## Executable Jar support with `project.javaexec` and `JavaExec`

TBD

## File case changes when copying files on case-insensitive file systems are now handled correctly

On case-insensitive file systems (e.g. NTFS and APFS), a file/folder rename where only the case is changed is now handled properly by Gradle's file copying operations. 
For example, renaming an input of a `Copy` task called `file.txt` to `FILE.txt` will now cause `FILE.txt` being created in the destination directory. 
The `Sync` task and `Project.copy()` and `sync()` operations now also handle case-renames as expected.

## Fail the build on deprecation warnings

The `warning-mode` command line option now has a [new `fail` value](userguide/command_line_interface.html#sec:command_line_warnings) that will behave like `all` and in addition fail the build if any deprecation warning was reported during the execution.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Transforming dependency artifacts on resolution

The API around [artifact transforms](userguide/dependency_management_attribute_based_matching.html#sec:abm_artifact_transforms) is not incubating any more.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
