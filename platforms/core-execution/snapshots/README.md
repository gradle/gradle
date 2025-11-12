# Snapshotting and Fingerprinting

> [!TIP]
> For general information on the execution platform and a glossary of the main terms in the [platform readme](../README.md).

> [!NOTE]
> The terms "snapshot" and "fingerprint" both refer to similar concepts.
> In essence they are both _fingerprints_ of a piece of data, but "snapshots" represent the verbatim state of the data,
> while "fingerprints" represent its normalized state according to its intended use.
> There _must_ be a better way to distinguish between these concepts of "non-normalized fingerprints" vs. "normalized fingerprints"
> but so far we haven't found one that does not result in such overly-verbose terms.
> So "snapshots" and "fingerprints" they remain until a better option is discovered.

## Snapshotting

A **snapshot** is a terse representation of the state of some data that we can use to check if the corresponding data has changed at all.
We use cryptographic hashes to represent the state of the data.

![File-system snapshotting](File-System%20Snapshotting.drawio.svg)

Snapshots for scalar (non-file) inputs are captured by the `ValueSnasphotter` as `ValueSnapshot` objects.
`FileSystemAccess` can be used to acquire snapshots of individual file-system locations as `FileSystemLocationSnapshot`s.
A `FileSystemLocationSnapshot` contains the absolute path of the file-system object along with the hash of its contents.

File collections are snapshotted by the `FileCollectionSnapshotter` as `FileSystemSnapshot`s (these can have multiple roots while `FileSystemLocationSnapshot`s have only one root).

## Hashing

We use the MD5[^md5-safety] cryptographic[^non-crypto-hashes] hash algorithm in calculating hashes from file contents and scalar inputs for snapshots and fingerprints.
We also use the same algorithm to calculate identifiers like the build cache key of a unit of work.

[^md5-safety]: MD5 has long been compromised from a security standpoint, but our goal is not to protect against malicious intent.
To avoid accidental collisions, MD5 is still sufficiently strong.
We use it because it is very fast and universally available on the JVM, and it requires only 16 bytes per hash, which helps conserve memory compared to SHA1 (20 bytes) or SHA256 (32 bytes).
Other cryptographic hashes could be used, and making the hashing configurable is sensible.
The [BLAKE family](https://en.wikipedia.org/wiki/BLAKE_(hash_function)) of cryptographic hash functions looks promising for performance, though more research is needed.

[^non-crypto-hashes]: While non-cryptographic hash algorithms like [xxHash](https://xxhash.com) or [MurmurHash](https://en.wikipedia.org/wiki/MurmurHash) are significantly faster, they lack the astronomical collision resistance we require.

We use [Merkle trees](https://en.wikipedia.org/wiki/Merkle_tree) to generate a single hash representing complex inputs.
(See `MerkleDirectorySnapshotBuilder`.)
This involves hashing together hashes of individual components to create a hash for the whole.

## Virtual File-System

The execution engine regularly needs information about the state of the file-system in the form of snapshots taken of files and directories.
It would be inefficient to re-read file hierarchies every time a snapshot of a part is needed, so file-system state is cached in the virtual file-system (VFS). 

![Virtual file-system](Virtual%20File-System.drawio.svg)

The VFS only stores information about the directory hierarchy of the running build.[^vfs-previous-builds]
This means that we do not retain information about file-system objects outside the root directory of the build (or that of any included builds).

[^vfs-previous-builds]: Data about previous builds can also be retained for a while.

The VFS is stored in an efficient sparse tree data structure using `FileSystemNode`s.
This data-structure allows for reusing already taken snapshots when a parent directory's snapshot is 
requested.
It also supports storing filtered snapshots (e.g. when only `*.java` files are requested from a directory hierarchy).

The VFS is not exposed directly; the entry point is via `FileSystemAccess`.
This service has multiple `read()` methods to acquire file and directory snapshots.

### Change Tracking

For the VFS to correctly cache file-system state care must be taken to invalidate parts of the VFS before the file-system is modified.
It is assumed that during a running build changes to file-system areas tracked by the VFS are always invalidated.
So code modifying the file-system should either be wrapped in `FileSystemAccess.write()`, or a call to `FileSystemAccess.invalidate()` should be made before the changes are enacted.

![File-system watching](File-System%20Watching.drawio.svg)

#### File-System Watching

To track modifications between builds, the VFS **watches the file-system** for changes on supported platforms.
When a modification happens, any parts of the VFS data that might be out-of-date is discarded.

When file-system watching is not available, we discard all VFS data collected during the build.

The VFS and the execution engine in general does not distinguish between content available directly and content accessed through symlinks.
However, file-system watching does not notify of changes happening to symlinked content reliably.
Because of this we discard content accessed via symlinks from the VFS at the end of the build even if file-system watching is available.

#### Caching File Hashes

Actual file content hashing is handled by the `FileHasher`.
File hashes are cached in-memory and on disk.
These caches use the file modification date and size as a heuristic to tell if the file has been modified.
This means that even if we invalidate a file's snapshot in the VFS for some reason, we'd still be able to cheaply recreate the snapshot using the cached hash as long as the file remains unchanged in the actual file-system.

## Input Normalization

Not all inputs are relevant for a unit of work, and we can exploit this for better performance.
For example, in a Java compilation task, line endings in source files are irrelevant; whether files use `\n` or `\r\n` does not affect the compilation result.
Therefore, changes in line endings do not necessitate recompilation, and previously generated `.class` files can be considered up-to-date.
Ignoring such differences also allows reusing cached compilation results across different operating systems via the remote build cache.

To leverage this, the execution engine allows units of work to define how their inputs should be **normalized**.
Besides line-ending normalization for source files, Java compilation can also declare the compilation classpath to be normalized via _ABI extraction_.
This means input hashes are calculated based on the extracted ABI of the classpath rather than raw file contents.
This allows reuse of existing compilation results despite differences in dependencies, as long as those differences don't affect the ABI.

Input normalization is a key feature that significantly increases the chance of reusing existing results, boosting execution performance.

> [!NOTE]
> Input normalization is currently only available for file inputs.
> However, normalization for scalar inputs can be introduced if needed.

### Fingerprints

Normalized inputs are captured as **fingerprints** (aka `FileSystemLocationFingerprint`s).
These objects are similar to _snapshots,_ but instead of the _absolute path,_ they carry the _normalized path_ of the file-system object, and they capture the hash of the _normalized_ contents instead of the _verbatim._
For example, when calculating the fingerprint of a Java source file, we replace all line-ending characters with `\n` before hashing the contents.
We also capture only the relative path from the source directory.

For `FileCollection`s we store pef-file fingerprints in a `Map` indexed by the absolute path of the file.
This means that fingerprints do not retain their respective file-system structures like snapshots do.[^hierarchical-fingerprints]

[^hierarchical-fingerprints]: This is a historic choice and can be changed.
Much of the machinery around hierarchical file-system snapshots can be reused for this purpose.

### Normalization Strategies

A file collection fingerprint is created by fingerprinting the files and paths in a file system snapshot via a _fingerprinting strategy._ A fingerprinting strategy can normalize file input in multiple ways:

* **archive comprehension** – archives can be considered equivalent to directories, their elements can be traversed and metadata like file order, timestamps and permissions ignored

* **pattern filtering** – can restrict the scope to some pattern of files, like `\*.class` in Java compile classpath normalization (note that this filtering is in addition to any filtering applied to the input `FileCollection` itself; `FileCollection`-level filtering is already reflected in the file collection snapshot)

* **empty directory filtering** - can also ignore empty directories for fingerprints with `@IgnoreEmptyDirectories`

* **path normalization** – can disregard parts or the whole of the path of each file, e.g. `@PathSensitive(RELATIVE)` used on a task property

* **order normalization** – file order can be ignored by sorting the files in some reproducible order; root element order can be considered differently to descendant order, this is handled by `FingerprintHashingStrategy`

* **content normalization** – each file can be normalized individually, for example a `.properties` file on a JVM runtime classpath can be parsed to ignore comments etc.

#### Path Normalization

For most input file properties we use path normalization and ignore entry order completely. We use the `@PathSensitive` annotation on input properties for this with the following options:

* `ABSOLUTE` – does not ignore the path, uses the whole absolute path of each entry,

* `RELATIVE` – ignores the path from the roots,

* `NAME_ONLY` – we consider only the names of files,

* `IGNORE` – completely ignores the path.

#### Content Normalization

Gradle handles Java compile and JVM runtime classpaths specially.
For these we normalize contents and paths; root element order is not normalized, but the order of entries of JARs and class directories *is* ignored.

* `@CompileClasspath`

    * assumes files are ZIPs and treats them as directory hierarchies

    * keeps order of root elements, ignores order of entries in subtrees

    * filters `.class` files

    * uses the extracted ABI of each `.class` file to calculate the content hash

* `@Classpath` (i.e. runtime classpath)

    * assumes files are ZIPs and treats them as directory hierarchies; does this recursively

    * keeps order of root elements, ignores order of entries in subtrees

    * applies filter from `project.normalization.runtimeClasspath`

    * normalizes `.properties` and `META-INF` content according to `project.normalization.runtimeClasspath`

We also handle inputs marked with `@NormalizeLineEndings` (typically source code) by, well, normalizing line endings.

### Comparing Fingerprints

The fingerprinting strategy also determines a *fingerprint compare strategy* for the resulting file collection fingerprint. The compare strategy is used for comparing two file collection fingerprints for up-to-date checking.
