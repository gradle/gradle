<meta property="og:image" content="https://gradle.org/images/releases/gradle-default.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:image" content="https://gradle.org/images/releases/gradle-default.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

### Publishing improvements

#### New `PublishingExtension.getSoftwareComponentFactory()` method

This release introduces a new method that exposes the [`SoftwareComponentFactory`](javadoc/org/gradle/api/component/SoftwareComponentFactory.html) service via the `publishing` extension, simplifying the creation of publishable components.
In many cases, a component is already present. 
For example, the bundled Java plugins already provide the `java` component by default.
This new method is especially useful for plugin authors who want to create and publish custom components without needing to depend on the Java plugins.

The following example shows how to use this new method to publish a custom component:

```kotlin
plugins {
    id("maven-publish")
}

val consumableConfiguration: Configuration = getAConfiguration()

publishing {
    val myCustomComponent = softwareComponentFactory.adhoc("myCustomComponent")
    myCustomComponent.addVariantsFromConfiguration(consumableConfiguration) {}
    
    publications {
        create<MavenPublication>("maven") {
            from(myCustomComponent)
        }
    }
}
```

#### New provider-based methods for publishing configurations

Two new methods have been added to [`AdhocComponentWithVariants`](javadoc/org/gradle/api/component/AdhocComponentWithVariants.html) which accept providers of consumable configurations:

- [`void addVariantsFromConfiguration(Provider<ConsumableConfiguration>, Action<? super ConfigurationVariantDetails>)`](javadoc/org/gradle/api/component/AdhocComponentWithVariants.html#addVariantsFromConfiguration(org.gradle.api.provider.Provider,org.gradle.api.Action))
- [`void withVariantsFromConfiguration(Provider<ConsumableConfiguration>, Action<? super ConfigurationVariantDetails>)`](javadoc/org/gradle/api/component/AdhocComponentWithVariants.html#withVariantsFromConfiguration(org.gradle.api.provider.Provider,org.gradle.api.Action))

These complement the existing methods that accept realized configuration instances.

With this new API, configurations can remain lazy and are only realized when actually needed for publishing.
Consider the following example:

```kotlin
plugins {
    id("base")
    id("maven-publish")
}

group = "org.example"
version = "1.0"

val myTask = tasks.register<Jar>("myTask")
val variantDependencies = configurations.dependencyScope("variantDependencies")
val myNewVariant: NamedDomainObjectProvider<ConsumableConfiguration> = configurations.consumable("myNewVariant") {
    extendsFrom(variantDependencies.get())
    outgoing {
        artifact(myTask)
    }
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>("foo"))
    }
}

publishing {
    val component = softwareComponentFactory.adhoc("component")
    // This new overload now accepts a lazy provider of consumable configuration
    component.addVariantsFromConfiguration(myNewVariant) {}

    repositories {
        maven {
            url = uri("<your repo url>")
        }
    }
    publications {
        create<MavenPublication>("myPublication") {
            from(component)
        }
    }
}
```

With this approach, the `myNewVariant` configuration will only be realized if the `myPublication` publication is actually published.

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
> HIGHLIGHT the use case or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

To embed videos, use the macros below. 
You can extract the URL from YouTube by clicking the "Share" button. 
For Wistia, contact Gradle's Video Team.
@youtube(Summary,6aRM8lAYyUA?si=qeXDSX8_8hpVmH01)@
@wistia(Summary,a5izazvgit)@

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->



<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

### Daemon toolchain is now stable

Gradle introduced the [Daemon toolchain](userguide/gradle_daemon.html#sec:daemon_jvm_criteria) in Gradle 8.8 as an incubating feature.
Since then the feature has been improved and stabilized.
It is now considered stable and will no longer print an incubation warning when used.

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
