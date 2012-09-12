
There are many shortcomings with the existing model and DSL for publishing. This specification describes a plan to implement a new publication model and DSL.

The basic strategy taken here is to incrementally grow the new model along-side the existing model.

Note: this spec is very much a work in progress.

# Use cases

## I want my consumers to use the same dependency versions as I used to build and test my artifacts

AKA 'Ivy deliver'. In this instance, dependency declarations in the generated descriptors should use the resolved versions that were used to build the artifacts.

## I want to customise the Ivy or Maven meta-data for a publication

* Use a (groupId, artifactId, version) identifier that is different to defaults.
* Add some custom Ivy attributes for the module or a configuration or an artifact.
* Mark some dependencies as optional in the pom.xml.
* Map some runtime dependencies to provided scope in the pom.xml.

## I want to publish multiple Ivy or Maven modules from my project

* I have separate API and implementation Jars that I want to publish as separate Maven modules.
* I want to publish test fixtures as a separate module.
* I produce Groovy 1.8 and Groovy 2.0 variants that I want to publish as separate modules.

## I want to customise a publication based in its destination

* I want to map the 32-bit and 64-bit variants of a native library to `mylib-x86-1.2.dll` and `mylib-amd64-1.2.dll` when publishing to an Ivy repository,
  and to `mylib-1.2-x86.dll` and `mylib-1.2-amd64.dll` when publishing to a Maven repository.
* I want a consumer in a different build to use the JAR file, and a consumer in the same build to use the compiled classes dir.

## I want to generate the meta-data descriptor for a publication without publishing

* To smoke test the generated meta-data before publishing.

This list is not complete. There are more use cases to come.

# User visible changes

The end goal is to introduce 2 concepts:

* A software _component_.
* A _publication_.

Both of these are defined in the [dependency model spec](dependency-model.md).

A component is a logical description of a piece of software, such as a Java library or native executable or report.

A publication is a mapping of that component to a set of artifacts and meta-data, ready to be used by some consumer project. A publication is a strongly-typed
model element. There will be 3 types of publication:

* An Ivy publication, for publishing to an Ivy repository.
* A Maven publication, for publishing to a Maven repository.
* A local publication, for consumption within the same build.

The following sections present a series of steps that allow us to evolve towards this model.

At the end of the process described below, a project will have-a set of publications. Each publication will be fully and independently configurable.
The existing Maven deployer and Maven installer DSL will be discontinued, and the existing Configuration DSL will be split into incoming dependencies
and outgoing publications.

Note: for the following discussion, all changes are `@Incubating` unless specified otherwise.

## Customising Ivy and Maven descriptor XML

An initial step will provide some capability to modify the generated `ivy.xml` and `pom.xml` files before publication.

1. Introduce a `Publication` interface. Probably make `Publication` a `Named` thing.
2. Add a `Publication` container to the project. At this stage, this container will be read-only.
2. Add `MavenPublication` interface.
3. Change the Maven plugin so that `mavenInstaller()` and `mavenDeployer()` methods add a `MavenPublication` instance to the container for each `MavenPom` instance
   that the installers and deployers create (ie one for each filter).
4. Add `MavenPublication.withXml()` methods.
5. Add `IvyPublication` interface.
6. Add an `ivy` plugin that adds a single `IvyPublication` instance to the container. All Ivy repository instances used for upload will share the same `IvyPublication` instance.
   This instance would use the following defaults when generating the `ivy.xml`
    * `organisation` == `project.group`
    * `module` == `project.name` (_not_ `archivesBaseName`)
    * `revision` == `project.version`
    * `status` == `project.status`
    * Only dependency declarationss from public configurations are included, and no configuration extension is used.
    * Only artifacts from public configurations are included.
7. Add `IvyPublication.withXml()` methods.

To customise the `pom.xml`:

    apply plugin: 'maven'

    uploadArchives {
        repositories { mavenDeployer { ... } }
    }

    publications.withType(MavenPublication) {
        withXml { xml -> ... }
    }

    // or
    publications.mavenInstall.withXml { xml -> ... }

To customise the `ivy.xml`:

    apply plugin: 'ivy'

    uploadArchives {
        repositories { ivy { ... } }
    }

    publications.withType(IvyPublication) {
        withXml { xml -> ... }
    }

    // or
    publications.ivy.withXml { xml -> ... }

The `ivy` plugin is intended to move Ivy concepts out of the core Gradle DSL, but still allow them to be available for customisation for those projects
that use Ivy. It also allows us to introduce some breaking changes, in an opt-in way.

Note: there's a breaking change here. If you apply the `ivy` plugin, the `archivesBaseName` property is no longer used as the default Ivy module name
when you use any of the existing `upload$Config` tasks. In addition, only the dependency declarations and artifacts from public configurations are
included in the generated Ivy descriptor and the publication.

### Implementation approach

Each `MavenPublication` instance will delegate to the associated `MavenPom` instance.

The `IvyPublication` instance will need to be considered by `IvyBackedArtifactPublisher` when generating the Ivy descriptor. It might make sense
at this point to push the Ivy descriptor generation down to the repository implementation, as it is for the Maven descriptor generation.

## Customising the Maven and Ivy meta data

A second step will allow some basic customisation of the meta data model for each publication:

1. Add `groupId`, `artifactId` and `version` properties to `MavenPublication`. These properties will delegate to the associated `MavenPom` instance.
2. Add `organisation`, `module` and `revision` properties to `IvyPublication`.
3. Change the `ivy.xml` generation to prefer to (organisation, module, revision) identifier of the `IvyPublication` instance from the target project
   for a project dependencies, other the existing candidate identifiers.

To customise the `pom.xml`:

    apply plugin: 'maven'

    uploadArchives {
        repositories { mavenDeployer { ... } }
    }

    publications.withType(MavenPublication) {
        groupId = 'my-maven-group'
        artifactId = 'my-artifact-id'
        version = '1.2'
    }

To customise the `ivy.xml`:

    apply plugin: 'ivy'

    uploadArchives {
        repositories { ivy { ... } }
    }

    publications.ivy {
        organisation = 'my-organisation'
        module = 'my-module'
        revision = '1.2'
    }

We might also add an `ivy` and `maven` project extension as a convenience to specify defaults for all publications of the appropriate type:

    apply plugin: 'ivy'

    ivy {
        organisation = 'my-organisation'
        module = 'my-module'
        revision = '1.2'
    }

## Reuse Maven publication for Maven install and deploy

This step moves away from the existing Maven publication DSL:

1. Change `maven` plugin to add a `MavenPublication` instance to the publications container. This instance would have the following defaults:
    * `groupId` == `project.group`
    * `artifactId` == `project.name` (_not_ `archivesBaseName`)
    * `version` == `project.version`
    * TBD - the packaging, dependencies and artifacts to be included.
2. Change the `maven` repository type to handle publishing. Each `maven` repository used for upload will share the same `MavenPublication` instance.
3. Change the `base` plugin to apply defaults for the `Upload` task type, for the descriptor location and configuration properties.
4. Change the `pom.xml` generation to prefer the (groupId, artifactId, version) identifer from the shared `MavenPublication` instance of the target
   project for project dependencies, over the existing candidate identifiers.

To install into the maven cache:

    apply plugin: 'maven'

    task publishLocal(type: Upload) {
        repositories { mavenLocal() }
    }

To deploy to a remote repository:

    apply plugin: 'maven'

    uploadArchives {
        repositories { maven { ... } }
    }

To allow install and deploy with customisations:

    apply plugin: 'maven'

    publications.maven {
        groupId = 'my-group'
        artifactId = 'my-artifact-id'
        version = '1.2'
    }

    task publishLocal(type: Upload) {
        repositories { mavenLocal() }
    }

    uploadArchives {
        repositories { maven { ... } }
    }

Note: there's a potential breaking change here: if you use the `maven` plugin and the `maven` publication, the module `artifactId` will default
to the project name, not the value of `archivesBaseName`. Not strictly a breaking change, but more a potential problem.

## Allow outgoing dependencies to be customised

This step decouples the incoming and outgoing dependencies, to allow each publication to include a different set of dependencies:

1. Add a `MavenDependency` interface, with the following properties:
    * `groupId`
    * `artifactId`
    * `version`
    * `type`
    * `optional`
2. Add a `MavenDependencySet` concept. This is a collection of things that can be converted into a collection of `MavenDependency` instances.
3. Add a named container of `MavenDependencySet` scopes to `MavenPublication`. The following scopes would be made available:
    * `compile`
    * `provided`
    * `runtime`
    * `test`
4. Add an `IvyDependency` interface, with the following properties:
    * `organisation`
    * `module`
    * `revision`
    * `configuration`
5. Add an `IvyConfiguration` concept. This is a collection of things that can be converted into a collection of `IvyDependency` instances.
6. Add a named container of `IvyConfiguration` instances to `IvyPublication`.

For the singleton `IvyPublication` and `MavenPublication` instances, these properties would have defaults as specified earlier. For `MavenPublication`
instances backed by a `MavenPom`, these properties will default to those values specified in the `MavenPom` scope mappings. Mutating the scopes for
a `MavenPom` backed publication would not affect the scope mappings.

Note that there are some pieces left out intentionally here: Maven `scope` and `systemPath` dependency properties and `system` scope and `exclusions`,
and Ivy configuration extension, configuration mappings, and artifact includes and excludes.

To customise a Maven publication:

    apply plugin: 'maven'

    publications.maven {
        scopes {
            compile "some-group:some-artifactId:1.2"
            runtime = [compile, "some-group:some-artifactId:1.2:runtime"]
            test = []
        }
    }

To customise an Ivy publication:

    apply plugin: 'ivy'

    publications.ivy {
        configurations {
            compile "some-group:some-module:1.2"
            testRuntime = []
        }
    }

TBD - specify applicable dependency conversions

## Allow outgoing artifacts to be customised

This step allows the outgoing artifacts to be customised for individual publications:

1. Add a `MavenArtifact` interface with the following attributes:
    * `extension`
    * `classifier`
2. Add a `MavenArtifactSet` interface. This is a collection of objects that can be converted to a collection of `MavenArtifact` instances.
3. Add `mainArtifact` and `artifacts` collections to `MavenPublication`.
4. Add an `IvyArtifact` interface with the following attributes:
    * `name`
    * `type`
    * `extension`
5. Add an `IvyArtifactSet` interface. This is a collection of objects that can be converted to a collection of `IvyArtifact` instances.
6. Add an `artifacts` property to `IvyConfiguration`.

For the singleton `IvyPublication` and `MavenPublication` instances, these properties would have defaults as specified earlier. For `MavenPublication`
instances backed by a `MavenPom`, these properties will default to those artifacts that match the `MavenPom` artifact filter. Mutating the artifacts for
a `MavenPom` backed publication would not affect the artifact filter.

To customise a Maven publication:

    apply plugin: 'maven'

    publications.maven {
        mainArtifact = jar
        artifacts = [sourceJar, javadocJar]
        artifact file: distZip, classifier: 'dist'
    }

To customise an Ivy publication:

    apply plugin: 'ivy'

    publications.ivy {
        configurations {
            runtime {
                artifacts = [jar]
            }
            distributions {
                artifact file: distZip, type: 'java-library-distribution'
            }
        }
    }

TBD - applicable artifact conversions

## Allow additional publications to be defined

In this step, custom publications can be defined.

1. Allow `IvyPublication` and `MavenPublication` instances to be added to the publications container.
2. Allow upload task to target a set of publications and a set of repositories. Possibly type the upload tasks.

TBD - Use publish tasks instead of upload tasks.
TBD - Project dependency maps to all publications of a given type, or a particular set of publications?

## Remove old DSL

These would be mixed in to various steps above (TBD), rather than as one change at the end. They are grouped together here for now:

1. Deprecate and later remove MavenDeployer and MavenInstaller and associated classes.
2. Deprecate and later stop supporting publication to an Ivy repository without applying the `ivy` plugin.
3. Change signature plugin to sign publications. Deprecate and later remove support for signing a configuration.
4. Deprecate and later remove `Configuration.artifacts` and related types.
5. Change `DependendencyHandler` to become a container of `ResolvableDependencies` instances. Use `dependencies.compile` instead of
   `configurations.compile`
6. Deprecate and later remove `ResolvedConfiguration` and related types.
7. Deprecate and later remove `Configuration` and related types.

## Add further meta-data customisations

At any point above, and as required, more meta-data for a publication can be made available for customisation. In particular:

1. Add `packaging`, `name`, `description`, `url`, `licenses`, `organization`, `scm`, `issueManagement` and `mailingLists` to `MavenPublication`
2. Add `status` and extended attributes to `IvyPublication`, `IvyConfiguration` and `IvyArtifact`.
3. Add exclusions, inclusions, etc.


# Sad day cases

TBD

# Integration test coverage

TBD

# Open issues

* What things are published?
* How do components fit into all this?
* How to get rid of `Configuration.artifacts`?
* How to map a project dependency to Ivy publication or Maven publication when generating descriptor?
* Add in local publications.
