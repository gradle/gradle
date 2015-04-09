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

package org.gradle.internal.operations.logging

import org.gradle.api.logging.Logger
import org.gradle.logging.ConsoleRenderer

import static org.gradle.api.logging.LogLevel.*

import org.gradle.util.TextUtil
import spock.lang.Specification

class DefaultBuildOperationLoggerTest extends Specification {
    Logger logger = Mock()

    StringWriter logOutput = new StringWriter()
    PrintWriter logWriter = new PrintWriter(logOutput)

    def maxOperationsShown = 5
    def pathToLog = new File("path/to/log/file")
    def pathToLogStr = new ConsoleRenderer().asClickableFileUrl(pathToLog)
    BuildOperationLogInfo configuration = new BuildOperationLogInfo("<testTask>", pathToLog, maxOperationsShown)
    BuildOperationLogger log = new DefaultBuildOperationLogger(configuration, logger, logWriter)

    /**
     * @return output that would appear in the log file
     */
    def logOutput() {
        TextUtil.normaliseLineSeparators(logOutput.toString())
    }

    def "logs start of overall operation"() {
        when:
        log.start()
        then:
        logOutput() == """See $pathToLogStr for all output for <testTask>.
"""
    }

    def "logs completion of operation"() {
        given:
        log.start()
        when:
        log.operationSuccess("<operation>", "<output>")
        then:
        1 * logger.log(DEBUG, "<operation> successful.")
        1 * logger.log(INFO, "<output>")
        logOutput() == """See $pathToLogStr for all output for <testTask>.
<operation> successful.
<output>
"""
    }

    def "logs failure of operation"() {
        given:
        log.start()
        when:
        log.operationFailed("<operation>", "<output>")
        then:
        1 * logger.log(DEBUG, "<operation> failed.")
        1 * logger.log(ERROR, "<output>")
        logOutput() == """See $pathToLogStr for all output for <testTask>.
<operation> failed.
<output>
"""
    }
    def "logs output from multiple operations"() {
        when:
        log.start()
        4.times { log.operationFailed("<operation>", "<output>") }
        log.done()

        then:
        1 * logger.log(INFO, "See $pathToLogStr for all output for <testTask>.")
        4 * logger.log(DEBUG, "<operation> failed.")
        4 * logger.log(ERROR, "<output>")
        1 * logger.log(INFO, "Finished <testTask>, see full log $pathToLogStr.")

        logOutput() == """See $pathToLogStr for all output for <testTask>.
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
Finished <testTask>, see full log $pathToLogStr.
"""
    }

    def "logs continued message at end of overall operation"() {
        when:
        log.start()
        10.times { log.operationFailed("<operation>", "<output>") }
        log.done()

        then:
        1 * logger.log(INFO, "See $pathToLogStr for all output for <testTask>.")
        10 * logger.log(DEBUG, "<operation> failed.")
        5 * logger.log(ERROR, "<output>")
        1 * logger.log(ERROR, "...output for 5 more failed operation(s) continued in $pathToLogStr.")
        1 * logger.log(INFO, "Finished <testTask>, see full log $pathToLogStr.")

        logOutput() == """See $pathToLogStr for all output for <testTask>.
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
<operation> failed.
<output>
Finished <testTask>, see full log $pathToLogStr.
"""
    }

    def "logs end of overall operation"() {
        when:
        log.start()
        log.done()
        then:
        logOutput() == """See $pathToLogStr for all output for <testTask>.
Finished <testTask>, see full log $pathToLogStr.
"""
    }
}
