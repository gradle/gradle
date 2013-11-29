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

### Dependency result API provides information about project dependencies (i)

TBD

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

### Dependency resolution result produces a graph of components instead of a graph of module versions.

* The dependency resolution result is changed so that it produces a graph of components.
* Various interfaces were renamed to reflect this change:
    * `ResolvedModuleVersionResult` to `ResolvedComponentResult`
    * `ModuleVersionSelectionReason` to `ComponentSelectionReason`
* Renamed methods on `ResolutionResult`:
    * `getAllModuleVersions()` to `getAllComponents()`.
    * `allModuleVersions(Action)` to `allComponents(Action)`.
    * `allModuleVersions(Closure)` to `allComponents(Closure)`.
* Uses `ComponentIdentifier` and `ComponentSelector` instead of `ModuleVersionIdentifier` and `ModuleVersionSelector`.
* Various interface method signatures were changed to return the new component types: `DependencyResult`, `ResolvedComponentResult`, `UnresolvedDependencyResult` and `ResolutionResult`.

### Dependency resolution prefers the latest version regardless of whether it has meta-data or not

TBD

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
