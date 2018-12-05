/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.maven


import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenJavaModule
import spock.lang.Unroll

class MavenPublishResolvedVersionsJavaIntegTest extends AbstractMavenPublishIntegTest {
    MavenJavaModule javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    @Unroll
    def "can publish java-library with dependencies using default configuration"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:+"
                implementation "org.test:bar:+"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        versionMapping {
                            $apiMapping
                            $runtimeMapping
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("api") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtime") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << [
                [apiUsingVariantNames(), apiUsingVariantNames("fromResolutionOf('compileClasspath')"), apiUsingVariantNames("fromResolutionOf(project.configurations.compileClasspath)"),
                 apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
                [runtimeUsingVariantNames(), runtimeUsingVariantNames("fromResolutionOf('runtimeClasspath')"), runtimeUsingVariantNames("fromResolutionOf(project.configurations.runtimeClasspath)"),
                 runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")]
        ].combinations()
    }

    /**
     * This use case corresponds to the cases where the published versions should be different
     * from the versions published using the default configurations (compileClasspath, runtimeClasspath).
     * This can be the case if there are multiple compile classpath and one should be preferred for publication,
     * or when the component is not a Java library and we don't have a default.
     */
    @Unroll("can publish resolved versions from a different configuration (#config)")
    def "can publish resolved versions from a different configuration"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            configurations {
                extra.extendsFrom(api)
            }
            dependencies {
                api "org.test:foo:1.0"
                implementation "org.test:bar:1.0"
                extra "org.test:bar:1.1"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        versionMapping {
                            ${runtimeUsingUsage(config)}
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("api") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtime") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        where:
        config << [
                "fromResolutionOf('extra')",
                "fromResolutionOf(project.configurations.extra)"
        ]
    }

    private static String apiUsingVariantNames(String config = "fromResolutionResult()") {
        """
                            variant("api") { // Gradle Metadata
                                $config
                            }
                            variant("compile") { // Maven POM
                                $config
                            }
        """
    }

    private static String apiUsingUsage(String config = "fromResolutionResult()") {
        """
                            usage("java-api") {
                                $config
                            }
        """
    }

    private static String runtimeUsingVariantNames(String config = "fromResolutionResult()") {
        """
                            variant("runtime") {
                                $config
                            }
        """
    }

    private static String runtimeUsingUsage(String config = "fromResolutionResult()") {
        """
                            usage("java-runtime") {
                                $config
                            }
        """
    }

    private void createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            repositories {
                // use for resolving
                maven { url "${mavenRepo.uri}" }
            }
            
            publishing {
                repositories {
                    // used for publishing
                    maven { url "${mavenRepo.uri}" }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }


}
