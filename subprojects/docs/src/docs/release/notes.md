## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy

Think of every feature section as a mini blog post.

1) Make sure the release notes render properly: `./gradlew :docs:releaseNotes && open subprojects/docs/build/docs/release-notes.html`
  TIP: Continuous build is useful when working on release notes.
  NOTE: The markdown processor does not know GitHub flavored markdown syntax.

2) Explain why users should care.
  TIP: Avoid technical details with no indications how this impacts users.

3) Link to documentation or a blog post for more detailed information.

4) Show, don't just tell, if possible.
  NOTE: Totally fine to just link to an example that show the feature.
-->

### Provider API documentation

In this release, the Gradle team added a new chapter in the user guide documenting the [Provider API](userguide/lazy_configuration.html).

### Faster C/C++ compilation and builds

#### Build Cache Support

We introduced [experimental C/C++ caching support](https://docs.gradle.org/4.3/release-notes.html#experimental-task-output-caching-for-c/c++-compilation) in Gradle 4.3, but this feature was hidden behind an additional flag until we had fixed some underlying correctness issues.

In this release, we have fixed some underlying issues with calculating the correct build cache key for C++ compilation and have removed the special flag.  If you [enable the build cache](userguide/build_cache.html#sec:build_cache_enable), Gradle will try to reuse task outputs from C/C++ compile tasks when all inputs (compiler flags, source, dependencies) are identical, which can [greatly reduce](https://blog.gradle.org/introducing-gradle-build-cache) build times.

Please note that there are [some caveats](userguide/build_cache.html#sec:task_output_caching_known_issues_caveats) when using the build cache.  In particular for C++, object files that contain absolute paths (e.g., object files with debug information) are reusable and cacheable, but may cause problems when debugging.

#### Incremental Compilation

Gradle's incremental C/C++ compilation works by analysing and understanding the dependencies between source files and the header files that they include. Gradle can use this information to compile only those source files that are affected by a change in a header file. In some cases, Gradle could not analyze all of these dependencies and would assume all source files depend on all header files. Changes to any header file would require recompiling all source files, regardless of whether the compiler output would change or not. This also affected how well the Gradle build cache could be used to skip compilation.

In this release, Gradle's incremental C/C++ compilation is now able to understand most dependencies between source files and header files. This means incremental compilation will occur more often and builds are more likely to see cache hits.

### Arbitrary task property names

When registering task properties via the runtime API, property names are not required to be Java identifiers anymore, and can be any non-empty string.

### ANTLR task is now cacheable by default

When generating grammar sources with ANTLR, now the task's outputs are stored and retrieved from the build cache.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Deprecation of no-search-upward command line options 

The command line options for searching in parent directories for a `settings.gradle` file (`-u`/`--no-search-upward`) has been deprecated and will be removed in Gradle 5.0. A Gradle project should always define a `settings.gradle` file to avoid the performance overhead of walking parent directories.

### Deprecation of `TaskInputs` and `TaskOutputs` methods

Gradle 5.0 will remove support for the following methods:

- `TaskInputs.getHasInputs()`
- `TaskInputs.getHasSourceFiles()`
- `TaskInputs.getSourceFiles()`
- `TaskOutputs.getHasOutput()`

You can declare individual task properties and observe their values instead of calling these methods.

## Potential breaking changes

* Two overloaded `ValidateTaskProperties.setOutputFile()` methods were removed. They are replaced with auto-generated setters when the task is accessed from a build script.

* The `maven-publish` plugin now produces more complete `maven-metadata.xml` files, including maintaining a list of `<snapshotVersion>` elements. Some older versions of Maven may not be able to consume this metadata.

<!--
### Example breaking change
-->

### HTTP build cache does not follow redirects

When connecting to an HTTP build cache backend via [HttpBuildCache](dsl/org.gradle.caching.http.HttpBuildCache.html), Gradle does not follow redirects any more, and treats them as errors instead.
Getting a redirect from the build cache backend is mostly a configuration error (e.g. using an http url instead of https), and has negative effects on performance.

### Incubating `Depend` task removed

TBD - removed `Depend` task, this capability has been merged into the compile tasks.

### Gradle no longer tracks the canonical path of input file tree roots

Gradle was inconsistently handling symlinks when snapshotting inputs. For the root of a file tree it would take the canonical path into account. For individual files and contents of trees,
it would only consider the normalized path instead. Gradle will now always use the normalized path. This means that a task will not rerun if a directory is replaced with a symlink to the exact same contents.
If you have a use case that requires reacting to the canonical path of inputs, please open an issue and we'll consider an opt-in API that will canonicalize all inputs, not just tree roots.

### Project.file() no longer normalizes case

The `Project.file()` and related methods used to normalize the case on case-insensitive file systems. This means that the method would check whether any parts of the hierarchy of a given file already
existed in a different case and would adjust the given file accordingly. This lead to lots of IO during configuration time without a strong benefit. 

The `Project.file()` method will now ignore case and only normalize redundant segments like `/../`. It will not touch the file system.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

- [Nikita Skvortsov](https://github.com/nskvortsov) — Optimize generated IDEA dependencies (gradle/gradle#3460)
- [Theodore Ni](https://github.com/tjni) — Ignored TestNG tests should not throw an exception (gradle/gradle#3570)
- [James Wald](https://github.com/jameswald) — Introduce command line option for Wrapper task to set distribution SHA256 sum (gradle/gradle#1777)
- [zosrothko](https://github.com/zosrothko) — Restore Eclipse contribution instructions (gradle/gradle#3715)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
