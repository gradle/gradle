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

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

import org.gradle.integtests.fixtures.instantexecution.InstantExecutionBuildOperationsFixture
import org.gradle.integtests.fixtures.instantexecution.InstantExecutionProblemsFixture

import org.intellij.lang.annotations.Language


class AbstractInstantExecutionIntegrationTest extends AbstractIntegrationSpec {

    static final String STRICT_CLI_OPTION = InstantExecutionProblemsFixture.STRICT_CLI_OPTION
    static final String LENIENT_CLI_OPTION = InstantExecutionProblemsFixture.LENIENT_CLI_OPTION
    static final String MAX_PROBLEMS_CLI_OPTION = InstantExecutionProblemsFixture.MAX_PROBLEMS_CLI_OPTION

    protected InstantExecutionProblemsFixture problems

    def setup() {
        // Verify that the previous test cleaned up state correctly
        assert System.getProperty(ConfigurationCacheOption.PROPERTY_NAME) == null
        problems = new InstantExecutionProblemsFixture(executer, testDirectory)
    }

    @Override
    def cleanup() {
        // Verify that the test (or fixtures) has cleaned up state correctly
        assert System.getProperty(ConfigurationCacheOption.PROPERTY_NAME) == null
    }

    void buildKotlinFile(@Language("kotlin") String script) {
        buildKotlinFile << script
    }

    void instantRun(String... tasks) {
        run(STRICT_CLI_OPTION, *tasks)
    }

    void instantRunLenient(String... tasks) {
        run(LENIENT_CLI_OPTION, *tasks)
    }

    void instantFails(String... tasks) {
        fails(STRICT_CLI_OPTION, *tasks)
    }

    void instantFailsLenient(String... tasks) {
        fails(LENIENT_CLI_OPTION, *tasks)
    }

    String relativePath(String path) {
        return path.replace('/', File.separator)
    }

    protected InstantExecutionBuildOperationsFixture newInstantExecutionFixture() {
        return new InstantExecutionBuildOperationsFixture(new BuildOperationsFixture(executer, temporaryFolder))
    }

    protected void assertTestsExecuted(String testClass, String... testNames) {
        new DefaultTestExecutionResult(testDirectory)
            .testClass(testClass)
            .assertTestsExecuted(testNames)
    }
}
