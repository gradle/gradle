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

class GradlePluginIntegrationTest extends AbstractIntegrationSpec {
    File initFile;

    def setup() {
        initFile = temporaryFolder.createFile("initscripts/init.gradle")
        executer.usingInitScript(initFile);
    }

    def "can apply pluginClass from InitScript"() {
        when:
        initFile << """
        apply plugin:SimpleGradlePlugin

        class SimpleGradlePlugin implements Plugin<Gradle> {
            void apply(Gradle aGradle) {
                aGradle.addBuildListener(new BuildAdapter(){
                    public void buildFinished(BuildResult result) {
                        println "Gradle Plugin received build finished!"
                    }
                });
            }
        }
        """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }

    def "can apply script with relative path"() {
        setup:
        def externalInitFile = temporaryFolder.createDir("initscripts/somePath").createFile("anInit.gradle")
        externalInitFile << """
        gradle.addBuildListener(new BuildAdapter(){
            public void buildFinished(BuildResult result) {
                println "Gradle Plugin received build finished!"
            }
        });
        """
        when:
        initFile << """
        apply from: "somePath/anInit.gradle"
        """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }

    def "cannot apply script with relative path on Gradle"() {
        setup:
        def externalInitFile = temporaryFolder.createDir("somePath").createFile("anInit.gradle")
        externalInitFile << """
            gradle.addBuildListener(new BuildAdapter(){
                public void buildFinished(BuildResult result) {
                    println "Gradle Plugin received build finished!"
                }
            });
            """
        when:
        initFile << """
            gradle.apply(from: "somePath/anInit.gradle")
            """
        then:
        fails('tasks')
        errorOutput.contains("Cannot convert relative path somePath${File.separator}anInit.gradle to an absolute file")
    }

    def "applied script can apply scripts with relative path"() {
        setup:
        def externalInitFile = temporaryFolder.createFile("initscripts/somePath/anInit.gradle")
        externalInitFile << """
            gradle.addBuildListener(new BuildAdapter(){
                public void buildFinished(BuildResult result) {
                    println "Gradle Plugin received build finished!"
                }
            });
                        """
        def anotherExternalInitFile = temporaryFolder.createFile("initscripts/anotherInit.gradle")
        anotherExternalInitFile << """
            apply from: 'somePath/anInit.gradle'
            """

        when:
        initFile << """
            apply from: "anotherInit.gradle"
            """
        then:
        def executed = succeeds('tasks')
        executed.output.contains("Gradle Plugin received build finished!")
    }
}