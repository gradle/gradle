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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactViewDemoIntegrationTest extends AbstractIntegrationSpec {
    def directProducerBuildFile = file('directProducer/build.gradle')
    def transitiveProducerBuildFile = file('transitiveProducer/build.gradle')
    def consumerBuildFile = file('consumer/build.gradle')

    def setup() {
        multiProjectBuild('root', ['directProducer', 'transitiveProducer', 'consumer']) {
            file("directProducer/direct-red.txt").text = "RED"
            file("directProducer/direct-blue.txt").text = "BLUE"
            file("directProducer/direct-other.txt").text = "OTHER"
            file("directProducer/direct-none.txt").text = "NONE"

            directProducerBuildFile << '''
                plugins {
                    id 'java'
                }

                Attribute<String> shared = Attribute.of('shared', String)
                Attribute<String> color = Attribute.of('color', String)
                Attribute<String> other = Attribute.of('other', String)

                configurations {
                    redElements {
                        attributes {
                            attribute(shared, 'shared-value')
                            attribute(color, 'red')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('direct-red.txt'))
                        }
                    }
                    blueElements {
                        attributes {
                            attribute(shared, 'shared-value')
                            attribute(color, 'blue')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('direct-blue.txt'))
                        }
                    }
                    otherElements {
                        attributes {
                            attribute(other, 'foobar')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('direct-other.txt'))
                        }
                    }
                    noneElements {
                        outgoing {
                            artifact(layout.projectDirectory.file('direct-none.txt'))
                        }
                    }
                }

                dependencies {
                    implementation(project(':transitiveProducer'))
                }
            '''

            file("transitiveProducer/transitive-red.txt").text = "RED"
            file("transitiveProducer/transitive-blue.txt").text = "BLUE"
            file("transitiveProducer/transitive-other.txt").text = "OTHER"
            file("transitiveProducer/transitive-none.txt").text = "NONE"

            transitiveProducerBuildFile << '''
                plugins {
                    id 'java'
                }

                Attribute<String> shared = Attribute.of('shared', String)
                Attribute<String> color = Attribute.of('color', String)
                Attribute<String> other = Attribute.of('other', String)

                configurations {
                    redElements {
                        attributes {
                            attribute(shared, 'shared-value')
                            attribute(color, 'red')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('transitive-red.txt'))
                        }
                    }
                    blueElements {
                        attributes {
                            attribute(shared, 'shared-value')
                            attribute(color, 'blue')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('transitive-blue.txt'))
                        }
                    }
                    otherElements {
                        attributes {
                            attribute(other, 'foobar')
                        }

                        outgoing {
                            artifact(layout.projectDirectory.file('transitive-other.txt'))
                        }
                    }
                    noneElements {
                        outgoing {
                            artifact(layout.projectDirectory.file('transitive-none.txt'))
                        }
                    }
                }
            '''

            consumerBuildFile << '''
                plugins {
                    id 'java'
                }

                Attribute<String> shared = Attribute.of('shared', String)
                Attribute<String> color = Attribute.of('color', String)
                Attribute<String> other = Attribute.of('other', String)

                abstract class Resolve extends DefaultTask {
                    @InputFiles
                    abstract ConfigurableFileCollection getArtifacts()

                    @Internal
                    List<String> expectations = []

                    @TaskAction
                    void assertThat() {
                        logger.lifecycle 'Found files: {}', artifacts.files*.name
                        assert artifacts.files*.name == expectations
                    }
                }
            '''
        }
    }

    def 'default directDependency resolution produces default jar files for direct and transitive'() {
        consumerBuildFile << '''
            configurations {
                testResolve
            }

            dependencies {
                testResolve project(':directProducer')
            }

            tasks.register('resolve', Resolve) {
                artifacts.from(configurations.testResolve)
                expectations = [ 'directProducer-1.0.jar', 'transitiveProducer-1.0.jar' ]
            }
            '''

        expect:
        succeeds(':consumer:resolve')
    }

    def 'default directDependency resolution with shared attribute on configuration still produces default jar files for direct and transitive'() {
        consumerBuildFile << '''
            configurations {
                testResolve {
                    attributes {
                        attribute(shared, 'shared-value')
                    }
                }
            }

            dependencies {
                testResolve project(':directProducer')
            }

            tasks.register('resolve', Resolve) {
                artifacts.from(configurations.testResolve)
                expectations = [ 'directProducer-1.0.jar', 'transitiveProducer-1.0.jar' ]
            }
            '''

        expect:
        succeeds(':consumer:resolve')
    }

    def 'default directDependency resolution with shared attribute on artifactView but not configuration still produces default jar files for direct and transitive'() {
        consumerBuildFile << '''
            configurations {
                testResolve
            }

            dependencies {
                testResolve project(':directProducer')
            }

            // The artifactView here does not produce any change to the resolved files from the configuration
            tasks.register('resolve', Resolve) {
                artifacts.from(configurations.testResolve.incoming.artifactView {
                    attributes {
                        attribute(shared, 'shared-value')
                    }
                }.files)
                expectations = [ 'directProducer-1.0.jar', 'transitiveProducer-1.0.jar' ]
            }
            '''

        expect:
        succeeds(':consumer:resolve')
    }

    def 'default directDependency resolution with red attribute still produces default jar files for direct and transitive'() {
        consumerBuildFile << '''
            configurations {
                testResolve
            }

            dependencies {
                testResolve project(':directProducer')
            }

            // The artifactView here does not produce any change to the resolved files from the configuration
            tasks.register('resolve', Resolve) {
                artifacts.from(configurations.testResolve.incoming.artifactView {
                    attributes {
                        attribute(color, 'red')
                    }
                }.files)
                expectations = [ 'directProducer-1.0.jar', 'transitiveProducer-1.0.jar' ]
            }
            '''

        expect:
        succeeds(':consumer:resolve')
    }

    def 'default directDependency resolution with shared attribute on configuration and red attribute on artifact view should refine selection'() {
        consumerBuildFile << '''
            configurations {
                testResolve {
                    attributes {
                        attribute(shared, 'shared-value')
                    }
                }
            }

            dependencies {
                testResolve project(':directProducer')
            }

            tasks.register('resolve', Resolve) {
                artifacts.from(configurations.testResolve.incoming.artifactView {
                    attributes {
                        attribute(color, 'red')
                    }
                }.files)
                expectations = [ 'directProducer-1.0.jar', 'transitiveProducer-1.0.jar' ]
            }
            '''

        expect:
        succeeds(':consumer:resolve')
    }
}
