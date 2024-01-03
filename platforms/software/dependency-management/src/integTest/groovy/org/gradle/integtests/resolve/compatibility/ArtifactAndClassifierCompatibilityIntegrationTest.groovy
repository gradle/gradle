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
package org.gradle.integtests.resolve.compatibility

import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

class ArtifactAndClassifierCompatibilityIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "resolves a dependency with classifier targeting module without GMM"() {
        given:
        repository {
            'org:foo:1.0' {
                dependsOn(group: 'org', artifact:'bar', version:'1.0', classifier:'classy')
            }
            'org:bar:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                }
                withoutGradleMetadata()
            }
        }

        and:
        buildFile << """
            dependencies {
                conf 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'classy')
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:foo:1.0") {
                    module("org:bar:1.0") {
                        artifact(classifier: 'classy')
                    }
                }
            }
        }
    }

    def "resolves a dependency with classifier targeting module with GMM"() {
        given:
        repository {
            'org:foo:1.0' {
                dependsOn(group: 'org', artifact:'bar', version:'1.0', classifier:'classy')
            }
            'org:bar:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                }
                // The provider has GMM but we still allow classifier selection - even if the consumer has GMM as well.
                // Although it is discouraged to use this feature with GMM alone, the behavior is helpful, if a provider
                // starts publishing GMM but did not do so before.
                // Then consumers that use a classifier selection, do not suddenly break with a version upgrade that brings in GMM.
                withGradleMetadata()
            }
        }

        and:
        buildFile << """
            dependencies {
                conf 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'classy')
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:foo:1.0") {
                    module("org:bar:1.0") {
                        artifact(classifier: 'classy')
                    }
                }
            }
        }
    }

    /**
     * Test to demonstrate a real life use case.
     */
    def "existing oss library use case"() {
        given:
        repository {
            'org:mylib:1.0' {
                dependsOn(group: 'com.google.inject', artifact: 'guice', version: '4.2.2', classifier: 'no_aop')
            }
            // inspired by: https://repo1.maven.org/maven2/com/google/inject/guice/4.2.2/
            'com.google.inject:guice:4.2.2' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'no_aop')
                }
                dependsOn 'javax.inject:javax.inject:1'
                withoutGradleMetadata()
            }
            'javax.inject:javax.inject:1'()
        }

        and:
        buildFile << """
            dependencies {
                conf 'org:mylib:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:mylib:1.0' {
                expectResolve()
            }
            'com.google.inject:guice:4.2.2' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'no_aop')
            }
            'javax.inject:javax.inject:1' {
                expectResolve()
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:mylib:1.0') {
                    module('com.google.inject:guice:4.2.2') {
                        artifact(classifier: 'no_aop')
                        module('javax.inject:javax.inject:1')
                    }
                }
            }
        }
    }

    @Issue("gradle/gradle#11825")
    def "dependency on both classifier and regular jar of a given module"() {
        given:
        repository {
            'org:foo:1.0' {
                dependsOn(group: 'org', artifact:'bar', version:'1.0', classifier:'classy')
                dependsOn(group: 'org', artifact:'bar', version:'1.0')
            }
            'org:bar:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                }
                withoutGradleMetadata()
            }
        }

        and:
        buildFile << """
            dependencies {
                conf 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
                expectGetArtifact(classifier: 'classy')
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:foo:1.0") {
                    module("org:bar:1.0") {
                        artifact()
                        artifact(classifier: 'classy')
                    }
                }
            }
        }
    }

    @Issue("gradle/gradle#11825")
    def "dependency on different classifiers of a given module"() {
        given:
        repository {
            'org:foo:1.0' {
                dependsOn(group: 'org', artifact:'bar', version:'1.0', classifier:'classy')
                dependsOn(group: 'org', artifact:'bar', version:'1.0', classifier:'other')
            }
            'org:bar:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                    undeclaredArtifact(type: 'jar', classifier: 'other')
                }
                withoutGradleMetadata()
            }
        }

        and:
        buildFile << """
            dependencies {
                conf 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'other')
                expectGetArtifact(classifier: 'classy')
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:foo:1.0") {
                    module("org:bar:1.0") {
                        artifact(classifier: 'other')
                        artifact(classifier: 'classy')
                    }
                }
            }
        }

    }
}
