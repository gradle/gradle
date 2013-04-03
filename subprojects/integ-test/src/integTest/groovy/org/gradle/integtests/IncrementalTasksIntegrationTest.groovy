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
        file('inputs/file1.txt') << "inputFile1"
        file('inputs/file2.txt') << "inputFile2"

        file('outputs/file1.txt') << "outputFile1"
        file('outputs/file2.txt') << "outputFile2"
    }

    private static String getBuildFileBase() {
        """
    class BaseIncrementalTask extends DefaultTask {
        @InputDirectory
        def File inputDir

        @TaskAction
        void execute(TaskInputChanges inputs) {
            if (project.hasProperty('forceFail')) {
                throw new RuntimeException('failed')
            }

            allOutOfDate = inputs.allOutOfDate

            inputs.outOfDate({ change ->
                if (change.added) {
                    addedFiles << change.file
                } else {
                    changedFiles << change.file
                }
            } as Action)

            inputs.removed({ change ->
                removedFiles << change.file
            } as Action)

            touchOutputs()
        }

        def touchOutputs() {
        }

        def addedFiles = []
        def changedFiles = []
        def removedFiles = []
        def allOutOfDate
    }

    class IncrementalTask extends BaseIncrementalTask {
        @Input
        def String prop

        @OutputDirectory
        def File outputDir

        @Override
        def touchOutputs() {
            outputDir.eachFile {
                it << "more content"
            }
        }
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

    def "incremental task is informed that all input files are 'out-of-date' when run for the first time"() {
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

    def "incremental task is informed of 'out-of-date' files when input file modified"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesWithIncrementalContext("ext.changed = ['file1.txt']");
    }

    def "incremental task is informed of 'out-of-date' files when input file added"() {
        given:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesWithIncrementalContext("ext.added = ['file3.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when input file removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file2.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when all input files removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt').delete()
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file1.txt', 'file2.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when task has no declared outputs or properties"() {
        given:
        buildFile.text = buildFileBase
        buildFile << """
    task incremental(type: BaseIncrementalTask) {
        inputDir = project.mkdir('inputs')
    }
"""
        and:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesWithIncrementalContext("ext.added = ['file3.txt']")
    }

    def "incremental task is informed that all input files are 'out-of-date' when input property has changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.prop = 'changed'"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when task class has changed"() {
        given:
        previousExecution()

        when:
        buildFile.text = buildFileBase
        buildFile << """
    class IncrementalTask2 extends BaseIncrementalTask {}
    task incremental(type: IncrementalTask2) {
        inputDir = project.mkdir('inputs')
    }
"""

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output directory is changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputDir = project.mkdir('new-outputs')"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has changed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt") << "further change"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has been removed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when all output files have been removed"() {
        given:
        previousExecution()

        when:
        file("outputs").deleteDir()

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when Task.upToDate() is false"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputs.upToDateWhen { false }"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when gradle is executed with --rerun-tasks"() {
        given:
        previousExecution()

        when:
        executer.withArgument("--rerun-tasks")

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed of 'out-of-date' files since previous successful execution"() {
        given:
        previousExecution()

        and:
        file('inputs/file1.txt') << "changed content"

        when:
        failedExecution()

        then:
        executesWithIncrementalContext("ext.changed = ['file1.txt']");
    }
    /*
     7. Sad-day cases
         - Incremental task has input files declared
         - Incremental task action throws exception
         - Incremental task action processes outOfDate files multiple times
         - Attempt to process removed files without first processing outOfDate files
     */

    def previousExecution() {
        run "incremental"
    }

    def failedExecution() {
        executer.withArgument("-PforceFail=yep")
        assert fails("incremental")
        executer.withArguments()
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
