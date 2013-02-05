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



package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

/**
 * Tests maven POM customisation
 */
class MavenPublishUnicodeIntegTest extends AbstractIntegrationSpec {

    @Unroll
    def "can publish with version and description containing #title characters"() {
        // Group and Artifact are restricted to [A-Za-z0-9_\\-.]+ by org.apache.maven.project.validation.DefaultModelValidator
        def groupId = 'group'
        def artifactId = 'artifact'

        given:
        file("content-file") << "some content"
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = '${version}'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        pom.withXml {
                            asNode().appendNode('description', "${description}")
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module(groupId, artifactId, version)
        module.assertPublishedAsPomModule()
        module.parsedPom.description == description

        where:
        title        | version               | description
        "non-ascii"  | "version-₦ガき∆"        | "description-ç√∫"
        "whitespace" | "version with spaces" | "description with spaces"

        // TODO:DAZ This doesn't currently work: fix this or explicitly fail in validation story
//        "xml markup" "<version-html/>" | "Description <b>with</b> markup"
    }

    @Unroll
    def "can publish artifacts with version, extension and classifier containing #title characters"() {
        // Group and Artifact are restricted to [A-Za-z0-9_\\-.]+ by org.apache.maven.project.validation.DefaultModelValidator
        def groupId = 'group'
        def artifactId = 'artifact'


        given:
        file("content-file") << "some content"
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = '${version}'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        artifact file: "content-file", extension: "${extension}", classifier: "${classifier}"
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module(groupId, artifactId, version)
        module.assertPublished()

        module.assertArtifactsPublished("${artifactId}-${version}.pom", "${artifactId}-${version}-${classifier}.${extension}")

        where:
        title        | version               | extension               | classifier
        "non-ascii"  | "version-₦ガき∆"        | "extension-ç√∫"         | "classifier-₦ガき"
        "whitespace" | "version with spaces" | "extension with spaces" | "classifier with spaces"

        // TODO:DAZ HTML markup - get it working or validate early
//        "markup"     | "version <with/> markup" | "extension <with/> markup" | "classifier <with/> markup"
    }
}
