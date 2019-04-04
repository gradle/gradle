/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.publish.ivy

import org.gradle.test.fixtures.ivy.IvyJavaModule
import spock.lang.Unroll

class IvyPublishResolvedVersionsJavaIntegTest extends AbstractIvyPublishIntegTest  {
    IvyJavaModule javaLibrary = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

    @Unroll("can publish java-library with dependencies (#apiMapping, #runtimeMapping)")
    def "can publish java-library with dependencies (runtime last)"() {
        given:
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
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
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.1") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
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
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)"), apiJarsUsingUsage(), apiJarsUsingUsage("fromResolutionOf('compileClasspath')"), apiJarsUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)"), runtimeJarsUsingUsage(), runtimeJarsUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeJarsUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
    }

    @Unroll("can publish java-library with dependencies (#runtimeMapping, #apiMapping)")
    def "can publish java-library with dependencies (runtime first)"() {
        given:
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            $runtimeMapping
                            $apiMapping
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.1") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
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
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)"), apiJarsUsingUsage(), apiJarsUsingUsage("fromResolutionOf('compileClasspath')"), apiJarsUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)"), runtimeJarsUsingUsage(), runtimeJarsUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeJarsUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
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
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

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
                    ivy(IvyPublication) {
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
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
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

    @Unroll("can publish resolved versions from dependency constraints (#apiMapping, #runtimeMapping)")
    def "can publish resolved versions from dependency constraints"() {
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        given:
        createBuildScripts("""
            dependencies {
                constraints {
                    api "org.test:bar:+"
                }
                api "org.test:foo:1.0"
                implementation "org.test:bar"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
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
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:bar:1.1") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        def dependencies = javaLibrary.parsedIvy.dependencies
        dependencies.get("org.test:foo:1.0").with {
            assert it.org == 'org.test'
            assert it.module == 'foo'
            assert it.revision == '1.0'
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:bar:1.1") {
                exists()
            }
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
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')")]
        ].combinations() + [[allVariants(), noop()]])
    }

    private static String allVariants() {
        " allVariants { fromResolutionResult() } "
    }

    private static String noop() { "" }

    private static String apiUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-api") { $config } """
    }

    private static String apiJarsUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-api-jars") { $config } """
    }

    private static String runtimeJarsUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-runtime-jars") { $config } """
    }

    private static String runtimeUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-runtime") { $config } """
    }

    private void createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            repositories {
                // use for resolving
                ivy { url "${ivyRepo.uri}" }
            }
            
            publishing {
                repositories {
                    // used for publishing
                    ivy { url "${ivyRepo.uri}" }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }
}
