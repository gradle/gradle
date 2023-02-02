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
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenJavaModule
import org.gradle.test.fixtures.maven.MavenModule

class JvmPublishingPluginIntegTest extends AbstractIntegrationSpec {
    def "maven publish plugin for reference"() {
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
        MavenModule mavenMetadata = mavenRepo.module("org.gradle.test", "publishTest", "1.9")
        MavenJavaModule gmmMetadata = javaLibrary(mavenMetadata)

        then:
        gmmMetadata.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }
        gmmMetadata.parsedModuleMetadata.variant("runtimeElements") {
            noMoreDependencies()
        }
        gmmMetadata.parsedModuleMetadata.variant("custom") {
            dependency('log4j:log4j:1.2.17').exists()
            noMoreDependencies()
        }

        and:
        mavenMetadata.assertPublished()
        mavenMetadata.parsedPom.scopes.size() == 1
        mavenMetadata.parsedPom.scopes.runtime.assertDependsOn("log4j:log4j:1.2.17")
    }

    def "apply only the jvm-publish plugin"() {
        given:
        settingsFile << "rootProject.name = 'publishTest'"
        buildFile << """
            plugins {
                id 'java-library'
                id 'jvm-publish'
            }

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                publications {
                    myPub(PublicationInternal) {
                        from components.java
                    }
                }
            }
        """

        expect:
        succeeds "publish", "--console=plain"

        // TODO: the types on these might need to be changed
//        MavenJavaModule gmmMetadata = javaLibrary(mavenMetadata)
//        MavenModule mavenMetadata = mavenRepo.module("org.gradle.test", "publishTest", "1.9")
//
//        then:
//        gmmMetadata.assertPublished()
    }

    private static MavenJavaModule javaLibrary(MavenFileModule mavenFileModule, List<String> features = [MavenJavaModule.MAIN_FEATURE], boolean withDocumentation = false) {
        return new MavenJavaModule(mavenFileModule, features, withDocumentation)
    }
}
