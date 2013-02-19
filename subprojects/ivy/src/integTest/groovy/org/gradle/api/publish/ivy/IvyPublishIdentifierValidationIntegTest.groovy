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

import spock.lang.Unroll

class IvyPublishIdentifierValidationIntegTest extends AbstractIvyPublishIntegTest {
    private static final String PUNCTUATION_CHARS = '-!@#$%^&*()_+=,.?{}[]<>'
    private static final String NON_ASCII_CHARS = '-√æず∫ʙぴ₦ガき∆ç√∫'
    private static final String FILESYSTEM_RESERVED_CHARS = '-/\\?%*:|"<>.'
    private static final String XML_MARKUP_CHARS = '-<with>some<xml-markup/></with>'

    // TODO:DAZ These are currently unsupported for filesystem repositories. Fix or prevent with validation.
    private static final String UNSUPPORTED_CHARS = '/\\?%*:|"<>.'

    @Unroll
    def "can publish with project coordinates containing #title characters"() {
        given:
        file("content-file") << "some content"
        def organisation = "organisation${suffix}"
        def moduleName = "module${suffix}"
        def version = "revision${suffix}"
        def description = "description${suffix}"
        def module = ivyRepo.module(organisation, moduleName, version)

        settingsFile.text = "rootProject.name = '${moduleName}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = '${organisation}'
            version = '${version}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        descriptor.withXml {
                            asNode().info[0].appendNode('description', '${description}')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.ivy.description == description

        and:
        resolveArtifacts(module) == ["${moduleName}-${version}.jar"]

        where:
        title        | suffix
        "punctuation"| removeUnsupported(PUNCTUATION_CHARS)
        "non-ascii"  | removeUnsupported(NON_ASCII_CHARS)
        "whitespace" | " with spaces"
        "filesystem" | removeUnsupported(FILESYSTEM_RESERVED_CHARS)
        "xml markup" | removeUnsupported(XML_MARKUP_CHARS)
    }

    @Unroll
    def "can publish artifacts with attributes containing #title characters"() {
        given:
        file("content-file") << "some content"

        def organisation = "organisation${nameSuffix}"
        def moduleName = "module${nameSuffix}"
        def version = "revision${nameSuffix}"
        def module = ivyRepo.module(organisation, moduleName, version)

        def artifact = "artifact${nameSuffix}"
        def extension = "extension${nameSuffix}"
        def type = "type${nameSuffix}"
        def conf = "conf${nameSuffix}".replace(",", "") // conf uses ',' as a delimiter
        def classifier = "classifier${nameSuffix}"

        settingsFile.text = "rootProject.name = '${moduleName}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'

            group = '${organisation}'
            version = '${version}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        configurations.create('${conf}')
                        artifact source: 'content-file', name: '${artifact}', extension: '${extension}', type: '${type}', conf: '${conf}', classifier: '${classifier}'
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
        title        | nameSuffix
        "punctuation"| removeUnsupported(PUNCTUATION_CHARS)
        "non-ascii"  | removeUnsupported(NON_ASCII_CHARS)
        "whitespace" | " with spaces"
        "filesystem" | removeUnsupported(FILESYSTEM_RESERVED_CHARS)
        "xml markup" | removeUnsupported(XML_MARKUP_CHARS)
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
        failure.assertHasDescription "Execution failed for task ':publishIvyPublicationToIvyRepository'"
        failure.assertHasCause "Failed to publish publication 'ivy' to repository 'ivy'"
        failure.assertHasCause "Invalid publication 'ivy': organisation cannot be empty."
    }

    def removeUnsupported(String characterList) {
        String output = characterList
        for (char unsupportedChar  : UNSUPPORTED_CHARS.chars) {
            output = output.replace(String.valueOf(unsupportedChar), '')
        }
        return output
    }

}
