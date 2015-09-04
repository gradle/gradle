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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.performance.measure.DataAmount.kbytes
import static org.gradle.performance.measure.Duration.minutes

class CrossVersionResultsStoreTest extends ResultSpecification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final dbFile = tmpDir.file("results")

    def "persists results"() {
        def result1 = crossVersionResults(testId: "test1",
                testProject: "test-project",
                tasks: ["clean", "build"],
                args: ["--arg1"],
                operatingSystem: "some-os",
                jvm: "java 6",
                testTime: 10000,
                versionUnderTest: "1.7-rc-1",
                vcsBranch: "master",
                vcsCommit: "1234")
        def baseline1 = result1.baseline("1.0")
        def baseline2 = result1.baseline("1.5")
        result1.current << operation(totalTime: minutes(12),
                configurationTime: minutes(1),
                executionTime: minutes(10),
                totalMemoryUsed: kbytes(12.33),
                totalHeapUsage: kbytes(5612.45),
                maxHeapUsage: kbytes(124.01),
                maxUncollectedHeap: kbytes(45.22),
                maxCommittedHeap: kbytes(200))
        baseline1.results << operation()
        baseline2.results << operation()
        baseline2.results << operation()
        baseline2.results << operation()

        def result2 = crossVersionResults(testId: "test2", testTime: 20000, versionUnderTest: "1.7-rc-2")
        result2.current << operation()
        result2.current << operation()
        def baseline3 = result2.baseline("1.0")
        baseline3.results << operation()

        when:
        def writeStore = new CrossVersionResultsStore(dbFile)
        writeStore.report(result1)
        writeStore.report(result2)
        writeStore.close()

        then:
        tmpDir.file("results.h2.db").exists()

        when:
        def readStore = new CrossVersionResultsStore(dbFile)
        def tests = readStore.testNames

        then:
        tests == ["test1", "test2"]

        when:
        def history = readStore.getTestResults("test1")

        then:
        history.id == "test1"
        history.name == "test1"
        history.baselineVersions == ["1.0", "1.5"]
        history.experimentCount == 3
        history.experimentLabels == ["1.0", "1.5", "master"]

        and:
        def results = history.results
        results.size() == 1
        results[0].testId == "test1"
        results[0].displayName == "Results for test project 'test-project' with tasks clean, build"
        results[0].testProject == "test-project"
        results[0].tasks == ["clean", "build"]
        results[0].args == ["--arg1"]
        results[0].operatingSystem == "some-os"
        results[0].jvm == "java 6"
        results[0].testTime == 10000
        results[0].versionUnderTest == '1.7-rc-1'
        results[0].vcsBranch == 'master'
        results[0].vcsCommit == '1234'
        results[0].current.size() == 1
        results[0].current[0].totalTime == minutes(12)
        results[0].current[0].configurationTime == minutes(1)
        results[0].current[0].executionTime == minutes(10)
        results[0].current[0].totalMemoryUsed == kbytes(12.33)
        results[0].current[0].totalHeapUsage == kbytes(5612.45)
        results[0].current[0].maxHeapUsage == kbytes(124.01)
        results[0].current[0].maxUncollectedHeap == kbytes(45.22)
        results[0].current[0].maxCommittedHeap == kbytes(200)
        results[0].baselineVersions*.version == ["1.0", "1.5"]
        results[0].baseline("1.0").results.size() == 1
        results[0].baseline("1.5").results.size() == 3

        when:
        history = readStore.getTestResults("test2")
        results = history.results
        readStore.close()

        then:
        history.baselineVersions == ["1.0"]

        and:
        results.size() == 1
        results[0].testId == "test2"
        results[0].testTime == 20000
        results[0].versionUnderTest == '1.7-rc-2'
        results[0].current.size() == 2
        results[0].baselineVersions*.version == ["1.0"]
        results[0].baseline("1.0").results.size() == 1

        cleanup:
        writeStore?.close()
        readStore?.close()
    }

    def "returns test names in ascending order"() {
        given:
        def writeStore = new CrossVersionResultsStore(dbFile)
        writeStore.report(crossVersionResults(testId: "test3"))
        writeStore.report(crossVersionResults(testId: "test1"))
        writeStore.report(crossVersionResults(testId: "test2"))
        writeStore.close()

        when:
        def readStore = new CrossVersionResultsStore(dbFile)
        def tests = readStore.testNames

        then:
        tests == ["test1", "test2", "test3"]

        cleanup:
        writeStore?.close()
        readStore?.close()
    }

    def "returns top n test executions in descending date order"() {
        given:
        def writeStore = new CrossVersionResultsStore(dbFile)
        writeStore.report(crossVersionResults(testId: "some test", testTime: 30000, versionUnderTest: "1.7-rc-3"))
        writeStore.report(crossVersionResults(testId: "some test", testTime: 10000, versionUnderTest: "1.7-rc-1"))
        writeStore.report(crossVersionResults(testId: "some test", testTime: 20000, versionUnderTest: "1.7-rc-2"))
        writeStore.close()

        when:
        def readStore = new CrossVersionResultsStore(dbFile)
        def results = readStore.getTestResults("some test")

        then:
        results.results.size() == 3
        results.results*.versionUnderTest == ["1.7-rc-3", "1.7-rc-2", "1.7-rc-1"]
        results.resultsOldestFirst*.versionUnderTest == ["1.7-rc-1", "1.7-rc-2", "1.7-rc-3"]

        when:
        results = readStore.getTestResults("some test", 2)

        then:
        results.results.size() == 2
        results.results*.versionUnderTest == ["1.7-rc-3", "1.7-rc-2"]
        results.resultsOldestFirst*.versionUnderTest == ["1.7-rc-2", "1.7-rc-3"]

        cleanup:
        writeStore?.close()
        readStore?.close()
    }

    def "the known versions for a test is the union of all baseline versions in ascending order and the union of test branches"() {
        given:
        def writeStore = new CrossVersionResultsStore(dbFile)

        def results1 = crossVersionResults(vcsBranch: "master")
        results1.baseline("1.8-rc-2").results << operation()
        results1.baseline("1.0").results << operation()
        def results2 = crossVersionResults(vcsBranch: "release")
        results2.baseline("1.8-rc-1").results << operation()
        results2.baseline("1.0").results << operation()
        results2.baseline("1.10").results << operation()
        def results3 = crossVersionResults(vcsBranch: "master")
        results3.baseline("1.8").results << operation()
        results3.baseline("1.10").results << operation()

        writeStore.report(results1)
        writeStore.report(results2)
        writeStore.report(results3)
        writeStore.close()

        when:
        def readStore = new CrossVersionResultsStore(dbFile)
        def results = readStore.getTestResults("test-id")

        then:
        results.baselineVersions == ["1.0", "1.8-rc-1", "1.8-rc-2", "1.8", "1.10"]
        results.branches == ["master", "release"]
        results.knownVersions == ["1.0", "1.8-rc-1", "1.8-rc-2", "1.8", "1.10", "master", "release"]
        results.experimentCount == 7
        results.experimentLabels == ["1.0", "1.8-rc-1", "1.8-rc-2", "1.8", "1.10", "master", "release"]

        cleanup:
        writeStore?.close()
        readStore?.close()
    }

    def "returns empty results for unknown id"() {
        given:
        def store = new CrossVersionResultsStore(dbFile)

        expect:
        store.getTestResults("unknown").baselineVersions.empty
        store.getTestResults("unknown").results.empty

        cleanup:
        store?.close()
    }
}
