We want to scale Gradle so that it neatly consumes 1000+ module builds.

# Use cases / problems

    * gigantic 3.7k-module build requires a lot of memory to run (tens of gigabytes).
    * high memory consumption makes gc invoked more often, with periodical full pauses. This reduces the throughput of the whole build
    (in large builds, I typically observe the throughput at the level of 90%).
    * any state hooked up to project / task (e.g. fields in task implementations) is not garbage collected because projects and tasks are strongly referenced from GC roots.

# Memory bottlenecks

    * Configuration instances.
        * The old dependency graph model (DefaultResolvedDependency and friends)
        * The new dependency graph model (ResolutionResult)
        * ResolutionStrategy (if the client build configures lots of forced versions, etc.)
        * DefaultResolvedArtifact.artifactSource - Configuration which is resolved but artifacts were not yet requested is heavy because
        lazy factory (artifactSource) includes a lot of heavy ivy types (DefaultModuleDescriptor).
    * Project instances. Currently, Gradle always creates instances for all projects, even if one uses configure on demand feature and runs build for a single module.
        * Task instances, TaskContainer. They are strongly referenced from projects and other places.

# Possible stories

    * stop providing old dependency graph model
    * flush the new dependency graph model to disk (typically this model is not needed and client only needs files from the configration)
    * drop all the resolution strategy state after the configuration is resolved
    * drop more ivy types (DefaultModuleDescriptor)
    * clean up / project instances when Gradle is done with given project. Gradle knows when we're done with the project (e.g. last task for this project has completed).
        * we don't want to drop the core domain objects like tasks but perhaps we would slim them down (e.g. task.deleteAllActions())
        * tear down project services
        * etc.
    * improve configuration on demand mode so that it only loads projects that are requested
    * there's a lot of duplication in the dependency graphs in different configurations / projects. Perhaps some objects / parts of the graph can be reused.

# Actual stories

## Configuration drops redundant state after resolution

To conserve memory the configuration may drop the resolution strategy.
The resolution strategy has popped up in the profiler as pretty big, especially if lots of forced modules are configured.
Potentially there's more state we can drop once the configuration is resolved: beforeResolve / afterResolve actions.
We should be careful not to drop state that can be queried and to focus on things that stick out in the profiler.

### User visible changes

* Decent error message when the user attempts to configure the resolution strategy but the configuration is already resolved
* less heap used
* resolutionStrategy.getForcedModules() will fail when the configuration was already resolved. I'm uneasy about this change.

### Other potential state worth dropping after resolution:

* beforeResolve / afterResolve actions

## The old resolved dependency graph is memory friendly

Old resolved dependency graph may consume 20-50% of the heap size (non-retainable) of a large project
The graph is not needed for the typical scenarios (usually we just need the files)
The idea is to flush the information needed for the graph to the disk when the configuration is resolved
When the graph is requested, the information is read from the disk and the graph is assembled

### User visible changes

Ideally, we want the API to remain untouched and the only effect is faster & less memory hungry.

### Implementation plan

* The graph data will be flushed to disk from the DependencyGraphBuilder.assembleResult method
* The graph for the entire configuration is built when any part of the graph is requested:
    * configuration.resolvedConfiguration.getFirstLevelModuleDependencies() and friends
    * resolvedArtifact.getResolvedDependency()
* The graph data is removed from disk when the build is finished
* Use little files so that cleanup is fast. Let's try with one file per thread

### Coverage

* existing integ test coverage

### Questions

* Should the graph reside in memory after it was built?
    * keeping it in memory will bite us when IDE metadata is generated for the large project
    * I'd rather avoid fancy references. Soft references will cause huge memory allocation because -server jvms will prefer heap expansion over gc
    * not keeping it in memory will almost certainly reduce the performance if the graph info is accessed often
    * perhaps we keep it in memory but offer a LenientConfiguration.clear() that removes the graph data. The method obviously goes away once the old graph is gone.

## The new resolved dependency graph is memory friendly

Pretty much the same goals and implementation details as for the old dependency graph.

### Questions

* Should we build the new dependency graph from the data that we have stored for the old dependency graph? E.g. reuse the persistent format we already have.