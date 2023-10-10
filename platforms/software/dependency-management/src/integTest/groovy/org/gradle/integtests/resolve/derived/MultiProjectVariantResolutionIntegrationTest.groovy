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

package org.gradle.integtests.resolve.derived

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class MultiProjectVariantResolutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        multiProjectBuild('root', ['producer', 'direct', 'transitive', 'consumer']) {
            buildFile << '''

            '''

            defineVariants(file('producer'))

            file('consumer/build.gradle') << '''
                configurations {
                    producerArtifacts {
                        canBeConsumed = false
                        assert canBeResolved

                        attributes {
                            attribute(Attribute.of('shared', String), 'shared-value')
                            attribute(Attribute.of('unique', String), 'jar-value')
                        }
                    }
                }

                dependencies {
                    producerArtifacts project(':producer')
                }

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

                tasks.register('resolve', Resolve) {
                    artifacts.from(configurations.producerArtifacts)
                }

                tasks.register('resolveJavadoc', Resolve) {
                    artifacts.from(configurations.producerArtifacts.incoming.artifactView {
                        withVariantReselection()
                        attributes {
                            attribute(Attribute.of('shared', String), 'shared-value')
                            attribute(Attribute.of('unique', String), 'javadoc-value')
                        }
                    }.files)
                }

                tasks.register('resolveOther', Resolve) {
                    artifacts.from(configurations.producerArtifacts.incoming.artifactView {
                        withVariantReselection()
                        attributes {
                            attribute(Attribute.of('other', String), 'foobar')
                        }
                    }.files)
                }
                
                tasks.register('resolveAll', Resolve) {
                    artifacts.from(configurations.producerArtifacts)
                    artifacts.from(configurations.producerArtifacts.incoming.artifactView {
                        withVariantReselection()
                        attributes {
                            attribute(Attribute.of('shared', String), 'shared-value')
                            attribute(Attribute.of('unique', String), 'javadoc-value')
                        }
                    }.files)
                    artifacts.from(configurations.producerArtifacts.incoming.artifactView {
                        withVariantReselection()
                        attributes {
                            attribute(Attribute.of('other', String), 'foobar')
                        }
                    }.files)
                }
            '''
        }
    }

    void defineVariants(TestFile projectDir) {
        projectDir.file(projectDir.name + "-jar.txt").text = "jar file"
        projectDir.file(projectDir.name + "-javadoc.txt").text = "javadoc file"
        projectDir.file(projectDir.name + "-producer/other.txt").text = "other file"

        projectDir.file('build.gradle') << '''
            configurations {
                jarElements {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Attribute.of('shared', String), 'shared-value')
                        attribute(Attribute.of('unique', String), 'jar-value')
                    }

                    outgoing {
                        artifact(layout.projectDirectory.file(project.name + '-jar.txt'))
                    }
                }
                javadocElements {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Attribute.of('shared', String), 'shared-value')
                        attribute(Attribute.of('unique', String), 'javadoc-value')
                    }

                    outgoing {
                        artifact(layout.projectDirectory.file(project.name + '-javadoc.txt'))
                    }
                }
                otherElements {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Attribute.of('other', String), 'foobar')
                    }

                    outgoing {
                        artifact(layout.projectDirectory.file(project.name + '-other.txt'))
                    }
                }
            }
        '''
    }

    def 'producer has expected outgoingVariants'() {
        when:
        succeeds(':producer:outgoingVariants')

        then:
        result.groupedOutput.task(':producer:outgoingVariants').assertOutputContains('''--------------------------------------------------
Variant jarElements
--------------------------------------------------

Capabilities
    - org.test:producer:1.0 (default capability)
Attributes
    - shared = shared-value
    - unique = jar-value
Artifacts
    - producer-jar.txt

--------------------------------------------------
Variant javadocElements
--------------------------------------------------

Capabilities
    - org.test:producer:1.0 (default capability)
Attributes
    - shared = shared-value
    - unique = javadoc-value
Artifacts
    - producer-javadoc.txt

--------------------------------------------------
Variant otherElements
--------------------------------------------------

Capabilities
    - org.test:producer:1.0 (default capability)
Attributes
    - other = foobar
Artifacts
    - producer-other.txt''')
    }

    def 'consumer resolves jar variant of producer'() {
        file('consumer/build.gradle') << '''
            resolve {
                expectations = [ 'producer-jar.txt' ]
            }
        '''
        expect:
        succeeds(':consumer:resolve')
    }

    def 'consumer resolves javadoc variant of producer'() {
        file('consumer/build.gradle') << '''
            resolveJavadoc {
                expectations = [ 'producer-javadoc.txt' ]
            }
        '''
        expect:
        succeeds(':consumer:resolveJavadoc')
    }

    def 'consumer resolves other variant of producer'() {
        file('consumer/build.gradle') << '''
            resolveOther {
                expectations = [ 'producer-other.txt' ]
            }
        '''
        expect:
        succeeds(':consumer:resolveOther')
    }

    def 'consumer resolves all variants of producer'() {
        file('consumer/build.gradle') << '''
            resolveAll {
                expectations = [ 'producer-jar.txt', 'producer-javadoc.txt', 'producer-other.txt' ]
            }
        '''
        expect:
        succeeds(':consumer:resolveAll')
    }

    def 'consumer resolves jar variant of producer with dependencies'() {
        defineVariants(file('transitive'))

        defineVariants(file('direct'))
        file('direct/build.gradle') << '''
            dependencies {
                jarElements project(":transitive")
            }
        '''

        file('producer/build.gradle') << '''
            dependencies {
                jarElements project(":direct")
            }
        '''
        file('consumer/build.gradle') << '''
            resolve {
                expectations = ['producer-jar.txt', 'direct-jar.txt', 'transitive-jar.txt']
            }
        '''
        expect:
        succeeds(':consumer:resolve')
    }

    def 'consumer resolves javadoc variant of producer with dependencies on jarElements'() {
        defineVariants(file('transitive'))

        defineVariants(file('direct'))
        file('direct/build.gradle') << '''
            dependencies {
                jarElements project(":transitive")
            }
        '''

        file('producer/build.gradle') << '''
            dependencies {
                jarElements project(":direct")
            }
        '''
        file('consumer/build.gradle') << '''
            resolveJavadoc {
                expectations = ['producer-javadoc.txt', 'direct-javadoc.txt', 'transitive-javadoc.txt']
            }
        '''
        expect:
        succeeds(':consumer:resolveJavadoc')
    }

    def 'consumer resolves other variant of producer with dependencies on jarElements'() {
        defineVariants(file('transitive'))

        defineVariants(file('direct'))
        file('direct/build.gradle') << '''
            dependencies {
                jarElements project(":transitive")
            }
        '''

        file('producer/build.gradle') << '''
            dependencies {
                jarElements project(":direct")
            }
        '''
        file('consumer/build.gradle') << '''
            resolveOther {
                expectations = ['producer-other.txt', 'direct-other.txt', 'transitive-other.txt']
            }
        '''
        expect:
        succeeds(':consumer:resolveOther')
    }

    def 'consumer resolves other variant of producer with dependencies on otherElements'() {
        defineVariants(file('transitive'))

        defineVariants(file('direct'))
        file('direct/build.gradle') << '''
            dependencies {
                otherElements project(":transitive")
            }
        '''

        file('producer/build.gradle') << '''
            dependencies {
                otherElements project(":direct")
            }
        '''
        file('consumer/build.gradle') << '''
            resolveOther {
                expectations = ['producer-other.txt']
            }
        '''
        expect:
        succeeds(':consumer:resolveOther')
    }
}
