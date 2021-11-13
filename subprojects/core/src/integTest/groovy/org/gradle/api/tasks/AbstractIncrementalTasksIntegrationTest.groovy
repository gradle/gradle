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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.history.changes.ChangeTypeInternal

abstract class AbstractIncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    abstract String getTaskAction()

    abstract ChangeTypeInternal getRebuildChangeType();

    abstract String getPrimaryInputAnnotation();

    def setup() {
        setupTaskSources()
        buildFile << buildFileBase
        buildFile << """
    task incremental(type: IncrementalTask) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }
"""
        file('inputs/file0.txt') << "inputFile0"
        file('inputs/file1.txt') << "inputFile1"
        file('inputs/file2.txt') << "inputFile2"

        file('outputs/file1.txt') << "outputFile1"
        file('outputs/file2.txt') << "outputFile2"
    }

    void setupTaskSources(String inputDirAnnotation = primaryInputAnnotation) {
        file("buildSrc/src/main/groovy/BaseIncrementalTask.groovy").text = """
    import org.gradle.api.*
    import org.gradle.api.file.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*
    import org.gradle.work.*

    abstract class BaseIncrementalTask extends DefaultTask {
        ${inputDirAnnotation}
        @InputDirectory
        abstract DirectoryProperty getInputDir()

        @Optional
        @OutputFile
        abstract RegularFileProperty getOutputFile()

        @TaskAction
        $taskAction

        def touchOutputs() {
        }

        def createOutputsNonIncremental() {
        }

        @Internal
        def addedFiles = []
        @Internal
        def modifiedFiles = []
        @Internal
        def removedFiles = []
        @Internal
        def incrementalExecution
    }
        """
        file("buildSrc/src/main/groovy/IncrementalTask.groovy").text = """
    import org.gradle.api.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*

    abstract class IncrementalTask extends BaseIncrementalTask {
        @Input
        def String prop

        @OutputDirectory
        def File outputDir

        @Override
        def createOutputsNonIncremental() {
            new File(outputDir, 'file1.txt').text = 'outputFile1'
            new File(outputDir, 'file2.txt').text = 'outputFile2'
        }

        @Override
        def touchOutputs() {
            outputDir.eachFile {
                it << "more content"
            }
        }
    }
"""
    }

    private static String getBuildFileBase() {
        """
    ext {
        incrementalExecution = true
        added = []
        modified = []
        removed = []
    }

    task incrementalCheck(dependsOn: "incremental") {
        def ext = project.ext
        doLast {
            assert incremental.incrementalExecution == ext.incrementalExecution
            assert incremental.addedFiles.collect({ it.name }).sort() == ext.added
            assert incremental.modifiedFiles.collect({ it.name }).sort() == ext.modified
            assert incremental.removedFiles.collect({ it.name }).sort() == ext.removed
        }
    }
"""
    }

    def "incremental task is informed that all input files are 'out-of-date' when run for the first time"() {
        expect:
        executesNonIncrementally()
    }

    def "incremental task is skipped when run with no changes since last execution"() {
        given:
        previousExecution()

        when:
        run "incremental"

        then:
        skipped(":incremental")
    }

    def "incremental task is informed of 'out-of-date' files when input file modified"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesIncrementally(modified: ['file1.txt'])
    }

    def "incremental task is informed of 'out-of-date' files when input file added"() {
        given:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesIncrementally(added: ['file3.txt'])
    }

    def "incremental task is informed of 'out-of-date' files when input file removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file2.txt').delete()

        then:
        executesIncrementally(removed: ['file2.txt'])
    }

    def "incremental task is informed of 'out-of-date' files when all input files removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file0.txt').delete()
        file('inputs/file1.txt').delete()
        file('inputs/file2.txt').delete()

        then:
        executesIncrementally(removed: ['file0.txt', 'file1.txt', 'file2.txt'])
    }

    def "incremental task is informed of 'out-of-date' files with added, removed and modified files"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"
        file('inputs/file2.txt').delete()
        file('inputs/file3.txt') << "new file 3"
        file('inputs/file4.txt') << "new file 4"

        then:
        executesIncrementally(
            modified: ['file1.txt'],
            removed: ['file2.txt'],
            added: ['file3.txt', 'file4.txt']
        )
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
        executesIncrementally(added: ['file3.txt'])
    }

    def "incremental task is informed that all input files are 'out-of-date' when input property has changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.prop = 'changed'"

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when input file property has been removed"() {
        given:
        buildFile << """
            if (file('new-input.txt').exists()) {
                incremental.inputs.file('new-input.txt')
            }
        """
        def toBeRemovedInputFile = file('new-input.txt')
        toBeRemovedInputFile.text = "to be removed input file"
        previousExecution()

        when:
        toBeRemovedInputFile.delete()

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when task class has changed"() {
        given:
        previousExecution()

        when:
        buildFile.text = buildFileBase
        buildFile << """
    abstract class IncrementalTask2 extends BaseIncrementalTask {}
    task incremental(type: IncrementalTask2) {
        inputDir = project.mkdir('inputs')
    }
"""

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output directory is changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputDir = project.mkdir('new-outputs')"

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has changed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt") << "further change"

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has been removed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when all output files have been removed"() {
        given:
        previousExecution()

        when:
        file("outputs").deleteDir()

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when Task.upToDate() is false"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputs.upToDateWhen { false }"

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed that all input files are 'out-of-date' when gradle is executed with --rerun-tasks"() {
        given:
        previousExecution()

        when:
        executer.withArgument("--rerun-tasks")

        then:
        executesNonIncrementally()
    }

    def "incremental task is informed of 'out-of-date' files since previous successful execution"() {
        given:
        previousExecution()

        and:
        file('inputs/file1.txt') << "changed content"

        when:
        failedExecution()

        then:
        executesIncrementally(modified: ['file1.txt'])
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
        executer.withArgument("-DforceFail=yep")
        assert fails("incremental")
        executer.withArguments()
    }

    def executesIncrementally(Map changes) {
        executesIncrementalTask(incremental: true, *:changes)
    }

    def executesNonIncrementally(List<String> rebuiltFiles = preexistingInputs) {
        executesIncrementalTask(
            incremental: false,
            (rebuildChangeType.name().toLowerCase(Locale.US)): rebuiltFiles
        )
    }

    List<String> preexistingInputs = ['file0.txt', 'file1.txt', 'file2.txt']

    def executesIncrementalTask(Map options) {
        boolean incremental = options.incremental != false
        List<String> added = options.added ?: []
        List<String> modified = options.modified ?: []
        List<String> removed = options.removed ?: []

        buildFile << """
            ext.added = ${added.collect { "'${it}'"}}
            ext.modified = ${modified.collect { "'${it}'"}}
            ext.removed = ${removed.collect { "'${it}'"}}
            ext.incrementalExecution = ${incremental}
        """
        succeeds("incrementalCheck")
    }
}
