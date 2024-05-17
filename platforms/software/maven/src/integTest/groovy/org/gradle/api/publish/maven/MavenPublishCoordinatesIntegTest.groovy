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

class MavenPublishCoordinatesIntegTest extends AbstractMavenPublishIntegTest {

    def "can publish with specified coordinates"() {
        given:
        using m2

        def repoModule = javaLibrary(mavenRepo.module('org.custom', 'custom', '2.2'))
        def localModule = javaLibrary(m2.mavenRepo().module('org.custom', 'custom', '2.2'))

        and:
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
                        from components.java
                        groupId 'org.custom'
                        artifactId 'custom'
                        version '2.2'
                    }
                }
            }
        """

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        repoModule.assertNotPublished()
        localModule.assertPublished()

        when:
        succeeds 'publish'

        then: "jar is published to defined maven repository"
        file('build/libs/root-1.0.jar').assertExists()

        and:
        repoModule.assertPublished()

        and:
        resolveArtifacts(repoModule) {
            expectFiles 'custom-2.2.jar'
        }
    }

    def "can produce multiple separate publications for single project"() {
        given:
        def module = mavenRepo.module('org.custom', 'custom', '2.2').withModuleMetadata()
        def apiModule = mavenRepo.module('org.custom', 'custom-api', '2')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            task apiJar(type: Jar) {
                from sourceSets.main.output
                archiveBaseName = "root-api"
                exclude "**/impl/**"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    impl(MavenPublication) {
                        groupId "org.custom"
                        artifactId "custom"
                        version "2.2"
                        from components.java
                    }
                    api(MavenPublication) {
                        groupId "org.custom"
                        artifactId "custom-api"
                        version "2"
                        artifact(apiJar)
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        file('build/libs').assertHasDescendants("root-1.0.jar", "root-api-1.0.jar")

        and:
        module.assertPublishedAsJavaModule()
        module.moduleDir.file('custom-2.2.jar').assertIsCopyOf(file('build/libs/root-1.0.jar'))

        and:
        apiModule.assertPublishedAsJavaModule()
        apiModule.moduleDir.file('custom-api-2.jar').assertIsCopyOf(file('build/libs/root-api-1.0.jar'))

        and:
        resolveArtifacts(module) {
            expectFiles 'custom-2.2.jar'
        }
        resolveArtifacts(apiModule) {
            withModuleMetadata {
                // customizing publications is not supported with Gradle metadata
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles 'custom-api-2.jar'
            }
        }
    }

    def "warns when multiple publications share the same coordinates"() {
        given:
        settingsFile << "rootProject.name = 'duplicate-publications'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.example'
            version = '1.0'

            task otherJar(type: Jar) {
                archiveClassifier = "other"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    main(MavenPublication) {
                        from components.java
                    }
                    other(MavenPublication) {
                        artifact(otherJar)
                    }
                }
            }
        """

        def module = mavenRepo.module('org.example', 'duplicate-publications', '1.0').withModuleMetadata()

        when:
        succeeds 'publishMainPublicationToMavenRepository'

        then:
        module.assertPublishedAsJavaModule()
        result.assertNotOutput("Multiple publications with coordinates")

        when:
        succeeds 'publish'

        then:
        outputContains("Multiple publications with coordinates 'org.example:duplicate-publications:1.0' are published to repository 'maven'. The publications 'main' in root project 'duplicate-publications' and 'other' in root project 'duplicate-publications' will overwrite each other!")

        when:
        succeeds 'publishToMavenLocal'

        then:
        outputContains("Multiple publications with coordinates 'org.example:duplicate-publications:1.0' are published to repository 'mavenLocal'. The publications 'main' in root project 'duplicate-publications' and 'other' in root project 'duplicate-publications' will overwrite each other!")
    }

    def "warns when publications in different projects share the same coordinates"() {
        given:
        createDirs("projectA", "projectB")
        settingsFile << """
include 'projectA'
include 'projectB'
"""
        buildFile << """
        subprojects {
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.example'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    main(MavenPublication) {
                        from components.java
                        artifactId "duplicate"
                    }
                }
            }
        }
        """

        when:
        succeeds 'publish'

        then:
        outputContains("Multiple publications with coordinates 'org.example:duplicate:1.0' are published to repository 'maven'. The publications 'main' in project ':projectA' and 'main' in project ':projectB' will overwrite each other!")
    }

    def "does not fail for publication with duplicate repositories"() {
        given:
        settingsFile << "rootProject.name = 'duplicate-repos'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.example'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    main(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        def module = mavenRepo.module('org.example', 'duplicate-repos', '1.0').withModuleMetadata()

        when:
        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
    }
}
