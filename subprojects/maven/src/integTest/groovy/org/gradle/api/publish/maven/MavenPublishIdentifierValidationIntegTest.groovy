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
import org.gradle.test.fixtures.maven.MavenFileModule
import spock.lang.Unroll

class MavenPublishIdentifierValidationIntegTest extends AbstractIntegrationSpec {
    // Group and Artifact are restricted to [A-Za-z0-9_\\-.]+ by org.apache.maven.project.validation.DefaultModelValidator
    def groupId = 'a-valid.group'
    def artifactId = 'valid_artifact.name'

    @Unroll
    def "can publish with version and description containing #title characters"() {
        given:
        def version = "version${suffix}"
        def description = "description${suffix}"
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = "${version}"

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
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
        module.assertPublishedAsJavaModule()
        module.parsedPom.description == description

        and:
        resolveArtifacts(module) == ["${artifactId}-${version}.jar"]

        where:
        title        | suffix
        "punctuation"| "-#%@*&(){}<>"
        "non-ascii"  | "-₦ガき∆ç√∫"
        "whitespace" | " with spaces"

        // TODO:DAZ These don't work. Fix or prevent with validation
//        "filesystem" | "\\\\ . /"
//        "xml markup" | "<with-xml-markup/>"
    }

    @Unroll
    def "can publish artifacts with version, extension and classifier containing #title characters"() {
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
                        from components.java
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

        module.assertArtifactsPublished("${artifactId}-${version}.pom", "${artifactId}-${version}.jar", "${artifactId}-${version}-${classifier}.${extension}")

        and:
        resolveArtifacts(module, extension, classifier) == ["${artifactId}-${version}-${classifier}.${extension}"]

        where:
        title        | version               | extension               | classifier
        "non-ascii"  | "version-₦ガき∆"        | "extension-ç√∫"         | "classifier-₦ガき"
        "whitespace" | "version with spaces" | "extension with spaces" | "classifier with spaces"

        // TODO:DAZ HTML markup - get it working or validate early
//        "markup"     | "version <with/> markup" | "extension <with/> markup" | "classifier <with/> markup"
    }


    def "fails with reasonable error message for invalid identifier value"() {
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = ''
            version = ''

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication)
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription "Execution failed for task ':publishMavenPublicationToMavenRepository'"
        failure.assertHasCause "Failed to publish publication 'maven' to repository 'maven'"
        failure.assertHasCause "The groupId value cannot be empty"
    }

    private def resolveArtifacts(MavenFileModule module) {
        doResolveArtifacts("group: '${module.groupId}', name: '${module.artifactId}', version: '${module.version}'")
    }

    private def resolveArtifacts(MavenFileModule module, def extension, def classifier) {
        doResolveArtifacts("group: '${module.groupId}', name: '${module.artifactId}', version: '${module.version}', classifier: '${classifier}', ext: '${extension}'")
    }

    private def doResolveArtifacts(def dependency) {
        buildFile << """
            configurations {
                resolve
            }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                resolve $dependency
            }

            task resolveArtifacts(type: Sync) {
                from configurations.resolve
                into "artifacts"
            }

"""
        assert succeeds("resolveArtifacts")
        return file("artifacts").list()
    }
}
