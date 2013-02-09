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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Ignore

/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishBasicIntegTest extends AbstractDependencyResolutionTest {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    MavenFileRepository m2Repo

    def "setup"() {
        m2Repo = m2Installation.mavenRepo()
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
        module.assertPublished()
        module.parsedPom.scopes.isEmpty()
    }

    def "can publish simple jar"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')
        def localModule = m2Repo.module('group', 'root', '1.0')

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
        repoModule.assertPublishedAsJavaModule()
        localModule.assertNotPublished()

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        localModule.assertPublishedAsJavaModule()
    }

    def "can publish a snapshot version"() {
        settingsFile << 'rootProject.name = "snapshotPublish"'
        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = 'org.gradle'
    version = '1.0-SNAPSHOT'

    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
"""

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle', 'snapshotPublish', '1.0-SNAPSHOT')
        module.assertArtifactsPublished('snapshotPublish-1.0-SNAPSHOT.jar', 'snapshotPublish-1.0-SNAPSHOT.pom')
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
        failure.assertHasDescription("A problem occurred evaluating root project 'bad-project'")
        failure.assertHasCause("Maven publication 'maven' cannot include multiple components")
    }

    @Ignore("Not yet implemented - currently the second publication will overwrite") // TODO:DAZ fix in validation story
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
        failure.assertHasDescription("A problem occurred evaluating root project 'bad-project'")
        failure.assertHasCause("Publication with name 'mavenJava' already exists")
    }
}
