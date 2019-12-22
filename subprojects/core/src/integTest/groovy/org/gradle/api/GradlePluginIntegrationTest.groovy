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


package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class GradlePluginIntegrationTest extends AbstractIntegrationSpec {
    File initFile;

    def setup() {
        initFile = temporaryFolder.createFile("initscripts/init.gradle")
        executer.usingInitScript(initFile);
    }

    @ToBeFixedForInstantExecution
    def "can apply binary plugin from init script"() {
        when:
        initFile << """
        apply plugin:SimpleGradlePlugin

        class SimpleGradlePlugin implements Plugin<Gradle> {
            void apply(Gradle aGradle) {
                aGradle.buildFinished {
                    println "Gradle Plugin received build finished!"
                }
            }
        }
        """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }

    @ToBeFixedForInstantExecution
    def "can apply script with relative path"() {
        setup:
        def externalInitFile = temporaryFolder.createFile("initscripts/somePath/anInit.gradle")
        externalInitFile << """
        buildFinished {
            println "Gradle Plugin received build finished!"
        }
        """
        when:
        initFile << """
        apply from: "somePath/anInit.gradle"
        """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }

    @ToBeFixedForInstantExecution
    def "can apply script with relative path on Gradle instance"() {
        setup:
        def externalInitFile = temporaryFolder.createFile("initscripts/somePath/anInit.gradle")
        externalInitFile << """
        buildFinished {
            println "Gradle Plugin received build finished!"
        }
        """
        when:
        initFile << """
            gradle.apply(from: "initscripts/somePath/anInit.gradle")
            """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }

    @ToBeFixedForInstantExecution
    def "path to script is interpreted relative to the applying script"() {
        setup:
        def externalInitFile = temporaryFolder.createFile("initscripts/path1/anInit.gradle")
        externalInitFile << """
            buildFinished {
                println "Gradle Plugin received build finished!"
            }
        """
        def anotherExternalInitFile = temporaryFolder.createFile("initscripts/path2/anotherInit.gradle")
        anotherExternalInitFile << """
            apply from: '../path1/anInit.gradle'
            """

        when:
        initFile << """
            apply from: "path2/anotherInit.gradle"
            """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }
}
