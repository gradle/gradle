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
import spock.lang.Issue

class MavenPublishPomPackagingIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    def "uses 'pom' packaging when no artifact is unclassified"() {
        given:
        createBuildScripts """
            artifact("content.txt") {
                classifier "custom"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'pom'
        mavenModule.assertArtifactsPublished("publishTest-1.9-custom.txt", "publishTest-1.9.pom")
    }

    def "uses 'pom' packaging where multiple artifacts are unclassified"() {
        given:
        createBuildScripts """
            artifact("content.txt")
            artifact("content.txt") {
                extension "rtf"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'pom'
        mavenModule.assertArtifactsPublished("publishTest-1.9.txt", "publishTest-1.9.rtf", "publishTest-1.9.pom")
    }

    def "uses extension of single unclassified artifact as pom packaging"() {
        given:
        createBuildScripts """
            artifact("content.txt")
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'txt'
        mavenModule.assertArtifactsPublished("publishTest-1.9.txt", "publishTest-1.9.pom")
    }

    @Issue("GRADLE-3211")
    def "can specify packaging when no artifact is unclassified"() {
        given:
        createBuildScripts """
            pom.packaging "foo"

            artifact("content.txt") {
                classifier "custom"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'foo'
        mavenModule.assertArtifactsPublished("publishTest-1.9-custom.txt", "publishTest-1.9.pom")
    }

    @Issue("GRADLE-3211")
    def "can set packaging to the extension of an unclassified artifact"() {
        given:
        createBuildScripts """
            pom.packaging "txt"

            artifact("content.txt") {
                classifier "custom"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'txt'
        mavenModule.assertArtifactsPublished("publishTest-1.9-custom.txt", "publishTest-1.9.pom")
    }

    @Issue("GRADLE-3211")
    def "can override packaging with single unclassified artifact"() {
        given:
        createBuildScripts """
            pom.packaging "foo"

            artifact("content.txt") {
                extension "txt"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'foo'
        mavenModule.assertArtifactsPublished("publishTest-1.9.txt", "publishTest-1.9.pom")
    }

    def "can specify packaging for known jar packaging without changing artifact extension"() {
        given:
        createBuildScripts """
            pom.packaging "ejb"

            artifact("content.txt") {
                extension "jar"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'ejb'
        mavenModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-1.9.pom")
    }

    def "can specify packaging with multiple unclassified artifacts"() {
        given:
        createBuildScripts """
            pom.packaging "other"

            artifact("content.txt")
            artifact("content.txt") {
                extension "other"
            }
"""

        when:
        succeeds "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.parsedPom.packaging == 'other'
        mavenModule.assertArtifactsPublished("publishTest-1.9.txt", "publishTest-1.9.other", "publishTest-1.9.pom")
    }

    def createBuildScripts(artifacts) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            file("content.txt") << 'some content'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        $artifacts
                    }
                }
            }
"""
    }
}
