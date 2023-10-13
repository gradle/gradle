/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile

class MavenPublishPomCustomizationKotlinDslIntegTest extends AbstractMavenPublishIntegTest {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    @Override
    protected TestFile getSettingsFile() {
        testDirectory.file('settings.gradle.kts')
    }

    def setup() {
        requireOwnGradleUserHomeDir() // Isolate Kotlin DSL extensions API jar
    }

    def "can customize POM using Kotlin DSL"() {
        given:
        settingsFile << 'rootProject.name = "customizePom"'
        buildFile << """
            plugins {
                `maven-publish`
            }

            group = "org.gradle.test"
            version = "1.0"

            publishing {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
                publications {
                    create<MavenPublication>("mavenCustom") {
                        pom {
                            packaging = "custom-packaging"
                            name.set("custom-name")
                            description.set("custom-description")
                            url.set("http://example.org/project")
                            inceptionYear.set("2018")
                            organization {
                                name.set("Example Organization")
                                url.set("https://example.org")
                            }
                            licenses {
                                license {
                                    name.set("Eclipse Public License v2.0")
                                    url.set("http://www.eclipse.org/legal/epl-v20.html")
                                    distribution.set("inside artifact")
                                    comments.set("Successor of EPL v1.0")
                                }
                            }
                            developers {
                                developer {
                                    id.set("foo")
                                    name.set("Foo Bar")
                                    email.set("foo.bar@example.org")
                                }
                                developer {
                                    id.set("baz")
                                    name.set("Baz Qux")
                                    email.set("baz.qux@example.org")
                                    url.set("http://example.org/users/baz.qux")
                                    organization.set("Example Organization")
                                    organizationUrl.set("https://example.org")
                                    roles.set(listOf("tester", "developer"))
                                    timezone.set("Europe/Berlin")
                                    properties.set(mapOf("user" to "baz.qux"))
                                }
                            }
                            contributors {
                                contributor {
                                    name.set("John Doe")
                                    email.set("john.doe@example.org")
                                    url.set("http://example.org/users/john.doe")
                                    organization.set("Example Organization")
                                    organizationUrl.set("https://example.org")
                                    roles.set(listOf("tester", "developer"))
                                    timezone.set("Europe/Berlin")
                                    properties.set(mapOf("user" to "john.doe"))
                                }
                            }
                            scm {
                                connection.set("scm:git:git://example.org/some-repo.git")
                                developerConnection.set("scm:git:git://example.org/some-repo.git")
                                url.set("https://example.org/some-repo")
                                tag.set("v1.0")
                            }
                            issueManagement {
                                system.set("Some Issue Tracker")
                                url.set("https://issues.example.org/")
                            }
                            ciManagement {
                                system.set("Some CI Server")
                                url.set("https://ci.example.org/")
                            }
                            distributionManagement {
                                downloadUrl.set("https://example.org/download/")
                                relocation {
                                    groupId.set("new-group")
                                    artifactId.set("new-artifact-id")
                                    version.set("42")
                                    message.set("the answer to life, the universe and everything")
                                }
                            }
                            mailingLists {
                                mailingList {
                                    name.set("Users")
                                    subscribe.set("users-subscribe@lists.example.org")
                                    unsubscribe.set("users-unsubscribe@lists.example.org")
                                    post.set("users@lists.example.org")
                                    archive.set("http://lists.example.org/users/")
                                    otherArchives.set(listOf("http://archive.org/", "http://backup.example.org/"))
                                }
                                mailingList {
                                    name.set("Developers")
                                    post.set("devs@lists.example.org")
                                }
                            }
                            properties.set(mapOf(
                                "myProp" to "myValue",
                                "prop.with.dots" to "anotherValue"
                            ))
                            withXml {
                                val dependency = asNode().appendNode("dependencies").appendNode("dependency")
                                dependency.appendNode("groupId", "junit")
                                dependency.appendNode("artifactId", "junit")
                                dependency.appendNode("version", "4.13")
                                dependency.appendNode("scope", "runtime")
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
}
