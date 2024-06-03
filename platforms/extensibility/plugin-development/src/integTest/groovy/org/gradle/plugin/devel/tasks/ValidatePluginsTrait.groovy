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
import org.gradle.util.internal.TextUtil

import static org.gradle.util.internal.TextUtil.getPluralEnding
import static org.hamcrest.Matchers.containsString

trait ValidatePluginsTrait implements CommonPluginValidationTrait {

    def setup() {
        enableProblemsApiCheck()
        buildFile """
            apply plugin: "java-gradle-plugin"
        """
    }


    @Override
    String getIterableSymbol() {
        '.*'
    }

    @Override
    String getNameSymbolFor(String name) {
        ".<name>"
    }

    @Override
    String getKeySymbolFor(String name) {
        '.<key>'
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "validatePlugins"
    }

    @Override
    void assertValidationFailsWith(List<AbstractPluginValidationIntegrationSpec.DocumentedProblem> messages) {
        fails("validatePlugins")
        failure.assertHasCause "Plugin validation failed with ${messages.size()} problem${getPluralEnding(messages)}"
        messages.forEach { problem ->
            String indentedMessage = problem.message.replaceAll('\n', '\n    ').trim()
            failure.assertThatCause(containsString("$problem.severity: $indentedMessage"))
        }

        // TODO (donat) do probably don't want to have this, as the explicit problem assertions are preferred
        def problems = collectedProblems
        assert problems.size() == messages.size()
        problems.any { problem ->
            messages.any { message ->
                if (message.config) {
                    TextUtil.endLineWithDot(problem.definition.id.displayName) == message.config.label().toString()
                } else {
                    message.message.contains(TextUtil.endLineWithDot(problem.definition.id.displayName))
                }
            }
        }
    }

    @Override
    TestFile source(String path) {
        return file(path)
    }
}
