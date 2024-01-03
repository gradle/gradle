/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.api.logging.LogLevel
import org.gradle.tooling.ProjectConnection

class ToolingApiTestCommon {

    public static final String LOG_LEVEL_TEST_SCRIPT = LogLevel.values()
        .collect { """logger.${it.name().toLowerCase()}("Hello $it\\n")""" }
        .join("\n") +
        """
        if(project.hasProperty("org.gradle.logging.level")){
            System.out.println("\\nCurrent log level property value (org.gradle.logging.level): "  + project.property("org.gradle.logging.level"))
        }
        else {
            System.out.println("\\\\nNo property org.gradle.logging.level set")
        }
"""

    static getOutputPattern(LogLevel logLevel) {
        /[\s\S]*Hello $logLevel[\s\S]*/
    }

    static validateLogs(Object stdOut, LogLevel expectedLevel) {
        def output = stdOut.toString()
        LogLevel.values().findAll { it < expectedLevel }.collect {
            getOutputPattern(it)
        }.every { !output.matches(it) }
            &&
            LogLevel.values().findAll { it >= expectedLevel }.collect {
                getOutputPattern(it)
            }.every { output.matches(it) }

    }

    static runLogScript(ToolingApi tapi, List<String> arguments) {
        def stdOut = new ByteArrayOutputStream()
        tapi.withConnection { ProjectConnection connection ->
            connection.newBuild()
                .withArguments(arguments)
                .setStandardOutput(stdOut)
                .setStandardError(stdOut)
                .run()
        }
        stdOut
    }
}
