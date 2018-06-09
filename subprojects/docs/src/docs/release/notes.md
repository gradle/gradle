## New and noteworthy

Here are the new features introduced in this Gradle release.

### Command line args supported by JavaExec

Since Gradle 4.9, the command line arguments can be passed to `JavaExec` with `--args`. For example, if you want to launch the application with command line arguments `foo --bar`,
you don't need to hardcode it into the build script - you can just run `gradle run --args 'foo --bar'` (see [application plugin](userguide/application_plugin.html) for more information).

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

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

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

### Using Groovy GPath with `tasks.withType()`

In previous versions of Gradle, it was sometimes possible to use a [GPath](http://docs.groovy-lang.org/latest/html/documentation/#gpath_expressions) expression with a project's task collection to build a list of a single property for all tasks.

For instance, `tasks.withType(SomeTask).name` would create a list of `String`s containing all of the names of tasks of type `SomeTask`. This was only possible with the method [`TaskCollection.withType(Class)`](javadoc/org/gradle/api/tasks/TaskCollection.html#withType-java.lang.Class-).

Plugins or build scripts attempting to do this will now get a runtime exception.  The easiest fix is to explicitly use the [spread operator](http://docs.groovy-lang.org/latest/html/documentation/#_spread_operator).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Luke Usherwood](https://github.com/lukeu) Fix `ClassCastException` when generating Eclipse files for Gradle (gradle/gradle#5278)
- [Theodore Ni](https://github.com/tjni) Reduce string allocations when working with paths. (gradle/gradle#5543)
- [Theodore Ni](https://github.com/tjni) Suppress redundant warning message (gradle/gradle#5544)
- [Lars Grefer](https://github.com/larsgrefer) Remove dependencies between `javadoc` tasks of dependent Java projects (gradle/gradle#5221)
- [Aaron Hill](https://github.com/Aaron1011) Continue executing tests if irreplaceable security manager is installed (gradle/gradle#5324)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) Throw `UnknownDomainObjectException` instead of `NullPointerException` when extension isn't found (gradle/gradle#5547)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
