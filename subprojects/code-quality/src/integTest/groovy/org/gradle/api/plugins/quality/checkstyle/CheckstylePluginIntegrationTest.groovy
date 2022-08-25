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
package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Issue

import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.javaClassWithNewLineAtEnd
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.simpleCheckStyleConfig

class CheckstylePluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "check"
    }

    def setup() {
        buildFile << """
            apply plugin: 'groovy'
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/21301")
    def "can pass a URL in configProperties"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            dependencies { implementation localGroovy() }
            ${mavenCentralRepository()}

            checkstyle {
                configProperties["some"] = new URL("https://gradle.org/")
            }

            checkstyleMain {
                workerOptions {
                    systemProperties {
                        systemProperty("gradletest.string", "string")
                        systemProperty("gradletest.ignored", ignoredValue(project.provider { "string" }))
                        systemProperty("gradletest.value", value("string"))
                        systemProperty("gradletest.inputFile", inputFile(project.layout.buildDirectory.file("foo")))
                        systemProperty("gradletest.inputDir", inputDirectory(project.layout.buildDirectory.dir("foo")))
                    }
                }
            }
        """
        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        when:
        succeeds 'check'

        then:
        executedAndNotSkipped ':checkstyleMain'
    }
}
