
# Feature: Component selection rules

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
- DSL decoration should add Action and Closure overloads for a method that accepts a rule source.
- Error message for a badly-formed module id could be improved.
- Inconsistent error messages for badly-formed rule class.

## Story: Build reports reasons for failure to resolve due to custom component selection rules

To make it easy to diagnose why no components match a particular version selector, this story adds context to the existing
'not found' exception message reported. The extra information in the exception will include a list of any candidate versions
that matched the specified version selector, together with the reason each was rejected.

### Test cases

- For a dynamic version selector "1.+":
    - Custom rule rejects all candidates: user gets error message listing each candidate that matched and the rejection reason
    - No versions "1.+" found and other versions are available: error message lists some similar versions that were found.
    - No versions "1.+" found and no versions found: error message informs user that no versions were found.
- For a static version selector "1.0":
    - Custom rule rejects candidate: user gets useful error message including the rejection reason.
    - Version 1.0 not found and other versions are available: error message lists some similar versions that were found.
    - Version 1.0 not found and no versions found: error message informs user that no versions were found.
- A Maven module candidate is not considered when a custom rule requires an `IvyModuleDescriptor` input
    - Reason is reported as "not an Ivy Module" (or similar)

### Open issues

- Dependency reports should indicate reasons for candidate selection (why other candidates were rejected).

## Feature open issues:

- Filtering applies to parent poms. It should apply only to those components that are candidates to be included in the graph. Same is probably
    true for imported poms and imported ivy files.
- No way to say 'this must be an Ivy module'. Currently, can only say 'if this happens to be an Ivy module, then filter'.
- Component metadata rules get called twice when a cached version is found and an updated version is also found in a repository
- `ComponentChooser.isRejectedByRules` takes a mutable meta-data. It should be immutable.
- `ComponentMetadata.id` returns `ModuleVersionIdentifier`, whereas `ComponentSelection.candidate` returns `ModuleComponentIdentifier`
- `DependencyResolveDetails` uses `ModuleVersionSelector` whereas the result uses `ModuleComponentSelector`.
- `ComponentMetadataDetails` extends `ComponentMetadata`, which means that `ComponentMetadata` is not immutable.

## Story: Dependency reports inform user that some versions were rejected

# Later milestones

## Add DSL to allow resolution strategy to be applied to all resolution

A mock up:

    dependencies {
        eachResolution { details ->
            // Can tweak the inputs to the resolution, eg the strategy, repositories, etc
        }
    }

