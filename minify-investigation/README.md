# Minification timings

Data behind the prototype in the previous commit. Not meant to be merged.

Setup: 12 vCPU Linux VM, Temurin JDK 25, ProGuard 7.10.0, master at `78784df621a`.
Libraries: kotlin-compiler-embeddable 2.4.21, bcprov-jdk18on 1.85, fastutil 8.5.2, jnr-constants 0.10.4.
All timings are single runs.

## In the build

Duration of the `Executing Minify` build operations, from `-Dorg.gradle.internal.operations.trace`, with a
cold transform cache. All four transforms ran in the same build, concurrently.
See `logs/gradle-transforms-*.txt`, and `tools/minified.init.gradle.kts` for the task that resolves them.

| Library                     | ProGuard (master) | ASM (prototype) |
|-----------------------------|------------------:|----------------:|
| kotlin-compiler-embeddable  |             40.3s |            5.6s |
| bcprov-jdk18on              |             16.5s |            1.3s |
| fastutil                    |             14.8s |            1.8s |
| jnr-constants               |              7.6s |            0.3s |

## Outputs

| Library                    |  Input (bytes) | ProGuard (bytes) | ASM (bytes) | Classes (ProGuard / ASM) | Unresolved refs (ProGuard / ASM) |
|----------------------------|---------------:|-----------------:|------------:|-------------------------:|---------------------------------:|
| kotlin-compiler-embeddable |     58,593,291 |       46,603,358 |  48,020,842 |            23,474 / 23,474 |                  52,154 / 52,154 |
| bcprov-jdk18on             |     10,321,819 |        4,734,464 |   4,937,447 |              4,014 / 4,049 |                          22 / 1 |
| fastutil                   |     23,504,030 |        1,590,187 |   1,687,684 |                1,305 / 952 |                           0 / 0 |
| jnr-constants              |      1,579,612 |           34,403 |      41,461 |                    22 / 23 |                           0 / 0 |

Unresolved references are from `jdeps --multi-release 25 -verbose:class` on the jar alone. For the Kotlin compiler
they are its references to its own dependencies. In both versions, no method is missing a `StackMapTable`, and
no `LocalVariableTable` is left (`tools/Compare.java`, `tools/Lvt.java`).

Where the sizes differ:

- **Kotlin compiler:** ProGuard's obfuscator, although every name is kept, also blanks the parameter names passed
  to `Intrinsics.checkNotNullParameter` (`"seq"` becomes `""`), drops the message argument of 3,229
  `checkNotNull` calls, and clears `ACC_BRIDGE` on 156 methods (`tools/DiffLines.java`).
- **bcprov:** ProGuard also removes unused members of the classes it keeps only because they are referred to,
  which drops 36 more classes. The prototype empties `loadPQCKeys()` instead of removing its call, so
  `PQCObjectIdentifiers` and 21 of the dangling references to `pqc.jcajce` go away. The one left is in
  `BouncyCastleProvider.getPublicKey` (Picnic), and both versions have it.
- **fastutil:** ProGuard keeps 900 classes of the core packages even when no keep rule matches anything
  (`-keep class does.not.Exist`), cause not found. The prototype keeps fewer classes, but whole ones.

Both versions drop all 1,584 `META-INF/versions/**` entries of bcprov, while its manifest still says
`Multi-Release: true`: the implementations for Java 11, 15, 17 and 25 (XDH and EdDSA keys, ML-KEM SPIs,
the KDF SPI) are not in the distribution.

## ProGuard alone

ProGuard run outside Gradle on the rules `Minify.minifyRules` generates (`rules/`), with timestamped `-verbose`
output (`logs/proguard-*.log`). It produces the same bytes as in the build.

| Phase            | Kotlin compiler | bcprov | fastutil | jnr-constants |
|------------------|----------------:|-------:|---------:|--------------:|
| Reading input    |            3.0s |   2.4s |     2.7s |          2.2s |
| Initializing     |            3.5s |   0.5s |     1.5s |          0.2s |
| Shrinking        |               - |   0.7s |     1.0s |          0.0s |
| Optimizing       |               - |   3.4s |        - |             - |
| Obfuscating      |            8.7s |   0.6s |     0.3s |          0.1s |
| Preverifying     |            9.8s |   1.0s |     0.3s |          0.0s |
| Writing output   |            4.9s |   0.5s |     0.2s |          0.0s |
| **Total**        |       **31.2s** |  **9.9s** | **6.7s** |      **2.7s** |

Start-up, marking and inlining subroutines make up the rest of each total. Most of "Reading input" is the JDK and the Kotlin compiler, which are passed as library jars to every library.

Variations tried:

| Library         | Change                                                         | Time  | Output |
|-----------------|----------------------------------------------------------------|------:|--------|
| Kotlin compiler | `-dontobfuscate`                                               | 21.1s | frames dropped from 59,430 methods |
| Kotlin compiler | `-dontpreverify`                                               | 18.6s | frames dropped from 59,430 methods |
| Kotlin compiler | `-dontobfuscate -dontpreverify`                                |  6.9s | valid, but keeps local variable tables: 52.1MB |
| Kotlin compiler | `-keep class ** { *; }` instead of `-dontshrink`, plus the above | 14.1s | frames dropped from 59,430 methods |
| bcprov          | no Kotlin compiler as a library                                |  8.8s | same classes |
| bcprov          | ... plus `-dontobfuscate -optimizations code/removal/simple`   |  4.9s | the call to `loadPQCKeys()` stays: `NoClassDefFoundError` |
| bcprov          | ... plus `-dontobfuscate -optimizations code/removal/advanced` |  5.9s | valid, 4.76MB |
| Kotlin compiler | ASM pass instead (`tools/Strip.java`)                          |  3.8s | valid, 48.3MB |
| bcprov          | ASM pass instead (`tools/BcMin.java`)                          |  0.8s | valid, 4.94MB |

## Reproducing

`WORK` is a directory with the libraries in `jars/`, ProGuard and its dependencies in `jars/pg/`, ASM 9.8 in
`jars/asm/`, and the JDK classes in `jars/platform-25.jar` (`java tools/Platform.java jars/platform-25.jar`).

    WORK=... tools/run.sh rules/kotlin.pro -verbose
    ./gradlew --init-script minify-investigation/tools/minified.init.gradle.kts printMinified -Dorg.gradle.internal.operations.trace=/tmp/trace
    python3 minify-investigation/tools/transform_times.py /tmp/trace-log.txt
