
Currently there are some inefficiencies in the implementation of incremental build.

### Unnecessary directory scanning 

When a task is about to be executed, Gradle performs an up-to-date check. To do this Gradle scans the declared inputs to create a _snapshot_, which 
is essentially a set of the file paths and their associated hashes. It also scans the declared outputs to create a snapshot of the outputs.
The snapshots are compared against the snapshots from previous task execution. If the snapshots are the same, the task is considered up-to-date and skipped.

If not up-to-date, the task actions are executed. Typically these actions will iterate over the inputs and produce the outputs.

After the actions have run, the task outputs are scanned again and another output snapshot produced. This is compared against the output snapshot taken prior to the 
task actions, to determine which files the task produced. 
Finally, the snapshot of the inputs prior to the task action and outputs after the task action is persisted for next execution.

There are several problems with the implementation of this algorithm:

1. When the same directory is declared multiple times as an input or output, it is scanned multiple times. For example, the test classes directory is used twice as an input 
for the test task, once in the classpath definition and once as the set of candidate to use to detect test classes. Instead, a directory should be scanned exactly once during 
the up-to-date check, regardless of how it is referenced.
2. When a task is out-of-date, the up-to-date check and the task action both scan the same inputs, and in some cases, the same outputs. Instead, the task action should
reuse the result of scanning the inputs. For reliability, the output scan should not be reused, as Gradle has no insight into whether the task action has done anything with those
outputs at the time the scans is requested.
3. When a task uses the output of another task as its input, the generated files are scanned once at the end of the producer task, and again in the up-to-date check of the consumer task.
Instead, the result of scanning the outputs of the producer task should be reused during the up-to-date check of the consumer task.

For #1 and #2, we can assume that the inputs of the task will not change as the task is executing, as this is the contract of task dependency ordering. However, for
item #3, we can reuse the result only when nothing has run that is likely to modify the files. There are several cases where the result should be invalidated: 

- A task that has overlapping outputs has run.
- A task that does not declare its inputs and outputs has run.
- The result was calculated in an earlier build and these files are not being watched for changes.

### Inefficient caching

Currently, Gradle maintains 3 global caches:

- A mapping from file path to (file size, file last modified time, file contents hash). This is used to avoid hashing a file when its hash is already known.
- A mapping from task path to task history. The task history contains the properties of task execution and references the input and output snapshots by an id.
- A mapping from snapshot id to snapshot. A snapshot is essentially a set of (file path, file type, file content hash) tuples. This is separated from the task history
  to avoid loading large snapshots that aren't required, for example when the task is out-of-date. More on this below.

Each of these caches is held in memory and is backed by a persistent store.

Caches are maintained only for the most recent 2 builds, where `buildSrc` counts as a build. This means that switching between builds will cause all of this cached state to
be discarded.

To produce a snapshot, Gradle scans the directories to produce a stream of (file path, file type, file last modified time, file size) tuples. For each tuple, the
global cache is used to calculate the file contents hash. The result is a set of (file path, file type, file content hash) tuples, or the snapshot.

To do an up-to-date check, Gradle produces snapshots of the declared inputs and outputs, then uses the global cache to fetch the task history. If the task properties match, 
Gradle loads the input snapshot from the global cache and compares. If the input snapshots match, Gradle loads the output snapshot from the global cache and compares. 

Using the global cache to fetch the hash for each file is expensive, due to the constraints that allow the caches to be safely shared by multiple processes. 
There are improvements we could make here, but a global cache lookup is going to always be somewhat costly. 
There is another source for this information, which is the most recently calculated snapshot for the same directory, which is immutable and so much cheaper to share. 
The global cache would be used in the case of a miss.

The in memory cache of snapshots is useless. The snapshot object is attached to the task history object, which is also cached in memory, and the snapshots are only reached 
via the task history object.

Loading snapshot objects from the persistent store is expensive, and wasted work when the snapshot ends up the same as the one that is being compared with. 
This could be avoided by first comparing a hash of the snapshot stored in the task history object, against the hash of the calculated snapshot. 
Only when the hashes are different would the snapshot need to be loaded, to generate diagnostics (if required).

## Potential improvements

A potential approach would be to maintain an additional in-memory cache:

- (dir, patterns) to a set of (relative-path, file type, file last modified time, file size, file content hash) tuples, or _tree snapshot_

This cache would contain the most recently calculated tree snapshot for the given directory. When a directory is to be scanned or a snapshot calculated, the matching
entry from this cache can be used instead of scanning the directory.

The entries in this cache would be invalidated as tasks that produce outputs run. It would be discarded at the end of the build.
It could potentially be reused across a build session, and invalidated on file change events. A simple initial implementation could invalidate the cache when _any_ task 
actions are run. This would work well for mostly up-to-date builds.

A second in-memory cache could be added:

- (tree snapshot hash) -> tree snapshot

This would be used to share snapshots across task history entries and avoid loading snapshots when a copy is already loaded into memory. The entries of this cache would
not be invalidated.

### Candidate changes

- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Use a hash to short circuit loading task input or output snapshots into heap, and to share snapshots between tasks
- Parallel scanning of directory trees (in the worker pool)

### Stories

### Incremental build reuses directory scanning results in simple cases

Improve performance when a task takes files as input, that are produced by another task or tasks, by reusing the directory scanning results produced when scanning the outputs of the tasks.

Initially start with reuse in simple, but common, cases, such as when everything in a directory tree is to be scanned.

Add integration test coverage.

##### Goal of changes

There are two main possibilities to reduce directory scanning:
- when task output gets snapshotted, directory scanning results should get reused in the following tasks that use the output
  - the task output snapshotting always scans the whole output directory without any pattern
  - the task input might use a pattern to filter the results. The directory scanning results of the output snapshotting should be reused also in this case. 
- when task input gets snapshotted, directory scanning results should get reused when the task input gets read

This story doesn't implement all of this. This is just to clarify the direction which this story takes the implementation.


##### Implementation notes

- Implementation should change `DefaultFileCollectionSnapshotter` to unpack the file collection to a backing set of file trees, then use an in-memory cache to cache visiting each individual directory tree.
- Simple invalidation strategy, such as invalidate everything when any task action runs. This can be improved later.
- Invalidate cache at the end of the build. 

#### Open issues
- reusing directory scanning results of an output snapshot without a pattern when the input is using a pattern.
- currently the simple cache invalidation strategy flushes the cache before each task execution. A directory scanning result will only get reused when the task that produces the input to a certain task preceeds the task that uses the output.


### Improvement: Split snapshotting to 2 phases to improvement performance

Currently a input or output snapshot is relatively heavy weight. 
It is possible to skip creating a full snapshot when nothing has changed compared to the previous snapshot.

##### Goal of changes

- when nothing has changed
  - improve performance of up-to-date checking 
    - by skipping creating a new full snapshot which also requires the previous snapshot
      - skipping loading previous snapshot from persistent storage
        - also saves memory since there is less use for the in-memory cache of snapshots

##### Implementation notes

- Split snapshotting to 2 phases: pre-check and snapshot.

- In the pre-check phase a single integer valued hash is calculated.
- The hash is calculated from a list of sorted file information.
  - For files, the hash is updated with the file name, size and modification time
  - For directories, the hash is updated with the file name
- The pre-check hash doesn't contain any hashing of the file contents. 
  - No changes will be noticed if the file size or modification time doesn't change. 
    - This is also the current behaviour of Gradle since file content hashes are cached based on file name, size and modification time.
- if the pre-check hash is same as pre-check for previous snapshot, it is considered up-to-date and the actual file snapshot doesn't have to be loaded and no new snapshot has to be created.
- if the pre-check hash is different and snapshot up-to-date check doesn't  contain changes (false positive), the persisted hash gets updated
  - this might happen when file modification times are different, but content is the same
- fileSnapshots in-memory cache can now use weak references for values.
  - loaded fileSnapshots will get GCd under memory pressure which works fine with the new up-to-date checking.

### Improvement: Change snapshot persistence to use shared tree snapshots

There is currently duplication in the snapshots of outputs and inputs of dependent tasks.
Change persistence of input and output snapshots to reference shared tree snapshots.
An input or output snapshot might be composed of multiple tree snapshots.

#### Implementation notes

Output directory snapshotting is special currently. it handles the case
where the output directory is shared. The current algoritm is:
  - snapshot before task executions
  - snapshot after task executions
  - save snapshot for the files created by the task
    - uses previous saved snapshot as template for list of files
    - adds any changed or new files to the list of files created by the task
Output directory snapshotting uses updateFrom and applyAllChangesSince methods on
FileCollectionSnapshot to create the snapshot. This complicates the implementation.
It should be replaced with a single method that is optimized for the use case of creating an output snapshot.
This method should be added to OutputFilesCollectionSnapshotter.

Introduce new concepts `VisitedTree` and `TreeSnapshot` for structuring the tree snapshot sharing solution.
The `CachingTreeVisitor` implemented in the previous story "Incremental build reuses directory scanning results in simple cases" should be modified to return `VisitedTree` instances.

```java
public interface VisitedTree {
    Collection<FileTreeElement> getEntries();
    TreeSnapshot maybeCreateSnapshot(FileSnapshotter fileSnapshotter, StringInterner stringInterner);
    boolean isShareable();
}
```
A `TreeSnapshot` can be created from a `VisitedTree`. The `VisitedTree` instance will keep state of a created `TreeSnapshot` instance and will only create it if it hasn't been done. 

Furthermore, a `TreeSnapshot` instance has methods for accessing the stored snapshot information and a method for storing the content. The `maybeStoreEntry` method persists the entry only if it hasn't been done before.
```java
public interface TreeSnapshot {
    boolean isShareable();
    Collection<FileSnapshotWithKey> getFileSnapshots();
    Long getAssignedId();
    Long maybeStoreEntry(Action<Long> storeEntryAction);
}
```

#### Open issues

##### Memory consumption increased for incremental builds

For large builds, the current visited tree cache invalidation strategy is insufficient.
Currently the cache is flushed when a task gets executed.
When all tasks are up-to-date, no tasks are executed and the visited trees pile up in memory because no cache flushing
is happening. This increases the build-time memory consumption of current builds.
  1. The visited trees created in task output snapshotting should be removed from the cache after the downstream tasks
have been executed.
  2. Only those input file collections that are inputs for several tasks should be kept in cache until all
tasks using the inputs have executed.

#### Storing output and input snapshots isn't yet shared in all cases

The problem is that the output doesn't have a filtering pattern and 
the input usually does. There is a solution in place to refer to the 
snapshot directly when the filtered result is the same as the
unfiltered. However this usually doesn't happen since the unfiltered
result would also contain the directories and the filtered wouldn't.

### Improvement: Improve pre-check hash calculation performance

The pre-check hash calculation should calculate the pre-check hash for
each tree snapshot and combine the result of the tree snapshot hashes.
The result of the tree snapshot hashes can be cached and shared.

### Improvement: Add caching for visiting configurations

Configurations can be considered immutable after they have been resolved. File visiting of configurations should be cached
and shared.

### Improvement: Use relative paths in Jdk7DirectoryWalker and in snapshots

Currently the snapshot uses absolute paths. Absolute paths contain a lot of redundant information.
- Absolute path of root + relative path
  - change in directory scanning to create File instances on demand
    - Path.toFile relatively expensive
- TreeSnapshot can hold the root information

### Incremental build reuses directory scanning results in most cases

The story implements cache invalidation strategy that makes it possible to reuse directory scanning results across multiple task executions. Besides the cache invalidation strategy change, there should be a solution for reusing a directory scanning result when the input is using a pattern to filter the results. Currently it's a common case that the output filesnapshot will scan the output directory with the _all_ pattern, but the input will be using a pattern to filter the results.

### Incremental build reuses directory scanning results for task inputs

The story adds reusing of directory scanning results for input file collections used in task inputs.
Currently the directories are scanned again when the files are listed in task execution.

### Improvement: minimize File.isDirectory, File.lastModified and File.length calls for resolved artifacts

Currently there are a lot of file system operations involved when the file metadata for  classpath artifacts is looked up in snapshotting. It should be safe to cache all lookups for artifact files that are stored under the `fileStoreDirectory` for the duration of the build.

### Incremental build avoids snapshotting duplicate task input or output directories in simple cases

Sometimes a task may accept a given directory as input or output multiple times. The `Test` task is an example of this.

Currently, such directories will be scanned multiple times. Instead, each directory should be scanned once when calculating the input or output snapshots for a task.

The implementation of this is made more complex when different patterns or specs are used. For this story, simply merge those file trees with the same base directory
and where one of the file trees has an 'accept everything' spec.

Add integration test coverage.
