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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.RequiresIntegratedComposite
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions

/**
 * Tests for composite build.
 *
 * Note that this test should be migrated to use the command-line entry point for composite build, when this is developed.
 * This is distinct from the specific test coverage for Tooling API access to a composite build.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
@RequiresIntegratedComposite
abstract class AbstractCompositeBuildIntegrationTest extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def stdErr = new ByteArrayOutputStream()
    List builds = []

    protected void execute(File build, String task, Iterable<String> arguments = []) {
        stdOut.reset()
        stdErr.reset()
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.setStandardError(stdErr)
            buildLauncher.forTasks(build, task)
            buildLauncher.withArguments(arguments)
            buildLauncher.run()
        }
        println stdOut
        println stdErr
    }

    protected ExecutionResult getResult() {
        return new OutputScrapingExecutionResult(stdOut.toString(), stdErr.toString())
    }

    protected void executed(String... tasks) {
        def executedTasks = result.executedTasks
        for (String task : tasks) {
            assert executedTasks.contains(task)
//            assert executedTasks.findAll({ it == task }).size() == 1
        }
    }

}
