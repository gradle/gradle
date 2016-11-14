## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Clickable links to project reports

When generating project reports with the [Project Reports Plugin](userguide/project_reports_plugin.html), Gradle now displays a clickable URL.

### Custom task property annotations can be overridden in subclasses

In previous versions of Gradle, a custom task class overriding a property from a base class couldn't reliably change the type of the property via annotations. It is now possible to change an `@InputFiles` property to `@Classpath` or an `@OutputFile` to `@OutputDirectory`. This can be useful when extending or working around problems with custom tasks that you do not control.

    class BrokenTask extends DefaultTask {
        @InputFile File inputFile // wrong, task does not depend on the contents of this file.
        @OutputDirectory File outputDir
        @TaskAction void generate() { ... }
    }

    class FixedTask extends BrokenTask {
        @Internal File inputFile // fixed, internal properties are ignored in up-to-date checks.
    }

In the above example, `FixedTask.inputFile` will be a ignored in up-to-date checks.

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

<!--
### Example deprecation
-->

### Deprecated Ant-related Java compiler properties

The Ant-based Java compiler itself was removed in Gradle 2.0. We now have deprecated the Ant-based `<depend/>` task support as well. These properties will be removed in Gradle 4.0.

* `JavaCompile.dependencyCacheDir`
* `JavaCompileSpec.dependencyCacheDir`
* `JavaPluginConvention.dependencyCacheDir`
* `JavaPluginConvention.dependencyCacheDirName`
* `CompileOptions.useDepend`
* `CompileOptions.depend()`

### Deprecated methods

* `ProjectDependency.getProjectConfiguration()` is deprecated, and will be removed in Gradle 4.0. A project dependency is not guaranteed to resolve to a particular `Configuration` instance, for example, when dependency substitution rules are used, so the return value of this method can be misleading. To determine the actual target for a project dependency, you should query the resolution results provided by `Configuration.getIncoming().getResolutionResult()`.
* `ModuleDependency.getConfiguration()` is deprecated, replaced by `getTargetConfiguration()`. This method will be removed in Gradle 4.0.
* `FileCollectionDependency.registerWatchPoints()` is deprecated. This method is intended only for internal use and will be removed in Gradle 4.0. You can use the new `getFiles()` method as a replacement, if required.

## Potential breaking changes

### BuildInvocations model is always returned for the connected project

In previous Gradle versions, when connected to a sub-project and asking for the `BuildInvocations` model using a `ProjectConnection`,
the `BuildInvocations` model for the root project was returned instead. Gradle will now
return the `BuildInvocations` model of the project that the `ProjectConnection` is connected to.

### Java `Test` task doesn't track working directory as input

Previously changing the working directory for a `Test` task made the task out-of-date. Changes to the contents had no such effect: Gradle was only tracking the path of the working directory. Tracking the contents would have been problematic since the default working directory is the project directory. 

Most tests don't rely on the working directory at all and those that do depend on its contents.

From Gradle 3.3, the working directory is not tracked at all. Due to this, changing the path of the working directory between builds won't make the task out-of-date.

If it's needed, the working directory can be added as an explicit input to the task, with contents tracking:

    test {
        workingDir "$buildDir/test-work"
        inputs.dir workingDir
    }

To restore the previous behavior of tracking only the path of the working directory:

    test {
        inputs.property "workingDir", workingDir
    }

### Order of task property annotations from hierarchy 

The annotated type of a property (`@InputFile`, `@OutputFile`, etc) for a custom task is now determined by the class hierarchy when conflicting types are present. In previous Gradle releases, the way the conflict was resolved was unspecified. This change affects incremental builds and may cause Gradle to treat a property as a different kind of input or output than it did.

See [Custom task property annotations can be overridden in subclasses](#custom-task-property-annotations-can-be-overridden-in-subclasses) above for an example.

### `LenientConfiguration.getFiles()` returns the same set of files as other dependency query methods

There are several different methods you can use to query the set of files for the dependencies defined for a `Configuration`.
One such method is `LenientConfiguration.getFiles()`. In previous versions of Gradle this method would not include files defined by file dependencies. These are dependencies that are declared using a `FileCollection`, such as:

    dependencies {
        compile fileTree(dir: 'some-dir', include: '**/*.jar')
    }
    
In this version of Gradle, `LenientConfiguration.getFiles()` now includes these files in the result. This change makes this method consistent with other query methods such as `ResolvedConfiguration.getFiles()` or `Configuration.getFiles()`.    

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Martin Mosegaard Amdisen](https://github.com/martinmosegaard) - Fix minor typos in the native software documentation
 - [Francis Andre](https://github.com/zosrothko) - Import Gradle production source into Eclipse without compile errors
 - [David Illsley](https://github.com/davidillsley) - Update docs to indicate use of HTTPS maven central (#774)
 - [Fedor Korotkov](https://github.com/fkorotkov) - Zinc compiler enhancements (#707)
 - [John Martel](https://github.com/johnmartel) - Print links to project reports on CLI (#762)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
