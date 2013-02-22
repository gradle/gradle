/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ivy.IvyDescriptor

class IvyPublishDescriptorCustomisationIntegTest extends AbstractIntegrationSpec {

    def module = ivyRepo.module("org.gradle", "publish", "2")

    def setup() {
        settingsFile << """
            rootProject.name = "${module.module}"
        """

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '${module.revision}'
            group = '${module.organisation}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """
    }

    def "can customise descriptor xml during publication"() {
        when:
        succeeds 'publish'

        then:
        ":jar" in executedTasks

        and:
        module.ivy.revision == "2"

        when:
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor {
                            withXml {
                                asNode().info[0].appendNode('description', 'Customized descriptor')
                            }
                        }
                    }
                }
            }
        """
        succeeds 'publish'


        then:
        ":jar" in skippedTasks

        and:
        module.ivy.description == "Customized descriptor"
    }

    def "can generate ivy.xml without publishing"() {
        given:
        def moduleName = module.module
        buildFile << """
            generateIvyModuleDescriptor {
                destination = 'generated-ivy.xml'
            }
        """

        when:
        succeeds 'generateIvyModuleDescriptor'

        then:
        file('generated-ivy.xml').assertIsFile()
        IvyDescriptor ivy = new IvyDescriptor(file('generated-ivy.xml'))
        ivy.expectArtifact(moduleName).hasAttributes("jar", "jar", ["runtime"])
        module.ivyFile.assertDoesNotExist()
    }

    def "produces sensible error when withXML fails"() {
        when:
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor.withXml {
                            asNode().foo = "3"
                        }
                    }
                }
            }
        """
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':generateIvyModuleDescriptor'")
        failure.assertHasCause("Could not apply withXml() to Ivy module descriptor")
        failure.assertHasCause("No such property: foo for class: groovy.util.Node")
    }

    def "produces sensible error when withXML modifies publication coordinates"() {
        when:
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor.withXml {
                            asNode().info[0].@revision = "2.1"
                        }
                    }
                }
            }
        """
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Invalid publication 'ivy': supplied revision does not match ivy descriptor (cannot edit revision directly in the ivy descriptor file).")
    }
}
