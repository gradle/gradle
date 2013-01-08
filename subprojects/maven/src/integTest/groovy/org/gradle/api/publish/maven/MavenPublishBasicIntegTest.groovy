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
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Ignore

/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishBasicIntegTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    M2Installation m2Installation
    MavenFileRepository m2Repo

    def "setup"() {
        m2Installation = new M2Installation(testDirectory)
        using m2Installation
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

    @Ignore("Not yet implemented") // TODO:DAZ
    def "publishes empty pom without component"() {
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
                    add('maven', org.gradle.api.publish.maven.MavenPublication)
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
                    add('maven', org.gradle.api.publish.maven.MavenPublication) {
                        from components['java']
                    }
                }
            }
        """

        when:
        succeeds 'assemble'

        then: "jar is built but not published"
        mavenRepo.module('group', 'root', '1.0').assertNotPublished()
        m2Repo.module('group', 'root', '1.0').assertNotPublished()
        file('build/libs/root-1.0.jar').assertExists()

        when:
        succeeds 'publish'

        then: "jar is published to defined maven repository"
        mavenRepo.module('group', 'root', '1.0').assertPublishedAsJavaModule()
        m2Repo.module('group', 'root', '1.0').assertNotPublished()

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        m2Repo.module('group', 'root', '1.0').assertPublishedAsJavaModule()
    }
}
