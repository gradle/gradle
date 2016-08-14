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

package org.gradle.performance.fixture

import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class BuildEventTimestampCollectorTest extends Specification {

    private static final String FILENAME = "timestamps.txt"

    @Rule TestNameTestDirectoryProvider directoryProvider = new TestNameTestDirectoryProvider()

    MeasuredOperation measuredOperation = new MeasuredOperation()
    BuildEventTimestampCollector collector = new BuildEventTimestampCollector(FILENAME)

    private void collect() {
        collector.collect([getProjectDir: { directoryProvider.testDirectory }] as BuildExperimentInvocationInfo, measuredOperation)
    }

    private void timestampFileContents(String contents) {
        directoryProvider.file(FILENAME) << TextUtil.toPlatformLineSeparators(contents)
    }

    private String getLogFilePath() {
        directoryProvider.testDirectory.file(FILENAME).absolutePath
    }

    def "throws when output file is not found"() {
        when:
        collect()

        then:
        IllegalStateException e = thrown()
        e.message == "Could not find $logFilePath. Cannot collect build event timestamps."
    }

    def "throws when file does not contain 3 lines"() {
        given:
        timestampFileContents """1425907240000000
1425907245000000
"""
        when:
        collect()

        then:
        IllegalStateException e = thrown()
        e.message == "Build event timestamp log at $logFilePath should contain at least 3 lines."
    }

    def "throws when file contains anything that can't be parsed to a long"() {
        given:
        timestampFileContents """null
1425907240000000
1425907245000000"""

        when:
        collect()

        then:
        IllegalStateException e = thrown()
        e.message == "One of the timestamps in build event timestamp log at $logFilePath is not valid."
        e.cause instanceof NumberFormatException
    }

    def "configuration time and execution time are collected"() {
        given:
        timestampFileContents """1425907240000000
1425907245000000
1425907255000000"""

        when:
        collect()

        then:
        measuredOperation.configurationTime == Duration.millis(5)
        measuredOperation.executionTime == Duration.millis(10)
    }
}
