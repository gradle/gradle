/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.joda.time.DateTime
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class GCLoggingCollectorTest extends Specification {
    @Rule
    Resources resources = new Resources()
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def collector = new GCLoggingCollector()

    @Unroll
    def "parses GC Log #logName with locale #locale"() {
        def operation = new MeasuredOperation(start: DateTime.parse("2015-01-22T$startTime"), end: DateTime.parse("2015-01-22T$endTime"))
        def projectDir = tmpDir.createDir("project")
        resources.getResource(logName).copyTo(projectDir.file("gc.txt"))

        when:
        collector.getAdditionalJvmOpts(projectDir)
        collector.collect(operation, locale)

        then:
        operation.totalHeapUsage == DataAmount.kbytes(totalHeapUsage)
        operation.maxHeapUsage == DataAmount.kbytes(maxHeapUsage)
        operation.maxUncollectedHeap == DataAmount.kbytes(maxUncollectedHeap)
        operation.maxCommittedHeap == DataAmount.kbytes(maxCommittedHeap)

        where:
        logName             | totalHeapUsage | maxHeapUsage | maxUncollectedHeap | maxCommittedHeap | locale         | startTime      | endTime
        "gc-1.txt"          | 76639          | 33334        | 20002              | 44092            | Locale.US      | "16:04:50.000" | "16:05:00.000"
        "gc-2.txt"          | 140210         | 40427        | 34145              | 223360           | Locale.US      | "16:04:50.000" | "16:05:00.000"
        "gc-3.txt"          | 183544         | 119384       | 37982              | 295488           | Locale.US      | "16:04:50.000" | "16:05:00.000"
        "gc-4.txt"          | 709909         | 292868       | 86455              | 474176           | Locale.GERMANY | "16:04:50.000" | "16:05:00.000"
        "win-1.txt"         | 76639          | 33334        | 20002              | 44092            | Locale.US      | "16:04:50.000" | "16:05:00.000"
        "gc-3.txt"          | 40704          | 18661        | 6827               | 65472            | Locale.US      | "16:04:50.519" | "16:04:51.319"
        "gc-3.txt"          | 142840         | 119384       | 37982              | 295488           | Locale.US      | "16:04:51.319" | "16:04:52.519"
        "mac-jdk8.0.25.txt" | 48177          | 49967        | 13008              | 308224           | Locale.US      | "16:04:51.319" | "16:04:52.519"
    }
}
