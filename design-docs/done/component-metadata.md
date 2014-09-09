## Consume an Ivy module with custom status

Gradle should be able to consume an Ivy module with custom status. Example:

    repositories {
        ivy {
            url ...
        }
    }

    dependencies {
        compile "someGroup:someModule:1.0.0"
    }

The module's status is mentioned in its Ivy descriptor, but not in the build script.

### User visible changes

No API changes are necessary. Ivy modules with custom status should be resolved in the same way as modules with a default status.

### Test coverage

* Consume a module with custom Ivy status.
* Consume a module that depends on a module with custom Ivy status.

## Consume a "latest" version of an Ivy module with custom status

A version selector of the form `latest.someStatus` will select the highest version with either the given status or a
more mature status. For example, given a status scheme `gold`, `silver`, `bronze`, `latest.silver` will select the
highest version whose status is either `silver` or `gold`.

### User visible changes

A Gradle build script may declare component metadata rules that compute the metadata for a component. This could look as follows:

    componentMetadata {
        eachComponent { details ->
            details.statusScheme = ['bronze', 'silver', 'gold']
        }
    }

Different components may use different status schemes:

    componentMetadata { details ->
        if (details.id.group == 'olympic') {
            details.statusScheme = ['bronze', 'silver', 'gold']
        } else {
            details.statusScheme = ['flop', 'top']
        }
    }

It should also be possible to set the component's status, overriding its default status.

Note: For this story, the metadata rules do not apply to components resolved from an Ivy `DependencyResolver` instance.

### Test coverage

* Declare no status scheme. Publish module versions with different statuses (from the default scheme), and consume them using different "latest" selectors.
* Declare a single "global" status scheme. Publish module versions with different statuses, and consume them using different "latest" selectors.
* Same as the previous case but with multiple different status schemes.
* Use a "latest" version selector with a status that is not contained in the module's status scheme. A meaningful error should occur.
* Publish a module version with a status that is not contained in the module's status scheme. A meaningful error should occur.
* If a module version's descriptor does not declare a status, its status defaults to the least mature status for the module's status scheme. (Matches Ivy's behavior.)
    * Verify that the status exposed in meta-data tracks changes in the status scheme.
* If a module version has no descriptor, its status defaults to the least mature status for the module's status scheme.
    * Verify that the status exposed in meta-data tracks changes in the status scheme.
* Override the status of a module in the build script, and verify that it affects "latest" resolution in the expected way.
* Verify that the original metadata is cached, not the mutated metadata:
    * Publish 2 versions of a module to an HTTP repository
    * Use a meta-data rule to set the statuses of each version so that version 1 is selected.
    * Change the meta-data rule so that version 2 is selected instead.

## Declare a module as "changing" via rule

The `componentMetadata` rule should allow to declare a component as "changing". This effectively makes Ivy changing patterns a first-class Gradle concept. Example:

    componentMetadata { details ->
        if (details.id.version.endsWith("-dev")) {
            details.changing = true
        }
    }

Out-of-scope for this story is to deal with changes to the rule logic and caching. This is dealt with by the next story. Implementation-wise, this means it
is sufficient for this story to execute the meta-data rules where they currently are (in the ExternalResourceResolver) and to store the result in the
cache.

### User visible changes

Add a "changing" property to `ComponentMetadataDetails`.

### Test coverage

* Add a rule that forces a static component to changing and verify that the component is treated as changing (cf. the existing concept of changing dependency).
    * Verify that the `changing` flag defaults to `false`.
    * Verify that the component is treated as changing.
    * When expired, HTTP requests are made to verify the component meta-data and artifacts have changed, and artifacts downloaded if they have changed.
* Add a rule that forces a changing component to not changing and verify that the component is treated as static.
    * Verify that the `changing` flag defaults to `true`.
    * Verify that the component is treated as not changing.
* Verify that `details.changing` is initialized to true for:
    * Dependency declaration with `changing` flag set to `true`
    * Maven snapshots.
* Verify that `details.changing` is initialized to false for:
    * Static dependency
    * Dynamic dependency (that is, the dependency may refer to different components over time, but the components themselves do not change)

## Use Ivy extra info properties to determine status of module

An ivy.xml `<info>` element permits arbitrary child elements with string values. This story makes these extra info properties available to component metadata rules,
on request.

A rule should declare that these extra info properties form an input to the rule, in which case they will be provided.
While this is perhaps not important for Ivy properties, which are cheap to determine, this will be more important for
Artifactory properties (see below).

A medium-term goal is to sync the Component Metadata Rules DSL with the new general-purpose Rules DSL. So the same mechanism will be
used for implementing rules to apply configuration to a native binary and rules to process custom metadata attributes. This story should
simply attempt to introduce a DSL to declare such rules.

### User visible changes

    interface IvyModuleMetadata {
        Map<String, String> extraInfo
    }

    componentMetadata {
        eachComponent { ComponentMetadataDetails details, IvyModuleMetadata ivyModule ->
            if (ivyModule.extraInfo['my-custom-attribute'] == 'value') {
                details.status == 'release'
            }
        }
    }

### Implementation

* Add a model for Ivy-specific module metadata and make this available via `ModuleVersionMetaData`
    * Include any name/value pairs defined as child elements of the `<info>` element. Do not include the namespace qualifier.
    * The actual values should already be available (and cached) via the underlying Ivy ModuleDescriptor
    * The API should assume that other metadata models may be present as well
* For any rule that declares IvyModuleMetadata as an input:
    * Provide the IvyModuleMetadata as input where the resolved module came from an ivy repository
    * Do not execute the rule where the resolved module does not have an associated ivy.xml file

### Test coverage

* Publish with arbitrary extra info properties, and ensure these are available in resolve.
* Publish again with changed values:
    * Original values are take from cache
    * New values are obtained when changing module is refreshed
* Component metadata rule does not have access to ivy extra info properties if not declared as rule input
* Component metadata rule is not evaluated for non-ivy module when rule declares ivy attributes as input
* Resolve with rule that does not have ivy extra attributes as input. Modify rule to include those inputs and resolve again
  Attributes are made available to rule (extra HTTP requests are OK, but not required).

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

## Story: Make branch attribute available when publishing and resolving Ivy modules

The `ivy` metadata file format has a `branch` attribute, which is currently not available to Gradle users. This story makes this
attribute available in ivy-specific APIs within Gradle.

### User visible changes

Users will be able to set the `branch` attribute when publishing with `IvyPublication`

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.branch = 'testing'
            }
        }
    }

Users will be able to access the `branch` attribute when resolving, via the `IvyModuleMetadata`

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details, IvyModuleMetadata ivyModule ->
                if (ivyModule.branch == 'some-feature') {
                    details.status = 'testing'
                } else {
                    details.status = 'master'
                }
            }
        }

        // can use status to select the appropriate branch
        compile 'group:module:latest.testing'
        compile 'group:other-module:latest.master'
    }

### Implementation

- Add 'branch' property to `IvyModuleDescriptor`. Default is null.
- Validate branch value in `ValidatingIvyPublisher`: can be either null, or valid as per 'organisation'.
- Add a branch attribute to the generated ivy descriptor if the `IvyModuleDescriptor` has a non-empty branch value.
- Add read-only 'branch' property to both `IvyModuleMetadata` and `IvyModuleVersionMetaData` (internal).
- Populate the branch property from the Ivy module descriptor
- Sync up the IvyModuleDescriptor and IvyModuleMetadata:
    - add extra-info to descriptor (validate keys as per branch)
    - add ivyStatus to IvyModuleMetadata (should always return the value from the descriptor, not the value set on ComponentMetadataDetails).
- Rename IvyModuleDescriptor -> IvyModuleDescriptorSpec, IvyModuleMetadata -> IvyModuleDescriptor
- Validate status in ValidatingIvyPublisher as for branch

### Test coverage

- Publish an ivy module with a branch value specified
    - Verify published ivy.xml file
    - Resolve and verify branch value is correct in `IvyModuleDescriptor`.
- Publish and resolve with branch value that contains non-ascii characters
- Reasonable error message when publishing with invalid branch value
- Branch attribute is cached until module is refreshed
- Test coverage for publishing with extra-info set
    - Resolve and verify extra-info values are correct in `IvyModuleDescriptor`.
- Publish and resolve with extra-info key and value that contains non-ascii characters
- Test coverage for accessing ivy status in component metadata rules

## Story: Build script reports all versions tested for dynamic version

This story takes a step toward allowing a build author to provide logic for selecting the correct version for a dynamic dependency.

A 'versionSelection' rule can be added to `ResolutionStrategy`.
This rule is fired any time a candidate version is compared to see if it matches a dynamic version.

- Any number of `versionSelection` rules can be added to a `ResolutionStrategy`.
- All rules are fired.
- The order in which they are fired is not deterministic.

### User visible changes

    configurations.all {
        resolutionStrategy {
            versionSelection {
                all { VersionSelection selection ->
                    println "Comparing module ${selection.candidate} to requested version ${selection.requested.version}"
                }
            }
        }
    }


### Implementation

    interface VersionSelectionRules {
        any(Action<VersionSelection> selection)
    }

    interface VersionSelectionRulesInternal {
        apply(VersionSelection selection)
    }

    interface VersionSelection {
        ModuleComponentSelector getRequested()
        ModuleComponentIdentifier getCandidate()
    }

- Add `VersionSelectionRules` that will be returned by `ResolutionStrategy.getVersionSelection()`
- When `VersionSelectionRulesInternal.apply()` is called, all Actions added with the `any` method are executed.
- Supply the VersionSelectionRules instance to the NewestVersionComponentChooser, and apply the rules wherever compare a version against a candidate (`versionMatcher.accept`).
- When custom version selection rules are involved, treat any version as a dynamic version

### Test cases

- Resolve '1.+' against ['2.0', '1.1', '1.0']: rules are fired for ['2.0', '1.1']
- Resolve 'latest.integration' against ['2.0', '1.1', '1.0]: rules are fired for ['2.0']
- Resolve 'latest.release' against ['2.0', '1.1', '1.0] where '2.0' has status of 'integration' and '1.1' has status of release: rules are fired for ['2.0', '1.1']
- Resolve '1.0' against ['2.0', '1.1', '1.0']: rules are fired for ['2.0', '1.1', '1.0']
- Each of multiple declared rules are fired.

## Story: Build logic selects the module that matches an external dependency

This story allows the build script to provide logic that can specify if a particular module version satisfies a particular version
selector.

- If no rule sets the status of the VersionSelection, the default VersionMatcher algorithm is used
- If 2 rules set the status of the VersionSelection in different ways, the resolution will fail

### User visible changes

    interface VersionSelection {
        ModuleComponentSelector getRequested()
        ModuleComponentIdentifier getCandidate()
        void accept()
        void reject()
    }

    configurations.all {
        resolutionStrategy {
            versionSelection {
                // Selector 'dev' matches any version ending with 'dev'
                all { VersionSelection selection ->
                    if (selection.requested.version == 'dev' && selection.candidate.version.endsWith('dev')) {
                        selection.accept()
                    }
                }
                // Selector 'not-zero' matches any version that doesn't start with '0'
                all { VersionSelection selection ->
                    if (selection.requested.version == 'zero' && !selection.candidate.version.startsWith('0')) {
                        selection.reject()
                    }
                }
            }
        }
    }

### Implementation

Something like...

    interface VersionSelectionInternal {
        public enum State {
            NOT_SET,
            ACCEPTED,
            REJECTED
        }
        State getState()
    }

- Add accept() and reject() method to VersionSelection
- After applying rules against the VersionSelection instance, check to see if the state is ACCEPTED or REJECTED.
    - Don't use the default version matching strategy in these cases
- Add a sample for using version selection rules to implement semantic version matching:
    - 1.1+ should match [1.1, 1.1.0, 1.1.1, 1.1.0.1] but not [1.10]

### Test cases

- Resolution fails if 2 rules set the VersionSelection to different values
- Can use hard-coded version selection rule so that:
    - '1.0' does not select any of [2.0, 1.1, 1.0] (override default behaviour)
    - '1.0' selects '2.0' (override default behaviour)
    - use custom syntax to select a particular version
    - use custom syntax to reject a particular version (accepting the next default)
- When rules do not accept or reject, default strategy is used, for:
    - Static version: '1.0'
    - Dynamic version that doesn't require metadata: '1.+', 'latest.integration'
    - Dynamic version that requires metadata: 'latest.milestone'
