This release adds some nice command-line usability features, with the ability to run a single test method and view task help from the command-line.

Another usability feature is a new 'should run after' task ordering.

This release also brings more features for C and C++ development, with the introduction of incremental compilation for C and C++, plus better integration
with the Visual Studio and GCC tool chains.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Single test method execution (and more)

tbd.

### `shouldRunAfter` task ordering

Gradle 1.6 introduced task ordering by way of the `mustRunAfter` method(s) added to tasks.
This release brings a new ordering mechanism: `shouldRunAfter`.
This feature was contributed by [Marcin Erdmann](https://github.com/erdi).

If it is specified that…

    task a {}
    task b { 
        mustRunAfter a 
    }

Then under all circumstances Gradle will ensure that `b` is only executed after `a` has executed. 
However it does not imply that task `b` _depends on_ task `a`.
It is only used to order the execution if both `a` and `b` are to be executed in a given build.

The new `shouldRunAfter` ordering works much the same way, except that it specifies an ordering preference and not a requirement.
If it is specified that…

    task a {}
    task b { 
        shouldRunAfter a 
    }

Then Gradle will execute `b` after `a` if there is not a good reason to do otherwise.
This means that use of `shouldRunAfter` can not create a dependency cycle and it also does not prevent parallel execution of tasks.

For more examples please see [Ordering tasks](userguide/more_about_tasks.html#sec:ordering_tasks) in the User Guide.

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

### Incremental compile for C++ and C sources (i)

Gradle 1.10 introduces support for incremental compile of C++ and C source files. After an initial build, the only sources that will be
recompiled in subsequent builds are those where:

- The source file has changed
- One of the header files that is included by that source file has changed (directly or transitively)

No action is required to enable incremental compile. Gradle will always compile incrementally for a non-clean build.

Support for incremental compilation takes Gradle one step closer to the goal of providing a production-scale build tool for
C++ and C sources. Further performance testing and tuning will be required to attain the rapid speeds that C++ developers are
used to, but this new feature provides the infrastructure required to make this possible.

### Use Visual Studio to compile Windows Resources (i)

When building native binaries with the `VisualCpp` tool chain, Gradle can now compile Windows Resource (.rc) files and link them
into the binary. This functionality is made available by the `windows-resources` plugin.

    apply plugin: 'cpp'
    apply plugin: 'windows-resources'

    libraries {
        hello {}
    }

By default, Gradle creates a single `WindowsResourceSet` for each component, which will includes any sources found under `src/$component.name/rc`.
The windows resource source directories can be configured via the associated `WindowsResourceSet`.

    sources {
        hello {
            rc {
                source {
                    srcDirs "src/main/rc", "src/common/resources"
                    include "**/*.rc", "**/*.res"
                }
            }
        }
    }

For more details please see the [Windows Resources](userguide/nativeBinaries.html#native_binaries:windows-resources) section in the User Guide.

### Support for GCC cross-compilers (i)

Due to the wide array of Gcc cross-compilers that may be used, Gradle require the build author to supply specific configuration
to use a cross-compiler. The build author must define any specific command-line arguments that are required,
as well as define the target platform of any generated binaries.

Configuring a GCC tool chain as a cross-compiler involves providing a `TargetPlatformConfiguration` to the tool chain.
Each Gcc tool chain has a set of such configurations, which are queried in turn when attempting to build a binary targeting
a particular platform. If the configuration indicates that the target platform is supported, then the specified arguments will
be passed to the compiler, linker or other tool.

    model {
        toolChains {
            crossCompiler(Gcc) {
                addPlatformConfiguration(new ArmSupport())
            }
        }
    }

    class ArmSupport implements TargetPlatformConfiguration {
        boolean supportsPlatform(Platform element) {
            return element.getArchitecture().name == "arm"
        }

        List<String> getCppCompilerArgs() {
            ["-mcpu=arm"]
        }

        List<String> getCCompilerArgs() {
            ["-mcpu=arm"]
        }

        List<String> getAssemblerArgs() {
            []
        }

        List<String> getLinkerArgs() {
            ["-arch", "arm"]
        }

        List<String> getStaticLibraryArchiverArgs() {
            []
        }
    }

Note that the current DSL is experimental, and will be simplified in upcoming releases of Gradle.

### Fine-grained control of command line arguments for GCC (i)

While the goal is to provide a set of tool chain implementations that 'just work', there may be times when the way that Gradle
drives the underlying command-line tools does not suit your purpose. For these cases it is now possible to tweak the generated
command-line arguments immediately prior to them being provided to the underlying tool.

The command-line arguments of a tool can be modified via a `withArguments` closure, which is provided with the full set of
generated arguments as a list. The list can be changed directly, by adding, removing and replacing entries. This modified list
is the used to actually drive the underlying tool.

    model {
        toolChains {
            gcc(Gcc) {
                cppCompiler.withArguments { args ->
                    Collections.replaceAll(args, "OPTIMISE", "-O3")
                    args << "extra_arg"
                }
            }
        }
    }

Note that the current DSL is experimental, and will be simplified in upcoming releases of Gradle.

### Better auto-detection of Visual Studio and Windows SDK (i)

Gradle will now automatically locate and use more versions of Visual Studio and the Windows SDK for the `VisualCpp` tool chain.

- Visual Studio 2012 & Visual Studio 2013
- Windows SDK versions 7.1A, 8.0 & 8.1

Support for Visual Studio remains experimental.
Please let us know via the Gradle forums if you experience problems with Gradle using your Visual Studio installation.

### Dependency resolution result API provides information about project dependencies (i)

The `ResolutionResult` API is used to provide information about about a resolved graph of dependencies. Previously, this API did not
provide any way to determine which of the dependencies in the graph are produced by a project in the build and which dependencies
originated outside the current build, such as from a binary repository.

The API has been extended so you can now query whether a given dependency originated from a project in the build or not.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Promoted methods

The following methods have been promoted and are no longer incubating:

- `NamedDomainObjectContainer.maybeCreate()`
- `RepositoryHandler.jcenter()`

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Dependency resolution result produces a graph of components instead of a graph of module versions (i)

As part of some initial work to support more powerful dependency management, such as dependency management for native binaries, Android libraries, or
Scala libraries built for multiple Scala versions, the dependency resolution result API has been changed.

#### What does this change mean?

The model is now that dependency resolution produces a graph of _components_ instead of _module versions_. A component represents things such as a
Java library, or native executable, and so on. This is a higher level and more general concept than a module version, which simply represents something
published to a binary repository.

The main change in this release is to model the fact that not all components included in the result of a dependency resolution are necessarily published to
a binary repository. For example, a component might be built by some other project in the build, or may be prebuilt and installed on the local machine
somewhere, or might be built by some other build tool.

#### Changes to the `ResolutionResult` API

* The following interfaces were renamed:
    * `ResolvedModuleVersionResult` is now called `ResolvedComponentResult`
    * `ModuleVersionSelectionReason` is now called `ComponentSelectionReason`
* The following interfaces were replaced:
    * `ComponentSelector` is now used instead of `ModuleVersionSelector`
    * `ComponentIdentifier` is now used instead of `ModuleVersionIdentifier`
* The following methods on `ResolutionResult` where renamed:
    * `getAllModuleVersions()` is now called `getAllComponents()`
    * `allModuleVersions()` is now called `allComponents()`
* Method signatures were changed on the following types to reflect these changes:
    * `ResolutionResult`
    * `DependencyResult`
    * `ResolvedComponentResult`
    * `UnresolvedDependencyResult`

### Dependency resolution prefers the latest version of a module regardless of whether it has meta-data or not

In this release, the way that Gradle selects a matching version for a dynamic version, such as `1.2+` or `latest.integration` has changed. We expect that for
the large majority of cases, the result will continue to be the same as previous releases. However, there may be cases where this change will produce a different
result.

#### Selecting a match for dynamic version criteria

Previous versions of Gradle would select a match for a dynamic version by first searching for versions that include a meta-data file, such as a `pom.xml` or
an `ivy.xml` file. If any such versions were found, Gradle would select the highest version that meet the criteria. If no versions were found with a meta-data file,
then Gradle would search again, this time for versions without a meta-data file. Gradle would then select the highest version.

There are several problems with this approach: Firstly, it requires two separate repository searches, which can be a performance or stability problem, in
particular when multiple repositories need to be searched. Secondly, this can give unexpected results when the module, for whatever reason, is sometimes published
with meta-data and sometimes without meta-data.

In the 1.10 release, Gradle will now search once for all versions of the module and select the highest version that meets the criteria, regardless of whether the version
includes a meta-data file or not.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Christo Zietsman](https://github.com/czietsman) - added support for locating the Visual Studio 2012 tool set
* [Michael Putters](https://github.com/mputters) - added support the Visual Studio 2013 tool set and Windows 8 SDK
* [Andrew Oberstar](https://github.com/ajoberstar) - fixed GRADLE-2695: ClassFormatError introduced in 1.3
* [Bobby Warner](https://github.com/ajoberstar) - updated Groovy samples to Groovy 2.2.0
* [Marcin Erdmann](https://github.com/erdi) - `shouldRunAfter` task ordering rule
* [Ramon Nogueira](https://github.com/ramonza) - fixed issues serializing test failures

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
