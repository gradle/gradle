
## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Extensions now have a public type

Extensions can now be registered in `ExtensionContainer`s with an explicit public type.
 This allows plugin authors to hide their implementation type from build scripts and
 allow `ExtensionContainer`s to expose a schema of all the registered extensions.

For example, if you have a `FancyExtension` type, implemented by some `DefaultFancyExtension` type, here is how
 you should register it:

    // If you want to delegate the extension instance creation to Gradle:
    project.extensions.create FancyExtension, 'fancy', DefaultFancyExtension

    // Or if you need to create the extension instance yourself:
    FancyExtension fancyInstance = new DefaultFancyExtension(...)
    project.extensions.add FancyExtension, 'fancy', fancyInstance

<!--
### Example new and noteworthy
-->

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
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Core extensions should be addressed by their public type

Now that extensions implementation type is hidden from plugins and build scripts that extensions can only be
 addressed by their public type, some Gradle core extensions are not addressable by their implementation type anymore:

- `DefaultExtraPropertiesExtension`, use `ExtraPropertiesExtension` instead
- `DefaultDistributionContainer`, use `DistributionContainer` instead
- `DefaultPublishingExtension`, use `PublishingExtension` instead
- `DefaultPlatformContainer`, use `PlatformContainer` instead
- `DefaultBuildTypeContainer`, use `BuildTypeContainer` instead
- `DefaultFlavorContainer`, use `FlavorContainer` instead
- `DefaultNativeToolChainRegistry`, use `NativeToolChainRegistry` instead

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->
 - [Attila Kelemen](https://github.com/kelemen) - Project.file supports java.nio.file.Path instances ([gradle/gradle#813](https://github.com/gradle/gradle/pull/813))
 - [Kevin Page](https://github.com/kpage) - Eclipse resource filters ([gradle/gradle#846](https://github.com/gradle/gradle/pull/846))
 - [Jacob Beardsley](https://github.com/jacobwu) - Fix `NullPointerException` when excluding transitive dependencies in dependency configuration ([gradle/gradle#1113](https://github.com/gradle/gradle/pull/1113))
 - [Eitan Adler](https://github.com/grimreaper) - Minor tests cleanup ([gradle/gradle#1219](https://github.com/gradle/gradle/pull/1219))
 - [Vladislav Soroka](https://github.com/vladsoroka) - Allow environment variables to be configured through Tooling API ([gradle/gradle#1029](https://github.com/gradle/gradle/pull/1029))
 - [Björn Kautler](https://github.com/Vampire) - Update user guide for build comparison about supported builds ([gradle/gradle#1266](https://github.com/gradle/gradle/pull/1266))

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
