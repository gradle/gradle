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
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class MavenPublishLoggingIntegTest extends AbstractMavenPublishIntegTest {

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        using m2
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
    }

    def "logs missing metadata as info and not error"() {
        when:
        succeeds 'publish'

        then:
        !errorOutput.contains("Could not find metadata")
        !output.contains("Could not find metadata")

        when:
        mavenRepo.rootDir.deleteDir()
        succeeds 'publish', "-i"

        then:
        output.contains("Could not find metadata")
    }

    def "logging is associated to task"() {
        when:
        succeeds 'publish', "-i"

        then:
        def output = result.groupedOutput.task(":publishMavenPublicationToMavenRepository").output

        output.contains("Publishing to repository 'maven'")
        // Logging from LoggingMavenTransferListener
        output.contains("Deploying to")
        output.contains("Uploading: group/root/1.0/root-1.0.jar")
        output.contains("Uploading: group/root/1.0/root-1.0.pom")
        output.contains("group/root/1.0/root-1.0.module")
        output.contains("Downloading: group/root/maven-metadata.xml from repository")
        output.contains("Could not find metadata")
        output.contains("Uploading: group/root/maven-metadata.xml to repository")
    }

    def "does not log uploads when installing to mavenLocal"() {
        when:
        succeeds 'publishToMavenLocal', '-i'

        then:
        output.contains("Publishing to maven local repository")
        !output.contains("Uploading:")
    }
}
