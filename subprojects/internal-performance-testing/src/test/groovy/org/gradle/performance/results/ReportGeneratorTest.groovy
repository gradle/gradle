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
import org.gradle.performance.util.Git
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class ReportGeneratorTest extends ResultSpecification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule SetSystemProperties properties = new SetSystemProperties("org.gradle.performance.db.url": "jdbc:h2:" + tmpDir.testDirectory)
    final ReportGenerator generator = new ReportGenerator()
    final dbFile = tmpDir.file("results")
    final reportDir = tmpDir.file("report")

    def "generates report"() {
        setup:
        def store = new CrossVersionResultsStore(dbFile.name)
        def result2 = crossVersionResults()
        result2.current << operation()
        result2.current << operation()
        store.report(result2)

        when:
        generator.generate(store, reportDir)

        then:
        reportDir.file("index.html").isFile()

        cleanup:
        store.close()
    }

    def "does not show tests without results for most recent commit in summary"() {
        given:
        long now = Calendar.getInstance().time.time
        String currentCommitId = Git.current().commitId
        def store = new CrossVersionResultsStore(dbFile.name)
        def resultPrevOld = crossVersionResults()
        def resultPrevExisting = crossVersionResults()
        def resultExisting = crossVersionResults()
        def resultNew = crossVersionResults()

        resultPrevOld.vcsCommits = ['0001']
        resultPrevOld.testId = 'Old Test'
        resultPrevOld.current << operation()
        resultPrevOld.current << operation()
        resultPrevOld.startTime = now - 600
        resultPrevOld.endTime = now - 400
        store.report(resultPrevOld)

        resultPrevExisting.vcsCommits = ['0001']
        resultPrevExisting.testId = 'Existing Test'
        resultPrevExisting.current << operation()
        resultPrevExisting.current << operation()
        resultPrevExisting.startTime = now - 600
        resultPrevExisting.endTime = now - 400
        store.report(resultPrevExisting)

        resultExisting.vcsCommits = [currentCommitId]
        resultExisting.testId = 'Existing Test'
        resultExisting.current << operation()
        resultExisting.current << operation()
        resultExisting.startTime = now - 200
        resultExisting.endTime = now
        store.report(resultExisting)

        resultNew.vcsCommits = [currentCommitId]
        resultNew.testId = 'New Test'
        resultNew.current << operation()
        resultNew.current << operation()
        resultNew.startTime = now - 300
        resultNew.endTime = now - 100
        store.report(resultNew)

        when:
        generator.generate(store, reportDir)

        then:
        !reportDir.file("index.html").text.contains('Test: Old Test')
        reportDir.file("index.html").text.contains('Test: Existing Test')
        reportDir.file("index.html").text.contains('Test: New Test')

        cleanup:
        store.close()
    }
}
