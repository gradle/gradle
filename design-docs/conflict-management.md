Improved version conflict management

# Use cases.

1. It is hard to figure out why Gradle picks up version X of certain dependency because the dependency report
 only includes the version information *after* conflicts have been resolved.

# User visible changes

1. At minimum the dependencies report should include information of the 'asked' dependency version.
2. Consider a new report that focuses on the version conflict information.

# Integration test coverage

1. Assert on the report output.

# Implementation approach

1. There are 3 goals:
    1. deliver the functionality to solve the use case
    2. model the dependency graph better so that we can provide a 'lower level' API, (unopinionated toolkit stuff)
    3. leave the codebase in a better state than it was
        (kinda obvious, but wanted to point out that we should end up with better internal model, some clean-up with legacy ivy impl, etc.)
2. I'm keen on not changing DependencyGraphBuilder too much because it's complex and I want avoid impact.
3. I plan to create a working prototype that incurs small changes in the internal types (and no changes in public types).
    This allows me to understand the problem and required abstractions better. I plan to drive the requirement top-down.
    This is also a nice incremental step.

# Open issues

1. The plan of deprecating ResolvedDependency type with a new model.
2. The plan/goals for the lower-level API.
3. The design of the new dependency graph. Rough plan from the email thread:

     - The replacement for ResolvedDependency should be reachable from ResolvedConfiguration, and would eventually replace getFirstLevelModuleDependencies() plus the corresponding stuff on LenientConfiguration.
     - It would be a graph, with the nodes representing resolved module versions (using ResolvedModuleVersion or some subtype). The edges would represent resolved dependencies (using some new interface).
     - The nodes need to provide the module version id, plus the set of outgoing dependencies, plus the set of artefacts for the module. The edges need the above attributes, i.e. the requested module version, a set of references to the module versions that matched, and a reference to the module version that was selected.
     - We might think about making the nodes also carry resolution failure information, so that unresolved dependencies end up in the appropriate place in the graph.
     - Later, we'd add some conveniences to ResolvedConfiguration to do some traversal of this graph in interesting ways - find me all the module versions, find me all the artefacts, find me all the files, find me all the unresolved dependencies, and so on.
     - I would also think about wrapping access to the graph in some kind of action

# Notes

This feature is sponsored by client.