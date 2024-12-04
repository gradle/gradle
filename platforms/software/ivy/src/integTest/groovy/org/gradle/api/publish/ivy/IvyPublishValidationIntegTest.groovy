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

import org.gradle.test.fixtures.encoding.Identifier

import javax.xml.namespace.QName

class IvyPublishValidationIntegTest extends AbstractIvyPublishIntegTest {

    def "can publish with metadata containing #identifier characters"() {
        given:
        file("content-file") << "some content"
        def organisation = identifier.safeForFileName().decorate("org")
        def moduleName = identifier.safeForGradleDomainObjectName().decorate("module")
        def version = identifier.safeForFileName().decorate("revision")
        def extraValue = identifier.decorate("extra")
        def resolver = identifier.decorate("description")
        def branch = identifier.safeForBranch().decorate("branch")
        def status = identifier.safeForFileName().decorate("status")
        def module = ivyRepo.module(organisation, moduleName, version)

        settingsFile << "rootProject.name = '${sq(moduleName)}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = '${sq(organisation)}'
            version = '${sq(version)}'

            println project.version
            println '${sq(branch)}'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        descriptor.branch = '${sq(branch)}'
                        descriptor.status = '${sq(status)}'
                        descriptor.extraInfo 'http://my.extra.info1', 'foo', '${sq(extraValue)}'
                        descriptor.extraInfo 'http://my.extra.info2', 'bar', '${sq(extraValue)}'
                        descriptor.withXml {
                            asNode().info[0].@resolver = '${sq(resolver)}'
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.parsedIvy.resolver == resolver.toString()
        module.parsedIvy.extraInfo == [
                (new QName('http://my.extra.info1', 'foo')): extraValue.toString(),
                (new QName('http://my.extra.info2', 'bar')): extraValue.toString(),
        ]

        and:
        resolveArtifacts(module) {
            setStatus(status)
            expectFiles "${moduleName}-${version}.jar"
        }

        where:
        identifier << Identifier.all
    }

    def "can publish artifacts with attributes containing #identifier characters"() {
        given:
        file("content-file") << "some content"

        def organisation = identifier.safeForFileName().decorate("org")
        def moduleName = identifier.safeForGradleDomainObjectName().decorate("module")
        def version = identifier.safeForFileName().decorate("revision")
        def module = ivyRepo.module(organisation, moduleName, version)

        def artifact = identifier.safeForFileName().decorate("artifact")
        def extension = identifier.safeForFileName().decorate("extension")
        def type = identifier.safeForFileName().decorate("type")
        def conf = identifier.safeForFileName().decorate("conf").replace(",", "")
        def classifier = identifier.safeForFileName().decorate("classifier")

        settingsFile << "rootProject.name = '${sq(moduleName)}'"
        buildFile.text = """
            apply plugin: 'ivy-publish'

            group = '${sq(organisation)}'
            version = '${sq(version)}'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
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
        resolveArtifacts(module) {
            configuration = conf
            withoutModuleMetadata {
                expectFiles "${artifact}-${version}-${classifier}.${extension}"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }

        where:
        identifier << Identifier.all
    }

    def "fails with reasonable error message for invalid identifier value"() {
        buildFile << """
            apply plugin: 'ivy-publish'

            group = ''
            version = ''

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
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

    def "fails with reasonable error message for invalid metadata value" () {
        when:
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'org'
            version = '2'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        descriptor {
                            ${metadata}
                        }
                    }
                }
            }
        """
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause(message)

        where:
        metadata        | message
        "branch = ''"     | "Invalid publication 'ivy': branch cannot be an empty string. Use null instead"
        "branch = 'a\tb'" | "Invalid publication 'ivy': branch cannot contain ISO control character '\\u0009'"
        "status = ''"     | "Invalid publication 'ivy': status cannot be an empty string. Use null instead"
        "status = 'a\tb'" | "Invalid publication 'ivy': status cannot contain ISO control character '\\u0009'"
        "status = 'a/b'"  | "Invalid publication 'ivy': status cannot contain '/'"
    }

    def "fails with reasonable error message for invalid #invalidComponent name"() {
        settingsFile << "rootProject.name = 'invalid'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'org'
            version = '2'

            publishing {
                repositories {
                    ivy {
                        name = '${repoName}'
                        url = "${ivyRepo.uri}"
                    }
                }
                publications {
                    "${publicationName}"(IvyPublication)
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription "A problem occurred configuring root project 'invalid'"
        failure.assertHasCause "${invalidComponent} name 'bad:name' is not valid for publication. Must match regex [A-Za-z0-9_\\-.]+"

        where:
        invalidComponent | repoName    | publicationName
        "Repository"     | "bad:name"  | "mavenPub"
        "Publication"    | "mavenRepo" | "bad:name"
    }

}
