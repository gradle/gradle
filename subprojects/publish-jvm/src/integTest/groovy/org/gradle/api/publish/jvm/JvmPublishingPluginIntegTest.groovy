/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyJavaModule
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenJavaModule

class JvmPublishingPluginIntegTest extends AbstractIntegrationSpec {
    def "maven publish plugin with minimal custom variant"() {
        given:
        settingsFile << "rootProject.name = 'publishTest'"
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }

            group = 'org.gradle.test'
            version = '1.9'

            configurations {
                custom {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                }
            }
            dependencies {
                custom 'log4j:log4j:1.2.17'
            }
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('runtime')
            }
            publishing {
                publications {
                    myPub(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            """

        when:
        succeeds "publishMyPubPublicationToMavenRepository", "--console=plain"
        MavenJavaModule mavenModule = moduleFor(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))
        GradleModuleMetadata gmmMetadata = mavenModule.parsedModuleMetadata

        then:
        gmmMetadata.variant("apiElements") {
            noMoreDependencies()
        }
        gmmMetadata.variant("runtimeElements") {
            noMoreDependencies()
        }
        gmmMetadata.variant("custom") {
            dependency('log4j:log4j:1.2.17').exists()
            noMoreDependencies()
        }

        and:
        mavenModule.assertPublished(['apiElements', 'custom', 'runtimeElements'] as Set)
        mavenModule.parsedPom.scopes.size() == 1
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("log4j:log4j:1.2.17")
    }

    def "ivy publish plugin with minimal custom variant"() {
        given:
        settingsFile << "rootProject.name = 'publishTest'"
        buildFile << """
            plugins {
                id 'java-library'
                id 'ivy-publish'
            }

            group = 'org.gradle.test'
            version = '1.9'

            configurations {
                custom {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                }
            }
            dependencies {
                custom 'log4j:log4j:1.2.17'
            }
            components.java.addVariantsFromConfiguration(configurations.custom) {
                // TODO: I shouldn't neeed to provide an action here if I have nothing to do
            }
            publishing {
                publications {
                    myPub(IvyPublication) {
                        from components.java
                    }
                }
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
            }
            """

        when:
        succeeds "publishMyPubPublicationToIvyRepository", "--console=plain"
        IvyJavaModule ivyModule = moduleFor(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))
        GradleModuleMetadata gmmMetadata = new GradleModuleMetadata(ivyModule.moduleMetadata.file)

        then:
        gmmMetadata.variant("apiElements") {
            noMoreDependencies()
        }
        gmmMetadata.variant("runtimeElements") {
            noMoreDependencies()
        }
        gmmMetadata.variant("custom") {
            dependency('log4j:log4j:1.2.17').exists()
            noMoreDependencies()
        }

        and:
        ivyModule.assertPublished(['apiElements', 'custom', 'runtimeElements'] as Set)
        with(ivyModule.parsedIvy) {
            configurations.keySet() == ["default", "compile", "custom", "runtime"] as Set
            configurations["default"].extend == ["runtime", "custom"] as Set
            configurations["runtime"].extend == null

            expectArtifact("publishTest").hasAttributes("jar", "jar", ["compile", "runtime"])
        }
        ivyModule.assertApiDependencies(/* none */)
        ivyModule.assertRuntimeDependencies(/* none */)
        assert ivyModule.parsedModuleMetadata.variant('custom').dependencies*.coords as Set == ['log4j:log4j:1.2.17'] as Set
    }

    def "can apply only the jvm-publish plugin"() {
        given:
        settingsFile << "rootProject.name = 'publishTest'"
        buildFile << """
            plugins {
                id 'java-library'
                id 'jvm-publish'
            }

            group = 'org.gradle.test'
            version = '1.9'
        """

        when:
        succeeds 'tasks'

        then:
        outputContains "publish - Publishes all publications produced by this project."

        expect:
        succeeds "publish", "--console=plain"
    }

    private static MavenJavaModule moduleFor(MavenFileModule mavenFileModule, List<String> features = [MavenJavaModule.MAIN_FEATURE], boolean withDocumentation = false) {
        return new MavenJavaModule(mavenFileModule, features, withDocumentation)
    }

    private static IvyJavaModule moduleFor(IvyFileModule module) {
        new IvyJavaModule(module)
    }
}
