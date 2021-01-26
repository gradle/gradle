/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.play.integtest.fixtures.external

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractPlayExternalContinuousBuildIntegrationTest extends AbstractContinuousIntegrationTest {
    abstract RunningPlayApp getRunningApp()

    protected PlayApp playApp

    def setup() {
        playApp = new BasicPlayApp(versionNumber)
        writeSources()
        buildTimeout = 90
    }

    TestFile getPlayRunBuildFile() {
        buildFile
    }

    def writeSources() {
        playApp.writeSources(testDirectory)

        playRunBuildFile << """
            runPlay {
                httpPort = 0
                ${java9AddJavaSqlModuleArgs()}
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

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        return super.succeeds(tasks)
    }

    static java9AddJavaSqlModuleArgs() {
        if (JavaVersion.current().isJava9Compatible()) {
            return "forkOptions.jvmArgs += ['--add-modules', 'java.sql']"
        } else {
            return ""
        }
    }
}
