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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.tooling.GradleConnector

class InstantExecutionToolingApiInvocationIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    def "can run tasks via tooling api when instant execution is enabled"() {
        buildFile << """
            plugins {
                id("java")
            }    
        """

        when:
        runWithInstantExecutionViaToolingApi("assemble")
        runWithInstantExecutionViaToolingApi("assemble")

        then:
        outputContains("Reusing instant execution cache. This is not guaranteed to work in any way.")
    }

    ExecutionResult runWithInstantExecutionViaToolingApi(String... tasks) {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        def context = new IntegrationTestBuildContext()
        def connector = GradleConnector
            .newConnector()
            .forProjectDirectory(testDirectory)
            .useGradleUserHomeDir(context.gradleUserHomeDir)
            .searchUpwards(false)
        if (GradleContextualExecuter.embedded) {
            connector.embedded(true).useClasspathDistribution()
        } else {
            connector.embedded(false).useInstallation(context.gradleHomeDir)
        }
        def connection = connector.connect()
        try {
            connection.newBuild()
                .forTasks(tasks)
                .withArguments(INSTANT_EXECUTION_PROPERTY)
                .setStandardOutput(output)
                .setStandardError(error)
                .run()
        } finally {
            connection.close()
        }
        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
        return result
    }
}
