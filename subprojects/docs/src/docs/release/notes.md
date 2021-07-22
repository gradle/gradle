The Gradle team is excited to announce Gradle @version@.

This release adds [several usability improvements](#usability), such as toolchain support for Scala projects, and [improves build cache hits](#performance) between operating systems. 

There are also several [bug fixes](#fixed-issues) and changes to make the [remote HTTP build cache more resilient](#http-build-cache) when encountering problems.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<!-- 

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details 

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv



^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

<a name="http-build-cache"></a>
## Remote build cache changes

### Automatic retry of uploads on temporary network error

Previously, only load (i.e. GET) requests that failed during request transmission, after having established a TCP connection, would be automatically retried.
Now, store (i.e. PUT) requests are also retried.

This prevents temporary problems, such as connection drops, read or write timeouts, or low-level network failures, to cause cache operations to fail and disable the remote cache for the remainder of the build.

Requests will be retried up to 3 times. If the problem persists, the cache operation will fail and the remote cache will be disabled for the remainder of the build.

### Follow redirects by default

Redirect responses are now followed by default with no additional configuration needed.

This can be leveraged to gracefully migrate to new cache locations, utilize some form of request signing to read to and write from other systems, or reroute requests from certain users or geographies to other locations.

For more information on the effect of different types of redirects, consult the [User manual](userguide/build_cache.html#sec:build_cache_redirects).

### Use Expect-Continue to avoid redundant uploads

It is now possible to opt-in to the use of [Expect-Continue](https://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html#sec8.2.3) for upload requests.

This is useful when build cache upload requests are regularly rejected or redirected by the server,
as it avoids the overhead of transmitting the large file just to have it rejected or redirected.

Consult the [User manual](userguide/build_cache.html#sec:build_cache_expect_continue) for more on use of expect-continue.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
