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

### Improvement to C/C++ incremental compilation

Gradle' incremental C/C++ compilation works by analysing and understanding the dependencies between source files and the header files that they include. Gradle can use this information to compile only those source files that are affected by a change to a header file. This means much faster builds. However, in some cases Gradle cannot analyze all of these dependencies, and in these cases it assumes all source files depend on all header files and recompiles all source files when any header file changes regardless of whether the change affects the compiler output or not. This also affects how well the Gradle build cache can be used to skip the compilation. None of this is good for performance.

In this release, Gradle's incremental C/C++ compilation is now able to understand most dependencies between source files and header files. This means much better incremental compilation and more build cache hits. And this means faster builds.

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

## Potential breaking changes

* Two overloaded `ValidateTaskProperties.setOutputFile()` methods were removed. They are replaced with auto-generated setters when the task is accessed from a build script.

<!--
### Example breaking change
-->

### HTTP build cache does not follow redirects

When connecting to an HTTP build cache backend via [HttpBuildCache](dsl/org.gradle.caching.http.HttpBuildCache.html), Gradle does not follow redirects any more, and treats them as errors instead.
Getting a redirect from the build cache backend is mostly a configuration error (e.g. using an http url instead of https), and has negative effects on performance.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

- [Theodore Ni](https://github.com/tjni) — Ignored TestNG tests should not throw an exception (gradle/gradle#3570)
- [James Wald](https://github.com/jameswald) — Introduce command line option for Wrapper task to set distribution SHA256 sum (gradle/gradle#1777)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
