# Introduction

**Note**: This document is a work in progress, and not define the final policy

This document describes the approach that the Gradle project takes to forwards and backwards compatibility across releases.

Our general goal is that a build that works with a given version of Gradle should continue to work with a future version of Gradle with
the same major version number. It does not need to work with any older version, or any other version with a different major version number.

However, we want to be able to continue to evolve certain features in a controlled way. And this means that in certain well-defined cases, a
particular build may not work with any version of Gradle other than the version it is currently using. These are described below.

# Evolving Gradle

The general approach is to give compatibility guarantees on a per-feature basis, rather than on Gradle as a whole, so that we can
evolve certain features while keeping other features stable:

* Each feature is either 'public' or 'internal'.
* A feature may additionally be marked as 'deprecated'.
* A feature may additionally be marked as 'incubating'.
* An internal feature may be added, changed or removed at any time, including between release candidates and the final release.
* A public feature may be added in the first release candidate of a release.
* A deprecated public feature may be removed in the first release candidate of a major release.
* An incubating public feature may be changed or removed in the first release candidate of a release.
* The deprecated tag can be added in the first release candidate of a release.
* The incubating or deprecated tags can be removed in the first release candidate of a release.
* A public feature may not be added, changed or removed at any other time, other than the above.

Some notes:

* Generally, we would not deprecate a feature until a non-incubating replacement is available, and we would not remove an
  incubating feature without a replacement of some kind. There may be some exceptions to this rule.
* New features will generally be marked as 'incubating' when they are added.
* We don't make changes to any public feature during the release candidate phase.
* Internal features are not intended to be used outside the Gradle project. You should avoid using them.
* Incubating features are intended to become public features. However, they are experimental in nature and may need to change
  based on feedback before they become public. If you use them, you need to be prepared for them to change between releases.
* Deprecated features are intended to be removed at some point. You can continue to use them until the next major Gradle
  version. You should start to migrate your build to the replacement features.
* All other public features are intended to be used, and we currently have no intention of removing them.

To replace a feature, we will:

* add an incubating replacement
* remove the incubating tag
* deprecate the old feature
* remove the old feature.

There will usually be some soak time between these steps. Between deprecation and removal of an old feature there will be a sufficient adoption time. The removal will only happen as part of a major release. Not necessarily all deprecated features will be removed as part of a major release. This is a case-by-case decision. This decision is influenced by many factors like time between deprecation and major release as well as the usage scenario of the deprecated feature.

To change a default behaviour, we will:

* add an incubating mechanism to choose between the new and old behaviour
* remove the incubating tag
* add a warning when the old default behaviour is used
* change the default and remove the warning
* optionally remove the flag and the old behaviour, via deprecation

Some additional details are available in the [DSL reference](http://gradle.org/docs/nightly/dsl/index.html#dsl-element-types)

# Open issues

Some open issues:

What constitutes a 'feature'? Some candidates:

* DSL
* API
* build environment settings
* command-line options
* default behaviours
* validation/error conditions
* Classloader visibility
* local file formats and locations (settings.gradle, wrapper properties file, init scripts, gradle.properties, etc).
* mappings to various repository types.
* various algorithms (dependency resolution, up-to-date check, searching for settings.gradle, etc).
* samples
* documentation
* URLs for remote resources (for the distribution and tooling API, documentation URLs, meta-data at services.gradle.org)
* the distribution format, artefact names, partitioning of Gradle into various jars.
* format of meta-data at services.gradle.org
* logging output
* heap usage
* performance

What do we do with bugs? For some bugs, we will need to deprecate and keep the old behaviour for a while. For example, our usage of
the packaging specified in a pom is a bug. Although this behaviour is 'wrong', it is nevertheless the default behaviour of Gradle.
In order to avoid breaking stuff, we will deprecate the usage of the packaging (i.e warn you in the cases where the old behaviour
and new behaviour will give you a different result).

How long to we keep a deprecated feature for? For some features, we can simply remove them at the next major release. For other
releases, we might keep the deprecated behaviour for several major releases.

What's the policy for features that work across versions? e.g. which Gradle versions should a given version of the tooling API
support? What about the wrapper? What about our meta-data formats and repository mappings? How about future features, such as the
'manage the daemons for all versions', or 'verify I can migrate my build', or 'clean my cache'?

How do we verify we haven't broken anything? We're going to break stuff. Do we add more test coverage? Is it the code reviewer's
responsibility to catch breakages? Do we wait for people to find the problems? Do we use more verification tools, such as API
comparison tools? All of the above?

When can we bend the rules above? It's all a trade off. Sometimes the cost of backwards compatibility won't be worth the cost of
breaking something. In general, we want to be very strict with backwards compatibility.
