Provide the users way to read and navigate the dependency graph.

# Use cases

    * The old dependency result graph is expensive in terms of heap usage. It also has somewhat awkward api. We should remove it.

# Stories

## Promote (un-incubate) the new dependency graph types.

    * Since we're about to remove an old feature, we should un-incubate the replacement API.
    * Most likely it requires few stories.

## New dependency graph uses less heap

The new dependency graph also requires substantial heap (in very large projects). Possible options:

    * serialize the result to disk. Optionally, we could use soft references so that the result is flushed to disk at heap pressure.
    * don't provide the result, instead allow the users to specify actions that receive the result as parameters.
    I'm leaning towards this option atm.

### User visible changes

    * less heap used
    * (API) if the user wants to browse the dependency graph he needs to supply an action that receives the resolution result as an argument.

### Coverage

    * Existing dependency reports tests work neatly
    * The report is generated when the configuration was already resolved (e.g. some previous task triggered resolution)
    * The report is generated when the configuration was unresolved yet.

### Implementation plan (rough)

    * Replace ResolvableDependencies.getResolutionResult() with ResolvableDependencies.withResolutionResult(Action<ResolutionResult>)
    * The existing dependency reports need to supply an action to every configuration and remember the resolution results
    * When the dependency report is done, it removes the remembered resolution result so that gc can claim the heap
    * Gradle no longer keeps the ResolutionResult by default
    * Mention the change to the incubating feature in the release notes along with information why we do it

## Remove old dependency graph model

tbd