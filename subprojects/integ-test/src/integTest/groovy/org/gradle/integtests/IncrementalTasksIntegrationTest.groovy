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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore

class IncrementalTasksIntegrationTest extends AbstractIntegrationSpec {
    TestFile outputFile

    def "setup"() {
        buildFile << """
    class IncrementalTask extends DefaultTask {
        @Input
        def String prop

        @InputDirectory
        def File inputDir

        @OutputDirectory
        def File outputDir

        @TaskAction
        void execute(TaskExecutionContext executionContext) {
            rebuild = executionContext.rebuild

            executionContext.inputFileChanges({ change ->
                if (change.added) {
                    addedFiles << change.file
                } else if (change.modified) {
                    changedFiles << change.file
                } else if (change.removed) {
                    removedFiles << change.file
                }
            } as Action)
        }

        def addedFiles = []
        def changedFiles = []
        def removedFiles = []
        def rebuild
    }

    ext {
        rebuild = false
        added = []
        changed = []
        removed = []
    }

    task incremental(type: IncrementalTask) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }

    task incrementalCheck(dependsOn: incremental) << {
        assert incremental.rebuild == project.ext.rebuild
        assert incremental.addedFiles.collect { it.name } == project.ext.added
        assert incremental.changedFiles.collect { it.name } == project.ext.changed
        assert incremental.removedFiles.collect { it.name } == project.ext.removed
    }
"""
        file('inputs/file1.txt') << "file content"
        file('inputs/file2.txt') << "file content2"

        outputFile = file('outputs/output.txt')
        outputFile << "content"
    }

    def "incremental task action is executed with rebuild context when run for the first time"() {
        when:
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        then:
        succeeds "incrementalCheck"
    }

    def "incremental task is skipped when run with no changes"() {
        given:
        run "incremental"

        when:
        run "incremental"

        then:
        ":incremental" in skippedTasks
    }

    def "incremental task execution context reports modified input file"() {
        buildFile << "ext.changed = ['file1.txt']"
        when:
        run "incremental"

        and:
        file('inputs/file1.txt') << "changed content"

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task execution context reports added input file"() {
        buildFile << "ext.added = ['file3.txt']"
        when:
        run "incremental"

        and:
        file('inputs/file3.txt') << "file3 content"

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task execution context reports removed input file"() {
        buildFile << "ext.removed = ['file2.txt']"
        when:
        run "incremental"

        and:
        file('inputs/file2.txt').delete()

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task action is executed with rebuild context when input property changes"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        buildFile << "incremental.prop = 'changed'"

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task action is executed with rebuild context when output directory is changed"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        buildFile << "incremental.outputDir = project.mkdir('new-outputs')"

        then:
        succeeds "incrementalCheck"
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when output file has changed"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        outputFile.touch() << "changed"

        then:
        succeeds "incrementalCheck"
    }

    @Ignore("Not sure why this isn't picking up output file changes")
    def "incremental task action is executed with rebuild context when output file has been removed"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        outputFile.delete()
        file('outputs/new-file.txt') << 'new output'

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task action is executed with rebuild context when Task.upToDate() is false"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        buildFile << "incremental.outputs.upToDateWhen { false }"

        then:
        succeeds "incrementalCheck"
    }

    def "incremental task action is executed with rebuild context when gradle is executed with --rerun-tasks"() {
        buildFile << """
    ext.added = ['file1.txt', 'file2.txt']
    ext.rebuild = true
"""
        when:
        run "incremental"

        and:
        executer.withArgument("--rerun-tasks")

        then:
        succeeds "incrementalCheck"
    }

}
