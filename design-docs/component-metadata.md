
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

    interface RuleAction<T> {
        List<Class<?>> getInputTypes()
        void execute(T subject, List<?> inputs)
    }

- Add ComponentMetadata as read-only view of ComponentMetadataDetails
    - Rename internal ComponentMetaData -> ExternalComponentMetaData
- Add `VersionSelectionRules.all(RuleAction<VersionSelection> rule)
    - Only allowable input types are `ComponentMetadata` and `IvyModuleDescriptor`
    - Provide a `ModuleComponentRepositoryAccess` to `VersionSelectionRulesInternal.apply()`
    - Look up and supply the module metadata for any rule that requires it.
- Add `VersionSelectionRules.all(Closure)` : See ComponentMetadataHandler for example
    - Convert closure to `RuleAction`

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

### Open issues

- Should accept untyped subject parameter in rule closure?

## Story: Replace versionSelection rules with componentSelection rules

This is an update to the previous 3 stories, based on some further analysis and design.
The primary changes are:

- Rename functionality from 'versionSelection' rules to 'componentSelection' rules.
- Default version matching will be modeled as a 'componentSelection' rule, applied before any custom rules.
- Every defined rule can reject the candidate component. If a rule does not reject the candidate, it is assumed accepted.
- A reason must be specified when rejecting, for reporting purposes.
- For a candidate to be considered, every defined rule must accept candidate. If any rule rejects the candidate, it is not considered.
- Once a rule rejects a candidate, no other rules will be evaluated (short-circuit rules).
    - Custom rules are not evaluated for any component where default version matching rejects candidate.
- Rules that require additional inputs (`ComponentMetadata` or `IvyModuleDescriptor`) will be evaluated _after_ rules that do not declare these inputs.
    - This includes the built-in version matching rule, so a custom rule that doesn't require additional input will be evaluated before `latest.release`.
- If a rule requires an `IvyModuleDescriptor` input, then that rule is not applied to non-ivy modules.
- Order of rule execution cannot be specified
- Custom rules can further refine, but not replace the built-in version matching algorithm.

### User visible changes

    ComponentSelection {
        ModuleComponentSelector getRequested()
        ModuleComponentIdentifier getCandidate()
        void reject(String reason)
    }

    resolutionStrategy.all {
        componentSelection {
            // Reject all beta versions for module components
            all { ModuleComponentSelection selection ->
                ModuleComponentIdentifier componentId = selection.getCandidate()
                def isBeta = determineIfBeta(componentId.getVersion())
                if (isBeta) {
                    selection.reject("component is beta")
                }
            }

            // Accept only modules from a particular branch
            all { ModuleComponentSelection selection, IvyModuleMetadata ivy ->
                if (ivy.branch != 'foo') {
                    selection.reject("Not the correct branch")
                }
            }
        }
    }

### Implementation

- ~~Use the term 'ComponentSelection' in place of 'VersionSelection' for custom rules.~~
- ~~Replace `VersionSelection` with `ComponentSelection` as defined above.~~
- ~~Change `NewestVersionComponentChooser` to evaluate version selector prior to evaluating custom rules.~~
- Change the `ComponentSelectionRules` mechanism to:
    - ~~Short-circuit remaining rules when any rule rejects candidate~~
    - ~~Log the reason for rejecting a particular candidate~~
    - ~~Evaluate rules with no additional inputs prior to rules with additional inputs~~
- ~~For this story, `NewestVersionComponentChooser` will simply log the reason for rejecting a candidate~~
- ~~Convert standard version matching algorithm into componentSelection rules.~~

### Test cases

- ~~No rules are fired when no versions match the version selector~~
- For a dynamic version selector "1.+":
    - ~~Custom rule can reject all candidates: user gets general 'not found' error message.~~
    - ~~Custom rule can select one of the candidates, no further candidates are considered.~~
    - ~~Custom rule can select one of the candidates using the component metadata~~
    - ~~Custom rule can select one of the candidates using the ivy module descriptor~~
    - ~~Custom rule can reject all candidates from one repository, and accept a candidate from a subsequent repository.~~
- For a static version selector "1.0":
    - ~~Custom rule can reject candidate: user gets general 'not found' error message.~~
    - ~~Custom rule can reject candidate from one repository, and accept a matching candidate from a subsequent repository.~~
- With multiple custom rules:
    - ~~If any rule rejects a candidate, the candidate is not selected.~~
    - ~~Once a rule rejects a candidate, no other rules are evaluated for the candidate.~~
    - ~~A rule that declares only a `ModuleComponentSelection` input is evaluated before a rule that declares a `ComponentMetadata` input.
- ~~Component selection rule that requires an `IvyModuleDescriptor` input does not affect selection of maven module~~
- ~~All test cases from the previous story (ComponentMetadataDetails/IvyModuleMetadata input) should be adapted~~
- ~~Test cases from earlier stories will be modified or replaced by the test cases here~~

## Story: Java API for component selection rules

A few options:

### Option 1

    interface ComponentMetaDataAction<T> {
        void execute(T target, ComponentMetadata metadata);
    }

    interface IvyComponentMetaDataAction<T> {
        void execute(T target, ComponentMetadata metadata, IvyModuleDescriptor descriptor);
    }

    interface ComponentSelectionRules {
        void all(Action<? super ComponentSelection> action);
        void all(ComponentMetaDataAction<? super ComponentSelection> action);
        void all(IvyComponentMetaDataAction<? super ComponentSelection> action);
    }

### Option 2

    class MyCustomRule {
        @Mutate
        void whatever(ComponentSelection selection, ComponentMetadata metadata) { ... }
    }

    class MyIvyCustomRule {
        @Mutate
        void whatever(ComponentSelection selection, ComponentMetadata metadata, IvyModuleDescriptor descriptor) { ... }
    }

    interface ComponentSelectionRules {
        void all(Object rule);
    }

Option 1 can turn into option 2 later.

## Story: Add Java API for component metadata rules

The approach from the previous story and apply it to `ComponentMetadataHandler` and component metadata rules.

Generate closure-based methods for any methods that take a `RuleAction` parameter, and remove the existing Closure-accepting duplicates.

## Story: Build script targets component selection rule to particular module

This story adds some convenience DSL to target a selection rule a particular group or module:

### User visible changes

    configurations.all {
        resolutionStrategy {
            componentSelection {
                group("foo") { ComponentSelection selection ->
                }
                group("foo").module("bar") { ComponentSelection selection ->
                }
                module("foo:bar") { ComponentSelection selection ->
                }
            }
        }

### Test cases

- Use rule to control selection of components within a specific module.
- Multiple rules can target a particular module: combine a group and a module targeted rule
- Rules are not fired for components of non-targeted module.
- If a rule requires metadata input, that rule does not trigger metadata download for non-targeted modules.
- Useful error message when:
    - 'group' value is empty or null
    - 'module' value is empty or null
    - 'module' value that has preceding 'group' contains ':' character
    - 'module' value that has no preceding group does not match `group:module` pattern
    - 'group' or 'module' value contains invalid characters: '*', '+', ???


## Open issues

- Component metadata rules get called twice when a cached version is found and an updated version is also found in a repository

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
