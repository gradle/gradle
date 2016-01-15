/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.continuous

import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.filewatch.ChangeReporter.SHOW_INDIVIDUAL_CHANGES_LIMIT

// Developer is able to easily determine the file(s) that triggered a rebuild
class ContinuousBuildChangeReportingIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    TestFile inputDir
    private static int LIMIT = SHOW_INDIVIDUAL_CHANGES_LIMIT

    def setup() {
        buildFile << """
            task theTask {
              inputs.dir "inputDir"
              doLast {}
            }
        """
        inputDir = file("inputDir").createDir()
    }

    def "should report the absolute file path of the file added when 1 file is added to the input directory"() {
        given:
        def inputFile = inputDir.file("input.txt")
        when:
        succeeds("theTask")
        inputFile.text = 'New input file'

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, [new ChangeEntry('new file', inputFile)])
    }

    def "should report the absolute file path of the file added when 3 files are added to the input directory"() {
        given:
        def inputFiles = (1..LIMIT).collect { inputDir.file("input${it}.txt") }
        when:
        succeeds("theTask")
        inputFiles.each { it.text = 'New input file' }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputFiles.collect { new ChangeEntry('new file', it) })
    }

    def "should report the absolute file path of the first 3 changes and report the number of other changes when more that 3 files are added to the input directory of each task"() {
        given:
        def inputFiles = (1..9).collect { inputDir.file("input${it}.txt") }
        when:
        succeeds("theTask")
        inputFiles.each { it.text = 'New input file' }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputFiles.take(LIMIT).collect { new ChangeEntry('new file', it) }, 6)
    }

    def "should report the changes when files are removed"(changesCount) {
        given:
        def inputFiles = (1..changesCount).collect { inputDir.file("input${it}.txt") }
        inputFiles.each { it.text = 'New input file' }
        def acceptedMoreChanges
        if (changesCount > LIMIT) {
            int expectedMoreChangesCount = changesCount - LIMIT
            if (OperatingSystem.current().isWindows()) {
                expectedMoreChangesCount *= 2
            }
            acceptedMoreChanges = [expectedMoreChangesCount, expectedMoreChangesCount + 1]
        }

        when:
        succeeds("theTask")
        inputFiles.each { it.delete() }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputFiles.take(LIMIT).collect { new ChangeEntry('deleted', it) }, acceptedMoreChanges)

        where:
        changesCount << [1, LIMIT, 11]
    }

    def "should report the changes when files are modified"(changesCount) {
        given:
        def inputFiles = (1..changesCount).collect { inputDir.file("input${it}.txt") }
        inputFiles.each { it.text = 'New input file' }
        def acceptedMoreChanges
        if (changesCount > LIMIT) {
            int expectedMoreChangesCount = changesCount - LIMIT
            acceptedMoreChanges = [expectedMoreChangesCount, expectedMoreChangesCount + 1]
        }

        when:
        succeeds("theTask")
        inputFiles.each { it.text = 'File modified' }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputFiles.take(LIMIT).collect { new ChangeEntry('modified', it) }, acceptedMoreChanges)

        where:
        changesCount << [1, LIMIT, 11]
    }

    def "should report the changes when directories are added"(changesCount) {
        given:
        def inputDirectories = (1..changesCount).collect { inputDir.file("input${it}Directory") }
        int expectedMoreChangesCount = changesCount - LIMIT

        when:
        succeeds("theTask")
        inputDirectories.each { it.mkdir() }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputDirectories.take(LIMIT).collect { new ChangeEntry('new directory', it) }, expectedMoreChangesCount)

        where:
        changesCount << [1, LIMIT, 11]
    }


    def "should report the changes when directories are deleted"(changesCount) {
        given:
        def inputDirectories = (1..changesCount).collect { inputDir.file("input${it}Directory").createDir() }
        int expectedMoreChangesCount = (changesCount - LIMIT) * 2

        when:
        succeeds("theTask")
        inputDirectories.each { it.delete() }

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, inputDirectories.take(LIMIT).collect { new ChangeEntry('deleted', it) }, expectedMoreChangesCount)

        where:
        changesCount << [1, LIMIT, 11]
    }


    def "should report the changes when multiple changes are made at once"() {
        given:
        def inputFiles = (1..11).collect { inputDir.file("input${it}.txt") }
        inputFiles.each { it.text = 'Input file' }
        int expectedMoreChangesCount = 7
        if (OperatingSystem.current().isWindows()) {
            expectedMoreChangesCount = 8
        }

        when:
        succeeds("theTask")
        inputDir.file("input12.txt").text = 'New Input file'
        inputFiles.eachWithIndex { TestFile file, int i ->
            if (i % 2 == 0) {
                file.text = 'Modified file'
            } else if (i == 1 || i == 7) {
                file.delete()
            }
        }
        inputDir.file("input13.txt").text = 'New Input file'

        then:
        def result = succeeds()
        sendEOT()
        assertReportsChanges(result, null, expectedMoreChangesCount)
    }

    @TupleConstructor
    static class ChangeEntry {
        String type
        File file
    }

    private void assertReportsChanges(ExecutionResult result, List<ChangeEntry> entries) {
        assertReportsChanges(result, entries, null)
    }

    private void assertReportsChanges(ExecutionResult result, List<ChangeEntry> entries, Integer moreChanges) {
        assertReportsChanges(result, entries, moreChanges > 0 ? [moreChanges] : null)
    }

    private void assertReportsChanges(ExecutionResult result, List<ChangeEntry> entries, List<Integer> acceptedMoreChanges) {
        String changeReportOutput
        result.output.with {
            int pos = it.indexOf('Change detected, executing build...')
            if (pos > -1) {
                changeReportOutput = it.substring(0, pos)
            }
        }
        assert changeReportOutput != null: 'No change report output.'

        List<String> actualLines = changeReportOutput.readLines()
        int actualMoreChanges = 0
        (actualLines.last() =~ /and (\d+) more changes/).find { line, matchedChangeCount ->
            actualLines.remove(actualLines.size() - 1)
            actualMoreChanges = matchedChangeCount as int
        }

        if (entries != null) {
            List<String> expectedLines = entries.collect { "${it.type}: ${it.file.absolutePath}".toString() }
            assert expectedLines == actualLines
        }

        if (actualMoreChanges > 0 || acceptedMoreChanges) {
            assert actualMoreChanges != 0: "Expecting 'more changes' line, but it wasn't found"
            assert acceptedMoreChanges: "Not expecting a 'more changes' line"

            if (acceptedMoreChanges.size() == 1) {
                def expectedMoreChanges = acceptedMoreChanges.first()
                assert expectedMoreChanges == actualMoreChanges
            } else {
                boolean moreChangesMatches = acceptedMoreChanges.any {
                    it == actualMoreChanges
                }
                assert moreChangesMatches: "'more changes' doesn't match, actualMoreChanges ${actualMoreChanges} , acceptedMoreChanges ${acceptedMoreChanges}"
            }
        }
    }
}
