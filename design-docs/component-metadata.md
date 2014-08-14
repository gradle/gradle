
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

### Open issues

- Rules should be fired after the built in version matching rules are executed
- Need some way to fall back to some other version if preferred version is not available, eg use something from 'master' branch if none from 'feature' branch is available.
Could do this using two rules, if there were some guarantee to the order of rule executions.

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

## Story: Version selection rule takes ComponentMetadataDetails and/or IvyModuleMetadata as input

This story makes available the component and Ivy meta-data as optional read only inputs to a version selection rule:

### User visible changes

    configurations.all {
        resolutionStrategy {
            versionSelection {
                all { VersionSelection selection, ComponentMetadata metadata ->
                }
                all { VersionSelection selection, IvyModuleDescriptor ivyModule, ComponentMetadata metadata ->
                }
            }
        }
    }

### Implementation

    interface MetadataRule<T> {
        Class<T> getSubjectType()
        List<Class<?>> getInputTypes()
        void execute(T subject, List<?> inputs)
    }

- Add ComponentMetadata as read-only view of ComponentMetadataDetails
    - Rename internal ComponentMetaData -> ExternalComponentMetaData
- Add `VersionSelectionRules.all(MetadataRule<VersionSelection> rule)
    - Only allowable input types are `ComponentMetadata` and `IvyModuleDescriptor`
    - Provide a `ModuleComponentRepositoryAccess` to `VersionSelectionRulesInternal.apply()`
    - Look up and supply the module metadata for any rule that requires it.
- Add `VersionSelectionRules.all(Closure)` : See ComponentMetadataHandler for example
    - Convert closure to `MetadataRule`
- Add `ComponentMetadataHandler.eachComponent(MetadataRule<ComponentMetadataDetails>) as Java API for component metadata rule
    - Map closure method to `MetadataRule`

### Test cases

- Component metadata is not requested for rule that doesn't require it
- Using closure parameter, create custom selector scheme that:
    - matches on Ivy `branch` attribute
    - matches on `ComponentMetadata.status`
    - uses information from both `IvyModuleDescriptor` and `ComponentMetadata`
- Can use Java API for creating metadata rule
- For custom rule that uses `ComponentMetadata` combined with `latest.release`, component metadata is only requested once for each version (is cached)
- Reasonable error message if:
    - First parameter is not VersionSelection
    - Unsupported other parameter type
    - No closure parameter
    - Rule action throws exception

## Story: Replace versionSelection rules with componentSelection rules

This is an update to the previous 3 stories, based on some further analysis and design.
The primary changes are:

- Rename functionality from 'versionSelection' rules to 'componentSelection' rules.
- Default version matching will be applied first. Custom rules are not evaluated for any component where default version matching rejects candidate.
- Custom rules provide a fixed selection criteria for a candidate component, without consideration of the `ModuleComponentSelector`.
- Every defined rule must either accept or reject the candidate component. It is an error for a rule _not_ to specify accept or reject.
- A reason must be specified when rejecting, for reporting purposes.
- For a candidate to be considered, every defined rule must accept candidate. If any rule rejects the candidate, it is not considered.
- Once a rule rejects a candidate, no other rules will be evaluated (short-circuit rules).
- If a rule requires an `IvyModuleDescriptor` input, then that rule implicitly reject any non-ivy module.
    - Until we add module targeting (next story), rules will not be usable in a mixed ivy/maven environment.
- Rules that require additional inputs (`ComponentMetadata` or `IvyModuleDescriptor`) will be evaluated _after_ rules that do not declare these inputs.
- Order of rule execution cannot be specified
- It will no longer be possible to implement a custom version matching algorithm using these rules.

### User visible changes

    ModuleComponentSelection {
        ModuleComponentIdentifier getCandidate()
        void accept()
        void reject(String reason)
        void rejectIf(Boolean condition, String reason)
    }

    resolutionStrategy.all {
        componentSelection {
            // Reject all beta versions for module components
            all { ModuleComponentSelection selection ->
                ModuleComponentIdentifier componentId = selection.getCandidate()
                def isBeta = determineIfBeta(componentId.getVersion())
                selection.rejectIf(isBeta, "component is beta")
            }

            // Accept only modules from a particular branch
            all { ModuleComponentSelection selection, IvyModuleMetadata ivy ->
                if (ivy.branch == 'foo') {
                    selection.accept()
                } else {
                    selection.reject("Not the correct branch")
                }
            }
        }
    }

### Implementation

- Use the term 'ComponentSelection' in place of 'VersionSelection' for custom rules.
- Replace `VersionSelection` with `ModuleComponentSelection` as defined above.
- Change `NewestVersionComponentChooser` to evaluate version selector prior to evaluating custom rules.
- Change the `ComponentSelectionRules` mechanism to:
    - Fail if any rule doesn't accept or reject candidate
    - Short-circuit remaining rules when any rule rejects candidate
    - Report the reason for rejecting a particular candidate
    - Evaluate rules with no additional inputs prior to rules with additional inputs
- For this story, `NewestVersionComponentChooser` will simply log the reason for rejecting a candidate

### Test cases

- No rules are fired when no versions match the version selector
- For a dynamic version selector "1.+":
    - Custom rule can reject all candidates: user gets general 'not found' error message.
      the version selector, and the reason each was rejected.
    - Custom rule can select one of the candidates, no further candidates are considered.
    - Custom rule can reject all candidates from one repository, and accept a candidate from a subsequent repository.
- For a static version selector "1.0":
    - Custom rule can reject candidate: user gets general 'not found' error message.
    - Custom rule can reject candidate from one repository, and accept a matching candidate from a subsequent repository.
- With multiple custom rules:
    - If any rule rejects a candidate, the candidate is not selected.
    - Once a rule rejects a candidate, no other rules are evaluated for the candidate.
    - A rule that declares only a `ModuleComponentSelection` input is evaluated before a rule that declares a `ComponentMetadata` input.
- Useful error message when a rule neither accepts or rejects a candidate
- A Maven module candidate is not considered when a custom rule requires an `IvyModuleDescriptor` input
    - Reason is logged as "not an Ivy Module" (or similar)
- All test cases from the previous story (ComponentMetadataDetails/IvyModuleMetadata input) should be adapted
- Test cases from earlier stories will be modified or replaced by the test cases here

## Story: Build reports reasons for failure to resolve due to custom component selection rules

To make it easy to diagnose why no components match a particular version selector, this story adds context to the existing
'not found' exception message reported. The extra information in the exception will include a list of any candidate versions
that matched the specified version selector, together with the reason each was rejected.

### Test cases

- For a dynamic version selector "1.+":
    - Custom rule can reject all candidates: user gets error message listing each candidate that matched and the rejection reason
- For a static version selector "1.0":
    - Custom rule can reject candidate: user gets useful error message including the rejection reason.
- A Maven module candidate is not considered when a custom rule requires an `IvyModuleDescriptor` input
    - Reason is reported as "not an Ivy Module" (or similar)

### Open issues

- Dependency reports should indicate reasons for candidate selection (why other candidates were rejected).

## Story: Build script targets versionSelection rule to particular module

This story adds some convenience DSL to target a selection rule a particular group or module:

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

## Open issues

- Component metadata rules get called twice when a cached version is found and an updated version is also found in a repository

# Later milestones

## Add DSL to allow resolution strategy to be applied to all resolution

A mock up:

    dependencies {
        eachResolution { details ->
            // Can tweak the inputs to the resolution, eg the strategy, repositories, etc
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
