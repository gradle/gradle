/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.results

import org.gradle.performance.ResultSpecification
import org.gradle.performance.fixture.PerformanceResults
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.performance.fixture.BaselineVersion.baseline
import static org.gradle.performance.measure.DataAmount.kbytes
import static org.gradle.performance.measure.Duration.minutes

class ResultsStoreTest extends ResultSpecification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final dbFile = tmpDir.file("results")

    def "persists results"() {
        def result1 = new PerformanceResults(displayName: "test1", testTime: 10000, versionUnderTest: "1.7-rc-1")
        def baseline1 = baseline("1.0")
        def baseline2 = baseline("1.5")
        result1.baselineVersions = [baseline1, baseline2]
        result1.current << operation(executionTime: minutes(12), heapUsed: kbytes(12.33))
        baseline1.results << operation()
        baseline2.results << operation()
        baseline2.results << operation()
        baseline2.results << operation()

        def result2 = new PerformanceResults(displayName: "test2", testTime: 20000, versionUnderTest: "1.7-rc-2")
        result2.current << operation()
        result2.current << operation()
        def baseline3 = baseline("1.0")
        result2.baselineVersions = [baseline3]
        baseline3.results << operation()

        when:
        def writeStore = new ResultsStore(dbFile)
        writeStore.report(result1)
        writeStore.report(result2)
        writeStore.close()

        then:
        tmpDir.file("results.h2.db").exists()

        when:
        def readStore = new ResultsStore(dbFile)
        def results = readStore.loadResults()
        readStore.close()

        then:
        results.size() == 2
        results[0].displayName == "test1"
        results[0].testTime == 10000
        results[0].versionUnderTest == '1.7-rc-1'
        results[0].current.size() == 1
        results[0].current[0].executionTime == minutes(12)
        results[0].current[0].totalMemoryUsed == kbytes(12.33)
        results[0].baselineVersions*.version == ["1.0", "1.5"]
        results[0].baselineVersions.find { it.version == "1.0" }.results.size() == 1
        results[0].baselineVersions.find { it.version == "1.5" }.results.size() == 3
        results[1].displayName == "test2"
        results[1].testTime == 20000
        results[1].versionUnderTest == '1.7-rc-2'
        results[1].current.size() == 2
        results[1].baselineVersions*.version == ["1.0"]
        results[1].baselineVersions.find { it.version == "1.0" }.results.size() == 1
    }
}
