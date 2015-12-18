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

package org.gradle.performance.results

import org.gradle.performance.ResultSpecification
import org.gradle.performance.fixture.BuildDisplayInfo
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.performance.measure.DataAmount.kbytes
import static org.gradle.performance.measure.Duration.minutes

class CrossBuildResultsStoreTest extends ResultSpecification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final dbFile = tmpDir.file("results")

    def "persists reports"() {
        given:
        def results1 = crossBuildResults(testId: "test1", testGroup: "group1")
        def buildResults1 = results1.buildResult(
                new BuildDisplayInfo(
                        "simple",
                        "simple display",
                        ["build"],
                        ["-i"]
                )
        )
        buildResults1 << operation(totalTime: minutes(12),
                configurationTime: minutes(1),
                executionTime: minutes(10),
                totalMemoryUsed: kbytes(12.33),
                totalHeapUsage: kbytes(5612.45),
                maxHeapUsage: kbytes(124.01),
                maxUncollectedHeap: kbytes(45.22),
                maxCommittedHeap: kbytes(200)
        )
        def buildResults2 = results1.buildResult(new BuildDisplayInfo("complex", "complex display", [], []))
        buildResults2 << operation()
        buildResults2 << operation()

        and:
        def results2 = crossBuildResults(testId: "test2", testGroup: "group2")
        results2.buildResult(new BuildDisplayInfo("simple", "simple display", ["build"], ["-i"]))

        when:
        def writeStore = new CrossBuildResultsStore(dbFile)
        writeStore.report(results1)
        writeStore.report(results2)
        writeStore.close()

        then:
        tmpDir.file("results.h2.db").exists()

        when:
        def readStore = new CrossBuildResultsStore(dbFile)
        def tests = readStore.testNames

        then:
        tests == ["test1", "test2"]

        when:
        def history = readStore.getTestResults("test1")

        then:
        history.id == "test1"
        history.name == "test1"
        history.experimentCount == 2
        history.experimentLabels == ["complex display", "simple display"]

        and:
        def firstSpecification = history.builds[0]
        firstSpecification == new BuildDisplayInfo("complex", "complex display", [], [])
        history.results.first().buildResult(firstSpecification).size() == 2

        and:
        def secondSpecification = history.builds[1]
        secondSpecification == new BuildDisplayInfo("simple", "simple display", ["build"], ["-i"])
        def crossBuildPerformanceResults = history.results.first()
        crossBuildPerformanceResults.testId == "test1"
        crossBuildPerformanceResults.jvm == "java 7"
        crossBuildPerformanceResults.versionUnderTest == "Gradle 1.0"
        crossBuildPerformanceResults.operatingSystem == "windows"
        crossBuildPerformanceResults.testTime == 100
        crossBuildPerformanceResults.vcsBranch == "master"
        crossBuildPerformanceResults.vcsCommit == "abcdef"

        and:
        def operation = crossBuildPerformanceResults.buildResult(secondSpecification).first
        operation.totalTime == minutes(12)
        operation.configurationTime == minutes(1)
        operation.executionTime == minutes(10)
        operation.totalMemoryUsed == kbytes(12.33)
        operation.totalHeapUsage == kbytes(5612.45)
        operation.maxHeapUsage == kbytes(124.01)
        operation.maxUncollectedHeap == kbytes(45.22)
        operation.maxCommittedHeap == kbytes(200)

        cleanup:
        writeStore?.close()
        readStore?.close()
    }

    def "returns top n results in descending date order"() {
        given:
        def results1 = crossBuildResults(testId: "test1", testTime: 1000)
        results1.buildResult(new BuildDisplayInfo("simple1", "simple 1", ["build"], ["-i"]))

        and:
        def results2 = crossBuildResults(testId: "test1", testTime: 2000)
        results2.buildResult(new BuildDisplayInfo("simple2", "simple 2", ["build"], ["-i"]))

        and:
        def results3 = crossBuildResults(testId: "test1", testTime: 3000)
        results3.buildResult(new BuildDisplayInfo("simple3", "simple 3", ["build"], ["-i"]))

        and:
        def writeStore = new CrossBuildResultsStore(dbFile)
        writeStore.report(results2)
        writeStore.report(results3)
        writeStore.report(results1)
        writeStore.close()

        when:
        def readStore = new CrossBuildResultsStore(dbFile)
        def history = readStore.getTestResults("test1")

        then:
        history.results*.testTime == [3000, 2000, 1000]

        when:
        history = readStore.getTestResults("test1", 2)

        then:
        history.results*.testTime == [3000, 2000]

        cleanup:
        writeStore?.close()
        readStore?.close()
    }
}
