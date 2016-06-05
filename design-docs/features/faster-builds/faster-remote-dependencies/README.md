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

### Cost of declaring dependencies

The following profiling results are based on an experiment to use _static Groovy scripts_ to create:

- 2500 projects applying the Java plugin
- each project declaring 50 dependencies

And calling `help`. The results show that:

- 14.6% of build time is spent in registering the dependencies, that is to say executing the `dependencies` block.
- time spent in `dependencies` block is equally dominated by parsing the dependency notation (6.85%) and adding the dependencies to the container (4.8%)
- 54% of build time is spent in applying the Java plugin, and 21% just in creating the source sets plus 8% in creating the `Test` tasks

### Large project with lot of snapshot dependencies and local repository

Those measurements were made as of May 17th, using `master`, after optimizing the depdendency management engine to remove use of Ivy structures in our engine.
The project used is `lotDependencies`, which defines:

- a local Maven repository
- 4 sub-projects with lots of snapshot dependencies

The task being executed is `dependencyReport` which triggers full dependency resolution. Times are measured with a hot daemon.

- dependency resolution represents 58% of build execution time
- 42.3% of build execution time is spent in resolving dependencies
   - 13% spent in parsing `maven-metadata.xml`. This time is totally dominated by the SAX parser initialization itself. Actual parsing is less than 2% of time.
   - 17% spent in parsing the POM descriptors. This time is also totally dominated by parser initialization. Only 5% of time is really spent in parsing.
   - 4% of time is spent in `ExternalResourceResolver#getId`. This ID cannot be cached efficiently because it depends on the configuration of the resolver which is not immutable.
   - 3% of time spent in `DefaultMavenModuleResolveMetaData#copy`
- 11.8% of build execution time is spent in assembling the result (mostly populating internal datastructures / copying)



