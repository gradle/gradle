An Ivy module has a *status*, declared in the module's descriptor, which indicates the module's maturity. The status can be used
in a version selector of the form `latest.someStatus`, which resolves to the highest version with the given or a more mature status.
By default, the list of Ivy statuses is `release`, `milestone`, `integration`, with the latter being the least mature. If a module descriptor
has no status, it defaults to `integration`. It's possible to specify a user-defined list of statuses (and a user-defined default status)
in the Ivy settings file.

To support user-defined module statuses in Gradle, we introduce the concept of *Gradle component metadata*, Gradle's own model
for component metadata. Initially, this type of metadata will be declared in the build script. In the future, it might also come from other places
(repository, custom descriptor, external service, etc.). Initially, the model will contain two properties:

* `status`: The status of a component.
* `statusScheme`: A list of valid statuses for a component, ordered from least to most mature.

`statusScheme` defaults to [`integration`, `milestone`, `release`]. `status` defaults to the status declared in the (Ivy) descriptor, or
`integration` if no status has been declared.

# Use cases

Some users have existing Ivy repositories containing modules with custom statuses. To facilitate coexistence and stepwise
(i.e. build-by-build) migration from Ant to Gradle, the latter needs to be able to publish and consume modules with custom statuses.

Publishing modules with custom statuses is already possible, as Gradle allows to fully customize the generated Ivy descriptor.
However, Gradle cannot currently consume modules with custom statuses.

Rich module metadata is an enabler for other interesting features. (What are some good examples?)

## Story: Make branch attribute available when publishing and resolving Ivy modules

The `ivy` metadata file format has a `branch` attribute, which is currently not available to Gradle users. This story makes this
attribute available in ivy-specific APIs within Gradle.

### User visible changes

Users will be able to set the 'branch' attribute when publishing with `IvyPublication`

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.branch = 'testing'
            }
        }
    }

Users will be able to access the 'branch' attribute when resolving, via the `IvyModuleMetadata`

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details, IvyModuleMetadata ivyModule ->
                if (ivyModule.branch == 'testing') {
                    details.status == 'testing'
                }
            }
        }
    }

### Implementation

- Add 'branch' property to `IvyModuleDescriptor`. Default is null.
- Validate branch value in `ValidatingIvyPublisher`: can be either null, or valid as per 'organisation'.
- Add a branch attribute to the generated ivy descriptor if the `IvyModuleDescriptor` has a non-empty branch value.
- Add read-only 'branch' property to both `IvyModuleMetadata` and `IvyModuleVersionMetaData` (internal).
- Populate the branch property from the Ivy module descriptor

### Test coverage

- Publish an ivy module with a branch value specified
    - Verify published ivy.xml file
    - Resolve and verify branch value is correct in `IvyModuleMetadata`.
- Publish and resolve with branch value that contains non-ascii characters
- Reasonable error message when publishing with invalid branch value
- Branch attribute is cached until module is refreshed

### Open issues

- Sync up the IvyModuleDescriptor and IvyModuleMetadata: add extra-info and status to descriptor, add
- Rename IvyModuleDescriptor -> IvyModuleDescriptorSpec, IvyModuleMetadata -> IvyModuleDescriptor

## Story: Build script reports all versions tested for dynamic version

This story takes a step toward allowing a build author to provide logic for selecting the correct version for a dynamic dependency.

A 'versionSelection' rule can be added to `ResolutionStrategy`.
This rule is fired any time a candidate version is compared to see if it matches a dynamic version.

- Any number of `versionSelection` rules can be added to a `ResolutionStrategy`.
- All rules are fired.
- The order in which they are fired is not deterministic.

### User visible changes

    interface VersionSelection {
        ModuleComponentSelector getRequested()
        ModuleComponentIdentifier getCandidate()
    }

    configurations.all {
        resolutionStrategy {
            versionSelection {
                any { VersionSelection selection ->
                    println "Comparing module ${selection.candidate} to requested version ${selection.requested.version}"
                }
            }
        }
    }


### Implementation

- Rules will be fired from `NewestVersionComponentChooser.chooseBestMatchingDependency`

## Story: Build logic selects the module that matches an external dependency

- If no rule sets the status of the VersionSelection, the default VersionMatcher algorithm is used
- Failure if 2 rules set the status of the VersionSelection in different ways

### User visible changes

    configurations.all {
        resolutionStrategy {
            versionSelection {
                // Selector 'dev' matches any version ending with 'dev'
                any { VersionSelection selection ->
                    if (selection.requested.version == 'dev' && selection.candidate.version.endsWith('dev')) {
                        selection.accept()
                    }
                }
                // Selector 'not-zero' matches any version that doesn't start with '0'
                any { VersionSelection selection ->
                    if (selection.requested.version == 'zero' && !selection.candidate.version.startsWith('0')) {
                        selection.reject()
                    }
                }
            }
        }
    }

## Story: Build script targets versionSelection rule to particular module

### User visible changes

    configurations.all {
        resolutionStrategy {
            versionSelection {
                group "foo" { VersionSelection selection ->
                }
                group "foo" module "bar" { VersionSelection selection ->
                }
                module "foo:bar" { VersionSelection selection ->
                }
            }
        }

## Story: Version selection rule takes ComponentMetadataDetails and/or IvyModuleMetadata as input

### User visible changes

    configurations.all {
        resolutionStrategy {
            versionSelection {
                any { VersionSelection selection, ComponentMetadataDetails metadata ->
                }
                any { VersionSelection selection, IvyModuleMetadata ivyModule ->
                }
            }
        }
    }

## Use Artifactory properties to determine status of module

This story makes it possible to access published Artifactory properties from within a Component Metadata Rule:
http://www.jfrog.com/confluence/display/RTF/Properties.

If rule declares that it requires custom Artifactory properties as an input, these properties will be read, cached and provided to the rule.

For now, Artifactory properties will be treated like other metadata in terms of caching (ie: will be updated when changing module is refreshed).

### User visible changes

    apply plugin: 'artifactory-properties'

    componentMetadata {
        eachComponent { ComponentMetadataDetails details, ArtifactoryProperties props ->
            if (props['my-custom-attribute'] == 'value') {
                details.status == 'release'
            }
        }
    }

### Implementation

#### Notes

* Property access is described here: http://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ItemProperties
* Implementation should be in a plugin that is optionally applied. Can access internal apis.

#### Plan

TBD

### Test cases

* Artifactory properties are available to rule that declares them as input (both Ivy and Maven repositories)
* Artifactory properties are not available to a rule that does not declare them as input
* Artifactory properties are not downloaded if no rule declares them as input
* Artifactory properties are cached, and are updated when a changing module is refreshed.
* If an (unchanging) module is resolved initially without requiring Artifactory properties,
  a new rule that requires the properties will trigger download of these properties in a subsequent resolve.

### Open issues

* For caching, do we:
    1. Hack these values into the cache Ivy module descriptor
    2. Persist these separately
    3. Introduce a new internal module persistence format?
* Introduce public APIs so that this could be produced/maintained by JFrog
* Integrate with rule infrastructure

## Allow 'pipeline' metadata to be treated a 'changing' with a non-changing module

Sometimes certain metadata about a component is updated over time, while the component itself does not change.
An example is metadata added to the component as it travels through a build or QA pipeline, like "Passed the stage 1 tests", or "Approved for release".

Publishing a different component multiple times with the same id is not generally a good idea (changing module), but updating 'ancillary'
metadata like this is important for composing a sophisticated build pipeline.

This story will allow the pipeline metadata for a component to be treated as 'changing', while the module metadata is not.

## GRADLE-2903 - Component metadata respects changes to metadata rule implementation

It should be possible to change the implementation of a metadata rule and have those changes reflected in the meta-data components, regardless of
whether the component is cached or not, as if the rule is evaluated on each resolution (and this is certainly one possible implementation).

### Implementation

Whenever `CachingMavenRepository` needs to query the changing flag for a component, component metadata rules are evaluated just beforehand. Rules are
evaluated at most twice per dependency to be resolved (not counting any rule evaluations performed by other classes). Rules are evaluated after writing into
the metadata cache, hence any changes made by rules won't be cached.

### Test coverage

* Changes made to the changing flag by a component metadata rule aren't cached.
    * Add a rule that makes a non-changing component changing
    * Resolve the component
    * Verify that on the next resolve, component is again presented as non-changing to metadata rule

## Consume a "latest" version of an Ivy module with custom status that exists in multiple Ivy repositories

If a module exists in multiple Ivy repositories, the "latest" version should be computed across all repositories.

### User visible changes

Resolution selects highest version across all repositories, rather than highest version in first repository that contains the module.

### Implementation

TBD. May turn out that it's better/easier to implement cross-repository "latest" resolution from the start.

### Test coverage

* Declare multiple Ivy repositories. Publish different versions of the same module to different repositories. Make sure that highest version of highest compatible status is selected.

## Consume a "latest" version of a Maven module

## Consume a "latest" version of a flatDir module

## Consume a "latest" version of a module that exists in different types of repositories (Ivy, Maven, flatDir)

## The "+" sub version selector considers versions semantically

Currently, this selector does not consider the versions semantically. For example, `1+` matches `1.0` and `11.0` but not `2.0`.

Also improve the version ordering algorithm to handle cases like `1` vs `1.0`.

# Open issues

* How to deal with the situation where a configuration depends on a configuration from another project? Will all metadata come from the
  project whose configuration gets resolved, rather than from the project that declares the dependency? (We already have the same problem
  for repositories.)
* Figure out what the `integration` flag of an Ivy status means, and if/how we need to support it. See: http://ant.apache.org/ivy/history/2.0.0/settings/statuses.html
* How does "latest" resolution interact with conflict resolution? (If the latest integration version is higher than the latest release version, our conflict resolution might declare it as the winner.)
  (For now we decided not to do anything special about this, but we might have to eventually.)
