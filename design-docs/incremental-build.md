# Incremental Build Improvements

This spec defines some improvements to improve incremental build and task up-to-date checks

## Story: not loading the file snapshots in up-to-date checking

- Add hash based pre-check phase to up-to-date checking
- pre-check hash is calculated from the list of files sorted by their name
  - the file's name, size and last modified times are added to the hash
  - there are no file content hashes as part of the pre-check hash
- if pre-check hash is same as pre-check for previous snapshot, it is considered up-to-date and the actual file snapshot doesn't have to be loaded
- if pre-check hash is different and snapshot up-to-date check doesn't contain changes (false positive), the persisted hash gets updated
  - this might happen when file modification times are different, but content is the same
- The "fileSnapshots" in-memory cache should use weak references for values.
  - loaded fileSnapshots will get GCd under memory pressure

## Story: Reuse native source file dependency information within a build

Gradle parses each source file to determine the source file dependencies.
Currently this is cached on a per-task basis, meaning it is recalculated many
times when building multiple variants and compiling test suites. We should
instead move this to a cache that is shared across all tasks for a build,
and kept in memory across builds.

## Story: Inline the data from the file hash cache into the task history cache

Change `DefaultFileCollectionSnapshotter` to store (length, last-modified, hash)
in the `FileCollectionSnapshot` implementation it creates. This is referenced by
the task history cache.

When deciding whether something has changed, iterate over the elements of the
`FileCollectionSnapshot` and use the (length, last-modified, hash) from the
snapshot. Do not fetch this information from the file hash cache. If something
has changed, hash it, and add the (length, last-modified, hash) tuple to the
file hash cache. Also make a copy of the snapshot and update it with this new
entry. This copy will be used as the new snapshot in the task history.

What this means is that file hash cache is used only for new and modified files,
which means there’s no point caching these entries in-memory (except perhaps for
the duration of a build). For unmodified files, there’s never a cache miss. When
nothing has changed for a snapshot, we also don’t need to hold a lock on the
cache, which means less work and better concurrency when most snapshots are
unmodified.

### Test coverage

TBD

## Story: Author specifies all files to include in source directory

Currently, all source sets consist of a collection of root directories and a set of
include/exclude patterns. Since we do not distinguish between patterns that have a
single match (path/to/foo.c) and a glob (**/*.c), we must always scan all root
directory paths and check all include and exclude patterns.  It's common for some
projects to have an exhaustive list of all source files and no globbing.  In those
cases, we could avoid directory walking and pattern matching.

### DSL

Introduces a method `source(String, Closure<PatternFilterable-like>)` to `LanguageSourceSet`:

    model {
        components {
            lib(NativeLibrarySpec) {
                sources {
                    cpp {
                        source("src/lib/cpp") // 1. no configuration, assume default pattern for source set
                        source("src/lib/cpp") {
                            // 2. only include patterns
                            include '**/*.cpp', '**/*.c++', '**/*.C'
                        }
                        source("src/lib/cpp") {
                            // 3. mix of explicit files and patterns
                            include 'foo/bar.cpp'
                            include 'baz/**/*.cpp'
                            exclude '**/*.h'
                            exclude 'baz/fubar.cpp'
                        }
                        source("src/lib/cpp") {
                            // 4. only explicit list of files
                            include 'foo/bar.cpp'
                            include 'baz/baz.cpp'
                        }
                        source {
                            // 5. existing API
                            srcDirs 'src/lib/cpp'
                            include 'foo/bar.cpp', 'baz/**/*.cpp'
                            exclude '**/*.h', 'baz/fubar.cpp'
                        }
                    }
                }
            }
        }
    }

1. turns into a file collection with 'src/lib/cpp' as a root dir and some default set of patterns
1. turns into the same thing as #1 with a different set of patterns
1. turns into a union of file collections:
    - file collection with just "foo/bar.cpp" (no scanning needed)
    - file collection root dir 'src/lib/cpp' with include pattern 'baz/**/*.cpp' and exclude pattern '**/*.h'
    - file collection of excluded files 'baz/fubar.cpp' (no scanning needed)
    - union = (a+b)-c
1. is the fastest and turns into a file collection with just a set of files. (no scanning needed)
1. is what we have today.  I think the migration would be
    - Introduce new syntax (release X)
    - Deprecate and warn that old syntax is going away -- part of the warning might be a generated "this is what it would look like" message
    - Remove old API (release X+?), but keep source(Closure) around a bit longer to point people back to the migration path.  We wouldn't honor configuration done with source(Closure) anymore.
1. Figuring out if an include/exclude pattern is really a file would be just checking for '*' in the pattern.

### Things to consider:

- What do the defaults look like and how do they mix with this? e.g., how do I add exclusions without duplicating the default path?


    model {
        components {
            lib(NativeLibrarySpec) {
                cpp {
                    source("src/lib/cpp") {
                        exclude 'fubar.cpp'
                    }
                    // maybe repurpose old syntax to mean "exclude/include patterns for default"?
                    source {
                        exclude 'fubar.cpp'
                    }
                }
            }
        }
    }

## Story: Reduce number of directory scans in up-to-date checks

TBD

## Story: Reduce the in-memory size of the task history cache

1. Discard history that will never be used, eg for tasks that no longer exist,
or that is unlikely to be used, eg for tasks in builds other than the current.
2. Keep a hash of the file snapshots in memory rather than the file snapshots
themselves. It’s only when we need to know exactly which files have changed that
we would need to load up the snapshot.
3. Do something similar for the task properties as well.
4. Reduce the cost of a cache miss, by improving the efficiency of serialisation
to the file system, and reducing the cache size.
5. Reduce the cost of a cache miss, by spooling to an efficient transient second
level cache, and reducing the cache size.

## Open Issues

- Check behavior of Prebuilt Libraries with large number of headers (https://discuss.gradle.org/t/native-performance-issue-with-no-op-builds-with-boost-as-a-dependency)
- See incremental-build-old.md for other ideas and specific issues.
- Profiler support: Skip warm-up or have explicit opt-in for warm up profiling?
  - Conditionally enabling the profiler in the code by using Yourkit API.
- Profiler support: For cross-version tests, skip all versions except the current?
- Profiler support: Do we care about conditionally profiling the CLI and daemon processes (such that the profiling options should be configurable for each)?

## Avoid unnecessary Ant default excludes in software model source sets

- Add internal support for `DefaultSourceDirectorySet`-like class for use with `LanguageSourceSet`.  It would not use the default Ant exclude patterns.
- We should try to leverage the existing PatternSet caching where that makes sense.

## Story: Allow a task to register inputs "discovered" during task execution

This story adds a way for a task to register additional inputs once execution has started.  At the end of execution, the discovered inputs are recorded in the task's execution history.

    void perform(IncrementalTaskInputs inputs) {
      getInputs().getFiles.each { source ->
        File discoveredInput = complicatedAnalysis(source)
        inputs.newInput(discoveredInput)
      }
      if (inputs.incremental) {
        inputs.outOfDate {
          // do stuff
        }
      }
    }

An initial implementation of "discovered inputs" was put in place to improve performance of native incremental compile. Extracted header files are registered as discovered inputs. This is an internal feature of `IncrementalTaskInputs` and is only used by the `IncrementalNativeCompiler`. To make this a public feature that build authors can use to add their own discovered inputs, we need to make this feature more friendly.

### Test coverage

- Discovered inputs must be Files (not directories).
- On the first build, no discovered inputs are known, so discovered inputs are not needed for up-to-date checks.
- On the second build, discovered inputs from previous build for a task are checked for up-to-date-ness.
- When a discovered input is modified, the task is out of date on the next build.
- When a discovered input is deleted, the task is out of date on the next build.
- The set of discovered inputs after the task executes represents the inputs discovered for that execution, so if on build#1 discovered inputs are A, B, C and on build#2 discovered inputs are D, E, F.  Discovered inputs after #2 should be D, E, F (not a combination).
- A task that is up-to-date should not lose its discovered inputs. Following an up-to-date build, a change in a discovered inputs should cause a task to be out of date.  i.e., A task that discovers inputs A, B, C in build#1, is up-to-date in build #2, should still have discovered inputs A, B, C in build#3.

### Open Issues

- When discovered inputs changed, without knowing which source files contributed to the discovered inputs, we should mark the task inputs as incremental=false and treat it like a rebuild.  We don't do this because `IncrementalNativeCompiler` doesn't handle this case.
- When discovering inputs, we should make sure that all source files are visited each time or provide a way to incrementally discover inputs.
- We may want to revisit the API to better model the relationship between a particular source file input and it's discovered inputs.
    - Finalize API (should it remain on IncrementalTaskInputs or move somewhere else).
- We should provide documentation/samples for using discovered inputs.
- Any change to discovered inputs should cause all inputs to be out-of-date -- currently, a task's IncrementalTaskInputs are still marked as incremental when discovered inputs change.  Incremental native compiler relies on `incremental` flag to do any incremental builds.
    - If task has inputs [A,B,C]. If only A changes, IncrementalTaskInputs will only report A as changed and incremental=true.  The task may do a "incremental execution".
    - If task has input property foo.  If foo changes, IncrementalTaskInputs will have incremental=false and the task should do a "full execution".
    - If a task has discovered inputs [X,Y,Z], if any discovered input changes, IncrementalTaskInputs will report _no_ files as changed and incremental=true (this is partially wrong).
    - If we change discovered inputs to be more like regular inputs, we need to decide on the behavior of the set of files changed (as seen by `outOfDate`) and incremental=true or false.
    - If we make the task non-incremental (incremental=false), IncrementalNativeCompiler needs to handle this by always doing a scan of all files and incremental build (this pushes the hard work down into the task that wants to use discovered inputs).
    - If we make the task incremental (incremental=true), do we also include discovered inputs in the `outOfDate` list? IncrementalNativeCompiler ignores this list right now.
- Discovered inputs do not work with continuous build.
- The previous discovered files snapshot can be thrown away as soon as we know we'll be executing.
- It would be nice to perform discovery incrementally.
- Eventually be pretty much anything that you can use as a declared input for a task (including non-file properties and objects) should be able to be treated as "discovered inputs".


## Background information about the in-memory caches

### Task History related caches

File path Strings are duplicated in memory in different in-memory caches. The different in-memory caches in Gradle are:
  - [`fileSnapshots` used in `CacheBackedFileSnapshotRepository`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CacheBackedFileSnapshotRepository.java#L29)
    - key is Long id
    - value is `FileCollectionSnapshot`, serialized with `DefaultFileSnapshotterSerializer`
  - [`taskArtifacts` used in `CacheBackedTaskHistoryRepository`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CacheBackedTaskHistoryRepository.java#L37)
    - key is String, a task path
    - value is `CacheBackedTaskHistoryRepository.TaskHistory`, serialized with `CacheBackedTaskHistoryRepository.TaskHistorySerializer`
  - [`outputFileStates` used in `OutputFilesCollectionSnapshotter`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/OutputFilesCollectionSnapshotter.java#L54)
    - key is String, a output directory path
    - value is Long, a random id
  - [`fileHashes` used in `CachingFileSnapshotter`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CachingFileSnapshotter.java#L35)
    - key is String, a file path
    - value is `CachingFileSnapshotter.FileInfo`, serialized with `CachingFileSnapshotter.FileInfoSerializer`
  - [`compilationState` in nativeplatform's `DefaultCompilationStateCacheFactory`](https://github.com/gradle/gradle/blob/master/subprojects/language-native/src/main/java/org/gradle/language/nativeplatform/internal/incremental/DefaultCompilationStateCacheFactory.java#L28)
    - key is String, a task path
    - value is `org.gradle.language.nativeplatform.internal.incremental.CompilationState`, serialized with `CompilationStateSerializer`

The in-memory caches are a decorator for the underlying persistent cache. In-memory cache decorators are created in InMemoryTaskArtifactCache and live as part of that instance which is in the `GlobalScopeServices`. Persistent cache instances are typically part of the [`TaskExecutionServices`](https://github.com/gradle/gradle/blob/ef18b81c9ee03cf58a49e69a3c6f5abb4557d8f7/subprojects/core/src/main/groovy/org/gradle/internal/service/scopes/TaskExecutionServices.java#L73-L81) which are in `GradleScopeServices` (instances live for the duration of a single build execution). In-memory cache decorators are only used in the Gradle daemon process.

### Relationship of `fileSnapshots` and `taskArtifacts` caches

The `taskArtifacts` cache contains `TaskHistory` entries. Each `TaskHistory` can contain multiple task executions (`CacheBackedTaskHistoryRepository.LazyTaskExecution`). Each `LazyTaskExecution` contains it's input and output filesnapshots (`FileCollectionSnapshot`).
Input and output instances are lazily loaded from the `fileSnapshots` cache when the `TaskHistory` instance gets loaded in an incremental build.

The life-time of the `TaskHistory` instance can be seen in the
[`SkipUpToDateTaskExecuter.execute`](https://github.com/gradle/gradle/blob/3a0cfd6ac94eb9db4c7884c46ef4f9e973dca114/subprojects/core/src/main/groovy/org/gradle/api/internal/tasks/execution/SkipUpToDateTaskExecuter.java#L66) method. The in-memory `TaskHistory` instance is persisted in the call to it's `update` method. This call originates from the `TaskArtifactState.afterTask` call in the `SkipUpToDateTaskExecuter.execute` method.

When the `TaskHistory` gets persisted, it adds the current task execution to the list of executions and limits the number of executions to 3 by removing any additional task executions. When the task execution (`LazyTaskExecution`) gets persisted, the input and output file snapshots get persisted in the `filesnapshots` cache. This serves at least 2 purposes: the filesnapshot don't have to be loaded when the `TaskHistory` is loaded. It also prevents updating the input and output filesnapshots to the persistent storage when the `TaskHistory` instance gets updated. When the `TaskHistory` instance gets updated, all data gets re-serialized to disk.

### Other caches

- `HashClassPathSnapshotter` uses an unbounded cache instantiated in [`GlobalScopeServices`](https://github.com/gradle/gradle/blob/56404fa2cd7c466d7a5e19e8920906beffa9f919/subprojects/core/src/main/groovy/org/gradle/internal/service/scopes/GlobalScopeServices.java#L211-L212)

## Story: Clean up stale output files from Java compilation after Gradle version change

Tasks that define inputs and outputs to support incremental build functionality might leave behind stale output files in case task history does not exist. This situation might arise
if the build changes the Gradle version or if `.gradle` is deleted manually _and_ one or many of the inputs have been changed. Any tasks using those outputs as inputs for further processing are affected as
well.

The issue is documented by [issue #821](https://github.com/gradle/gradle/issues/821). This story only addresses stale output files created by Java compilation.

**Example:**

A user compiles two Java source files, A and B, with Gradle version X via `compileJava`. The produced output are the class files C and D. Those class files might be packaged into
 a JAR file via the `jar` task.

Let's assume the user upgrades the version of Gradle to Y _and_ one of the input files is moved or deleted e.g. B is deleted (which might happen upon a branch change or refactoring of the code). 
Gradle does not have a task history for the task `compileJava` as `.gradle` directory is based on the used Gradle version e.g. `./gradle/3.1` vs. `.gradle/3.2`. As a result `compileJava` would compile
source file A but not B as it doesn't exist anymore. The output directory of the compilation task would still contain A and B which leads to an incorrect result.

The goal of this story is to 1) detect this situation and 2) clean up any stale output.

### User visible changes

Gradle detects and removes stale Java class files after a Gradle version change. Further processing of output files (e.g. by the `jar` task) does not result in faulty behavior.

### Implementation

- Establish a registry implementation that allows for registering strategies with the purpose of cleaning up outputs produced by previous builds.
    - The registry is not specific to tasks. It's rather a global concept that allows for registering clean up strategies for any "thing" in Gradle.
    - The registry is going to be an internal concept and is not going to be exposed via a public API.
    - The registry needs to be instantiated by Gradle's global service registry. 
    - When a build is executed, the registry is contacted and provides all registered strategies.
    - If any of the strategies indicate that a clean up is needed then delete all registered outputs.

<!-- -->

    public interface BuildOutputCleanupStrategy {
        /**
         * Determines if cleanup is needed based on provided Gradle instance.
         */
        boolean needsCleanup(Gradle gradle);
    }

    public interface BuildOutputCleanupRegistry {
        /**
         * Registers clean up strategy.
         */
        void registerStrategy(BuildOutputCleanupStrategy strategy);
        
        /**
         * Returns all registered clean up strategies.
         */
        List<BuildOutputCleanupStrategy> getStrategies();
        
        /**
         * Registers outputs to be cleaned up.
         */
        void registerOutputs(File... outputs);

        /**
         * Registers outputs to be cleaned up. An output can be file or directorie.
         */
        void registerOutputs(List<File> outputs);
        
        /**
         * Returns all registed outputs.
         */
        List<File> getOutputs();
    }

- Provide an implementation for a clean up strategy that identifies if task history is available.
    - Create an instance of the implementation in Gradle's global service registry.
    - Persist the Gradle version to a cross-version file [similar to what we have for `buildSrc`](https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/initialization/buildsrc/BuildSourceBuilder.java#L103).
    - Only initiate a clean if one of the following conditions is encountered:
        - If the cross-version file does not exist.
        - If the current Gradle version used by the build is different from the one persisted in the cross-version file.
    - Stale files produced by older versions of Gradle (that do not know about the registry implementation) are out of scope.

<!-- -->

    public class PersistedGradleVersionCleanupStrategy implements BuildOutputCleanupStrategy {
        @Override
        public boolean needsCleanup(Gradle gradle) {
            // identify existing task history
        }
    }

- Clean up registry concept applies to output of Java compilation.
    - Register the task history clean up implementation with the clean up registry in the `JavaPlugin`. 
    - As output define outputs of the `main` and `test` source set.
    - Add classes output directory via `sourceSet.getOutput().getClassesDir()`.
    - Add resources output directory via `sourceSet.getOutput().getResourcesDir()`.
- Delete outputs if needed.
    - Retrieve all clean up strategies as one of the last things during the configuration phase.
    - Delete outputs for a strategy if needed. If output does not exist then skip. Render a message with log level `quiet` to the console that indicates operation.
    - The delete operation is not represented by a task in task graph.
    - Failure to delete an output (e.g. due to file locking) will render a helpful warning message but not fail the build.
- Cleaning the output works the same way when executing in parallel mode (via `--parallel` command line option or setting in `gradle.properties`) with and without
the use of configuration on demand (via `--configure-on-demand` command line option or setting in `gradle.properties`).
- As dogfooding exercise also use the registry for cleaning up `buildSrc` if task history is out-of-date.
- Out of scope for this story are the following aspects:
    - Do not apply the concept to outputs of custom source sets created by a user.
    - Outputs generated by renamed or removed tasks are not taken under consideration.

### Test coverage

- Task history for Gradle version exists. No need to remove outputs.
- Task history does not exist for Gradle version.
    - Registered classes and resources output directories are deleted including all contents.
    - Expect a message rendered to the console information the user if run with `quiet` log level.
    - A locked output file does not delete the output directory and renders an appropriate error message.
    - The `GroovyPlugin` and `ScalaPlugin` work in the same way as they apply the `JavaPlugin`.
    - The same behavior applies to `buildSrc`.
- All test cases work with a multi-project build executed in parallel mode and configure on demand.

## Story: Expose stale output clean up concept as public API

This story is a continuation of the previous story. There are use cases that would require a user to register use-defined outputs for clean up. 

Some examples:

- A build defines custom source sets. Each of these source sets would create a compilation task need writes to output directories.
- A build incorporates code generation logic or uses annotation processing. Outputs are likely written to custom directories.

Gradle cannot anticipate outputs defined by custom logic that are meant to be cleaned up. For supporting these uses cases, the existing API would
have to be exposed as public API.