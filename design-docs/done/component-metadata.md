
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

## Story: Version selection rule takes ComponentMetadataDetails and/or IvyModuleMetadata as input (DONE)

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
        - No network requests are made when this build is run a second time.
- For a static version selector "1.0":
    - ~~Custom rule can reject candidate: user gets general 'not found' error message.~~
    - ~~Custom rule can reject candidate from one repository, and accept a matching candidate from a subsequent repository.~~
        - No network requests are made when this build is run a second time.
- With multiple custom rules:
    - ~~If any rule rejects a candidate, the candidate is not selected.~~
    - ~~Once a rule rejects a candidate, no other rules are evaluated for the candidate.~~
    - ~~A rule that declares only a `ModuleComponentSelection` input is evaluated before a rule that declares a `ComponentMetadata` input.
- ~~Component selection rule that requires an `IvyModuleDescriptor` input does not affect selection of maven module~~
- ~~All test cases from the previous story (ComponentMetadataDetails/IvyModuleMetadata input) should be adapted~~
- ~~Test cases from earlier stories will be modified or replaced by the test cases here~~

## Story: Java API for component selection rules

This story adds a Java API for defining component selection rules that is closely modelled on configuration rules.
A component selection rule can be provided as an instance of a Java class that has a single method annotated with @Mutate.

### User visible changes

    class MySimpleRule {
        @Mutate
        void whatever(ComponentSelection selection) { ... }
    }

    class MyMetadataRule {
        @Mutate
        void whatever(ComponentSelection selection, ComponentMetadata metadata) { ... }
    }

    class MyIvyRule {
        @Mutate
        void whatever(ComponentSelection selection, ComponentMetadata metadata, IvyModuleDescriptor descriptor) { ... }
    }

    interface ComponentSelectionRules {
        void all(Object rule);
        void module(Object id, Object rule);
    }

Rule source class:
    - Must have a single method annotated with @Mutate (other methods are ignored)
    - The @Mutate method must have void return
    - The @Mutate method must have `ComponentSelection` as it's first parameter
    - The @Mutate method may have additional parameters of type `ComponentMetadata` and `IvyModuleDescriptor`

### Implementation

- Remove `RuleAction` methods from `ComponentSelectionRules` and remove `RuleAction` from public API
- Add `RuleSourceBackedRuleAction` implementation that adapts a `@Mutate` method to `RuleAction`
- Add new rule methods to `ComponentSelectionRules`
- Include a `@Mutate` rule in the component selection sample
- Update release notes, DSL reference and Userguide

### Test coverage

- Can provide a `@Mutate` rules as defined above
- Reasonable error message for:
    - Invalid rule source class (unit tests)
    - `@Mutate` method does not have `ComponentSelection` as first parameter (integration test)
    - Exception thrown by rule method

## Story: Build script targets component selection rule to particular module

This story adds some convenience DSL to target a selection rule to a particular module:

### User visible changes

    configurations.all {
        resolutionStrategy {
            componentSelection {
                withModule("foo:bar") { ComponentSelection selection ->
                }
            }
        }

### Test cases

- Use rule to control selection of components within a specific module.
- Multiple rules can target a particular module
- Rules are not fired for components of non-targeted module.
- If a rule requires metadata input, that rule does not trigger metadata download for non-targeted modules.
- Useful error message when:
    - 'module' value is empty or null
    - 'module' value does not match `group:module` pattern
    - 'module' value contains invalid characters: '*', '+', '[', ']', '(', ')', ',' (others?)

## Story: Don't apply component selection rules to parent pom references

- Filtering applies to parent poms. It should apply only to those components that are candidates to be included in the graph.
  Same is probably true for imported poms and imported ivy files.

### Implementation

When constructing `ResolveIvyFactory.ParentModuleLookupResolver` we should instantiate a separate `UserResolverChain` instance with an
empty set of component selection rules.

### Test cases

- Resolve a POM that has a parent POM that would be rejected by a component selection rule
   - i.e. rule that reject all components for module 'group:my-parent', where that is the parent of resolved module
- Resolve an Ivy file that imports another ivy module that would be rejected by component selection rule

## Story: Add Java API for component metadata rules

This story adds '@Mutate' rule definitions to `ComponentMetadataHandler` and component metadata rules, and adds an API
consistent with component selection rules.

- Deprecate and replace `ComponentMetadataHandler.eachComponent` methods with `.all()` equivalents
- Add `ComponentMetadataHandler.all(Object)` that takes a rule source instance
- Add `ComponentMetadataHandler.withModule()` methods to target a rule at a particular module.

### User visible changes

    interface ComponentMetadataHandler {
        all(Action<? super ComponentMetadataDetails>)
        all(Closure)
        all(Object ruleSource)
        withModule(Action<? super ComponentMetadataDetails>)
        withModule(Closure)
        withModule(Object ruleSource)
    }

    class MyCustomRule {
        @Mutate
        void whatever(ComponentMetadataDetails metadata) { ... }
    }

    class MyIvyRule {
        @Mutate
        void whatever(ComponentMetadataDetails metadata, IvyModuleDescriptor ivyDescriptor) { ... }
    }

### Implementation

- Change `DefaultComponentMetadataHandler` so that it adapts `Action` and `Closure` inputs to `RuleAction` for execution
- Add new method `ComponentMetadataHandler.all(Object)`
- Deprecate `ComponentMetadataHandler.eachComponent()` methods, replacing them with `ComponentMetadataHandler.all()`
    - Update samples, Userguide, release notes etc.

### Open issues

- `@Mutate` is not documented or included in default imports.
- Rules that accept a closure should allow no args, as the subject is made available as the delegate.
- Rules that accept a closure should allow the first arg to be untyped.

