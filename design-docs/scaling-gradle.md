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
        * clear and null out task container
        * tear down project services
        * etc.
    * improve configuration on demand mode so that it only loads projects that are requested
    * there's a lot of duplication in the dependency graphs in different configurations / projects. Perhaps some objects / parts of the graph can be reused.
