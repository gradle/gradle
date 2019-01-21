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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenJavaModule

class MavenPublishOptionalDependenciesJavaIntegTest extends AbstractMavenPublishIntegTest {
    MavenJavaModule javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    def setup() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")
    }

    def "can publish java-library with optional feature"() {
        mavenRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }
            
            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }
            
            components.java.addOptionalFeatureVariantFromConfiguration('optionalFeature', configurations.optionalFeatureRuntimeElements)
        """

        when:
        run "publish"

        then:
        javaLibrary.parsedModuleMetadata.variant("api") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtime") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("optionalFeature") {
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedPom.scope('compile') {
            assertOptionalDependencies('org:optionaldep:1.0')
        }
        javaLibrary.parsedPom.hasNoScope('runtime')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org:optional-feature:1.0"
            withModuleMetadata {
                expectFiles "publishTest-1.9.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                shouldFail {
                    // documents the current behavior
                    assertHasCause("Unable to find a variant of org.gradle.test:publishTest:1.9 providing the requested capability org:optional-feature:1.0")
                }
            }
        }
    }

    def "doesn't allow publishing an optional feature without capability"() {
        mavenRepo.module('org', 'optionaldep', '1.0').publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }
            
            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }
            
            components.java.addOptionalFeatureVariantFromConfiguration('optionalFeature', configurations.optionalFeatureRuntimeElements)
        """

        when:
        fails "publish"

        then:
        failure.assertHasCause("Cannot publish optional feature variant optionalFeature because configuration optionalFeatureRuntimeElements doesn't declare any capability")
    }

    def "reasonable error message when declaring a variant which name already exists"() {
        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }
            
            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }
            
            components.java.addOptionalFeatureVariantFromConfiguration('api', configurations.optionalFeatureRuntimeElements)
        """

        when:
        fails "publish"

        then:
        failure.assertHasCause("Cannot add optional feature variant 'api' as a variant with the same name is already registered")
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }

    def "can group dependencies by optional feature"() {
        mavenRepo.module('org', 'optionaldep-g1', '1.0').publish()
        mavenRepo.module('org', 'optionaldep1-g2', '1.0').publish()
        mavenRepo.module('org', 'optionaldep2-g2', '1.0').publish()

        given:
        buildFile << """
            configurations {
                optionalFeature1Implementation
                optionalFeature1RuntimeElements {
                    extendsFrom optionalFeature1Implementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature1:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeature1Implementation)
                
                optionalFeature2Implementation
                optionalFeature2RuntimeElements {
                    extendsFrom optionalFeature2Implementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature2:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeature2Implementation)
            }
            
            dependencies {
                optionalFeature1Implementation 'org:optionaldep-g1:1.0'
                optionalFeature2Implementation 'org:optionaldep1-g2:1.0'
                optionalFeature2Implementation 'org:optionaldep2-g2:1.0'
            }
            
            components.java.addOptionalFeatureVariantFromConfiguration('optionalFeature1', configurations.optionalFeature1RuntimeElements)
            components.java.addOptionalFeatureVariantFromConfiguration('optionalFeature2', configurations.optionalFeature2RuntimeElements)
        """

        when:
        run "publish"

        then:
        javaLibrary.parsedModuleMetadata.variant("api") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtime") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("optionalFeature1") {
            dependency('org', 'optionaldep-g1', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("optionalFeature2") {
            dependency('org', 'optionaldep1-g2', '1.0')
            dependency('org', 'optionaldep2-g2', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedPom.scope('compile') {
            assertOptionalDependencies(
                    'org:optionaldep-g1:1.0',
                    'org:optionaldep1-g2:1.0',
                    'org:optionaldep2-g2:1.0')
        }
        javaLibrary.parsedPom.hasNoScope('runtime')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
    }


}
