/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DockerPluginSmokeTest extends AbstractSmokeTest {

    // Plugin after 7.0.0 requires Java 11+ to run
    @Requires(UnitTestPreconditions.Jdk11OrLater)
    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.docker-java-application')
    def 'docker plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'application'
                id "com.bmuschko.docker-java-application" version "${TestedVersions.docker}"
            }

            application.mainClass = 'org.gradle.JettyMain'

            docker {
                javaApplication {
                    baseImage = 'dockerfile/java:openjdk-7-jre'
                    ports = [9090]
                    images = ['jettyapp:1.115']
                }
            }
            """.stripIndent()

        when:
        def result = runner('assemble').forwardOutput().build()

        then:
        result.task(':assemble').outcome == SUCCESS
    }

}
