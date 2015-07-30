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

### Additions to the Gradle TestKit

The last release of Gradle introduced the [Gradle TestKit](userguide/test_kit.html) for functionally testing Gradle plugins. This release extends
the existing TestKit feature set by the following aspects:

* **Test execution isolation:** Test execution is performed in an isolated "working space" in order to prevent builds under test inheriting any
environmental configuration from the current user. The TestKit uses dedicated, reusable Gradle daemon processes. After executing the whole suite of
functional tests existing daemon processes are stopped.

### Improved Play Support

The initial release of Gradle's Play plugin supported running Play applications in continuous build.  When a build failure occurred, Gradle
would leave the application running.  Now, after a build failure, the Play application will show you the exception message from Gradle.

In this release, Gradle's Play plugin supports more Play 2.4.x features.  You can configure the routes compiler to use the
[injected routes generator](https://www.playframework.com/documentation/2.4.x/JavaDependencyInjection#Dependency-injecting-controllers).
By default, Gradle will still use the static routes generator.

To configure your Play application to use the injected routes generator, you'll need to configure the [RoutesCompile](dsl/org.gradle.play.tasks.RoutesCompile.html) task:

    model {
        components {
            play {
                tasks.withType(RoutesCompile) {
                    staticRoutesGenerator = false
                }
            }
        }
    }

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
