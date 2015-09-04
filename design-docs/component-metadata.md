
# Feature: Component selection rules

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

## Story: Dependency reports inform user that some versions were rejected

# Later milestones

## Don't apply component meta-data rules to parent pom references

Also pom import, ivy extends and so on.

## Dependency reports should indicate reasons for candidate selection (e.g. why other candidates were rejected).

## Declarative substitution of group, module and version

## Feature open issues:

- No way to say 'this must be an Ivy module'. Currently, can only say 'if this happens to be an Ivy module, then filter'.
- `ComponentMetadata.id` returns `ModuleVersionIdentifier`, whereas `ComponentSelection.candidate` returns `ModuleComponentIdentifier`
- `ComponentMetadataDetails` extends `ComponentMetadata`, which means that `ComponentMetadata` is not immutable.
- DSL decoration should add Action and Closure overloads for a method that accepts a rule source.
- Error message for a badly-formed module id could be improved.
- Inconsistent error messages for badly-formed rule class.
