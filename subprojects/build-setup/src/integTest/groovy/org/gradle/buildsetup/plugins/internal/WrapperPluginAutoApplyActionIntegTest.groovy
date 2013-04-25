/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

class WrapperPluginAutoApplyActionIntegTest extends AbstractIntegrationSpec {

    public static final String GRADLEW_BASH_SCRIPT = "gradlew"
    public static final String GRADLEW_BATCH_SCRIPT = "gradlew.bat"
    public static final String GRADLEW_WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar"
    public static final String GRADLEW_PROPERTY_FILE = "gradle/wrapper/gradle-wrapper.properties"

    def "can apply wrapper plugin dynamically"() {
        when:
        run 'tasks'
        then:
        output.contains("wrapper - Generates Gradle wrapper files.")

        when:
        run 'wrapper'
        then:
        wrapperIsGenerated()
    }

    def "wrapper plugin not applied on subprojects"() {
        setup:
        settingsFile << "include 'moduleA'"
        TestFile subprojectsDir = file("moduleA").createDir()

        when:
        executer.inDirectory(subprojectsDir)
        run 'tasks'
        then:
        !output.contains("wrapper - Generates Gradle wrapper files.")

        when:
        executer.inDirectory(subprojectsDir)
        then:
        fails("wrapper")
    }

    def "can reference dynamic applied wrapper plugin"() {
        when:
        buildFile << """
            wrapper{
                gradleVersion = '12.34'
            }
    """
        run 'wrapper'
        then:
        wrapperIsGenerated("12.34")
    }

    def "can depend on dynamic applied wrapper task"() {
        when:
        buildFile << """
            task myTask(dependsOn:wrapper)
        """
        run 'myTask'
        then:
        wrapperIsGenerated()
    }

    def "manually declared wrapper task is preferred"() {
        when:
        buildFile << """

                task wrapper << {
                    println "running custom wrapper task"
                }
            """
        run 'wrapper'
        then:
        output.contains("running custom wrapper task")
        wrapperIsNotGenerated()
    }

    private def wrapperIsGenerated(String version = GradleVersion.current().version) {
        file(GRADLEW_BASH_SCRIPT).assertExists()
        file(GRADLEW_BATCH_SCRIPT).assertExists()
        file(GRADLEW_WRAPPER_JAR).assertExists()
        file(GRADLEW_PROPERTY_FILE).assertExists()
        file(GRADLEW_PROPERTY_FILE).text.contains("gradle-${version}-bin.zip")
    }

    private def wrapperIsNotGenerated() {
        file(GRADLEW_BASH_SCRIPT).assertDoesNotExist()
        file(GRADLEW_BATCH_SCRIPT).assertDoesNotExist()
        file(GRADLEW_WRAPPER_JAR).assertDoesNotExist()
        file(GRADLEW_PROPERTY_FILE).assertDoesNotExist()
    }
}
