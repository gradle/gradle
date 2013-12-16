## New and noteworthy

Here are the new features introduced in this Gradle release.

### Generate Visual Studio configuration for a native binary project (i)

One of the great things about using Gradle for building Java projects is the ability to generate IDE configuration files: this
release of Gradle brings a similar feature when you use Microsoft Visual Studio as your IDE. With this integration, you can
use the best tool for the job: Gradle to build your binaries and Visual Studio to edit your code.

Visual Studio integration is supplied by the `visual-studio` plugin. When this plugin is applied, for each component
Gradle will create a task to produce a Visual Studio solution for a selected binary variant of that component.
The generated solution will include a project file for the selected binary, as well as project files for each depended-on library.

Similar to the Java IDE plugins, you can customize the generated Visual Studio configuration files with programmatic hooks.
These hooks are applied to the `visualStudio` element in the model registry.
For example, you can change the default locations of the generated files:

    model {
        visualStudio {
            solutions.all { VisualStudioSolution solution ->
                solutionFile.location = "vs/${solution.name}.sln"
            }
            projects.all { VisualStudioProject project ->
                projectFile.location = "vs/${project.name}.vcxproj"
                filtersFile.location = "vs/${project.name}.vcxproj.filters"
            }
        }
    }

Additionally, you can change the content of the generated files:

    model {
        visualStudio {
            solutions.all { VisualStudioSolution solution ->
                solutionFile.withText { StringBuilder text ->
                    ... customise the solution content
                }
            }
            projects.all { VisualStudioProject project ->
                projectFile.withXml { XmlProvider xml ->
                    xml.asNode()...
                }
            }
        }
    }


While Visual Studio support is functional, there remain some limitations:

- Macros defined by passing '/D' to compiler args are not included in your project configuration. Use 'cppCompiler.define' instead.
- Includes defined by passing '/I' to compiler args are not included in your project configuration. Use library dependencies instead.
- External dependencies supplied via `sourceSet.dependency` are not yet handled.

Please try it out an let us know how it works for you.

### Choose applicable platforms, build types and flavors for a native component (i)

It is now possible to specify a global set of build types, platforms and flavors and then specifically choose which of
these should apply for a particular component. This makes it easier to have a single plugin that adds support for a
bunch of platforms, build types, and/or flavors, and have the build script choose which of these are appropriate.

- `buildTypes` is now `model.buildTypes`
- `targetPlatforms` is now `model.platforms`
- `executable.flavors` or `library.flavors` is now `model.flavors`


    model {
        platforms {
            create('x86') {
                ... config
            }
        }
        buildTypes {
            create('debug')
        }
        flavors {
            create('my-flavor')
        }
        ... Many others, perhaps added by capability plugins
    }

    executables {
        main {
            targetPlatforms "x86" // Only build for this platform
            targetFlavors "foo", "bar" // Build these 2 flavors
            // targetBuildTypes - without this, all build types are targeted.
        }
    }

#### Current Limitations

The model registry and it's DSL are very new, and impose some DSL limitations. We plan to improve these in the future.

- Elements in containers under `model` must be added with the `create(name)` method.
- The `component.target*` methods match on element _name_. It is not possible to supply an element instance at this time.

### Better support for declaring library dependencies when building native binaries (i)

#### New dependency notation

When building native binaries, it is now possible to declare a dependency on a library by a dependency notation.

    executables {
        main {}
    }
    sources.main.cpp.lib library: 'hello', linkage: 'static'
    sources.main.c.lib project: ':another', library: 'hi'

    libraries {
        hello {}
    }

The 'project', 'library' and 'linkage' can be specified. Only 'library' is required and must be the name of a library
in the specified project, or in the current project if the 'project' attribute is omitted.

This notation based syntax provides a number of benefits over directly accessing the library when declaring the dependency requirement:

- The library referenced does not have to be declared before the dependency declaration in the build script
- For libraries in another project, the depended-on project does not need to have been evaluated when the dependency declaration is added
- The linkage is clearly specified

#### Support for header-only libraries

#### Support for api dependencies

There are times when your source may require the headers of a library at compile time, but not require the library binary when linking.
To support this use case, it is now possible to add a dependency on the 'api' linkage of a library. In this case, the headers
of the library will be available when compiling, but no binary will be provided when linking.

A dependency on the 'api' linkage can be specified by both the direct and the map-based syntax.

    sources.main.cpp.lib project: ':A', library: 'my-lib', linkage: 'api'
    sources.main.cpp.lib libraries.hello.api

### Independent control of TestNG output directory

It is now possible to specify the location that TestNG should write its output files to.
In previous versions of Gradle, this location was inseperable from the location that Gradle writes its output files to.

It can be set via the `TestNGOptions.outputDirectory` property, which can be set during the `useTestNG()` configuration closure…

    test {
      useTestNG {
        outputDirectory = file("$buildDir/testngoutput")
        useDefaultListeners()
      }
    }

By default, the value for this new property will be the same as the initial value for the `destination` property of the test task's HTML report.

If you are using the file outputs from TestNG listeners (i.e. you are calling `useDefaultListeners()` or registering a custom listener), 
it is recommended that you explicitly set this new property to a value other than the default.
The default value for this property will change in Gradle 2.0.

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

### Changes to native binary support

- Moved definitions of `buildTypes`, `targetPlatforms` and `flavors` into model block (see above)
- Classes moved from org.gradle.nativebinaries:
    - ToolChain, ToolChainRegistry -> org.gradle.nativebinaries.toolchain
    - Architecture, OperatingSystem, Platform, PlatformContainer -> org.gradle.nativebinaries.platform
- Tasks are not created for empty source sets

### A requested dependency returns different types of selectors

The method `DependencyResult.getRequested()` method was changed to return an implementation of type `ComponentSelector`. This change to the API has to be taken into account
when writing a `Spec` for the `DependencyInsightReportTask`. Here's an example for such a use case:

    task insight(type: DependencyInsightReportTask) {
        setDependencySpec { it.requested instanceof ModuleComponentSelector && it.requested.module == 'leaf2' }
    }

### Changes to incubating test filtering.

JUnit tests that JUnit API internally represents by 'null' test methods are filtered only by class name.
This is a very internal change and should not affect users. It is mentioned for completeness.

### Changes HTML test reports

Ignored tests are no longer considered in the calculation of the reported percentage success rate.
A test run with 1 failed test, 1 successful test and 8 ignored tests will now report a success rate of 50%
(previously it would have been 90%)

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Michael Putters](https://github.com/mputters) - improved the native binaries support to use the Windows registry to locate available Windows SDKs
* [Jean-Baptiste Nizet](https://github.com/jnizet) - improved HTML test reports to better report on ignored tests
We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
