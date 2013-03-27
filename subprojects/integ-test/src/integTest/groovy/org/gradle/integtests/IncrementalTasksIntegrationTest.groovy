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

package org.gradle.integtests
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class IncrementalTasksIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << buildFileBase
        buildFile << """
    task incremental(type: IncrementalTask) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }
"""
        file('inputs/file1.txt') << "file content"
        file('inputs/file2.txt') << "file content2"

        file('outputs/file1.txt') << "output content1"
        file('outputs/file2.txt') << "output content1"
    }

    private static String getBuildFileBase() {
        """
    class IncrementalTask extends DefaultTask {
        @Input
        def String prop

        @InputDirectory
        def File inputDir

        @OutputDirectory
        def File outputDir

        @TaskAction
        void execute(TaskInputChanges inputs) {
            allOutOfDate = inputs.allOutOfDate

            inputs
            .outOfDate({ change ->
                if (change.added) {
                    addedFiles << change.file
                } else {
                    changedFiles << change.file
                }
            } as Action)
            .removed({ change ->
                removedFiles << change.file
            } as Action)
            .process()
        }

        def addedFiles = []
        def changedFiles = []
        def removedFiles = []
        def allOutOfDate
    }

    ext {
        allOutOfDate = false
        added = []
        changed = []
        removed = []
    }

    task incrementalCheck(dependsOn: "incremental") << {
        assert incremental.allOutOfDate == project.ext.allOutOfDate
        assert incremental.addedFiles.collect { it.name } as Set == project.ext.added as Set
        assert incremental.changedFiles.collect { it.name } as Set == project.ext.changed as Set
        assert incremental.removedFiles.collect { it.name } as Set == project.ext.removed as Set
    }
"""
    }

    def "incremental task action is executed with rebuild context when run for the first time"() {
        expect:
        executesWithRebuildContext()
    }

    def "incremental task is skipped when run with no changes since last execution"() {
        given:
        previousExecution()

        when:
        run "incremental"

        then:
        ":incremental" in skippedTasks
    }

    def "incremental task execution context reports modified input file"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesWithIncrementalContext("ext.changed = ['file1.txt']");
    }

    def "incremental task execution context reports added input file"() {
        given:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesWithIncrementalContext("ext.added = ['file3.txt']")
    }

    def "incremental task execution context reports removed input file"() {
        given:
        previousExecution()

        when:
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file2.txt']")
    }

    def "incremental task execution context reports all input files removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt').delete()
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file1.txt', 'file2.txt']")
    }

    def "incremental task action is executed with rebuild context when input property changes"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.prop = 'changed'"

        then:
        executesWithRebuildContext()
    }

    def "incremental task action is executed with rebuild context task class changes"() {
        given:
        previousExecution()

        when:
        buildFile.text = buildFileBase
        buildFile << """
    class IncrementalTask2 extends IncrementalTask {}
    task incremental(type: IncrementalTask2) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }
"""

        then:
        executesWithRebuildContext()
    }

    def "incremental task action is executed with rebuild context when output directory is changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputDir = project.mkdir('new-outputs')"

        then:
        executesWithRebuildContext()
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when output file has changed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").touch().text == "foobar"

        then:
        executesWithRebuildContext()
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when output file has been removed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()

        then:
        executesWithRebuildContext()
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when all output files have been removed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()
        file("outputs/file2.txt").delete()

        then:
        executesWithRebuildContext()
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when task has no declared outputs"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()
        file("outputs/file2.txt").delete()

        then:
        executesWithRebuildContext()
    }

    def "incremental task action is executed with rebuild context when Task.upToDate() is false"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputs.upToDateWhen { false }"

        then:
        executesWithRebuildContext()
    }

    def "incremental task action is executed with rebuild context when gradle is executed with --rerun-tasks"() {
        given:
        previousExecution()

        when:
        executer.withArgument("--rerun-tasks")

        then:
        executesWithRebuildContext()
    }

    def previousExecution() {
        run "incremental"
    }

    def executesWithIncrementalContext(String fileChanges) {
        buildFile << fileChanges
        succeeds "incrementalCheck"
    }

    def executesWithRebuildContext() {
        buildFile << """
    ext.changed = ['file1.txt', 'file2.txt']
    ext.allOutOfDate = true
"""
        succeeds "incrementalCheck"
    }
}
