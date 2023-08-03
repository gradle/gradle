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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenJavaModule
import org.gradle.test.fixtures.maven.MavenModule

class MavenScopesTestIntegTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "rootProject.name = 'publishTest'"
        buildFile << """
            plugins {
                id 'maven-publish'
                id 'java-library'
            }

            group = 'org.gradle.test'
            version = '1.9'

            configurations {
                custom {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                }
            }
            
            dependencies {
                custom 'log4j:log4j:1.2.17'
            }
        """
    }

    def "test adding custom variant with dependency mapped to Maven runtime scope"() {
        given:
        buildFile << """
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
        succeeds "publishMyPubPublicationToMavenRepository"
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

    def "test adding custom variant with dependency mapped to Maven runtime scope, then changed to compile scope"() {
        given:
        buildFile << """
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('runtime')
            }

            components.java.withVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('compile')
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
        succeeds "publishMyPubPublicationToMavenRepository"
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
        mavenMetadata.parsedPom.scopes.compile.assertDependsOn("log4j:log4j:1.2.17")
    }

    def "test adding custom variant with dependency mapped to Maven compile scope, then changed to runtime scope"() {
        given:
        buildFile << """
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('compile')
            }

            components.java.withVariantsFromConfiguration(configurations.custom) {
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
        succeeds "publishMyPubPublicationToMavenRepository"
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

    def "test adding custom variant with dependency mapped to Maven compile scope, then changed to runtime scope, then changed back to compile"() {
        given:
        buildFile << """
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('compile')
            }

            components.java.withVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('runtime')
            }

            components.java.withVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('compile')
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
        succeeds "publishMyPubPublicationToMavenRepository"
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
        mavenMetadata.parsedPom.scopes.compile.assertDependsOn("log4j:log4j:1.2.17")
    }

    def "test adding custom variant with dependency mapped to optional Maven runtime scope"() {
        given:
        buildFile << """
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToMavenScope('runtime')
                mapToOptional()
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
        succeeds "publishMyPubPublicationToMavenRepository"
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
        mavenMetadata.parsedPom.scopes.runtime.assertOptionalDependencies("log4j:log4j:1.2.17")
    }

    def "test adding custom variant with dependency mapped to optional with no explicit Maven scope"() {
        given:
        buildFile << """
            components.java.addVariantsFromConfiguration(configurations.custom) {
                mapToOptional()
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
        succeeds "publishMyPubPublicationToMavenRepository"
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
        mavenMetadata.parsedPom.scopes.compile.assertOptionalDependencies("log4j:log4j:1.2.17")
    }

    private static MavenJavaModule javaLibrary(MavenFileModule mavenFileModule, List<String> features = [MavenJavaModule.MAIN_FEATURE], boolean withDocumentation = false) {
        return new MavenJavaModule(mavenFileModule, features, withDocumentation)
    }
}
