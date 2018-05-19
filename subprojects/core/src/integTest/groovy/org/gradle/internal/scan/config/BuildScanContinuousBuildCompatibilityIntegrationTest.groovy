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

package org.gradle.internal.scan.config

import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.internal.deployment.RunApplication
import org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture
import org.gradle.test.fixtures.file.TestFile

class BuildScanContinuousBuildCompatibilityIntegrationTest extends AbstractContinuousIntegrationTest {

    def fixture = new BuildScanPluginFixture(testDirectory, mavenRepo, createExecuter())

    TestFile resourceFile

    void doSetup() {
        settingsFile << fixture.pluginManagement()
        withoutContinuousArg = true
        fixture.logConfig = true
        buildFile << """
            apply plugin: "java"
            apply plugin: "application"
            
            task runC(type: $RunApplication.name) {
                classpath = sourceSets.main.runtimeClasspath
                mainClassName = "Main"
                arguments = []
                changeBehavior = ${DeploymentRegistry.ChangeBehavior.name}.NONE
            }     
        """

        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) throws Exception {
                    while (true) {
                        Thread.sleep(100);
                    }
                }
            }
        """

        resourceFile = file("src/main/resources/f")
        resourceFile.text = "."

        fixture.publishDummyBuildScanPluginNow()

        buildFile << """
            gradle.buildFinished {
                println "anyDeploymentsStartedAtBuildFinished: " + buildScanPluginConfig.attributes.anyDeploymentsStarted
            }
        """
    }

    def "deployment attribute is always false"() {
        when:
        doSetup()

        then:
        succeeds("runC", "--scan")
        def firstBuildOutput = results.last().output

        and:
        fixture.assertUnsupportedMessage(firstBuildOutput, "null") // first time through
        firstBuildOutput.count("anyDeploymentsStartedAtBuildFinished: false") == 2

        when:
        resourceFile.text += "."

        then:
        succeeds()
        def secondBuildOutput = results.last().output
        fixture.assertUnsupportedMessage(secondBuildOutput, "null")
        secondBuildOutput.count("anyDeploymentsStartedAtBuildFinished: false") == 1
    }

}
