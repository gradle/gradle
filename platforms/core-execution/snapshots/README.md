# Snapshotting and Fingerprinting

> [!TIP]
> For general information on the execution platform and a glossary of the main terms in the [platform readme](../README.md).

## Hashing

We use the MD5[^md5-safety] cryptographic[^non-crypto-hashes] hash algorithm currently in calculating hashes from file contents and scalar inputs for snapshots and fingerprints.

[^md5-safety]: MD5 has long been compromised from a security standpoint, but our goal is not to protect against malicious intent.
To avoid accidental collisions, MD5 is still sufficiently strong.
We use it because it is very fast and universally available on the JVM, and it requires only 16 bytes per hash, which helps conserve memory compared to SHA1 (20 bytes) or SHA256 (32 bytes).
Other cryptographic hashes could be used, and making the hashing configurable is sensible.
The [BLAKE family](https://en.wikipedia.org/wiki/BLAKE_(hash_function)) of cryptographic hash functions looks promising for performance, though more research is needed.

[^non-crypto-hashes]: While non-cryptographic hash algorithms like [xxHash](https://xxhash.com) or [MurmurHash](https://en.wikipedia.org/wiki/MurmurHash) are significantly faster, they lack the astronomical collision resistance we require.

We also use the same algorithm to calculate identifiers like the build cache key of a unit of work.

We use [Merkle trees](https://en.wikipedia.org/wiki/Merkle_tree) to generate a single hash representing complex inputs.
This involves hashing together hashes of individual components to create a hash for the whole.

## Virtual File-System

The execution engine regularly needs information about the state of the file-system in the form of snapshots taken of files and directories.
It would be inefficient to re-read file hierarchies every time there is a need, so file-system state is cached in the virtual file-system (VFS). 

![](Virtual%20File%20System.drawio.svg)

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
