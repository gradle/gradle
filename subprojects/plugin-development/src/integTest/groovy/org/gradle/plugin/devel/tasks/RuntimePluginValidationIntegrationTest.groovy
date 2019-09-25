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

package org.gradle.plugin.devel.tasks

import org.gradle.internal.reflect.TypeValidationContext
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR
import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING

class RuntimePluginValidationIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    @Override
    def setup() {
        buildFile << """
            tasks.register("run", MyTask)
        """
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "run"
        result.assertTaskNotSkipped(":run")
    }

    @Override
    void assertValidationFailsWith(Map<String, TypeValidationContext.Severity> messages) {
        def expectedWarnings = messages
            .findAll { message, severity -> severity == WARNING }
            .keySet()
            .collect { removeTypeForProperties(it) }
        def expectedErrors = messages
            .findAll { message, severity -> severity == ERROR }
            .keySet()
            .collect { removeTypeForProperties(it) }

        executer.withFullDeprecationStackTraceDisabled()
        expectedWarnings.forEach { warning ->
            executer.expectDeprecationWarning("$warning This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.")
        }
        if (expectedErrors) {
            fails "run"
        } else {
            succeeds "run"
        }
        result.assertTaskNotSkipped(":run")

        switch (expectedErrors.size()) {
            case 0:
                break
            case 1:
                failure.assertHasDescription("A problem was found with the configuration of task ':run' (type 'MyTask').")
                break
            default:
                failure.assertHasDescription("Some problems were found with the configuration of task ':run' (type 'MyTask').")
                break
        }
        expectedErrors.forEach { error ->
            failureHasCause(error)
        }
    }

    static String removeTypeForProperties(String message) {
        message.replaceAll(/Type '.*?': property/, "Property")
    }

    @Override
    TestFile source(String path) {
        return file("buildSrc/$path")
    }
}
