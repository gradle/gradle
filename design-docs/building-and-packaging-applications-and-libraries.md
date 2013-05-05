This specification defines some build-by-convention support for building and packaging JVM based and native applications and libraries.

# Use cases

## JVM based library

For a JVM based library, Gradle will build:

* The runtime JAR
* Runtime dependencies
* API documentation.

Optionally, Gradle will also be able to build:

* Library source ZIP.
* An API JAR and compile-time dependencies.
* User guide and other documentation.

Gradle will be able to assemble a distribution for the library, containing these artifacts.

It will be possible to share these artifacts between projects using dependency management, either locally or via an artifact repository.

## JVM based command-line application

For a JVM based command-line application, Gradle will build:

* Generated launcher scripts
* The runtime JAR and dependencies.

Optionally, Gradle will also be able to build:

* Application source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution for the application, containing these artifacts.

It will be possible to share these artifacts between projects using dependency management.

## C++/C library

For a C/C++ library, Gradle will build:

* The header files.
* The shared library binary and/or the static library binary.
* The import file, in the case of Windows.
* API documentation.

Optionally, Gradle will also be able to build:

* Debug file, in the case of Windows.
* Library source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution that contains the above artifacts for all variants of the library, and to build a distribution per variant.

It will be possible to share these artifacts between projects using dependency management.

## Native application

For a native application, Gradle will build:

* The native executable.
* Shared libraries required at runtime.

Optionally, Gradle will also be able to build:

* Debug file, in the case of Windows.
* Application source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution that contains the above artifacts for all variants of the library, and to build a distribution per variant.

It will be possible to share these artifacts between projects using dependency management.

## Gradle distribution

The Gradle -all, -bin, and -src distributions can be built and published using the distribution infrastructure.

## Libraries and applications composed from multiple projects

It should be possible to define a library or application that bundles or links together several libraries produced by other projects.

It should also be possible to define a project that does not contain any source, but simply aggregates together several libraries produced by other
projects to produce a composite library or application.

## Native executable for JVM based application

Gradle will be able to build a native launcher for a JVM based application, as an alternative to using launcher scripts.

## Executable Jar

Gradle will be able to package a JVM based application as an executable fat jar, as an alternative to using launcher scripts.

## JVM based daemon

For a JVM based application that implements a daemon, Gradle will package the application with launchers that allow the application to be run as a daemon.

## JVM based GUI application

For a JVM based application that implements a native GUI, Gradle will package that application as a platform-specific 'double-clickable' application.

## Native packaging

For a library or application, Gradle will build a native package that can be used to install the library or application using a native package manager.

More info in the [forum ticket](http://forums.gradle.org/gradle/topics/modeling_the_java_application_development_domain).

# Implementation Plan

## Introduce a `java-library-distribution` plugin (DONE)

An opinionated plugin that adds a single distribution that

- `java-library-distribution` plugin
    - applies `java` plugin
    - adds `distZip` task
        - packages up the jar and runtime dependencies of the library as a ZIP.
        - include contents of `src/dist`
    - adds `distribution` extension
        - has `name` property that is used to configure the `baseName` of the `distZip` task.

### DSL:

    apply plugin: 'java-library-distribution' // implies 'java' plugin

    distribution {
        name = 'someName'
    }

    distZip {  //type: Zip
        from { ... }
    }

### Integration test coverage

- add an integ test that extends `WellBehavedPluginTest` to pick up some basic verification of the plugin.
- add an integ test that runs task to build up a distro that:
    - has some stuff in src/dist
    - includes some runtime dependency that is declared.
    - uses the name of the distribution.name for the distro name.
- add an integ test that covers:
    - produces a distribution when src/dist does not exist.
    - works if only `java-library-distribution` is applied and nothing else (e.g. sensible defaults are used for the distro name, etc.)
    - does not crash if distribution.name is configured to null

### Implementation approach

- add unit tests for the plugin, validate all the features (separate tests) declared in 'user visible changes' section.
- documentation
    - add new plugin chapter and hook it up to the user guide
    - add the XML for the new extension object
    - new extension object should have a javadoc with small code sample (using our 'autoTested').
        - say, apply plugin and configure the distribution.name
    - link the new extension from plugins.xml

## Introduce a basic `distribution` plugin

Extract a general-purpose `distribution` plugin out of the `java-library-distribution` plugin.

1. Add a `distribution` plugin.
2. Add a `Distribution` type that extends `Named` plus implementation class.
3. Add a `DistributionContainer` type that extends `NamedDomainObjectContainer<Distribution>` plus implementation class.
4. Change the `distribution` plugin to add this container as an extension called `distributions`.
5. Change the `distribution` plugin to add a single instance called `main` to this container.
7. Change the `java-library-distribution` plugin to apply the `distribution` plugin.
8. Change the `distribution` plugin to add a ZIP task for each distribution in the container.
    - For the `main` distribution, this should be called `distZip`
    - For other distributions, this should be called `${dist.name}DistZip`.
9. Change the `java-library-distribution` plugin so that it no longer add a `distZip` task, but instead configures the `distZip`
   task instance that is added by the `distribution` plugin.

### DSL

To generate a distribution for a Java library:

    apply plugin: 'java-library-distribution` // implies `java` and `distribution` plugins

    distribution {
        name = 'someName'
    }

    distZip {
        from { ... }
    }

To generate an arbitrary distribution:

    apply plugin: 'distribution'

    distZip {
        from { ... }
    }

To generate multiple distributions:

    apply plugin: 'distribution'

    distributions {
        custom
    }

    distZip {
        from { ... }
    }

    customDistZip {
        from { ... }
    }

Running `gradle distZip customDistZip` will create the distribution ZIP files.

## Allow customisation of the `distribution` plugin

Allow the distributions defined by the `distribution` to be configured and remove the configuration options from the `java-library-distribution` plugin.

1. Change the `Distribution` type to add a `baseName` property. This should default to:
    - `project.name` for the `main` distribution.
    - `${project.name}-${dist.name}` for other distributions.
2. Make `Distribution.name` immutable.
3. Change the `distribution` plugin to configure the dist zip task to add `into {$dist.baseName}-${project.version}`. Remove the corresponding
   configuration from the `java-library-distribution` plugin.
4. Change the `java-library-distribution` plugin so that it no longer adds the `distribution` extension, and remove the `DistributionExtension`
   implementation.
5. Change the `Distribution` type to add a `contents` property of type `CopySpec`. This should default to: `from 'src/${dist.name}/dist'`
6. Change the `distribution` plugin to configure the dist ZIP task to add `from $dist.contents`.
7. Change the `java-library-distribution` plugin to configure the main distribution's `contents` property instead of the dist zip task.
8. Change the `Distribution` plugin to apply the base plugin.

### DSL

To generate a distribution for a Java library:

    apply plugin: 'java-library-distribution` // implies `java` and `distribution` plugins

    version = 1.2

    distributions {
        main {
            baseName = 'someName'
            contents {
                from { 'src/dist' }
            }
        }
    }

Given that the project name is `myproject`, then running `gradle distZip` will produce a ZIP file called `myproject-1.2.zip`, with the following
contents:

    myproject-1.2/
        lib/
            myproject-1.2.jar
        ... some files from `src/dist` ...

To generate an arbitrary distribution:

    apply plugin: 'distribution'

    distributions {
        main {
            baseName = 'someName'
            contents {
                from { ... }
            }
        }
    }

To generate multiple distributions:

    apply plugin: 'distribution'

    distributions {
        custom {
            contents {
                from { ... }
            }
        }
    }

## Test coverage

* ZIP file and prefix are correct when the project does and does not have a version specified.
* ZIP file and prefix are correct when the distribution `baseName` has been specified.
* ZIP file includes files from `src/main/${dist.name}`.
* ZIP file includes additional files specified in `dist.contents`.
* ZIP file is produced for custom distribution.
* ZIP file is produced in the appropriate subdirectory of `build`.

## Allow distributions to be installed

1. Change the `distribution` plugin to add an install task of type `Sync`.
    - called `installDist` for the `main` distributions.
    - called `install${dist.name}Dist` for other distributions.
    - installs `dist.contents` into `$buildDir/install/${dist.baseName}`.

## Generate a TAR distribution

1. Change the `distribution` plugin to add a TAR task for each distribution, configured in a similar way to the ZIP task.

## Share distribution definitions with the `application` plugin

1. Change the `application` plugin to apply the `distribution` plugin.
    - When `applicationPluginConvention.applicationName` is set, set the `main` distribution's `baseName` property.
    - Configures the `main` distribution's `contents` to add `from applicationPluginConvention.applicationDistribution`.
    - No longer adds the `distZip` or `distTar` tasks.

## All distribution archives are built when project is assembled

1. Running `gradle assemble` will build the ZIP and TAR archives for all distributions.
2. Running `gradle assemble${name}Dist` will build the ZIP and TAR archives for the given distribution.

## Allow distributions to be published

1. Change the `distribution` plugin to add a `SoftwareComponent` instance for each distribution that is added.
    - Publishes both the ZIP and TAR archives.
    - Generated meta-data does not include any dependency declarations.
    - Generated meta-data include details of components that have been bundled in the distribution.

## Deprecate distribution configuration from the `application` plugin

1. Deprecate `ApplicationPluginConvention.applicationName` and `applicationDistribution` properties.
2. Deprecate the `installApp` task.

# Later steps

See the use cases above.

- Extract application definitions from the application plugin.
- Allow java library, command-line application and web application components to be added to a distribution.
- Include Java library API documentation in distribution.
