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

class IncrementalTasksIntegrationTest extends AbstractIntegrationSpec {
    def discoveredDir = file('discoveredDir')

    def "setup"() {
        setupTaskSources()
        buildFile << buildFileBase
        buildFile << """
    task incremental(type: IncrementalTask) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }
"""
        file('discovered/file0.txt') << "discoveredFile0"
        file('discovered/file1.txt') << "discoveredFile1"
        file('discovered/file2.txt') << "discoveredFile2"
        file('discovered/file3.txt') << "discoveredFile3"

        file('inputs/file0.txt') << "inputFile0"
        file('inputs/file1.txt') << "inputFile1"
        file('inputs/file2.txt') << "inputFile2"

        file('outputs/file1.txt') << "outputFile1"
        file('outputs/file2.txt') << "outputFile2"
    }

    private void setupTaskSources() {
        file("buildSrc/src/main/groovy/BaseIncrementalTask.groovy") << """
    import org.gradle.api.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*

    class BaseIncrementalTask extends DefaultTask {
        @InputDirectory
        def File inputDir

        @TaskAction
        void execute(IncrementalTaskInputs inputs) {
            assert !(inputs instanceof ExtensionAware)

            if (project.hasProperty('forceFail')) {
                throw new RuntimeException('failed')
            }

            incrementalExecution = inputs.incremental

            inputs.outOfDate { change ->
                if (change.added) {
                    addedFiles << change.file
                } else {
                    changedFiles << change.file
                }
            }

            inputs.removed { change ->
                removedFiles << change.file
            }


            // register discovered inputs
            [ 'discovered/file0.txt', 'discovered/file1.txt', 'discovered/file2.txt', 'discoveredDir' ].each { fileName ->
                def discoveredInput = project.file(fileName)
                if (discoveredInput.exists()) {
                    inputs.newInput(discoveredInput)
                }
            }

            touchOutputs()

            discoveredFiles = inputs.getDiscoveredInputs()
        }

        def touchOutputs() {
        }

        def addedFiles = []
        def changedFiles = []
        def removedFiles = []
        def discoveredFiles = []
        def incrementalExecution
    }
        """
        file("buildSrc/src/main/groovy/IncrementalTask.groovy") << """
    import org.gradle.api.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*

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
"""
    }

    private static String getBuildFileBase() {
        """
    ext {
        incrementalExecution = true
        added = []
        changed = []
        removed = []
        // all discovered files are discovered each time
        discovered = [ 'file0.txt', 'file1.txt', 'file2.txt' ]
    }

    task incrementalCheck(dependsOn: "incremental") {
        doLast {
            assert incremental.incrementalExecution == project.ext.incrementalExecution
            assert incremental.addedFiles.collect({ it.name }).sort() == project.ext.added
            assert incremental.changedFiles.collect({ it.name }).sort() == project.ext.changed
            assert incremental.removedFiles.collect({ it.name }).sort() == project.ext.removed
            assert incremental.discoveredFiles.collect({ it.name }).sort() == project.ext.discovered
        }
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

    def "incremental task is skipped when run with no changes with discovered empty directory"() {
        discoveredDir.file('empty/dir').mkdirs()

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

    def "incremental task is informed of 'out-of-date' files when discovered input file modified"() {
        given:
        previousExecution()

        when:
        file('discovered/file1.txt') << "changed content"

        then:
        executesWithIncrementalContext()
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
        file('inputs/file0.txt').delete()
        file('inputs/file1.txt').delete()
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file0.txt', 'file1.txt', 'file2.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when discovered input file removed"() {
        given:
        previousExecution()

        when:
        file('discovered/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.discovered = [ 'file0.txt', 'file1.txt' ]")
    }

    def "incremental task discovered inputs are based on last execution only"() {
        given:
        // discovered inputs are file0-file2
        previousExecution()

        when:
        file('discovered/file2.txt').delete()

        then:
        // discovered inputs are file0-file1
        executesWithIncrementalContext("ext.discovered = [ 'file0.txt', 'file1.txt' ]")

        when:
        file('discovered/file2.txt') << "the file is back"
        and:
        run "incremental"
        then:
        // file2 isn't an input now
        ":incremental" in skippedTasks
    }

    def "incremental task discovered inputs are not lost after the task is up-to-date"() {
        given:
        // discovered inputs are file0-file2
        previousExecution()

        when:
        run 'incremental'
        then:
        ":incremental" in skippedTasks

        when:
        file('discovered/file2.txt') << "file changed"
        then:
        executesWithIncrementalContext()
    }

    def "incremental task is informed of 'out-of-date' files when all discovered input files removed"() {
        given:
        previousExecution()

        when:
        file('discovered/file0.txt').delete()
        file('discovered/file1.txt').delete()
        file('discovered/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.discovered = [ ]")
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
        executesWithIncrementalContext("""
ext.changed = ['file1.txt']
ext.removed = ['file2.txt']
ext.added = ['file3.txt', 'file4.txt']
""")
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

    def executesWithRebuildContext(String fileChanges="") {
        buildFile << fileChanges
        buildFile << """
    ext.changed = ['file0.txt', 'file1.txt', 'file2.txt']
    ext.incrementalExecution = false
"""
        succeeds "incrementalCheck"
    }
}
