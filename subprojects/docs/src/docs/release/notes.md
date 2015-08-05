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

The initial release of [Gradle's Play plugin](https://docs.gradle.org/current/release-notes#play-framework-support) supported running Play applications in continuous build.
When a build failure occurred, Gradle would leave the application running.  Now, after a build failure, the Play application will show you the exception message from Gradle.

In this release, Gradle's Play plugin supports more Play 2.4.x features.  You can configure the routes compiler to use the
[injected routes generator](https://www.playframework.com/documentation/2.4.x/Migration24#Routing).
By default, Gradle will still use the static routes generator.

To make your Play application build use the injected routes generator, you'll need to configure your [PlayApplicationSpec](dsl/org.gradle.play.PlayApplicationSpec.html) component:

    model {
        components {
            play {
                useStaticRouter = false
            }
        }
    }

We have [design specs](https://github.com/gradle/gradle/blob/master/design-docs/play-support.md#feature-developer-views-compile-and-other-build-failures-in-play-application) for improving
developer feedback even more in future Gradle releases.

### Explicit configuration of HTTP authentication schemes (i)

Support has been added for explicitly configuring the authentication schemes that should be used when authenticating to Maven or Ivy repositories over HTTP/HTTPS. By default,
Gradle will attempt to use all [schemes supported by the HttpClient library](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d5e625). To
override the default behavior, a new API has been added to allow configuring which authentication schemes should be used. For increased security, a repository can be configured
to only use digest authentication so as to never transmit credentials in plaintext:

    repositories {
        maven {
            url 'https://repo.mycompany.com/maven2'
            credentials {
                username 'user'
                password 'password'
            }
            authentication {
                digest(DigestAuthentication)
            }
        }
    }

Currently, only basic and digest authentication schemes for HTTP transports can be explicitly configured. More details can be found in the Gradle
[User Guide](userguide/dependency_management.html#sub:authentication_schemes).

### Support for preemptive authentication (i)

Building on the [new support for configuring authentication schemes](#explicit-configuration-of-http-authentication-schemes), support for preemptive authentication has been added.

Gradle's default behavior is to only submit credentials when a server responds with an authentication challenge in the form of a HTTP 401 response. In some cases, the server will
respond with a different code (ex. for repositories hosted on GitHub a 404 is returned) causing dependency resolution to fail. To get around this behavior, credentials may be sent
to the server preemptively. To enable preemptive authentication simply configure your repository to explicitly use the
[`BasicAuthentication`](javadoc/org/gradle/api/authentication/BasicAuthentication.html) scheme:

    authentication {
        basic(BasicAuthentication) // enable preemptive authentication
    }

### Managed model improvements

TBD: Currently, managed model works well for defining a tree of objects. This release improves support for a graph of objects, with links between parts of the
model.

- Can use a link property as input for a rule.

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

* [Bruno Bowden](https://github.com/brunobowden) - Documentation improvements
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
