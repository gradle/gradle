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
- Don't discover global services that are not required in the daemon client.
- Use a shorter classpath to bootstrap the daemon and daemon client.

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

#### Profiling results for ManyEmptyProjectsHelpPerformanceTest

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
            - ~~~47% of time is spent in setting the project properties. A large amount of this time is spent in filling exception stack traces for missing properties, that are always caught. In short, total dead time. Profiler reports 90k `MissingPropertyException` sent from Gradle code, and 90k `MissingPropertyExceptionNoStack` sent from Groovy. Given that there are 10k projects, that is 9 exceptions per project, that contribute most of the settings initialization!~~~ (fixed)
            - 53% of time is spent in initializing the project. Most of this time is spent in populating the model registry (41%) and initializing services (38%)
        - 45% of time is spent in evaluating the projects, and almost all time is spent in logging.

#### Profiling results for MediumWithJUnit

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

#### Profiling results for the Gradle build itself

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

3. running `gradle classes --dry-run`

This profiling session was done from the Gradle 2.13 release branch, already including numerous performance improvements, so the results show different results from 1. and 2. The `--dry-run` option is used to focus on configuration time.

Most of the time (90%) is spent in evaluating the projects. The problem is that visually, a "pause" is visible during the configuration phase: Gradle says its configuring the root project, and nothing seem to happen for seconds. This time is mostly spent in configuring the tasks. Instantiating and decorating the tasks take around 4% of total build time, but this includes the initialization of services. However, configuring the tasks and executing the build scripts cost a lot.

 In particular, resolution of:

 - properties
 - methods

 on `DynamicObject` is a clear hotspot. There are multiple reasons for this:

 - `DynamicObject` makes use of `PropertyMissingException` which are used for flow control, even if in the end, no exception is thrown. The problem is that we pay the price of filling the stack trace each time. For a simple dry run like this, that's 34k+ `MissingPropertyException` (with stack traces), 32.5k+ `MissingPropertyExceptionNoStack` (no stack traces, cheaper), 1600+ `MissingMethodException` (with stack traces), which are all used only for flow control (because in the end, no error is reported to the user). A lot of those `MissingPropertyException` are caused by lookups of `javaVersion` or `isCiServer` on "dynamic objects".

 Investigation shows that the implementation is very inefficient: a lot of `BeanDynamicObject` are created with `implementsMissing=true`, even though they effectively do not implement it. A spike that checks if `propertyMissing` is implemented and sets this flag to `false` if it's not the case (caching the result by delegate type) shows that we can avoid a lot of unnecessary `getProperty` calls that would throw a `MissingPropertyException`. Also, we're redundantly calling `hasProperty` and `getProperty`. There's also no caching of which delegate implements a property, so the lookup is done every time. Unfortunately, this lookup also has to be done per-instance (there's no structural dynamic object, it's a runtime mixin).

 A spike that combines:

 - caching lookup of properties in `CompositeDynamicObject`
 - avoiding setting `implementsMissing` on most of `BeanDynamicObject`
 - replaces `MissingPropertyException` and `MissingMethodException` with their no stack-trace equivalent

 shows a startup time decreasing from 5s to 3.5s. There's probably something even better to do if we change `DynamicObject` not to rely on exceptions at all. We could also think of generating real mixin classes at runtime (a bit like Groovy's traits) to avoid calls to `getProperty` at all whenever possible, because such calls are uncached and very inefficient. Note that this is also true for `invokeMethod`.

 Eventually, an experimental statically compiled DSL could also be worth investigating, but that's out of scope of the performance stream work.

 In terms of exceptions being thrown without being propagated to the user, we can also see `ClassNotFoundException` as a clear hotspot, with more than 2300 exceptions, thrown by `URLClassLoader` but through `FilteringClassLoader`, `MultiParentClassLoader` or `CachingClassLoader`. It would be worth checking if we can avoid those exceptions to be thrown, since we're capturing them.

#### Profiling results for an Android build

Here are some profiling results, as of March 16th, 2016 (`master`) on an [Android app](https://github.com/google/iosched).

Running `gradle help` isn't particularly slow (~700ms) so profiling was focused on a real use case of up-to-date build.

Running `gradle -Dcom.android.build.gradle.overrideVersionCheck=true assembleDebug` (daemon on, second, up-to-date run):

- 35% of time spent in creating the tasks (Android `BasePlugin`), and most of this time (65%) is spent in dependency resolution
- 7% of time spent in setting up the Android SDK
- 16% of time spent in dependency resolution for getting the script classpath (`org.gradle.plugin.use.internal.DefaultPluginRequestApplicator.defineScriptHandlerClassScope(ScriptHandlerInternal, ClassLoaderScope, Iterable)`)

That's roughly 40% of build time spent in dependency resolution. While somehow very diffuse, it seems that a lot of time is spent in executing the DSL build script logic, which is very costly due to heavy use of dynamic extensions. The call stacks are very long and involve a lot of method missing/extension handling.

#### Profiling results for task creation

The objective of this session is to estimate the cost of task creation, and give hints about how to improve it. The test was realized using the following `build.gradle` file:

```
@groovy.transform.CompileStatic
void createTasks(Project p) {
    15000.times {
        p.task("copy$it", Copy)
    }
}
createTasks(project)
```

The use of `CompileStatic` is done to make sure we only measure the cost of creation of the tasks. There's no configuration, and calling `gradle help`. As a result, 71% of the build duration (366ms) is spent in `createTasks(Project)`. Starting from here, times are relative to this duration.

- 66% of time is spent in instantiating the task (that is to say, not a direct instantiation, but task creation + dependency injection)
   - a significant anount of this time, most (40%) is spent in instantiating task input/outputs. The overweight is directly related to the eager creation of a `TaskMutator` (which is eagerly created, but even making this lazy wouldn't work since `AnnotationProcessingTaskFactory.Validator#addInputsAndOutputs` will make use of it)
   - 31% of the time (44ms) is spent in instantiating the task through the factory. 2 identifiable problems are immediatly visible:
      - `DependencyInjectingInstantiator` always validates the type, and this counts for 10 of the 31%. Adding a validation cache, or optionaly not validating (is this even useful in real builds?) would help.
      - calling `getConstructors` in `DirectInstantiator` is very expensive (21 of the 31%). Constructors could be cached, or we could even cache direct instantiators, that is to say classes generated specifically to instantiate delegates without reflection.
   - the last 10% (20ms) are spent in `DefaultTaskInputs.getFiles()` which is used to wire the dependencies of a task based on its input files. We are using generic algorithms/implementations here, and it's hard to tell if a collection is empty or not, so instead of being smart and returning an empty collection, for example, we always return a `UnionFileCollection` which is inefficient.
- 34% of time is spent in adding the task to the task collection. Almost all of this time is spent in creating a `RuleBinder`. This algorithm seems to be very inefficient. Basically a significant amount of time is spent in `HashSet#add` (because of `binders.add`). In general it looks like the "new model" rule engine algorithms leak into the old model too easily, and imply a significant amount of configuration time.


