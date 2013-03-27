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

## I want to customise a publication based on its destination

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

## Publish Java libraries and web applications to Ivy repository

1. Change the `ivy-publishing` plugin so that it no longer defines any publications.
2. Allow `IvyPublication` instances to be added to the publications container.
    - Default (organisation, module, revision) to (project.group, project.name, project.version)
3. Allow zero or one components to be added to an Ivy publication.
4. When publishing a java library, declare the runtime dependencies and the JAR artifact in the descriptor.
5. When publishing a web application, declare the WAR artifact in the descriptor.
6. Include a default configuration in the descriptor.

Note: there is a breaking change in this story.

### Test cases

* Run `gradle publish` for a project with just the `ivy-publish` plugin applied. Verify nothing is published.
* Publish a java project with compile, runtime and testCompile dependencies.
    * Verify that the jar artifact is included in the descriptor runtime configuration.
    * Verify only the compile and runtime dependencies are included in the descriptor runtime configuration.
* Publish a war project with compile, runtime, providedCompile, providedRuntime and testCompile dependencies.
    * Verify that the war artifact is published and included in the descriptor runtime configuration.
    * Verify that no dependencies are included in the descriptor.
* Publish multiple projects with the `java` or `war` plugin applied and project dependencies between the projects.
    * Verify descriptor files contain appropriate artifact and dependency declarations.
    * Verify that libraries and transitive dependencies can be successfully resolved from another build.
* Cross-version test that verifies a Java project published by the current version of Gradle can be consumed by a previous version of Gradle,
  and vice versa.

## Allow outgoing artifacts to be customised for Ivy publications

1. Add an `IvyArtifact` interface with the following attributes:
    * `name`
    * `type`
    * `extension`
    * `file`
    * `conf`
    * `classifier`
2. Add an `IvyArtifactSet` interface. This is a collection of objects that can be converted to a collection of `IvyArtifact` instances.
3. Add an `IvyConfiguration` interface. Add a `configurations` container to `IvyModuleDescriptor`
4. Add an `artifacts` property to `IvyConfiguration`.
5. When publishing or generating the descriptor, validate that the (name, type, extension, classifier) attributes are unique for each artifact.
6. When publishing, validate that the artifact file exists and is a file.

To customise an Ivy publication:

    apply plugin: 'ivy-publish'

    publishing {
        publications {
            ivy {
                configurations {
                     runtime
                     distributions
                     other {
                        extend "runtime"
                     }
                }
                artifacts = [jar]
                artifact sourceJar {
                    conf 'other'
                }
                artifact file: distZip, type: 'java-library-distribution', conf: 'other,distributions'
            }
        }
    }

The 'artifact' creation method will accept the following forms of input:
* A PublishArtifact, that will be adapted to IvyArtifact
* An AbstractArchiveTask, that will be adapted to IvyArtifact
* Anything that is valid input to Project.file()
* Any of the previous 4, together with a configuration closure that permits setting of name, type, classifier, extension and builtBy properties
* A map with 'file' entry, that is interpreted as per Project.file(). Additional entries for 'name', 'type', 'extension', 'classifier' and 'builtBy'.

Configurations will be constructed with no value for 'visibility', 'description', 'transitive', or 'deprecated' attributes, and will inherit ivy defaults.
The configuration 'extends' attribute is a string value, not validated.

Artifacts will be constructed with no attribute for 'conf' unless explicitly specified.
This allows them to inherit the default ('*') in ivy.xml or take the default value from the parent <publications/> element.

### Test cases

* Verify that `archivesBaseName` does not affect the published artifact names.

## Handle compound source inputs when adding artifacts to Ivy or Maven publications

Currently, all artifact inputs map one-one with a published artifact. We should handle compound inputs that will result in the creation of multiple artifacts.

Inputs to consider:
* FileCollection
* TaskOutputs
* Task (take TaskOutputs)
* Any Iterable<Object>

Any supplied configuration closure will be applied to each created artifact.

### Test cases

* Unit tests for various conversions
* For both of `ivy-publish` and `maven-publish`
    * Publish with artifact constructed by task with multiple file outputs. Validate that the task executes before publishing, and that each output is added to the publication as an artifact.
    * Publish with artifacts constructed from a Collection containing a File, Map, and Task inputs.
    * Verify that configuration closure is applied to each artifact generated from a compound input.
    * Verify that publication fails with artifact from TaskOutputs that includes a directory output.

## Validate publication coordinates

1. Validate the following prior to publication:
    * The groupId, artifactId and version specified for a Maven publication are non-empty strings.
    * The groupId and artifactId specified for a Maven publication match the regexp `[A-Za-z0-9_\\-.]+` (see `DefaultModelValidator` in Maven source)
    * The organisation, module and revision specified for an Ivy publication are non-empty strings.
    * Each publication identifier in the build (ie every publication in every project) is unique.
    * The XML actions do not change the publication identifier.
2. Reorganise validation so that it is triggered by the `MavenPublisher` service and the (whatever the ivy equialent is) service.
3. Use a consistent exception heirarchy for publication validation failures. For example, if using an `InvalidMavenPublicationException` then add an equivalent
   `InvalidateIvyPublicationException`.

### Test cases

* Publication fails for a project with no group or version defined.
* Publication coordinates can contain non-ascii characters, whitespace, xml markup and reserved filesystem characters, where permitted by the format.
  Verify that these publications can be resolved by Gradle.
* Reasonable error messages are given when the above validation fails.

## Validate artifact attributes

Validate the following prior to publication:

* The extension, classifier specified for a Maven artifact are non-empty strings.
* The name, extension, type, and classifier if specified, for an Ivy artifact are non-empty strings.
* When publishing to a repository, validate that each artifact (including the meta-data file) will be published as a separate resource.

### Test cases

* Artifact attributes can contain non-ascii characters, whitespace, xml markup and reserved filesystem characters, where permitted by the format.
  Verify that these artifacts can be resolved by Gradle.
* Reasonable error messages are given when the above validation fails.

## Verify that publications can be consumed by Ivy and Maven

### Test cases

* Check that an Ant build that uses Ivy can resolve a Java library published to an Ivy repository.
* Check that an Ant build that uses Ivy can resolve a Java library published to an Maven repository.
* Check that a Maven build can resolve a Java library published to a Maven repository.

## Report on failures to publish

There are many cases where a repository may fail to publish the requested artifacts successfully.
One example is publishing to a Windows FileRepository an artifact with version containing ":", which is illegal in a windows file name.
The Maven Ant tasks (and possibly the Ivy DependencyResolver) will silently fail in these cases.

This story will address this issue, by ensuring that failure to publish is detected by the supported repository implementations, and that this failure is reported to the user.

1. Replace DependencyResolverIvyPublisher with an implementation built directly on top of ExternalResourceRepository.
   - No ivy concepts should be in this implementation if possible
   - Reuse code from resolver for mapping artifact attributes -> primary URL
2. Replace AntTaskBackedMavenPublisher with an implementation built directly on top of ExternalResourceRepository.
   - Reuse Maven code for creating maven-metadata.xml if possible
   - No ivy concepts should be introduced to this implementation
   - Reuse code from resolver for mapping artifact attributes -> primary URL
3. Update ExternalResourceRepository.put() so that it reports on any failure to publish, and ensure that these failures are reported in the publishing output.

### Test cases

* Create Ivy publication with version = "1:3" and publish to FileSystem repository on Windows. Assert that failure is reported.
* Publish 2 Ivy publications with versions that only differ by case to a FileSystem repository on Windows. Assert that the second publication does not overwrite the first.
* Publish an Ivy publication with extension ending in '.' to FileSystem repository on Windows. Assert that failure is reported.
* Publish an Ivy publication to an HTTP repository that returns a 500. Assert that failure is reported.
* Similar tests for Maven publications.

## Customising the Maven and Ivy publication identifier

This step will allow some basic customisation of the meta data model for each publication:

1. Add `groupId`, `artifactId`, `version` properties to `MavenPublication` and `MavenPom`. Add `packaging` property to `MavenPom`.
2. Change `pom.xml` generation to use these properties.
3. Add `organisation`, `module`, `revision` properties to `IvyPublication` and `IvyModuleDescriptor`. Add `status` property to `IvyModuleDescriptor`.
4. Change `ivy.xml` generation to use these properties. Do not default `status` to `project.status` (this value should have not effect on ivy publication).
5. Change the `ivy.xml` generation to prefer the (organisation, module, revision) identifier of the `IvyPublication` instance from the target project
   for a project dependencies, over the existing candidate identifiers.
6. Change the `pom.xml` generation to prefer the (groupId, artifactId, version) identifier of the `MavenPublication` instance from the target project
   for project dependencies, over the existing candidate identifiers.

To customise the `pom.xml`:

    apply plugin: 'maven-publish'

    publishing {
        repositories.maven { ... }

        publications {
            maven(MavenPublication) {
                groupId 'my-maven-group'
                artifactId 'my-artifact-id'
                version '1.2'
                pom.packaging 'war'
            }
        }
    }

Running `gradle publish` will publish to the remote repository, with the customisations. Running `gradle publishLocalMaven` will publish to the local
Maven repository, with the same customisations.

To customise the `ivy.xml`:

    apply plugin: 'ivy-publish'

    publishing {
        repositories.ivy { ... }

        publications {
            ivy(IvyPublication) {
                organisation 'my-organisation'
                module 'my-module'
                revision '1.2'
            }
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
* Run `gradle publish` for a project that defines multiple publications and verify that they are all published

## Allow outgoing dependency declarations to be customised

This step decouples the incoming and outgoing dependency declarations, to allow each publication to include a different set of dependencies:

1. Add a `MavenDependency` interface, with the following properties:
    * `groupId` (required)
    * `artifactId` (required)
    * `version` (required)
    * `type` (optional, not empty string)
    * `optional` (boolean, default to false and do not include in POM)
    * `scope` (optional, default to null and restrict values to [compile, provided, runtime, test, system])
2. Add a `MavenDependencySet` concept. This is a collection of `MavenDependency` instances.
3. Add a `MavenDependencySet` to `MavenPublication`.
4. Extend the `IvyDependency` to add the following properties:
    * `organisation` (required)
    * `module` (required)
    * `revision` (required)
    * `confMapping` (optional, not empty string)
5. Add an `IvyDependencySet` concept. This is a collection of `IvyDependency` instances.
6. Add an `IvyDependencySet` to `IvyPublication`.

To add dependencies to a Maven publication:

    apply plugin: 'maven-publish'

    publishing {
        publications {
            maven(MavenPublication) {
                dependency "foo:bar:1.0:provided"
                dependency "other-group:other-artifact:1.0" {
                    scope "compile"
                }
                dependency groupId: "some-group", artifactId: "some-artifact", version: "1.4", scope: "provided"
            }
        }
    }

To replace dependencies in a Maven publication:

    apply plugin: 'maven-publish'

    publishing {
        publications {
            maven(MavenPublication) {
                dependencies = [
                    "other-group:other-artifact:1.0",
                    {groupId: "some-group", artifactId: "some-artifact", version: "1.4", scope: "provided"}
                ]
            }
        }
    }

To add dependencies to an Ivy publication:

    apply plugin: 'ivy-publish'

    publishing {
        publications {
            ivy(IvyPublication) {
                configurations {
                    ...
                }
                dependency "some-org:some-group:1.0" // empty confMapping value
                dependency "some-org:some-group:1.0:confMapping"
                dependency organisation: "some-org", module: "some-module", revision: "some-revision", confMapping: "*->default"
                dependency {
                    organisation "some-org"
                    module "some-module"
                    revision "1.1"
                    // use empty confMapping value
                }
            }
        }
    }

To replace dependencies in an Ivy publication:

    publishing {
        publications {
            ivy(IvyPublication) {
                configurations {
                    ...
                }
                dependencies = [
                    "some-org:some-group:1.0:confMapping"
                ]
            }
        }
    }

The 'dependency' creation method will accept the following forms of input:
* An `ExternalModuleDependency`, that will be adapted to IvyDependency/MavenDependency
* An string formatted as "groupId:artifactId:revision[:scope]" for Maven, or "organisation:module:version[:confMapping]" for Ivy
* A configuration closure to specify values for created dependency
* Either of the first 2, together with a configuration closure that permits further configuration (like adding scope/conf)
* A map that is treated as per the configuration closure.

## Fix POM generation issues

* excludes on configuration.
* dynamic versions.
* wildcard excludes.

## Warn when no repository of the appropriate type has been specified

TBD

## Customise the output file for the generated descriptor

TBD

## Web application is published with runtime dependencies

Provided dependencies should be included in the generated POM and ivy.xml

## Allow further types of components to be published

* Publishing Ear -> container runtime dependencies should be included.
* Publishing C++ Exe -> runtime dependencies should be included.
* Publishing C++ Lib -> runtime, link and compile-tome dependencies should be included. Artifacts should not use classifiers, header type should be 'cpp-headers', not 'zip'.
* Publishing distribition -> no dependencies should be included.
* Fix No pom published when using 'cpp-lib' plugin, due to no main artifact.

## Add support for resolving and publishing via SFTP

Add an SFTP resource transport and allow this to be used in an Ivy or Maven repository definition.

## Add support for resolving and publishing via WebDAV

Add a WebDAV resource transport and allow this to be used in an Ivy or Maven repository definition.

## Can sign a publication

To sign an Ivy module when it is published to the remote repository:

    TBD

To sign a Maven module when publishing a release build to the remote repository:

    TBD

Running `gradle release` will build, sign and upload the artifacts.
Running `gradle publish` will build and upload the artifacts, but not sign them.
Running `gradle publishMavenLocal` will build the artifact, but not sign them.

## Publish source and API documentation for JVM libraries

## Reuse the Gradle resource transports for publishing to a Maven repository

To provide progress logging, better error reporting, better handling of authenticated repositories, etc.

## Port Maven publication from Maven 2 to Maven 3 classes

1. Use Maven 3 classes to generate the POM.
2. Expose a Maven 3 `Project` object via `MavenPom.model`.
3. Use Maven 3 classes to update the `maven-metadata.xml` and wire this into `MavenResolver`.
4. Use `MavenResolver` to publish a Maven publication.
5. Change legacy `MavenDeployer` to use `MavenResolver`.
6. Remove Maven 2 as a dependency.
7. Remove jarjar hacks from Maven 3 classes.

## Can attach multiple components to a publication

TBD

## Publish components in dependency order

Ensure that when publishing multiple components to a given destination, that they are published in dependency order.

## Validate publish credentials early in the build

Fail fast when user-provided credentials are not valid.

## Publish artifacts as late as possible in the build

Schedule validation tasks before publication tasks, while still respecting task dependencies.

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

# Open issues

* Live collections of artifacts.
* Add a packaging to a publication, add multiple packagings to a publication.
* How to get rid of `Configuration.artifacts`?
* How to map a project dependency to Ivy publication or Maven publication when generating descriptor?
* Add in local publications.
* Add Gradle descriptor.
* Move Project.repositories to Project.dependencies.repositories.
* Validation: Is is an error to call `publish` without defining any publications and/or repositories?
