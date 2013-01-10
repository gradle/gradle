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

## I want to sign the artifacts when published

* I want to sign all artifacts published to a repository.
* I want to sign the artifacts only when performing a release build.

This list is not complete. There are more use cases to come.

# User visible changes

The end goal is to introduce 2 concepts:

* A software _component_.
* A _publication_.

Both of these are defined in the [dependency model spec](dependency-model.md).

A component is a logical piece of software, such as a Java library or native executable or report.

A publication is a mapping of that component to a set of artifacts and meta-data, ready to be used by some consumer project. A publication is a
strongly-typed model element. There will initially be 3 types of publication:

* An Ivy publication, for publishing to an Ivy repository.
* A Maven publication, for publishing to a Maven repository.
* A local publication, for consumption within the same build.

The following sections present a series of steps that allow us to evolve towards this model.

At the end of the process described below, a project will have a set of publications that define the major outputs of the project. Each publication
will be fully and independently configurable. The existing Maven deployer and Maven installer DSL will be discontinued, and the existing Configuration
DSL will be split into incoming dependencies and outgoing publications.

Note: for the following discussion, all changes are `@Incubating` unless specified otherwise.

## Completed stories

See [completed stories](done/publication-model.md)

## Publish Java library to Maven repository with correct runtime dependencies

This story introduces the concept of a component and the ability to publish components.

1. Introduce a `Component` interface that extends `Named` and add a `components` container to `Project`. This container will initially be _read-only_ from
   a user's point of view. Also add an associated `ComponentInternal` interface.
2. Change the Java plugin to add a `Component` instance called `java` to this container.
3. Change the Maven publish plugin so that it adds no publications by default.
4. Change the Maven publish plugin so that when the `java` component instance is defined:
    1. Adds a Maven publication called `maven`.
    2. Has (groupId, artifactId, version) identifier that defaults to (project.group, project.name, project.version).
    3. Includes dependencies declared in `configurations.runtime.allDependencies` in the generated POM. Add the appropriate methods to `ComponentInternal` to allow
       the component instance to specify which dependencies should be included so that the Maven publish plugin does not have any knowledge of the Java plugin.
    4. Publishes the JAR artifact only.

To publish a Java library to a Maven repository

    apply plugin: 'java'
    apply plugin: 'maven-publish'

    dependencies {
        compile 'group:libA:1.2'
        runtime 'group:libB:1.3'
    }

    publishing {
        repositories {
            maven { url '...' }
        }
    }

Running `gradle publish` will publish the JAR and POM to the repository.

### Test cases

- Run `gradle publish` for a project with just the `maven-publish` plugin applied. Verify nothing is published.
- Run `gradle assemble` for a Java project. Verify that the JAR is built.
- Publish a Java project with compile, runtime and testCompile dependencies. Verify only the compile and runtime dependencies are declared in the POM with runtime scope,
  and that only the JAR is uploaded. Verify that the packaging declared in the POM is `jar`.
- Publish multiple projects with the `java` plugin applied and project dependencies between the projects. Verify the POMs declares the appropriate dependencies.
- Add a cross version test that verifies that a Java library published to a Maven repository by the current Gradle version can be resolved by a previous Gradle version.
- Copy existing Maven publication tests for java libraries and rework to use `maven-publish` plugin.

## Publish Web application to Maven repository

This story adds a second type of component and a DSL to define which components are published.

1. Allow `MavenPublication` instances to be added to the publications container.
    - Default the publication's (groupId, artifactId, version) to (project.group, publication.name, project.version).
2. Allow zero or one components to be added to a Maven publication.
3. Change the `maven-publish` plugin so that it does not create any publications by default.
4. Change the `war` plugin to add a component called `web`. When this component is added to a publication, the WAR artifact is added to the publication.
5. Fix publishing a Maven publication with no artifacts.

To publish a Java library

    apply plugin: 'java'
    apply plugin: 'maven-publish'

    publishing {
        repositories {
            maven { url '...' }
        }
        publications {
            myLib(MavenPublication) {
                from components.java
                pom.withXml { ... }
            }
        }
    }

To publish a Web application

    apply plugin: 'war'
    apply plugin: 'maven-publish'

    publishing {
        repositories {
            maven { url '...' }
        }
        publications {
            myWebApp(MavenPublication) {
                from components.web
            }
        }
    }

Note: there is a breaking change in this story, as nothing is published by default.

TBD - Which publication does a project dependency refer to?

### Test cases

- Run `gradle assemble` for a web project. Verify that the WAR is built.
- Run `gradle publish` for a project that defines an empty publication. Verify that only a POM is uploaded and that the POM declares no dependencies.
- Run `gradle publish` for a web application that has compile, runtime and testRuntime dependencies. Verify that the WAR is uploaded and that no dependencies are declared
  in the generated POM. Verify that the packaging declared in the POM is `war`.
- Run `gradle publish` for a web application assembled from several other projects in the same build. Verify that the WAR is uploaded and that no dependencies are declared
  in the generated POM.
- Run `gradle publish` for a project that defines multiple publications.
- Add a cross version test that verifies that a web application published to a Maven repository by the current Gradle version can be resolved by a previous Gradle version.
- Copy existing Maven publication tests for web applications and rework to use `maven-publish` plugin.

## Allow outgoing artifacts to be customised for Maven publications

This step allows the outgoing artifacts to be customised for a Maven publication.

1. Add a `MavenArtifact` interface with the following attributes:
    * `extension`
    * `classifier`
    * `file`
2. Add a `MavenArtifactSet` interface. This is a collection of objects that can be converted to a collection of `MavenArtifact` instances.
3. Add `mainArtifact` property and `artifacts` collections to `MavenPublication`.
4. When publishing, validate that (extension, classifier) is unique for each artifact.

To customise a Maven publication:

    apply plugin: 'maven-publish'

    publishing {
        publications {
            myLib(MavenPublication) {
                mainArtifact jar
                artifacts = [sourceJar, javadocJar]
                artifact file: distZip, classifier: 'dist'
            }
        }
    }
    
    publishing.publications.myLib.mainArtifact.classifier = 'custom'
    publishing.publications.myLib.artifacts.each {
        ...
    }

TBD - applicable artifact conversions

### Test cases

* Existing empty publication test: Verify empty `mainArtifact` and `artifacts`.
* Existing publish 'java' & 'web' tests: Verify `mainArtifact` attributes and that `mainArtifact` is the only element of `artifacts`.
* Publish with java component, add source and javadoc jars as additional artifacts. Verify classifiers of additional artifacts.
* Run `gradle publish` with no component where mainArtifact and artifacts are taken from custom AbstractArchiveTasks. 
    * Verify that archives are automatically built and published.
    * Verify that classifier and extension from AbstractArchiveTask is honoured.
    * Verify that classifier and extension specified in publication DSL overrides that of AbstractArchiveTask.
* Run `gradle publish` where mainArtifact and custom artifacts specified via file[,classifier,extension].
    * Verify that extension is taken from file name by default, and can be overridden in DSL.
    * Verify that classifier is empty by default, and can be overridden in DSL.
* Publish with java component. Verify that the publishing DSL can be used to update the classifier & exension of mainArtifact taken from component.
    * `publishing.publications.myLib.mainArtifact.classifier = 'custom'`

## Allow outgoing artifacts to be customised for Ivy publications

1. Add an `IvyArtifact` interface with the following attributes:
    * `name`
    * `type`
    * `extension`
    * `file`
2. Add an `IvyArtifactSet` interface. This is a collection of objects that can be converted to a collection of `IvyArtifact` instances.
3. Add an `IvyConfiguration` interface. Add a `configurations` container to `IvyModuleDescriptor`
4. Add an `artifacts` property to `IvyConfiguration`.
5. When publishing, validate that (name, extension) is unique for each artifact.

To customise an Ivy publication:

    apply plugin: 'ivy-publish'

    publishing {
        publications {
            ivy {
                descriptor.configurations {
                    runtime {
                        artifacts = [jar]
                    }
                    distributions {
                        artifact file: distZip, type: 'java-library-distribution'
                    }
                }
            }
        }
    }

TBD - applicable conversions

### Test cases

TBD

## Publish Java libraries and web applications to Ivy repository

1. Allow `IvyPublication` instances to be added to the publications container.
    - Default (organisation, module, revision) to (project.group, publication.name, project.version)
2. Allow zero or one publications to be added to an Ivy publication.
3. Change the `ivy-publishing` plugin so that it no longer defines any publications.
4. When publishing a web application, declare the providedRuntime dependencies and the WAR artifact in the descriptor.
5. When publishing a java library, declare the runtime dependencies and the JAR artifact in the descriptor.
6. Include a default configuration in the descriptor.

Note: there is a breaking change in this story.

### Test cases

* Publish a project without any other plugins applied.
* Publish a java project with compile, runtime and testCompile dependencies. Verify only the compile and runtime dependencies are included in the
  descriptors.
* Publish a multi Java project build with project dependencies, and verify all libraries and transitive dependencies can be successfully resolved from
  another build.
* Add a cross-version test that verifies a Java project published by the current version of Gradle can be consumed by a previous version of Gradle,
  and vice versa.

## Validate publication coordinates

Validate the following prior to publication:

* The groupId, artifactId and version specified for a Maven publication are non-empty strings.
* The groupId and artifactId specified for a Maven publication match the regexp `[A-Za-z0-9_\\-.]+` (see `DefaultModelValidator` in Maven source)
* The organisation, module and revision specified for an Ivy publication are non-empty strings.

## Warn when no repository of the appropriate type has been specified

TBD

## Allow further types of components to be published

* Publishing Ear project -> only runtime dependencies should be included.
* Publishing C++ Exe project -> only runtime dependencies should be included.
* Publishing C++ Lib project -> only runtime and headers dependencies should be included. Artifacts should not use classifiers, header type should be 'cpp-headers', not 'zip'.
* Fix No pom published when using 'cpp-lib' plugin, due to no main artifact.

### Test cases

* Copy existing Maven publication tests for non-java projects and rework to use `maven-publish` plugin.

## Some fixes

* Publishing to Ivy currently uses archivesBaseName for archive names.
* Honour changes to the {organisation, module, revision} made by an ivy.xml XML hook
* Honour changes to the {group, artifact, version} made by a pom.xml XML hook

## Allow Maven POM to be generated without publishing to a repository

In this step, the POM generation for a publication is moved out of the `publish` tasks and into a separate task.

1. Add `GenerateMavenPom` task type. Takes a `MavenPom` instance and `destination` file as input. Generates a `pom.xml` from this.
2. The `maven-publish` task adds a rule to define a `generate${publication}Pom` task for each publication of type `MavenPublication` that is added to
   the publications container.
3. Provide a PublishArtifact as output that is added to the `publishableFiles` of the respective `MavenPublication`.
4. Change `MavenPublicationInternal` so it is not Buildable. All dependency wiring is done via `getPublishableArtifacts`.
5. Update DSL docs for new task
6. Update user guide to mention how to generate the POM file for a publication

Running `gradle generateMavenPom` would generate the `pom.xml` for the default Maven publication.

### Test cases

TBD

## Customising the Maven and Ivy publication identifier

This step will allow some basic customisation of the meta data model for each publication:

1. Add `groupId`, `artifactId`, `version` properties to `MavenPublication` and `Pom`. Add `packaging` property to `Pom`.
2. Change `pom.xml` generation to use these properties.
3. Add `organisation`, `module`, `revision` properties to `IvyPublication` and `IvyModuleDescriptor`. Add `status` property to `IvyModuleDescriptor`.
4. Change `ivy.xml` generation to use these properties.
5. Change the `ivy.xml` generation to prefer the (organisation, module, revision) identifier of the `IvyPublication` instance from the target project
   for a project dependencies, over the existing candidate identifiers.
6. Change the `pom.xml` generation to prefer the (groupId, artifactId, version) identifier of the `MavenPublication` instance from the target project
   for project dependencies, over the existing candidate identifiers.
7. Warn when multiple publications across all projects have the same identifier.

To customise the `pom.xml`:

    apply plugin: 'maven-publish'

    publishing {
        repositories.maven { ... }

        publications.withType(MavenPublication) {
            groupId 'my-maven-group'
            artifactId 'my-artifact-id'
            version '1.2'
        }
    }

Running `gradle publish` will publish to the remote repository, with the customisations. Running `gradle publishLocalMaven` will publish to the local
Maven repository, with the same customisations.

To customise the `ivy.xml`:

    apply plugin: 'ivy-publish'

    publishing {
        repositories.ivy { ... }

        publications.ivy(IvyPublication) {
            organisation 'my-organisation'
            module 'my-module'
            revision '1.2'
        }
    }

We might also add an `ivy` and `maven` project extension as a convenience to specify defaults for all publications of the appropriate type:

    apply plugin: 'ivy-publish'

    ivy {
        organisation 'my-organisation'
        module 'my-module'
        revision '1.2'
    }

And:

    apply plugin: 'maven-publish'

    maven {
        groupId 'my-group'
        artifactId 'my-module'
        version '1.2'
    }

### Integration test cases

* A build with project-A depends on project-B.
    1. Customise the Ivy module identifier and Maven coordinates of project-B.
    2. Publish both projects to an Ivy repository.
    3. Assert that another build can resolve project-A from this Ivy repository.
    4. Publish both projects to a Maven repository.
    5. Assert that another build can resolve project-A from this Maven repository.

## Allow outgoing dependencies to be customised

This step decouples the incoming and outgoing dependencies, to allow each publication to include a different set of dependencies:

1. Add a `MavenDependency` interface, with the following properties:
    * `groupId`
    * `artifactId`
    * `version`
    * `type`
    * `optional`
2. Add a `MavenDependencySet` concept. This is a collection of things that can be converted into a collection of `MavenDependency` instances.
3. Add a named container of `MavenDependencySet` scopes to `Pom`. The following scopes would be made available:
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
6. Add a named container of `IvyConfiguration` instances to `IvyModuleDescriptor`.

For the singleton `ivy` and `maven` instances, these properties would have defaults as specified earlier.

Note that there are some pieces left out intentionally here: Maven `scope` and `systemPath` dependency properties and `system` scope and `exclusions`,
and Ivy configuration extension, configuration mappings, and artifact includes and excludes.

To customise a Maven publication:

    apply plugin: 'maven-publish'

    publishing {
        publications {
            maven(MavenPublication) {
                pom.scopes {
                    compile "some-group:some-artifactId:1.2"
                    runtime = [compile, "some-group:some-artifactId:1.2:runtime"]
                    test = []
                }
            }
        }
    }

To customise an Ivy publication:

    apply plugin: 'ivy-publish'

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.configurations {
                    compile "some-group:some-module:1.2"
                    testRuntime = []
                }
            }
        }
    }

TBD - specify applicable dependency conversions

## Can attach multiple components to a publication

TBD

## Signing plugin supports signing a publication

To sign an Ivy module when it is published to the remote repository:

    TBD

To sign a Maven module when publishing a release build to the remote repository:

    TBD

Running `gradle release` will build, sign and upload the artifacts.
Running `gradle publish` will build and upload the artifacts, but not sign them.
Running `gradle publishMavenLocal` will build the artifact, but not sign them.

## Add support for resolving and publishing via SFTP

Add an SFTP resource transport and allow this to be used in an Ivy or Maven repository definition.

## Add support for resolving and publishing via WebDAV

Add a WebDAV resource transport and allow this to be used in an Ivy or Maven repository definition.

## Port Maven publication from Maven 2 to Maven 3 classes

1. Use Maven 3 classes to generate the POM.
2. Expose a Maven 3 `Project` object via `MavenPom.model`.
3. Use Maven 3 classes to update the `maven-metadata.xml` and wire this into `MavenResolver`.
4. Use `MavenResolver` to publish a Maven publication.
5. Change legacy `MavenDeployer` to use `MavenResolver`.
6. Remove Maven 2 as a dependency.
7. Remove jarjar hacks from Maven 3 classes.

## Publish components in dependency order

Ensure that when publishing multiple components to a given destination, that they are published in dependency order.

## Remove old DSL

These would be mixed in to various steps above (TBD), rather than as one change at the end. They are grouped together here for now:

1. Deprecate and later remove MavenDeployer and MavenInstaller and associated classes.
2. Deprecate and later remove the `Upload` task. This means the only mechanism for publishing an Ivy module is via the `ivy-publish` plugin.
3. Deprecate and later remove the `maven` plugin. This means the only mechanism for publishing a Maven module is via the `maven-publish` plugin.
4. Deprecate and later remove support for signing a configuration and Maven deployer.
5. Deprecate and later remove `Configuration.artifacts` and related types.
6. Change `DependendencyHandler` to become a container of `ResolvableDependencies` instances. Use `dependencies.compile` instead of
   `configurations.compile`
7. Deprecate and later remove `ResolvedConfiguration` and related types.
8. Deprecate and later remove `Configuration` and related types.
9. Deprecate and later remove support for resolving or publishing using an Ivy DependencyResolver implementation.

## Add further meta-data customisations

At any point above, and as required, more meta-data for a publication can be made available for customisation. In particular:

1. Add `name`, `description`, `url`, `licenses`, `organization`, `scm`, `issueManagement` and `mailingLists` to `MavenPublication`
2. Add extended attributes to `IvyModuleDescriptor`, `IvyConfiguration` and `IvyArtifact`.
3. Add exclusions, inclusions, etc.

## Introduce components

A rough plan:

1. Add `Component` and container of components.
2. The `java-base` plugin adds a `java` component.
    * Has-a api classpath and a runtime classpath, expressed as a `Classpath` (a container to which dependencies and files can be added, can be
      resolved as either a dependency set or a file collection).
3. The `java` plugin specialises the `java` component:
    * Wires up the `runtime` dependencies to the `runtime` classpath.
    * Adds the Jar to the `runtime` classpath.
4. The `ivy-publish` plugin wires up the `api` and `runtime` configurations to the `api` and `runtime` classpaths.
5. The `maven-publish` plugin wires up the `api` and `runtime` scopes to the `compile` and `runtime` classpaths.
6. The `idea` plugin and `eclipse` plugin export the dependencies included in the `api` classpath.
7. The `application`, `war` and `ear` plugins bundle up the `runtime` dependencies of the `java` component.
    * Adds a `jvm-application`, `war` and `ear` component, respectively.
8. The `cpp-lib` plugin adds a `cpp-library` component for each library.
9. The `cpp-exe` plugin add a `cpp-executable` component for each executable.
10. The `javascript` plugin adds a `javascript` component.

Each component is available as a local publication, and can be referenced from any other project.

TBD - how each component is mapped to Ivy and Maven modules.
    - is each component merged into the 'main' publications?
    - or is there a publication per component?
    - or is there explicit attachment?
    - does applying the '$lang-library' plugin implicitly make the library component available as a publication?

TBD - consuming components.

# Open issues

* Add a packaging to a publication, add multiple packagings to a publication.
* How to get rid of `Configuration.artifacts`?
* How to map a project dependency to Ivy publication or Maven publication when generating descriptor?
* Add in local publications.
* Add Gradle descriptor.
* Move Project.repositories to Project.dependencies.repositories.
* Validation: Is is an error to call `publish` without defining any publications and/or repositories?
