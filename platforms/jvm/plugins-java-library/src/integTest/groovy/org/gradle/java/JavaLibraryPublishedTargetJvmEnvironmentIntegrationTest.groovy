/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule

// These test cases are motivated by the Guava use case: https://github.com/google/guava/pull/3683
class JavaLibraryPublishedTargetJvmEnvironmentIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve
    MavenModule module

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            apply plugin: 'java-library'

            configurations.create('releaseCompileClasspath') {
                extendsFrom(configurations.implementation)
                attributes {
                    // This is what AGP 7 will most likely do:
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment, TargetJvmEnvironment.ANDROID))
                }
            }
            configurations.create('legacy') {
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                }
            }

            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation 'org:producer:1.0'
            }
        """

        module = mavenHttpRepo.module('org', 'producer', '1.0')
                .withModuleMetadata()
                .adhocVariants()
                .variant("apiElementsAndroid", [
                'org.gradle.jvm.environment': 'android',
                'org.gradle.jvm.version': '6',
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0-android.jar') })
                .variant("apiElementsJre", [
                'org.gradle.jvm.environment': 'standard-jvm',
                'org.gradle.jvm.version': '8',
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0-jre.jar') })
    }

    def prepareResolve(String config, String classifier) {
        resolve = new ResolveTestFixture(buildFile, config)
        resolve.prepare()

        module.publish()
        module.pom.expectGet()
        module.moduleMetadata.expectGet()
        module.getArtifact(classifier: classifier).expectGet()
    }

    def "prefers standard JVM variant in Java projects"() {
        given:
        prepareResolve('compileClasspath', 'jre')

        when:
        run ':checkDeps'

        then:
        expectStandardJVM()
    }

    def "Prefers Android variant in Android projects"() {
        given:
        prepareResolve('releaseCompileClasspath', 'android')

        when:
        run ':checkDeps'

        then:
        expectAndroid()
    }

    def "Uses Android variant that can run on Java6 in Java6 projects"() {
        given:
        prepareResolve('compileClasspath', 'android')

        buildFile << """
            java.targetCompatibility = JavaVersion.VERSION_1_6
            java.sourceCompatibility = JavaVersion.VERSION_1_6
        """

        when:
        run ':checkDeps'

        then:
        expectAndroid()
    }

    def "prefers standard JVM variant by default"() {
        given:
        prepareResolve('legacy', 'jre')

        when:
        run ':checkDeps'

        then:
        expectStandardJVM()
    }

    def "can enforce standard JVM variant on Android by constraint"() {
        given:
        prepareResolve('releaseCompileClasspath', 'jre')

        buildFile << """
            dependencies {
                constraints {
                    implementation('org:producer:1.0') {
                        attributes {
                            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment, 'standard-jvm'))
                        }
                    }
                }
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:producer:1.0')
                constraint('org:producer:1.0', 'org:producer:1.0') {
                    variant('apiElementsJre', [
                        'org.gradle.jvm.environment': 'standard-jvm',
                        'org.gradle.jvm.version': 8,
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.status': 'release'
                    ])
                    byConstraint()
                    artifact(classifier: 'jre')
                }
            }
        }
    }

    def "can select unknown environment"() {
        given:
        module.adhocVariants().variant("apiElements", [
                'org.gradle.jvm.environment': 'another-jvm-env',
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0.jar') })
        prepareResolve('compileClasspath', '')

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:producer:1.0')  {
                    variant('apiElements', [
                        'org.gradle.jvm.environment': 'another-jvm-env',
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-api',
                        'org.gradle.status': 'release'
                    ])
                }
            }
        }

    }

    void expectStandardJVM() {
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:producer:1.0') {
                    variant('apiElementsJre', [
                        'org.gradle.jvm.environment': 'standard-jvm',
                        'org.gradle.jvm.version': 8,
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.status': 'release'
                    ])
                    artifact(classifier: 'jre')
                }
            }
        }
    }

    void expectAndroid() {
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:producer:1.0') {
                    variant('apiElementsAndroid', [
                        'org.gradle.jvm.environment': 'android',
                        'org.gradle.jvm.version': 6,
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.status': 'release'
                    ])
                    artifact(classifier: 'android')
                }
            }
        }
    }
}
