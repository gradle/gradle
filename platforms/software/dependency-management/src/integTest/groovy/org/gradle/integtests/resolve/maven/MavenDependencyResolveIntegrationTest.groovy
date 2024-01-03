/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
class MavenDependencyResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    String getRootProjectName() { 'testproject' }

    def "dependency includes main artifact and runtime dependencies of referenced module"() {
        given:
        repository {
            'org.gradle:other:preview-1'()
            'org.gradle:test:1.45' {
                dependsOn 'org.gradle:other:preview-1'
                withModule {
                    artifact(classifier: 'classifier') // ignored
                }
            }
        }

        and:
        buildFile << """
group = 'org.gradle'
version = '1.0'
dependencies {
    conf "org.gradle:test:1.45"
}
"""

        repositoryInteractions {
            'org.gradle:test:1.45' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.gradle:other:preview-1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency that references a classifier includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        repository {
            'org.gradle' {
                'other' {
                    'preview-1'()
                }
                'test' {
                    '1.45' {
                        dependsOn 'org.gradle:other:preview-1'
                        withModule {
                            artifact(classifier: 'classifier')
                            artifact(classifier: 'some-other') // ignored
                        }
                    }
                }
            }
        }

        and:
        buildFile << """
group = 'org.gradle'
version = '1.0'
dependencies {
    conf "org.gradle:test:1.45:classifier"
}
"""

        repositoryInteractions {
            'org.gradle:test:1.45' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'classifier')
            }
            'org.gradle:other:preview-1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    artifact(classifier: 'classifier')
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency that references an artifact includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        repository {
            'org.gradle' {
                'other' {
                    'preview-1'()
                }
                'test' {
                    '1.45' {
                        dependsOn 'org.gradle:other:preview-1'
                        withModule {
                            artifact(type: 'aar', classifier: 'classifier')
                        }
                    }
                }
            }
        }

        and:
        buildFile << """
group = 'org.gradle'
version = '1.0'
dependencies {
    conf ("org.gradle:test:1.45") {
        artifact {
            name = 'test'
            type = 'aar'
            classifier = 'classifier'
        }
    }
}
"""
        repositoryInteractions {
            'org.gradle:test:1.45' {
                expectGetMetadata()
                expectGetArtifact(type: 'aar', classifier: 'classifier')
            }
            'org.gradle:other:preview-1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    artifact(type: 'aar', classifier: 'classifier')
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "uses the name of the current dependency by default"() {
        given:
        buildFile << """
group = 'org.gradle'
version = '1.0'
dependencies {
    conf ("org.gradle:test:1.45") {
        artifact {
            classifier = 'classifier'
        }
    }
}
"""
        repository {
            'org.gradle' {
                'test' {
                    '1.45' {
                        withModule {
                            artifact(classifier: 'classifier')
                        }
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            'org.gradle:test:1.45' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'classifier')
            }
        }
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    artifact(classifier: 'classifier')
                }
            }
        }
    }

    // only available with Maven metadata: Gradle metadata does not support "optional"
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "does not include optional dependencies of maven module"() {
        given:
        repository {
            'org.gradle:test:1.45' {
                dependsOn group:'org.gradle', artifact:'i-do-not-exist', version:'1.45', optional: 'true'
                dependsOn group:'org.gradle', artifact:'i-do-not-exist', version:'1.45', optional: 'true', scope: 'runtime'
            }
        }
        and:

        buildFile << """
dependencies {
    conf "org.gradle:test:1.45"
}
"""

        repositoryInteractions {
            'org.gradle:test:1.45' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.gradle:test:1.45")
            }
        }
    }

    def "mixing variant aware and artifact selection is forbidden"() {
        buildFile << """
            dependencies {
                conf('org:lib:1.0:indy') {
                    capabilities {
                        requireCapability("org:lib")
                    }
                }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failureHasCause('Cannot add attributes or capabilities on a dependency that specifies artifacts or configuration information')
    }
}
