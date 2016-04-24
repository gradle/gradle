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

Here are the results of profiling dependency management on the `largeProjectDependencies` template. This template includes:

- 100 projects
- 400 dependencies, with a transitive dependency depth of 6
- internal project dependencies

Times here were reported against `master` as of April, 7th, and relative to `Configuration.getResolvedConfiguration`. The template makes use of a local Maven repository, so external dependencies are fetched from a local filesystem. Therefore times reported here do not include any remote download.

* 41% of time spent in writing the resolved configuration to the persistent cache
    * 62% of it in `TransientConfigurationResultsBuilder.writeId(byte, ResolvedConfigurationIdentifier[])`
    * 31% of it in `StreamingResolutionResultBuilder.resolvedConfiguration(ModuleVersionIdentifier, Collection)`
    * 4% of it in `TransientConfigurationResultsBuilder.parentChildMapping(ResolvedConfigurationIdentifier, ResolvedConfigurationIdentifier, long)`
    * 2% of it in `StreamingResolutionResultBuilder.resolvedModuleVersion(ModuleVersionSelection)`
    * Most of this time seems to be spent in `flush`, and since flushing is mostly triggered from `writeString`, it probably indicates that the buffer size is too small (currently 4096 bytes). Increasing the default buffer size to `65536` reduces the time spent in writing the persistent cache from 41% to 10%.
* 9% of time spent in parsing the module descriptors
    * 69% of it spent in parsing the external module descriptors (Maven POM files)
    * 31% of time spent in parsing the project module descriptors (Ivy files). This one clearly indicates that a better serialized format for our internal modules would be better fitted. It's very expensive for information that is available locally.
* 20% of time spent in adding `DependencyEdge` instances to `incomingEdges`/`outcomingEdges` in `ConfigurationNode`. Time is totally dominated by calling `hashCode` on these instances. Since the `hashCode` is the native JVM one, it seems to be hardly optimizable without changing the algorithms. Question: do we need the `Set` semantics here?

Those results show that using a more appropriate cache, we can probably improve the performance of dependency resolution. For example, instead of using a persistent store, we could use an in-memory store backed with a persistent store for overflows.

#### Miscellaneous results

1. Iterating on `Configuration#getAllDependencies` is very inefficient. The code at `DefaultProjectDependency.resolve(DependencyResolveContext)` triggers a call to `iterator()`, which in turns delegates to `org.gradle.api.internal.CompositeDomainObjectSet#iterator()`. The problem is that this method returns an immutable iterator constructed from a `LinkedHashSet`. Creating this hash set requires iterating the backing collection twice, because of an implicit call to `size()`. We should investigate why returning an immutable iterator is required, and in any case avoid creating a `LinkedHashSet`.
