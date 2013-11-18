## New and noteworthy

Here are the new features introduced in this Gradle release.

### Show task usage details via help task

You can run the help task now with a commandline option '--task' to get detailed usage information for a specific task. The usage information
includes task type, path, description and available commandline options. To get details about the incubating `init` task you can run
`gradle help --task init` which will give you the following output

    Detailed task information for init

    Path
         :init

    Type
         InitBuild (org.gradle.buildinit.tasks.InitBuild)

    Options
         --type     Set type of build to create.

    Description
         Initializes a new Gradle build. [incubating]

<!-- TODO:DAZ Fill these in -->
### Incremental compile for C++ (i)

### Use Visual Studio to compile Windows Resources (i)

### Fine-grained control of command line arguments for GCC (i)

### Auto-detection of Visual Studio 2012 and Windows SDK 7.1A (i)

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Dependency resolution result produces a graph of components instead of a graph of module versions.

* The dependency resolution result is changed so that it produces a graph of components.
* Various interfaces were renamed to reflect this change:
    * `ResolvedModuleVersionResult` to `ResolvedComponentResult`
    * `ModuleVersionSelectionReason` to `ComponentSelectionReason`
* Renamed methods on `ResolutionResult`:
    * `getAllModuleVersions()` to `getAllComponents()`.
    * `allModuleVersions(Action)` to `allComponents(Action)`.
    * `allModuleVersions(Closure)` to `allComponents(Closure)`.
* Various interface method signatures were changed to return the new component types: `DependencyResult`, `ResolvedComponentResult`, `UnresolvedDependencyResult` and `ResolutionResult`.

### Dependency resolution prefers the latest version regardless of whether it has meta-data or not

TBD

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Christo Zietsman](https://github.com/czietsman) - added support for locating the Visual Studio 2012 tool set
* [Andrew Oberstar](https://github.com/ajoberstar) - Fix GRADLE-2695: ClassFormatError introduced in 1.3

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
