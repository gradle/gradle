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

## Customising Ivy descriptor XML (DONE)

This initial step will provide some capability to modify the generated `ivy.xml` files before publication. It will introduce a new set of tasks for publishing
to repositories.

1. Introduce a `Publication` interface. Probably make `Publication` a `Named` thing.
2. Add a `Publication` container to the project. At this stage, this container will be _read-only_.
3. Add `IvyPublication` interface and `IvyModuleDescriptor` interface.
    * Has-a property called `descriptor` of type `IvyModuleDescriptor`.
4. Add an `ivy-publish` plugin that adds a single `IvyPublication` instance to the container.
5. When using this `IvyPublication` for publishing, the following defaults are used:
    * `organisation` == `project.group`
    * `module` == `project.name` (_not_ `archivesBaseName`)
    * `revision` == `project.version`
    * `status` == `project.status`
    * Only dependency declarations from public configurations are included, and no configuration extension is used.
    * All artifacts from public configurations are published.
6. Add an `IvyPublish` task type.
    * Has-a property called `publication` of type `IvyPublication`
    * Has-a property called `repository` of type `IvyArtifactRepository`
7. When the `ivy-publish` plugin is applied, a rule is added that will add a `publish${publication.name}` task of type `Publish` for each publicaton
   added to the publications container.
    * The `publication` property will be wired up to the publication
    * The `repository` property will be left unspecified.
8. When the `ivy-publish` plugin is applied, a lifecycle task called `publish` is added that dependsOn those tasks added by the above rule.
9. Add `IvyModuleDescriptor.withXml()` methods and wire this up to `ivy.xml` generation.

TBD - Add a `publishing` extension with repositories and publications

To publish an Ivy module:

    apply plugin: 'ivy-publish'

    publishing {
        repositories {
            ivy { ... }
        }
    }

Running `gradle publish` will build the artifacts and upload to the specified repository.

To customise the `ivy.xml`:

    apply plugin: 'ivy-publish'

    publishing {
        repositories.ivy { ... }

        publications.ivy.descriptor.withXml { xml -> ... }
    }

Another example, using rules:

    apply plugin: 'ivy-publish'

    publishing {
        repositories.ivy { ... }

        publications.withType(IvyPublication) {
            ivy.withXml { xml -> ... }
        }
    }

To publish to multiple repositories:

    apply plugin: 'ivy-publish'

    publishing {
        repositories {
            ivy { ... }
            ivy { ... }
        }
    }

Running `gradle publish` will publish the module to both repositories.

The `ivy-publish` plugin is intended to move Ivy concepts out of the core Gradle DSL, but still allow them to be available for customisation for those
projects that use Ivy. It also allows us to introduce some breaking changes, in an opt-in way.

Note: there are some breaking changes here when you apply the `ivy-publish` plugin:

* The project name rather than the `archivesBaseName` property is  used as the default Ivy module name.
* Only the dependency declarations and artifacts from public configurations are referenced in the generated Ivy descriptor and published to the
      repository.

Note that publishing multiple Ivy modules is not yet supported. This is covered by later stories.

### Implementation approach

The `IvyPublication` instance will need to be considered by `IvyBackedArtifactPublisher` when generating the Ivy descriptor. It might make sense
at this point to start pulling descriptor generation up, so that it can eventually be generated by a task.

### Test cases

* Basic test that running the `publish` task actually publishes something.
    * With java plugin
    * With no other plugins
* Multi-project build with project dependencies that is published to an Ivy repository can be successfully resolved by another build.
* A `withXml` action can be used to modify the generated `ivy.xml`.
* Decent error message when the `withXml` action fails.
* Decent error message when no repository has been specified for the `publishIvy` task.
* Descriptor contains non-ascii characters.

## Customising Maven descriptor XML

This step will provide some capability to modify the generated `pom.xml` files before publication. It will introduce the ability to use the new
publication tasks to publish Maven modules.

1. Add `MavenPublication` interface and `MavenPom` interface.
    * A `MavenPublication` has-a property called `pom` of type `MavenPom`.
2. Add a `maven-publish` plugin. This plugin adds a single `MavenPublication` instance.
3. When this `MavenPublication` instance is used for publishing, the following defaults are used:
    * `groupId` == `project.group`
    * `artifactId` == `project.name` (_not_ `archivesBaseName`)
    * `version` == `project.version`
    * `packaging` == `null`
    * No dependencies are included in the pom.
    * All artifacts from public configurations are published.
4. Add a `PublishToMavenRepository` task.
5. Change the `maven` repository type to handle publishing a `MavenPublication`.
6. Add `Pom.withXml()` methods and wire these up to pom generation.
7. When the `maven-publish` plugin is applied, a rule is added to define a `PublishToMavenRepository` task instance for each `MavenPublication` and each publishing repository of
   type `MavenRepository`.
8. When the `maven-publish` plugin is applied, a rule is added to define a `publishLocal${publication.name}` task of type `MavenPublish` for each publication
   of type `MavenPublication` added to the publications container.
    * The `publication` property is wired up to the publication
    * The `repository` property defaults to `mavenLocal()`.
9. Update the "Ivy Publishing" chapter in the user guide to also describe how to publish to a Maven repository.

To publish a Maven module:

    apply plugin: 'maven-publish'

    repositories {
        maven { ... }
    }

Running `gradle publish` will build the artifacts and upload to the specified repository. Running `gradle publishMavenLocal` will build the artifacts and
copy them into the local Maven repository.

To customise the `pom.xml`:

    apply plugin: 'maven-publish'

    publishing {
        repositories.maven { ... }

        publications.maven.pom.withXml { xml -> ... }
    }

Or, using rules:

    apply plugin: 'maven-publish'

    publishing {
        repositories.maven { ... }

        publications.withType(MavenPublication) {
            pom.withXml { xml -> ... }
        }
    }

Publishing both an Ivy and Maven module:

    apply plugin: 'ivy-publish'
    apply plugin: 'maven-publish'

    publishing {
        repositories {
            ivy { ... }
            maven { ... }
        }
    }

Running `gradle publish` will build and upload both modules.

Note: there are some breaking changes here when you apply the `maven-publish` plugin:

* The project name rather than `archivesBaseName` property is used as the default Maven module artifactId.
* No dependencies are included in the generated `pom.xml`.
* Only artifacts from public configurations are included the publication.

Note that publishing multiple Maven modules is not yet supported. It is also not possible to add any dependencies to the pom (except via manipulating
the XML). These are covered by later stories.

### Implementation approach

It should be possible to implement this as an adapter over the existing MavenPom/MavenResolver infrastructure.

### Test cases

* Basic test that running the `publish` task publishes artifacts and the pom.
* A `withXml` action can be used to modify the generated `pom.xml`.
* Decent error message when the `withXml` action fails.
* Descriptor contains non-ascii characters.

## Allow Ivy module descriptor to be generated without publishing to a repository

In this step, the meta-data file generation for an Ivy publication is moved out of the `publish` tasks and into a separate task.

1. Add `GenerateIvyDescriptor` task type. Takes a `IvyModuleDescriptor` as input and generates an `ivy.xml` from this.
2. The `ivy-publish` task adds a rule to define a `generate${publication}MetaData` task for each publication of type `IvyPublication` added to the
   publications container.
3. Remove the `file` property from `IvyModuleDescriptor`, and replace it with a `descriptorFile` property on `IvyPublication`.
4. Change `IvyModuleDescriptor` so that it no longer extends `Buildable`.
5. Change the `GenerateIvyDescriptor` task type to remove the `module` and `configurations` properties, and replace these with a `descriptor` property of
   type `IvyModuleDescriptor`.
6. Update user guide to mention how to generate the `ivy.xml` for a publication.

To generate the Ivy module descriptor:

    apply plugin: 'ivy-publish`

Running `gradle generateIvyMetaData` would generate the `ivy.xml` for the default Ivy publication for this project.

### Test cases

* Unignore IvyLocalPublishIntTest.canGenerateTheIvyXmlWithoutPublishing().

## Allow Java libraries to be published

This story starts to introduce some component-specific publication.

1. Change the publish plugins to publish an empty publication when the `base` plugin has not been applied. Here 'empty' means no artifacts except the
   descriptor are uploaded and that the generated descriptor contain only the publication identifier.
2. Fix publishing a Maven publication with no artifacts. Currently, this does not work.
3. Change the Maven publish plugin to publish artifacts from the `archives` configuration when the `base` plugin has been applied. No dependencies should
   be included in the generated POM.
4. Change the Ivy publish plugin to publish artifacts from the `archives` configuration when the `base` plugin has been applied. No dependencies should
   be included in the generated descriptor. The descriptor should contain a single `artifacts` configuration.
5. Introduce a `Component` interface that extends `Named` and add a `components` container to `Project`. This container will initially be _read-only_ from
   a user's point of view. Also add an associated `ComponentInternal` interface.
6. Change the Java plugin to add a `Component` instance called `java` to this container.
7. Change the Maven publish plugin to include dependencies declared in `configurations.runtime.allDependencies` in the generated POM when the `java`
   component instance is present. Add the appropriate methods to `ComponentInternal` to allow the component instance to specify which dependencies
   should be included so that the Maven publish plugin does not have any knowledge of the Java plugin.
8. Change the Ivy publish plugin to include dependencies declared in `configurations.runtime.allDependencies` in the `runtime` configuration in the
   generated descriptor. Also include a `default` configuration that extends `runtime`. As for the Maven publishing, the Ivy publish plugin should have
   no knowledge of the Java plugin.
5. Change the Maven publish plugin to include no dependencies in the generated POM when the `java` plugin has not been applied.
6. Change the Maven publish plugin to include the dependencies from the `runtime` configuration in the generated POM when the `java` plugin has been applied.
7. Publishing Java project -> only runtime and default configurations should be included.

To publish a Java library:

    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = 'some.group'
    version = '1.2'

    dependencies {
        compile 'a:b:c'
        runtime 'e:f:g'
    }

    publishing {
        repositories {
            maven { url 'http://myorg.com' }
        }
    }

When `gradle publish` is run, the Jar and generated POM are uploaded. The generated POM will include:

    <dependencies>
        <dependency>
            <groupId>a</groupId>
            <artifactId>b</artifactId>
            <version>c</version>
        </dependency>
        <dependency>
            <groupId>e</groupId>
            <artifactId>f</artifactId>
            <version>g</version>
        </dependency>
    </dependencies>

To publish additional artifacts:

    apply plugin: 'java'
    apply plugin: 'maven-publish'

    artifacts {
        archives mySourceJar
    }

When `gradle publish` is run, the source Jar is built and uploaded along with the Jar and POM.

Note that there are several breaking changes here:

1. Only those artifacts from the `archives` configuration are published.
2. Only dependencies from the Java plugin's `runtime` configuration are included in the generated descriptors.
3. The Ivy plugin uses the `artifacts` configuration instead of `archives` to represent all the artifacts of the module.

### Test cases

* Publish a project without any other plugins applied.
* Publish a java project with compile, runtime and testCompile dependencies. Verify only the compile and runtime dependencies are included in the
  descriptors.
* Publish a multi Java project build with project dependencies, and verify all libraries and transitive dependencies can be successfully resolved from
  another build.
* Add a cross-version test that verifies a Java project published by the current version of Gradle can be consumed by a previous version of Gradle,
  and vice versa.
* Copy existing Maven publication tests for java libraries and rework to use `maven-publish` plugin.

## Disallow publication to Ivy or Maven repositories when group or version has not been specified

TBD

## Warn when no repository of the appropriate type has been specified

TBD

## Allow other types of components to be published

* Add a DSL for specifying exactly which components should be published. By default no components will be published.
* Maven publish fails when attempting to publish multiple components.
* No pom published when using 'cpp-lib' plugin, due to no main artifact.
* Publishing War project -> only runtime configuration should be included.
* Publishing Ear project -> only runtime configuration should be included.
* Publishing C++ Exe project -> only runtime configuration should be included.
* Publishing C++ Lib project -> only runtime and headers configurations should be included. Artifacts should not use classifiers, header type should be 'cpp-headers', not 'zip'.

### Tests

* Copy existing Maven publication tests for non-java projects and rework to use `maven-publish` plugin.
* Lots more TBD


## Some fixes

* Publishing to Ivy uses archivesBaseName for archive names.

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

Running `gradle publish` will publish to the remote repostory, with the customisations. Running `gradle publishLocalMaven` will publish to the local
Maven repository, with the same customisations.

To customise the `ivy.xml`:

    apply plugin: 'ivy-publish'

    publishing {
        repositories.ivy { ... }

        publications.ivy {
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
            maven {
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
            ivy {
                descriptor.configurations {
                    compile "some-group:some-module:1.2"
                    testRuntime = []
                }
            }
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

For the singleton `ivy` and `maven` instances, these properties would have defaults as specified earlier.

To customise a Maven publication:

    apply plugin: 'maven-publish'

    publishing {
        publications {
            maven {
                mainArtifact jar
                artifacts = [sourceJar, javadocJar]
                artifact file: distZip, classifier: 'dist'
            }
        }
    }

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

TBD - applicable artifact conversions

## Allow additional publications to be defined

In this step, additional publications can be defined.

1. Allow `IvyPublication` and `MavenPublication` instances to be added to the publications container.

To publish multiple Ivy modules:

    apply plugin: 'ivy-publish'

    publishing {
        repositories { ivy { ... } }

        publications {
            api(type: IvyPublication) {
                module 'my-api'
                ...
            }
        }
    }

Running `gradle publish` will build and upload both modules to the Ivy repositories.

TBD - Which publication does a project dependency refer to? The 'main' one? All of them?

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

* Which things are published, say, when the 'java', 'war' and 'ejb' plugins are all applied to a project?
* How do components fit into all this? What happens when a project produces multiple components?
* How to get rid of `Configuration.artifacts`?
* How to map a project dependency to Ivy publication or Maven publication when generating descriptor?
* Add in local publications.
* Add Gradle descriptor.
* Move Project.repositories to Project.dependencies.repositories.
* Validation: Is is an error to call `publish` without defining any publications and/or repositories?
