## Dependency report shows requested dependency versions (DONE)

### User visible changes

The dependencies report should include information of the 'requested' and 'selected' dependency version
Instead of the selected version, we can print something like: 1.0->3.0. Mock up:

<pre>
testCompile - Classpath for compiling the test sources.
+--- root:a:1.0
|    \--- foo:bar:1.0 -> 3.0
\--- root:b:1.0
     \--- foo:bar:3.0(*)
</pre>

We should also remove the confusing configurations from the view.

### Integration test coverage

Scenarios:

* conflicting direct dependencies
* conflicting direct & transitive dependency
* conflicting transitive dependencies
* conflicts with more than 2 candidates
* conflicts in multi-project build
* conflicts with dynamic versions
* conflicts resolved in multiple phases, e.g:

    root->foo:1.0 -> 3.0
    root->foo:2.0 -> 2.5
    root->bar:1.5

* include maven snapshots

* avoid regression of current features:
    * subtree is omitted (*)
    * the configurations are shown

### Implementation approach

1. There are 3 goals:
    1. deliver the functionality to solve the use case
    2. model the dependency graph better so that we can provide a 'lower level' API, (unopinionated toolkit stuff)
    3. leave the codebase in a better state than it was
        (kinda obvious, but wanted to point out that we should end up with better internal model, some clean-up with legacy ivy impl, etc.)
2. Be cautious with DependencyGraphBuilder - it's most complex and it is the heart of resolution.
3. We should not incur increased memory usage - try to serialize the dependency graph and make it available on demand.
    Make sure it does not make things slower.

## New report shows usages of each dependency (DONE)

Attempts to answer following questions:

* why this dependency is in the dependency tree?
* what are all the requested versions of this dependency?
* why this dependency has this version selected?

### User visible changes

A brand new report that traverses the graph the opposite way (Dependency Insight Report).

Mock up:

<pre>
com.coolcorp.util:util-core:4.0.7 (conflict resolution)
\--- com.coolcorp.sharedlibs:configuration-repository-impl:1.3.30
     \--- com.coolcorp.sharedlibs:lispring-lispring-core:1.3.30
          \--- com.coolcorp.container:container-http-impl:3.0.24
               \--- com.coolcorp.container:container-rpc-impl:3.0.24
                    \--- compile

com.coolcorp.util:util-core:2.4.6 -> 4.0.7
\--- com.coolcorp.cfg2:cfg:2.8.0
     \--- com.coolcorp.sharedlibs:lispring-lispring-core:1.3.30
          \--- com.coolcorp.container:container-http-impl:3.0.24
          |    \--- com.coolcorp.container:container-rpc-impl:3.0.24
          |         \--- compile
          \--- com.foo.bar:foo-bar:1.0
               \--- compile
</pre>

The idea is to traverse the graph the other way and print the dependent path for a given dependency.
This report is useful to track where the given version of some dependency was picked up from in case of conflict resolution.
This drives some conveniences to our dependency graph API.

For interesting version modules, the report shows also if the the version was 'forced' or if it was selected by 'conflict resolution'.

### Integration test coverage

* shows multiple trees if dependency occurs in the tree with different requested version
* annotates 'forced' version
* annotates version selected by 'conflict resolution'
* shows plain ordinary version, one tree
* what if there's no matching dependency and/or configuration?
* what if there are unresolved dependencies?
* deals with dependency cycles

### Implementation approach

- 'dependencyInsight' implicit task, pre-configured with:
    - searches for dependency in 'compile' configuration if java applied, otherwise it needs to be configured

- 'dependencyInsight' task configuration:
    - can provide ResolvedDependencyResult predicate
    - can have one configuration selected

- the report prints a warning if the configuration resolves with failures because this can affect the dependency tree
- the plugin implementation potentially should go to 'reporting' subproject

## Dependency report handles resolution failures

### User visible changes

Unresolved dependencies should be included in the report output, in the appropriate location in the tree. For example:

<pre>
testCompile - Classpath for compiling the test sources.
+--- root:a:1.0
|    \--- foo:bar:1.0 -> 3.0
\--- root:b:1.0
     \--- foo:unknown:1.0 FAILED
</pre>

The report should also consider the case where the requested and selected versions are different. For example, a module may be forced to use a version
that does not exist:

<pre>
testCompile - Classpath for compiling the test sources.
+--- root:a:1.0
|    \--- foo:bar:1.0 -> 3.0 FAILED
\--- root:b:1.0
</pre>

The task should not fail when there are unresolved dependencies in the dependency graph. However, it should display a warning informing the user
that the result is not complete.

For all other problems, the task should continue to fail as it does now.

### Test coverage

* A build that declares a dependency on a requested module version that does not exist (change or replace DependencyReportTaskIntegrationTest."renders even if resolution fails"). Assert that:
    * The unresolved dependency is rendered in the appropriate location in the tree, as above.
    * The report renders the tree for each requested configuration.
    * The build does not fail.
* A build that declares a dependency on a module version that does exist, but forces the dependency to use a version that does not exist. Assert that:
    * The unresolved dependency is rendered in the appropriate location in the tree. The output should show the user which version actually could not
      be resolved, as above.
    * The build does not fail.
* A build that declares a dependency on multiple dynamic versions (eg 1.2+, latest.integration), none of which exist.
* A build that declares a dependency on a static version (eg 1.2.), but a force rule replaces this with a dynamic version (e.g. 1.2+), for which no matches exist.
    * The dependency should be rendered as something like: `foo:bar:1.2 -> 1.2+ FAILED`

### Implementation approach

Should only require changes to the report rendering.

## Dependency insight report handles resolution failures

### User visible changes

Unresolved dependencies should be included in the report output, in the appropriate location in the tree. For example:

<pre>
foo:unknown:1.0 FAILED
\--- root:b:1.0
     \--- compile
</pre>

Or where multiple versions are involved:

<pre>
foo:unknown:1.0 (forced) FAILED
\--- root:b:1.0
     \--- compile

foo:unknown:2.1 -> 1.0 FAILED
\--- root:a:1.0
     \--- compile
</pre>

The task should not fail when there are unresolved dependencies in the dependency graph. However, it should display a warning informing the user
that the result is not complete.

For all other problems, the task should continue to fail as it does now.

### Test coverage

* A build that declares a dependency on a module version that does not exist (change or replace DependencyInsightReportTaskIntegrationTest."deals with unresolved dependencies").
    * The unresolved dependency is rendered in the appropriate location in the tree, as above.
    * The build does not fail.
    * When run with --dependencies that does not match anything, the user is warned that some dependencies could not be resolved.
* A build that declares a dependency on a module version that does exist, but forces the dependency to use a version
  that does not exist. Assert that:
    * The unresolved dependency is rendered in the appropriate location in the tree. The output should show the user which version actually could not
      be resolved, as above.
    * The build does not fail.

### Implementation approach

Should only require changes to the report rendering.
