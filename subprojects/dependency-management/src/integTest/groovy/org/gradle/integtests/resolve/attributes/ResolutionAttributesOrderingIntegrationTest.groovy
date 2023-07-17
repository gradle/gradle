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

package org.gradle.integtests.resolve.attributes


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests for ArtifactView that ensure it uses the "live" attributes of the configuration that created it to select files and artifacts.
 */
class ResolutionAttributesOrderingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """
        file("producer/build.gradle") << """
            def usage = Attribute.of('usage', String)
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)

            configurations {
                correctElements {
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(usage, 'usage-value')
                        attribute(buildType, 'buildType-value')
                    }
                    outgoing {
                        artifact file("wrong")
                        variants {
                            wrong {
                                attributes {
                                    attribute(flavor, 'flavor-value')
                                }
                                artifact file("correct")
                            }
                        }
                    }
                }
            }


        """

        buildFile << """
            def usage = Attribute.of('usage', String)
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)

            configurations {
                conf {
                    attributes {
                        attribute(usage, 'usage-value')
                        attribute(buildType, 'buildType-value')
                    }
                }
            }

            task verifyFiles(type: FilesVerificationTask) {
                incomingFiles.from(configurations.conf.incoming.artifactView { }.files)
            }

            configurations {
                conf {
                    attributes {
                        attribute(flavor, 'flavor-value')
                    }
                }
            }

            dependencies {
                conf project(':producer')
            }

            abstract class FilesVerificationTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getIncomingFiles()

                @TaskAction
                def verify() {
                    assert incomingFiles*.name == ["correct"]
                }
            }
        """
    }

    def "resolve the correct files through artifactView"() {
        expect:
        succeeds ':verifyFiles'
    }
}
