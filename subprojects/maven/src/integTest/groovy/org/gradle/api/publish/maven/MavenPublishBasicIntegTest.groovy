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

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Ignore

/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishBasicIntegTest extends AbstractMavenPublishIntegTest {
    private static final String DEFERRED_CONFIGURATION_WARNING = "we are removing the 'deferred configurable' behavior"

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    MavenLocalRepository localM2Repo

    def "setup"() {
        localM2Repo = m2.mavenRepo()
    }

    def "publishes nothing without defined publication"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        mavenRepo.module('group', 'root', '1.0').assertNotPublished()
    }

    def "publishes empty pom when publication has no added component"() {
        given:
        settingsFile << "rootProject.name = 'empty-project'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

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
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'empty-project', '1.0')
        module.assertPublishedAsPomModule()
        module.parsedPom.scopes.isEmpty()

        and:
        resolveArtifacts(module) {
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles()
            }
        }
    }

    def "can publish simple component"() {
        given:
        using m2
        def repoModule = javaLibrary(mavenRepo.module('group', 'root', '1.0'))
        def localModule = javaLibrary(localM2Repo.module('group', 'root', '1.0'))

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
                    }
                }
            }
        """

        when:
        succeeds 'assemble'

        then: "jar is built but not published"
        repoModule.assertNotPublished()
        localModule.assertNotPublished()
        file('build/libs/root-1.0.jar').assertExists()

        when:
        succeeds 'publish'

        then: "jar is published to defined maven repository"
        repoModule.assertPublished()
        localModule.assertNotPublished()

        and:
        repoModule.rootMetaData.groupId == "group"
        repoModule.rootMetaData.artifactId == "root"
        repoModule.rootMetaData.versions == ["1.0"]
        repoModule.rootMetaData.releaseVersion == "1.0"

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        localModule.assertPublished()

        and:
        resolveArtifacts(repoModule) {
            expectFiles 'root-1.0.jar'
        }
    }

    def "can publish to custom maven local repo defined in settings.xml"() {
        given:
        def customLocalRepo = new MavenLocalRepository(file("custom-maven-local"))
        m2.generateUserSettingsFile(customLocalRepo)
        using m2

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publishToMavenLocal'

        then:
        localM2Repo.module("group", "root", "1.0").assertNotPublished()
        javaLibrary(customLocalRepo.module("group", "root", "1.0")).assertPublished()
    }

    def "reports failure publishing when model validation fails"() {
        given:
        settingsFile << "rootProject.name = 'bad-project'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'war'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        from components.web
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasCause("Maven publication 'maven' cannot include multiple components")
    }

    @Ignore("Not yet implemented - currently the second publication will overwrite")
    def "cannot publish multiple maven publications with the same identity"() {
        given:
        settingsFile << "rootProject.name = 'bad-project'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'war'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                    mavenWeb(MavenPublication) {
                        from components.web
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad-project'.")
        failure.assertHasCause("Publication with name 'mavenJava' already exists")
    }

    def "asks the user to activate the stable publishing feature preview"() {

        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
        """

        when:
        succeeds("help")

        then:
        outputContains(DEFERRED_CONFIGURATION_WARNING)
    }

    def "no warning if the user already activated the stable feature preview"() {

        given:
        settingsFile << """
            rootProject.name = 'root'
        """
        FeaturePreviewsFixture.enableStablePublishing(settingsFile)
        buildFile << """
            apply plugin: 'maven-publish'
        """

        when:
        succeeds("help")

        then:
        outputDoesNotContain(DEFERRED_CONFIGURATION_WARNING)
    }
}
