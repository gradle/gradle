Investigate and improve build configuration time, when build configuration inputs such as build scripts or Gradle or Java version have not changed.

Audience is developers that are using the Gradle daemon.

## Implementation plan

- Review and update performance tests to measure this and lock in improvements.
- Profile test builds and use results to select improvements to implement.
- Profile daemon client

### Potential improvements

- Fix hotspots identified by profiling
- Replace usage of exceptions in decorated objects
- Faster rule execution for task configuration
- Make creation of project instances cheaper
- Faster startup by reducing fixed costs in daemon client and per build setup
- Start progress logging earlier in build lifecycle to give more insight into what's happening early in the build

## Stories

### Performance tests establish build startup baseline

Ensure there is a performance test build that:

- Uses the current Java plugin
- Has many projects

Use this build in a performance test that:

- Runs `help`
- Uses the daemon

Tune the number of projects in the test build based on this. We're aiming for a build that takes around 10-20 seconds.

Note: review the existing test builds and _reuse_ an existing build and existing templates if possible. Should also reuse existing test execution performance test class if possible.

### Write events to the daemon client asynchronously

Currently, events are written to the daemon client synchronously by the worker thread that produces the event.

Instead, queue the event for dispatch using a dedicated thread. Events should still be written in the order they are generated.

Daemon client should not need to be changed.

Add some more test coverage for logging from multiple tasks and threads, and for progress logging from multiple projects and tasks, for parallel execution.
These tests should run as part of the daemon and non-daemon test suites.

### Don't hash the build script contents on each build

Use the same strategy for detecting content changes for build scripts, as is used for other files.

Should use `CachingFileSnapshotter` for the implementation, if possible. Could potentially be backed by an in-memory `PersistentStore`, reusing the instance
created by `GlobalScopeServices.createClassPathSnapshotter()`. An additional refactoring could make this a persistent store.

### Reuse build script cache across builds

Currently, the cache for a given build script is closed at the end of the build.

Investigate options for reusing this across builds, when the build script has not changed.

### Lazy project creation and initialisation 

Rework the early `Project` lifecycle to defer work until as late as possible, and create and initialise more services lazily.

TBD exactly what this means.

### Understand where build startup is spending its time

Profile the daemon and Gradle client using the above test build to identify hotspots and potential improvements. Generate further stories based on this.

Note: this goal for this story is only to understand the behaviour, not to fix anything.

#### Some results

Here are some profiling results from running `ManyEmptyProjectsHelpPerformanceTest`, which is a performance test that involves a lot of empty projects, revealing the fixed cost of creating projects.

- Client (`GradleMain`)
   - a lot of duplicate `char[]` are found in the snapshot for `org.gradle.internal.progress.BuildProgressLogger`. Those strings are not strongly reachable, meaning that we're creating and throwing them away a lot of times, wasting memory and involving GC.
   - an equivalent amount of time is spent in reading/decoding the messages and outputting them
        - 44% of time spent in `DefaultSerializerRegistry.java:80 org.gradle.internal.serialize.kryo.KryoBackedDecoder.readSmallInt()`
        - 44% of time spent in `org.gradle.logging.internal.ConsoleBackedProgressRenderer.onOutput(OutputEvent)`

- Worker
   - Memory
       - a lot of duplicate strings are found in rule descriptors. Typically, `Project.<init>.extensionContainer()` found 10000 times. We could reduce memory usage by interning the descriptors.
       - a huge amount of memory is wasted in empty hashmaps, array lists and treemaps. A lot of those hash maps seem to come from the extensions/conventions (probably to store the dynamic properties). Could be worth investigating lazy initialization of those.
       - tens of thousands of duplicate `org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor` and `org.gradle.model.internal.core.ModelReference`
        - Another huge amount of wasted memory due to an empty inputs list in `AbstractModelAction` (because a lot of empty `ArrayList`s are used, each of them is a different instance). It's the main source of empty arrays in the memory dump.
        - A large amount of `org.gradle.model.internal.registry.RuleBindings.PredicateMatches` instances are found in memory (representing 17% of total memory usage). `ModelNodes` represent `15%` of total memory. It seems to be a lot for a project that doesn't use the software model.
    - CPU
        - 45% of time is spent in `org.gradle.initialization.SettingsLoader.findAndLoadSettings`, out of which:
            - 47% of time is spent in setting the project properties. A large amount of this time is spent in filling exception stack traces for missing properties, that are always caught. In short, total dead time. Profiler reports 90k `MissingPropertyException` sent from Gradle code, and 90k `MissingPropertyExceptionNoStack` sent from Groovy. Given that there are 10k projects, that is 9 exceptions per project, that contribute most of the settings initialization!
            - 53% of time is spent in initializing the project. Most of this time is spent in populating the model registry (41%) and initializing services (38%)
        - 45% of time is spent in evaluating the projects, and almost all time is spent in logging.


Here are results from profiling a `MediumWithJUnit` build consisting of 25 subprojects, each having 200 source files and an equivalent number of tests. We're running `gradle help` to measure the minimal configuration time.

- Memory
   - the various `NotationParser` implementation create display names even if they are never used. Seeing 480 copies of `java.lang.String "an object of type ComponentSelector` for example

- CPU
   - 630 ms is spent in running build stages
      - 156ms (25% of time) spent in applying the settings
        - 80ms in setting project properties. most of the time spent in dynamic property resolving (`Class#getInterfaces` seem to dominate time)
        - 76ms in creating the project and services. 3% of the whole build time seems to be spent just on checking `File#exists()` when creating the `ScriptSource` in `ProjectFactory#createProject`
      - 475ms (75% of time) spent in configuring the build
        - 414ms spent in applying scripts
        - of applying scripts, 393ms is spent in running the build scripts. Each build script execution consumes around 3% of build time, except the first one (9%, because of plugin resolution). There doesn't seem to be anything obvious from those numbers to make things faster here, but we're definitely seeing dynamic method resolution in action here.

Here are the results of profiling the Gradle build itself.

1. running `gradle tasks`

- Memory
    - The build is dominated by `Test` tasks. There are 3000 instances, eating 65MB of memory
- CPU
    - 36s (!), or 80% of the time is spent in generating the task report itself
        - 98% of this time is spent in `CachingDirectedGraphWalker#doSearch()`, and more specifically 39% of time spent in creating `NodeDetails`. What is more interesting it that most of this time and time spent in `doSearch` is directly related to adding and searching `NodeDetails` in sets (computing hash codes mostly). There must be something wrong in the algorithm here.

Given this result, a second profiling result, using `--dry-run` this time:

2. running `gradle tasks --dry-run`

- CPU
    - 7.265s spent in doing build stages
        - 1.536s in applying settings
        - 5.7s in configuring the build
    - The results are very consistent with what we have seen in the `MediumWithJUnit` case. There are lots of tasks configured and created even though they are not used, which speaks for the software model. Most of the time is spent in creating and configuring tasks. Which is interesting here since we're running with `--dry-run`, so while we could need the _specs_ of the tasks, we certainly don't need the tasks themselves.
