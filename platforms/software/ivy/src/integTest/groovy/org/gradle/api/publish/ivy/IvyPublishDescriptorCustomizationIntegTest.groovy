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

import org.gradle.test.fixtures.ivy.IvyDescriptor

import javax.xml.namespace.QName

class IvyPublishDescriptorCustomizationIntegTest extends AbstractIvyPublishIntegTest {

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
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """
    }

    def "can customize descriptor xml during publication"() {
        when:
        succeeds 'publish'

        then:
        executed(":jar")

        and:
        module.parsedIvy.revision == "2"

        when:
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor {
                            status = "custom-status"
                            branch = "custom-branch"
                            license {
                                name = 'The Apache License, Version 2.0'
                                url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                            }
                            author {
                                name = 'Jane Doe'
                                url = 'http://example.com/users/jane'
                            }
                            description {
                                text = 'A concise description of my library'
                                homepage = 'http://www.example.com/library'
                            }
                            extraInfo 'http://my.extra.info1', 'foo', 'fooValue'
                            extraInfo 'http://my.extra.info2', 'bar', 'barValue'
                            withXml {
                                asNode().info[0].@resolver = 'test'
                            }
                        }
                    }
                }
            }
        """
        succeeds 'publish'

        then:
        skipped(":jar")

        and:
        with (module.parsedIvy) {
            resolver == "test"
            status == "custom-status"
            branch == "custom-branch"
            licenses.size() == 1
            licenses[0].@name == 'The Apache License, Version 2.0'
            licenses[0].@url == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
            authors.size() == 1
            authors[0].@name == 'Jane Doe'
            authors[0].@url == 'http://example.com/users/jane'
            description.text() == "A concise description of my library"
            description.@homepage == 'http://www.example.com/library'
            extraInfo.size() == 2
            extraInfo[new QName('http://my.extra.info1', 'foo')] == 'fooValue'
            extraInfo[new QName('http://my.extra.info2', 'bar')] == 'barValue'
        }
    }

    def "can generate ivy.xml without publishing"() {
        given:
        def moduleName = module.module
        buildFile << """
            model {
                tasks.generateDescriptorFileForIvyPublication {
                    destination = 'generated-ivy.xml'
                }
            }
        """

        when:
        succeeds 'generateDescriptorFileForIvyPublication'

        then:
        file('generated-ivy.xml').assertIsFile()
        IvyDescriptor ivy = new IvyDescriptor(file('generated-ivy.xml'))
        ivy.expectArtifact(moduleName).hasAttributes("jar", "jar", ["compile", "runtime"])
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
        failure.assertHasDescription("Execution failed for task ':generateDescriptorFileForIvyPublication'.")
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(23)
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
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Invalid publication 'ivy': supplied revision does not match ivy descriptor (cannot edit revision directly in the ivy descriptor file).")
    }

    def "produces sensible error with invalid extra info elements" () {
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor {
                            extraInfo 'http://my.extra.info', '${sq(name)}', 'fooValue'
                        }
                    }
                }
            }
        """

        when:
        fails 'publish'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'publish'.")
        failure.assertHasCause("Invalid ivy extra info element name: '${name}'")

        where:
        name        | _
        ''          | _
        'foo\\n'    | _
        'foo<'      | _
        '1foo'      | _
     }

    def "produces sensible error with extra info containing null values" () {
        buildFile << """
            publishing {
                publications {
                    ivy {
                        descriptor {
                            extraInfo ${namespace}, ${name}, 'fooValue'
                        }
                    }
                }
            }
        """

        when:
        fails 'publish'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'publish'.")
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(23)
        failure.assertHasCause("Cannot add an extra info element with null ")

        where:
        namespace                | name
        null                     | "'foo'"
        "'http://my.extra.info'" | null
    }

    def "withXml should not loose Gradle metadata marker"() {
        buildFile << """
            publishing {
                repositories {
                    ivy { url = "${mavenRepo.uri}" }
                }
                publications {
                    ivy {
                        descriptor.withXml {
                           asNode().info[0].@resolver = 'wonderland'
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.hasGradleMetadataRedirectionMarker()
        def parsedIvy = module.parsedIvy
        parsedIvy.resolver == 'wonderland'
    }
}
