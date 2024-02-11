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


import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.util.SetSystemProperties
import org.junit.Rule

/**
 * Tests maven POM customization
 */
class MavenPublishPomCustomizationIntegTest extends AbstractMavenPublishIntegTest {
    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    def "can customize pom xml"() {
        given:
        settingsFile << "rootProject.name = 'customizePom'"
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
                        pom {
                            packaging = "custom-packaging"
                            name = "custom-name"
                            description = "custom-description"
                            url = "http://example.org/project"
                            inceptionYear = "2018"
                            organization {
                                name = "Example Organization"
                                url = "https://example.org"
                            }
                            licenses {
                                license {
                                    name = "Eclipse Public License v2.0"
                                    url = "http://www.eclipse.org/legal/epl-v20.html"
                                    distribution = "inside artifact"
                                    comments = "Successor of EPL v1.0"
                                }
                            }
                            developers {
                                developer {
                                    id = "foo"
                                    name = "Foo Bar"
                                    email = "foo.bar@example.org"
                                }
                                developer {
                                    id = "baz"
                                    name = "Baz Qux"
                                    email = "baz.qux@example.org"
                                    url = "http://example.org/users/baz.qux"
                                    organization = "Example Organization"
                                    organizationUrl = "https://example.org"
                                    roles = ["tester", "developer"]
                                    timezone = "Europe/Berlin"
                                    properties = ["user": "baz.qux"]
                                }
                            }
                            contributors {
                                contributor {
                                    name = "John Doe"
                                    email = "john.doe@example.org"
                                    url = "http://example.org/users/john.doe"
                                    organization = "Example Organization"
                                    organizationUrl = "https://example.org"
                                    roles = ["tester", "developer"]
                                    timezone = "Europe/Berlin"
                                    properties = ["user": "john.doe"]
                                }
                            }
                            scm {
                                connection = "scm:git:git://example.org/some-repo.git"
                                developerConnection = "scm:git:git://example.org/some-repo.git"
                                url = "https://example.org/some-repo"
                                tag = "v1.0"
                            }
                            issueManagement {
                                system = "Some Issue Tracker"
                                url = "https://issues.example.org/"
                            }
                            ciManagement {
                                system = "Some CI Server"
                                url = "https://ci.example.org/"
                            }
                            distributionManagement {
                                downloadUrl = "https://example.org/download/"
                                relocation {
                                    groupId = "new-group"
                                    artifactId = "new-artifact-id"
                                    version = "42"
                                    message = "the answer to life, the universe and everything"
                                }
                            }
                            mailingLists {
                                mailingList {
                                    name = "Users"
                                    subscribe = "users-subscribe@lists.example.org"
                                    unsubscribe = "users-unsubscribe@lists.example.org"
                                    post = "users@lists.example.org"
                                    archive = "http://lists.example.org/users/"
                                    otherArchives = ["http://archive.org/", "http://backup.example.org/"]
                                }
                                mailingList {
                                    name = "Developers"
                                    post = "devs@lists.example.org"
                                }
                            }
                            properties = [
                                myProp: "myValue",
                                "prop.with.dots": "anotherValue"
                            ]
                            withXml {
                                def dependency = asNode().appendNode('dependencies').appendNode('dependency')
                                dependency.appendNode('groupId', 'junit')
                                dependency.appendNode('artifactId', 'junit')
                                dependency.appendNode('version', '4.13')
                                dependency.appendNode('scope', 'runtime')
                            }
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'customizePom', '1.0')
        module.assertPublished()
        def parsedPom = module.parsedPom
        parsedPom.packaging == 'custom-packaging'
        parsedPom.scopes.runtime.assertDependsOn("junit:junit:4.13")

        and:
        parsedPom.name == 'custom-name'
        parsedPom.description == 'custom-description'
        parsedPom.url == 'http://example.org/project'
        parsedPom.inceptionYear == '2018'
        parsedPom.organization.name.text() == 'Example Organization'
        parsedPom.organization.url.text() == 'https://example.org'
        parsedPom.licenses.size() == 1
        parsedPom.licenses[0].name.text() == 'Eclipse Public License v2.0'
        parsedPom.licenses[0].url.text() == 'http://www.eclipse.org/legal/epl-v20.html'
        parsedPom.licenses[0].distribution.text() == "inside artifact"
        parsedPom.licenses[0].comments.text() == "Successor of EPL v1.0"

        and:
        parsedPom.developers.size() == 2
        parsedPom.developers[0].id.text() == 'foo'
        parsedPom.developers[0].name.text() == 'Foo Bar'
        parsedPom.developers[0].email.text() == 'foo.bar@example.org'
        parsedPom.developers[1].id.text() == 'baz'
        parsedPom.developers[1].name.text() == 'Baz Qux'
        parsedPom.developers[1].email.text() == 'baz.qux@example.org'
        parsedPom.developers[1].url.text() == 'http://example.org/users/baz.qux'
        parsedPom.developers[1].organization.text() == "Example Organization"
        parsedPom.developers[1].organizationUrl.text() == "https://example.org"
        parsedPom.developers[1].roles.role.collect { it.text() } == ["tester", "developer"]
        parsedPom.developers[1].timezone.text() == "Europe/Berlin"
        parsedPom.developers[1].properties.user.text() == "baz.qux"

        and:
        parsedPom.contributors.size() == 1
        parsedPom.contributors[0].name.text() == "John Doe"
        parsedPom.contributors[0].email.text() == "john.doe@example.org"
        parsedPom.contributors[0].url.text() == "http://example.org/users/john.doe"
        parsedPom.contributors[0].organization.text() == "Example Organization"
        parsedPom.contributors[0].organizationUrl.text() == "https://example.org"
        parsedPom.contributors[0].roles.role.collect { it.text() } == ["tester", "developer"]
        parsedPom.contributors[0].timezone.text() == "Europe/Berlin"
        parsedPom.contributors[0].properties.user.text() == "john.doe"

        and:
        parsedPom.scm.connection.text() == "scm:git:git://example.org/some-repo.git"
        parsedPom.scm.developerConnection.text() == "scm:git:git://example.org/some-repo.git"
        parsedPom.scm.url.text() == "https://example.org/some-repo"
        parsedPom.scm.tag.text() == "v1.0"

        and:
        parsedPom.issueManagement.system.text() == "Some Issue Tracker"
        parsedPom.issueManagement.url.text() == "https://issues.example.org/"

        and:
        parsedPom.ciManagement.system.text() == "Some CI Server"
        parsedPom.ciManagement.url.text() == "https://ci.example.org/"

        and:
        parsedPom.distributionManagement.downloadUrl.text() == "https://example.org/download/"
        parsedPom.distributionManagement.relocation[0].groupId.text() == "new-group"
        parsedPom.distributionManagement.relocation[0].artifactId.text() == "new-artifact-id"
        parsedPom.distributionManagement.relocation[0].version.text() == "42"
        parsedPom.distributionManagement.relocation[0].message.text() == "the answer to life, the universe and everything"

        and:
        parsedPom.mailingLists.size() == 2
        parsedPom.mailingLists[0].name.text() == "Users"
        parsedPom.mailingLists[0].subscribe.text() == "users-subscribe@lists.example.org"
        parsedPom.mailingLists[0].unsubscribe.text() == "users-unsubscribe@lists.example.org"
        parsedPom.mailingLists[0].post.text() == "users@lists.example.org"
        parsedPom.mailingLists[0].archive.text() == "http://lists.example.org/users/"
        parsedPom.mailingLists[0].otherArchives.otherArchive.collect { it.text() } == ["http://archive.org/", "http://backup.example.org/"]
        parsedPom.mailingLists[1].name.text() == "Developers"
        parsedPom.mailingLists[1].post.text() == "devs@lists.example.org"

        and:
        parsedPom.properties.children().size() == 2
        parsedPom.properties.myProp.text() == "myValue"
        parsedPom.properties["prop.with.dots"].text() == "anotherValue"
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

            model {
                tasks.generatePomFileForEmptyMavenPublication {
                    destination = 'build/generated-pom.xml'
                }
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
        failure.assertHasDescription("Execution failed for task ':generatePomFileForMavenPublication'.")
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(15)
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
        failure.assertHasDescription("Execution failed for task ':publishMavenPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Invalid publication 'maven': POM file is invalid. Check any modifications you have made to the POM file.")
    }

    def "has reasonable error message when withXML modifies publication coordinates"() {
        when:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        groupId "group"
                        artifactId "artifact"
                        version "1.0"

                        pom.withXml {
                            asNode().version[0].value = "2.0"
                        }
                    }
                }
            }
        """
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Invalid publication 'maven': supplied version (1.0) does not match value from POM file (2.0). Cannot edit version directly in the POM file.")
    }

    def "withXml should not loose Gradle metadata marker"() {
        settingsFile << """
            rootProject.name = 'customizePom'
        """
        buildFile << """
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    mavenCustom(MavenPublication) {
                        from components.java
                        pom.withXml {
                            def dependency = asNode().appendNode('dependencies').appendNode('dependency')
                            dependency.appendNode('groupId', 'junit')
                            dependency.appendNode('artifactId', 'junit')
                            dependency.appendNode('version', '4.13')
                            dependency.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'customizePom', '1.0')
        module.assertPublished()
        module.hasGradleMetadataRedirectionMarker()
        def parsedPom = module.parsedPom
        parsedPom.scope("runtime") {
            assertDependsOn("junit:junit:4.13")
        }
    }

    def "GenerateMavenPom scope attributes methods are deprecated"() {
        given:
        buildFile << """
            plugins {
                id("maven-publish")
            }

            publishing {
                publications {
                    maven(MavenPublication)
                }
            }

            tasks.generatePomFileForMavenPublication {
                withCompileScopeAttributes(org.gradle.api.internal.attributes.ImmutableAttributes.EMPTY)
                withRuntimeScopeAttributes(org.gradle.api.internal.attributes.ImmutableAttributes.EMPTY)
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The GenerateMavenPom.withCompileScopeAttributes(ImmutableAttributes) method has been deprecated. This is scheduled to be removed in Gradle 9.0. This method was never intended for public use. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#generate_maven_pom_method_deprecations")
        executer.expectDocumentedDeprecationWarning("The GenerateMavenPom.runtimeScopeAttributes(ImmutableAttributes) method has been deprecated. This is scheduled to be removed in Gradle 9.0. This method was never intended for public use. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#generate_maven_pom_method_deprecations")
        succeeds(":help")
    }
}
