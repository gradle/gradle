/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.test.fixtures.publish.Identifier
import spock.lang.Unroll

class IvyPublishIdentifierValidationIntegTest extends AbstractIvyPublishIntegTest {

    @Unroll
    def "can publish with project coordinates containing #title characters"() {
        given:
        file("content-file") << "some content"
        def organisation = identifier.safeForFileName().decorate("org")
        def moduleName = identifier.safeForFileName().decorate("module")
        def version = identifier.safeForFileName().decorate("revision")
        def description = identifier.decorate("description")
        def module = ivyRepo.module(organisation, moduleName, version)

        settingsFile.text = "rootProject.name = '${sq(moduleName)}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = '${sq(organisation)}'
            version = '${sq(version)}'

            println project.version

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        descriptor.withXml {
                            asNode().info[0].appendNode('description', '${sq(description)}')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.ivy.description == description.toString()

        and:
        resolveArtifacts(module) == [moduleName + '-' + version + '.jar']

        where:
        title        | identifier
        "punctuation"| Identifier.punctuation
        "non-ascii"  | Identifier.nonAscii
        "whitespace" | Identifier.whiteSpace
        "filesystem" | Identifier.fileSystemReserved
        "xml markup" | Identifier.xmlMarkup
    }

    @Unroll
    def "can publish artifacts with attributes containing #title characters"() {
        given:
        file("content-file") << "some content"

        def organisation = identifier.safeForFileName().decorate("org")
        def moduleName = identifier.safeForFileName().decorate("module")
        def version = identifier.safeForFileName().decorate("revision")
        def module = ivyRepo.module(organisation, moduleName, version)

        def artifact = identifier.safeForFileName().decorate("artifact")
        def extension = identifier.safeForFileName().decorate("extension")
        def type = identifier.safeForFileName().decorate("type")
        def conf = identifier.safeForFileName().decorate("conf").replace(",", "")
        def classifier = identifier.safeForFileName().decorate("classifier")

        settingsFile.text = "rootProject.name = '${sq(moduleName)}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'

            group = '${sq(organisation)}'
            version = '${sq(version)}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        configurations.create('${sq(conf)}')
                        artifact source: 'content-file', name: '${sq(artifact)}', extension: '${sq(extension)}', type: '${sq(type)}', conf: '${sq(conf)}', classifier: '${sq(classifier)}'
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-${version}.xml", "${artifact}-${version}-${classifier}.${extension}")

        and:
        resolveArtifacts(module, conf) == ["${artifact}-${version}-${classifier}.${extension}"]

        where:
        title        | identifier
        "punctuation"| Identifier.punctuation
        "non-ascii"  | Identifier.nonAscii
        "whitespace" | Identifier.whiteSpace
        "filesystem" | Identifier.fileSystemReserved
        "xml markup" | Identifier.xmlMarkup
    }

    def "fails with reasonable error message for invalid identifier value"() {
        buildFile << """
            apply plugin: 'ivy-publish'

            group = ''
            version = ''

            publishing {
                repositories {
                    ivy { url "${mavenRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication)
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription "Execution failed for task ':publishIvyPublicationToIvyRepository'."
        failure.assertHasCause "Failed to publish publication 'ivy' to repository 'ivy'"
        failure.assertHasCause "Invalid publication 'ivy': organisation cannot be empty."
    }

}
