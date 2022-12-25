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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultBuildOperationLoggerFactoryTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())

    Logger logger = Mock()
    def outputDir = tmpDirProvider.testDirectory.file("logs")
    DefaultBuildOperationLoggerFactory factory = new DefaultBuildOperationLoggerFactory(logger)

    def "enabling debug causes all failures to be logged"() {
        given:
        logger.isDebugEnabled() >> debugEnabled
        def outputFile = outputDir.file("test.out")
        when:
        def config = factory.createLogInfo("testTask", outputFile, 10)
        then:
        config.maximumFailedOperationsShown == maximumFailures
        where:
        debugEnabled | maximumFailures
        false        | 10
        true         | Integer.MAX_VALUE
    }

    def "creates path to output directory"() {
        when:
        def outputFile = factory.createOutputFile(outputDir)
        then:
        outputDir.exists()
        outputFile.name == "output.txt"
    }
}
