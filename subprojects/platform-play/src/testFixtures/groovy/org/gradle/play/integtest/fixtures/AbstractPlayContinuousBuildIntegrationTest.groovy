/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.launcher.continuous.Java7RequiringContinuousIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest.java9AddJavaSqlModuleArgs

abstract class AbstractPlayContinuousBuildIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    abstract PlayApp getPlayApp()
    abstract RunningPlayApp getRunningApp()

    def setup() {
        writeSources()
        buildTimeout = 90
    }

    TestFile getPlayRunBuildFile() {
        buildFile
    }

    def writeSources() {
        playApp.writeSources(testDirectory)

        playRunBuildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = 0
                    ${java9AddJavaSqlModuleArgs()}
                }
            }
        """

        settingsFile << """
            rootProject.name = '${playApp.name}'
        """
    }

    void appIsRunningAndDeployed() {
        runningApp.initialize(gradle)
        runningApp.verifyStarted()
        runningApp.verifyContent()
    }

    void appIsStopped() {
        runningApp.verifyStopped()
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        return super.succeeds(tasks)
    }
}
