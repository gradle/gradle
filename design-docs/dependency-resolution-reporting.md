Improved dependency resolution reporting

# Use cases.

1. It is hard to figure out why Gradle picks up version X of certain dependency because the dependency report
    only includes the version information *after* conflicts have been resolved.
2. User diagnoses dependency resolve failures
    (with a possible implementation being that the reporting is robust in the face of failures)
3. User views actual class paths
    (e.g currently the reports only show module dependencies.
    It should show external dependencies, self-resolving dependencies, tweaks to sourceSets.main.compileClasspath, etc).
4. User knows why certain dependencies appear / not appear in the graph (e.g. consider exclusions)

# Implementation plan

## Dependency report shows requested dependency versions

### User visible changes

The dependencies report should include information of the 'asked' dependency version
Instead of raw version we can print something like: 1.0->3.0. Mock up:

<pre>
testCompile - Classpath for compiling the test sources.
+--- root:a:1.0
|    \--- foo:bar:1.0 - >3.0
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

## New report shows usages of each dependency

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
This drives some conveniences to our DependencyGraph API.

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

### Open issues

- the name of the report is not good. The existing 'dependencies' report also gives 'insight' into the dependencies.
- the task-level api (the configuration & dependency spec inputs, outputs)
- should the new report extend AbstractReportTask? In that case we need decide what to do with 'projects' public property on AbstractReportTask.
- if there is a single configuration in the project, should it be used by default by the task?
- behavior when some dependencies are unresolved

## Dependency report handles resolution failures

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

TBD

## Dependency insight report handles resolution failures

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

TBD

## Dependency reports include file dependencies

Currently the dependencies report does not include: file dependencies, gradleApi(), localGroovy() dependencies
In addition to above, the dependency insight report does not include the project dependencies (not sure if they are needed in this report)

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

TBD

# Open issues / ideas

1. Model the unresolved dependencies - how to carry the resolution failure?
2. Later, we'd add some conveniences to ResolvedConfiguration to do some traversal of this graph in interesting ways - find me all the module versions, find me all the artefacts, find me all the files, find me all the unresolved dependencies, and so on.
3. I would also think about wrapping access to the graph in some kind of action