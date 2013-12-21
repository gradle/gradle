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
import org.gradle.test.fixtures.encoding.Identifier
import spock.lang.Unroll

class MavenPublishIdentifierValidationIntegTest extends AbstractMavenPublishIntegTest {

    // Group and Artifact are restricted to [A-Za-z0-9_\-.]+ by org.apache.maven.project.validation.DefaultModelValidator
    def groupId = 'a-valid.group'
    def artifactId = 'valid_artifact.name'

    @Unroll
    def "can publish with version and description containing #title characters"() {
        given:
        def version = identifier.safeForFileName().decorate("version")
        def description = identifier.decorate("description")
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = '${sq(version)}'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        pom.withXml {
                            asNode().appendNode('description', '${sq(description)}')
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
        title        | identifier
        "punctuation"| Identifier.punctuation
        "non-ascii"  | Identifier.nonAscii
        "whitespace" | Identifier.whiteSpace
        "filesystem" | Identifier.fileSystemReserved
        "xml markup" | Identifier.xmlMarkup
    }

    @Unroll
    def "can publish artifacts with version, extension and classifier containing #title characters"() {
        given:
        file("content-file") << "some content"
        def version = identifier.safeForFileName().decorate("version")
        def extension = identifier.safeForFileName().decorate("extension")
        def classifier = identifier.safeForFileName().decorate("classifier")

        and:
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = '${sq(version)}'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        artifact source: 'content-file', extension: '${sq(extension)}', classifier: '${sq(classifier)}'
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
        resolveArtifact(module, extension, classifier) == ["${artifactId}-${version}-${classifier}.${extension}"]

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
        failure.assertHasDescription "Execution failed for task ':publishMavenPublicationToMavenRepository'."
        failure.assertHasCause "Failed to publish publication 'maven' to repository 'maven'"
        failure.assertHasCause "Invalid publication 'maven': groupId cannot be empty"
    }
}
