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
import org.junit.Rule
import org.gradle.util.SetSystemProperties

/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishBasicIntegTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    M2Installation m2Installation;

    def "setup"() {
        m2Installation = new M2Installation(testDir)
        using m2Installation
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
            }
        """
        when:
        succeeds 'publish'

        then:
        modulePublished(mavenRepo, 'group', 'root', '1.0')

        when:
        succeeds 'publishToMavenLocal'

        then:
        modulePublished(m2Installation.mavenRepo(), 'group', 'root', '1.0')
    }

    def "can customise pom xml"() {
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
                    maven {
                        pom.withXml {
                            asNode().version[0].value = "foo"
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        modulePublished(mavenRepo, 'group', 'root', 'foo')

        when:
        succeeds 'publishToMavenLocal'

        then:
        modulePublished(m2Installation.mavenRepo(), 'group', 'root', 'foo')
    }

    def modulePublished(MavenFileRepository fileRepository, def group, def artifact, def expectedVersion) {
        def module = fileRepository.module(group, artifact, expectedVersion);
        module.assertArtifactsPublished("${artifact}-${expectedVersion}.jar", "${artifact}-${expectedVersion}.pom")
        with(module.pom) {
            assert groupId == group
            assert artifactId == artifact
            assert version == expectedVersion
        }
        true
    }
}
