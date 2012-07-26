Improved dependency resolution reporting

# Use cases.

1. Priority use case: It is hard to figure out why Gradle picks up version X of certain dependency because the dependency report
    only includes the version information *after* conflicts have been resolved.
2. User diagnoses dependency resolve failures
    (with a possible implementation being that the reporting is robust in the face of failures)
3. User views actual class paths
    (e.g currently the reports only show module dependencies.
    It should show external dependencies, self-resolving dependencies, tweaks to sourceSets.main.compileClasspath, etc).

# User visible changes

1. At minimum the dependencies report should include information of the 'asked' dependency version
Instead of raw version we can print something like: 1.0->3.0. Mock up:

    testCompile - Classpath for compiling the test sources.
    +--- root:a:1.0 [default]
    |    \--- foo:bar:1.0->3.0 [default]
    \--- root:b:1.0 [default]
         \--- foo:bar:3.0 [default] (*)

2. Consider a new report that focuses on the version conflict information.

# Integration test coverage

1. Scenarios:

    * conflicting direct dependencies
    * conflicting direct & transitive dependency
    * conflicting transitive dependencies
    * conflicts with more than 2 candidates
    * conflicts in multi-project build
    * conflicts with dynamic versions
    * conflicts resolved in multiple phases, e.g:

    root->foo:1.0->bar:3.0
    root->foo:2.0->bar:2.5
    root->bar:1.5

    * avoid regression of current features:
        * subtree is omitted (*)
        * the configurations are shown

# Implementation approach

1. There are 3 goals:
    1. deliver the functionality to solve the use case
    2. model the dependency graph better so that we can provide a 'lower level' API, (unopinionated toolkit stuff)
    3. leave the codebase in a better state than it was
        (kinda obvious, but wanted to point out that we should end up with better internal model, some clean-up with legacy ivy impl, etc.)
2. Be cautious with DependencyGraphBuilder - it's most complex and it is the heart of resolution.
3. I plan to create a working prototype that incurs small changes in the internal types (and no changes in public types).
    This allows me to understand the problem and required abstractions better. I plan to drive the requirement top-down.
    This is also a nice incremental step.

## Potential approaches to ResolvedDependency

1. We're not changing ResolvedDependency, instead we pass some kind of listener to the the dependency graph builder.
     This listener can receive information about the dependencies and potentially build a proper graph.
     If not listener provided then we graph builder works as usual
2. Make the ResolvedDependency graph work based on the new model. This will require some kind of an adapter.

## Potential stories:

* Add performance test coverage for builds with complex dependency graph.
* Make some changes to the command-line dependency report, making use of new internal APIs.
* Expose the dependency graph via a public experimental API.
* Add an HTML dependency report.

# Open issues

1. The plan/goals for the lower-level API.
2. The design of the new dependency graph. Rough plan from the email thread:

     - The replacement for ResolvedDependency should be reachable from ResolvedConfiguration, and would eventually replace getFirstLevelModuleDependencies() plus the corresponding stuff on LenientConfiguration.
     - It would be a graph, with the nodes representing resolved module versions (using ResolvedModuleVersion or some subtype). The edges would represent resolved dependencies (using some new interface).
     - The nodes need to provide the module version id, plus the set of outgoing dependencies, plus the set of artefacts for the module. The edges need the above attributes, i.e. the requested module version, a set of references to the module versions that matched, and a reference to the module version that was selected.
     - We might think about making the nodes also carry resolution failure information, so that unresolved dependencies end up in the appropriate place in the graph.
     - Later, we'd add some conveniences to ResolvedConfiguration to do some traversal of this graph in interesting ways - find me all the module versions, find me all the artefacts, find me all the files, find me all the unresolved dependencies, and so on.
     - I would also think about wrapping access to the graph in some kind of action

# Notes

This feature is sponsored by client.
