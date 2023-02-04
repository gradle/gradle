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

package org.gradle.integtests.resolve.maven

import org.gradle.api.attributes.Usage
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class ClassifierToVariantResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compileClasspath")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }

        """
        resolve.expectDefaultConfiguration('compile')
        resolve.prepare()
    }

    /**
     * This simulates the case where a library is published with Gradle metadata, and
     * that this library published additional variants, that use an artifact with a classifier.
     * If a Maven consumer wants to use that artifact, it has no choice but using <classifier>,
     * so if a Gradle consumer depends on that Maven published library, we want to make sure we
     * can match this classified dependency to a proper variant.
     */
    def "reasonable behavior when a Maven library uses a classifier to select a Gradle variant"() {
        def gradleLibrary = mavenHttpRepo.module("org", "lib", "1.0")
                .adhocVariants()
                .variant("apiElements", ['org.gradle.usage': Usage.JAVA_API, 'groovy.runtime': 'classic'])
                .variant("runtimeElements", ['org.gradle.usage': Usage.JAVA_RUNTIME, 'groovy.runtime': 'classic'])
                .variant("apiElementsIndy", ['org.gradle.usage': Usage.JAVA_API, 'groovy.runtime': 'indy']) {
                    artifact("lib-1.0-indy.jar")
                }
                .variant("runtimeElementsIndy", ['org.gradle.usage': Usage.JAVA_RUNTIME, 'groovy.runtime': 'indy']) {
                    artifact("lib-1.0-indy.jar")
                }
                .withModuleMetadata()
                .publish()
        def mavenConsumer = mavenHttpRepo.module("org", "maven-consumer", "1.0")
                .dependsOn("org", "lib", "1.0", "jar", "compile", "indy")
                .publish()

        buildFile << """
            dependencies {
                api 'org:maven-consumer:1.0'
            }
        """

        when:
        mavenConsumer.pom.expectGet()
        mavenConsumer.artifact.expectGet()
        gradleLibrary.pom.expectGet()
        gradleLibrary.moduleMetadata.expectGet()
        gradleLibrary.getArtifact(classifier: 'indy').expectGet()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:maven-consumer:1.0') {
                    module('org:lib:1.0') {
                        variant('apiElementsIndy', [
                                'org.gradle.usage': Usage.JAVA_API,
                                'org.gradle.status': 'release',
                                'groovy.runtime': 'indy'])
                        artifact(classifier: 'indy')
                    }
                }
            }
        }

    }

    /**
     * A Gradle consumer should _not_ do this, but use attributes instead. However,
     * there's nothing which prevents this from being done, so we must make sure it is
     * supported. The path is exactly the same as when a Maven consumer wants to depend
     * on a library published with Gradle that uses variants published using different
     * classified artifacts.
     */
    def "reasonable behavior when a Gradle consumer uses a classifier to select a Gradle variant"() {
        def gradleLibrary = mavenHttpRepo.module("org", "lib", "1.0")
                .adhocVariants()
                .variant("apiElements", ['org.gradle.usage': Usage.JAVA_API, 'groovy.runtime': 'classic'])
                .variant("runtimeElements", ['org.gradle.usage': Usage.JAVA_RUNTIME, 'groovy.runtime': 'classic'])
                .variant("apiElementsIndy", ['org.gradle.usage': Usage.JAVA_API, 'groovy.runtime': 'indy']) {
                    artifact("lib-1.0-indy.jar")
                }
                .variant("runtimeElementsIndy", ['org.gradle.usage': Usage.JAVA_RUNTIME, 'groovy.runtime': 'indy']) {
                    artifact("lib-1.0-indy.jar")
                }
                .withModuleMetadata()
                .publish()

        buildFile << """
            dependencies {
                api 'org:lib:1.0:indy'
            }
        """

        when:
        gradleLibrary.pom.expectGet()
        gradleLibrary.moduleMetadata.expectGet()
        gradleLibrary.getArtifact(classifier: 'indy').expectGet()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:lib:1.0') {
                    variant('apiElementsIndy', [
                            'org.gradle.usage': Usage.JAVA_API,
                            'org.gradle.status': 'release',
                            'groovy.runtime': 'indy'])
                    artifact(classifier: 'indy')
                }
            }
        }

    }

}
