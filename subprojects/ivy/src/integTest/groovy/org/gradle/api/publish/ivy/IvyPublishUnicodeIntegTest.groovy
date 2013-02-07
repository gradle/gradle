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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

/**
 * Tests maven POM customisation
 */
class IvyPublishUnicodeIntegTest extends AbstractIntegrationSpec {

    @Unroll
    def "can publish with version and description containing #title characters"() {
        given:
        file("content-file") << "some content"
        def module = ivyRepo.module(organisation, moduleName, version)

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
                        descriptor.withXml {
                            asNode().info[0].appendNode('description', "${description}")
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

        where:
        title        | organisation      | moduleName               | version               | description
        "non-ascii"  | "org-√æず"         | "module-∫ʙぴ"         | "version-₦ガき∆"        | "description-ç√∫"
        "whitespace" | "org with spaces" | "module with spaces" | "version with spaces" | "description with spaces"

        // TODO:DAZ This doesn't currently work: fix this or explicitly fail in validation story
//        "xml markup" "<version-html/>" | "Description <b>with</b> markup"
    }

    @Unroll
    def "can publish artifacts with version, extension and type containing #title characters"() {
        given:
        file("content-file") << "some content"
        def module = ivyRepo.module(organisation, moduleName, version)

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
                        configurations {
                            custom {
                                artifact file: "content-file", name: "${artifact}", extension: "${extension}", type: "${type}"
                            }
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-${version}.xml", "${artifact}-${version}.${extension}")

        // TODO:DAZ validate type in ivy file

        where:
        title        | organisation      | moduleName           | version               | artifact               | extension         | type
        "non-ascii"  | "org-√æず"        | "module-∫ʙぴ"        | "version-₦ガき∆"       | "artifact-ç√∫"         | "ext-ç√∫"         | "type-ç√∫"
        "whitespace" | "org with spaces" | "module with spaces" | "version with spaces" | "artifact with spaces" | "ext with spaces" | "type with spaces"
    }
}
