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
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.logging.LogLevel.*

@CleanupTestDirectory
class DefaultBuildOperationLoggerTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    Logger logger = Mock()

    def outputFile = temporaryFolder.file("test-output.txt")

    def maxOperationsShown = 5
    def pathToLog = new File("path/to/log/file")
    def pathToLogStr = new ConsoleRenderer().asClickableFileUrl(pathToLog)
    BuildOperationLogInfo configuration = new BuildOperationLogInfo("<testTask>", pathToLog, maxOperationsShown)
    BuildOperationLogger log = new DefaultBuildOperationLogger(configuration, logger, outputFile)

    /**
     * @return output that would appear in the log file
     */
    def logOutput() {
        TextUtil.normaliseLineSeparators(outputFile.text)
    }

    def "logs start of overall operation"() {
        when:
        log.start()
        then:
        logOutput() == """See $pathToLogStr for all output for <testTask>.
"""
        cleanup:
        log.done()
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
        cleanup:
        log.done()
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
        cleanup:
        log.done()
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
