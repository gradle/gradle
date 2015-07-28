Gradle 2.6 is an exciting release with some shiny new features and many under the hood improvements.

The new support for the [Play](https://www.playframework.com) framework is exciting for many reasons.
If you build Play applications, you can now do so with Gradle and take advantage of all of Gradle's features and capabilities.
Play developers with more sophisticated dependency management or orchestration requirements in particular may find building
Play apps with Gradle to be particularly appealing.

The Play support is also an exciting development for non-Play users as well.
The development time reloading implementation leverages the continuous build capabilities in Gradle 2.5 to be “change aware”.
This mechanism will eventually be generally available to be used by integrations with other web frameworks or indeed any
long running “thing” being built.

The new “Gradle TestKit” provides an official mechanism for testing custom plugins which will greatly improve the Gradle plugin development experience.
This initial version of the TestKit focuses on executing real builds and asserting the result (i.e. functional testing).
Expect the TestKit to improve and expand over coming releases.

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### New dependency management for JVM components (i)

This release introduces new incubating dependency management features for the JVM components. This is our first step towards variant-aware dependency resolution.
This feature is built upon the new model and requires you to use the new Java plugin:

    plugins {
        id 'jvm-component'
        id 'java-lang'
    }

Once the plugins are applied, it is possible to declare API dependencies between libraries. Gradle will automatically generate the appropriate binary variants (JARs)
and select the appropriate binary dependencies based on the target platforms:

    model {
        components {
            main(JvmLibrarySpec) {
                targetPlatform 'java7'
                targetPlatform 'java8'
                sources {
                    java {
                        dependencies {
                            library 'commons'
                        }
                    }
                }
            }
            commons(JvmLibrarySpec) {
                targetPlatform 'java7'
            }
        }
    }

More information about this incubating feature can be found in the [User Guide](userguide/new_java_plugin.html).

In addition, it is now possible to create custom library types, including custom binary types with specific variants. Binary properties can be
annotated with `@Variant` which will declare it as a custom variant dimension:

    @Managed
    interface FlavorAwareJar extends JarBinarySpec {
        @Variant
        String getFlavor()
        void setFlavor(String flavor)
    }

Those custom variant dimensions will participate into dependency resolution. Examples of how to define custom binaries can be found in
the [integration tests](https://github.com/gradle/gradle/tree/master/subprojects/language-java/src/integTest/groovy/org/gradle/language/java).

### Support for verifying Gradle wrapper distribution download against SHA-256 hash

It is now possible to verify the integrity of the Gradle distribution used by the [Gradle wrapper](userguide/gradle_wrapper.html) against a known checksum.
This provides an extra level of assurance that the Gradle runtime is exactly the intended version.

To enable verification you need only specify a `distributionSha256Sum` property in your project's `gradle-wrapper.properties` file.

    distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

Please see section [Verification of downloaded Gradle distributions](userguide/gradle_wrapper.html#sec:verification) of the User Guide for more information.

This feature was contributed by [Dominik Schürmann](https://github.com/dschuermann).

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
