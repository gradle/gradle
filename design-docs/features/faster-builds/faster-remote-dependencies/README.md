A set of candidates for making the resolution of remote dependencies faster, and in particular improving build speed perception in the presence of changing dependencies (such as snapshots, dependency ranges, changing resources, etc).

## Candidate changes

- Provide a way to declare the likelihood of a change on a per module level. For example, a way to say check for changes to this module more often, and this module occasionally.
- Check for changes as a batch. For example, check for changes at a given time each day, rather than after a particular period has expired since the particlar module was checked.
- Parallel downloads and remote up-to-date checks.
- Daemon performs preemptive remote up-to-date checks and downloads.
- Make network access visible as progress logging events.
- Improve performance of serializing resolution results to file system: 

## Profiling results

### Large project with lot of external dependencies and project dependencies

Here are the results of profiling dependency management on the `lotProjectDependencies` template. This template includes:

- 100 projects
- 400 dependencies, with a transitive dependency depth of 6
- internal project dependencies

Times here were reported against `master` as of May, 9th, using `help`, that is to say that we're not measuring
the performance of network access with an empty cache, but the configuration time of a project that has been
configured at least once. Profiling was done using `honest profiler` which doesn't
suffer issues reported by YourKit. No specific hotspot is visible in **dependency management** itself. However, there's
a clear problem with the `dependencies` block itself, which triggers an excessive number of `MethodMissingException`
being thrown, and stack traces filled for nothing. This represents 30% of build time.

No specific hotspot was discovered either in `lotDependencies` or the external `iosched` Android project. It's worth
noting that while honest profiler sometimes shows the ivy descriptor files parsing as being an issue, it is far from
being the 20% time shown by previous profiling sessions: it's only a fraction of total dependency time, and optimizing
this is less likely to dramatically change the performance of dependency resolution.
