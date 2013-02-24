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



package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule
/**
 * Tests maven POM customisation
 */
class MavenPublishPomCustomisationIntegTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def "can customise pom xml"() {
        given:
        settingsFile << "rootProject.name = 'customisePom'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    mavenCustom(MavenPublication) {
                        pom.withXml {
                            asNode().appendNode('description', "custom-description")

                            def dependency = asNode().appendNode('dependencies').appendNode('dependency')
                            dependency.appendNode('groupId', 'junit')
                            dependency.appendNode('artifactId', 'junit')
                            dependency.appendNode('version', '4.11')
                            dependency.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'customisePom', '1.0')
        module.assertPublished()
        module.parsedPom.description == 'custom-description'
        module.parsedPom.scopes.runtime.assertDependsOn("junit:junit:4.11")
    }

    def "can generate pom file without publishing"() {
        given:
        settingsFile << "rootProject.name = 'generatePom'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    emptyMaven(MavenPublication) {
                        pom.withXml {
                            asNode().appendNode('description', "Test for pom generation")
                        }
                    }
                }
            }
            generatePomFileForEmptyMavenPublication {
                destination = 'build/generated-pom.xml'
            }
        """

        when:
        run "generatePomFileForEmptyMavenPublication"

        then:
        def mavenModule = mavenRepo.module("org.gradle.test", "generatePom", "1.0")
        mavenModule.assertNotPublished()

        and:
        file('build/generated-pom.xml').assertIsFile()
        def pom = new org.gradle.test.fixtures.maven.MavenPom(file('build/generated-pom.xml'))
        pom.groupId == "org.gradle.test"
        pom.artifactId == "generatePom"
        pom.version == "1.0"
        pom.description == "Test for pom generation"
    }

    def "has reasonable error message when withXml fails"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        pom.withXml {
                            asNode().foo = 'this is not a real element'
                        }
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':generatePomFileForMavenPublication'")
        failure.assertHasCause("Could not apply withXml() to generated POM")
        failure.assertHasCause("No such property: foo for class: groovy.util.Node")
    }

    def "has reasonable error message when withXml produces invalid POM file"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        pom.withXml {
                            asNode().appendNode('invalid-node', "This is not a valid node for a Maven POM")
                        }
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenPublicationToMavenRepository'")
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Invalid publication 'maven': POM file is invalid. Check any modifications you have made to the POM file.")
    }
}
