/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import org.gradle.test.fixtures.file.TestFile

import static org.gradle.api.problems.Severity.ERROR
import static org.gradle.api.problems.Severity.WARNING

trait RuntimePluginValidationTrait implements CommonPluginValidationTrait{
    @Override
    def setup() {
        expectReindentedValidationMessage()
        buildFile << """
            tasks.register("run", MyTask)
        """
    }

    String iterableSymbol = '.$0'

    @Override
    String getNameSymbolFor(String name) {
        ".$name\$0"
    }

    @Override
    String getKeySymbolFor(String name) {
        ".$name"
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "run"
        result.assertTaskNotSkipped(":run")
    }

    void assertValidationFailsWith(List<AbstractPluginValidationIntegrationSpec.DocumentedProblem> messages) {
        def expectedDeprecations = messages
            .findAll { problem -> problem.severity == WARNING }
        def expectedFailures = messages
            .findAll { problem -> problem.severity == ERROR }

        expectedDeprecations.forEach { warning ->
            expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, warning.message, warning.id, warning.section)
        }
        if (expectedFailures) {
            fails "run"
        } else {
            succeeds "run"
        }

        switch (expectedFailures.size()) {
            case 0:
                break
            case 1:
                failure.assertHasDescription("A problem was found with the configuration of task ':run' (type 'MyTask').")
                break
            default:
                failure.assertHasDescription("Some problems were found with the configuration of task ':run' (type 'MyTask').")
                break
        }
        expectedFailures.forEach { error ->
            failureDescriptionContains(error.message)
        }
    }

    @Override
    TestFile source(String path) {
        return file("buildSrc/$path")
    }
}
