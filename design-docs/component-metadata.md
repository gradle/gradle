
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

## Story: Dependency reports inform user that some versions were rejected

# Later milestones

## Dependency reports should indicate reasons for candidate selection (e.g. why other candidates were rejected).

## Add DSL to allow resolution strategy to be applied to all resolution

A mock up:

    dependencies {
        eachResolution { details ->
            // Can tweak the inputs to the resolution, eg the strategy, repositories, etc
        }
    }

## Change dependency substitution rules to use same pattern as other rules

- `DependencyResolveDetails` uses `ModuleVersionSelector` whereas the result uses `ModuleComponentSelector`.

## Feature open issues:

- No way to say 'this must be an Ivy module'. Currently, can only say 'if this happens to be an Ivy module, then filter'.
- `ComponentMetadata.id` returns `ModuleVersionIdentifier`, whereas `ComponentSelection.candidate` returns `ModuleComponentIdentifier`
- `ComponentMetadataDetails` extends `ComponentMetadata`, which means that `ComponentMetadata` is not immutable.
- DSL decoration should add Action and Closure overloads for a method that accepts a rule source.
- Error message for a badly-formed module id could be improved.
- Inconsistent error messages for badly-formed rule class.
