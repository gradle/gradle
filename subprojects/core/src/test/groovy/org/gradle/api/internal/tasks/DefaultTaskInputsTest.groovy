/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification

import java.util.concurrent.Callable

class DefaultTaskInputsTest extends Specification {
    private final File treeFile = new File('tree')
    private final FileTree tree = [getFiles: { [treeFile] as Set}] as FileTree
    private final FileResolver resolver = [
            resolve: {new File(it)},
            resolveFilesAsTree: {tree}
    ] as FileResolver

    private TaskStatusNagger taskStatusNagger = Mock()
    private final DefaultTaskInputs inputs = new DefaultTaskInputs(resolver, {} as TaskInternal, taskStatusNagger)

    def defaultValues() {
        expect:
        inputs.files.empty
        inputs.properties.isEmpty()
        !inputs.hasInputs
        !inputs.hasSourceFiles
        inputs.sourceFiles.empty
    }

    def canRegisterInputFiles() {
        when:
        inputs.files('a')

        then:
        inputs.files.files == [new File('a')] as Set
    }

    def canRegisterInputDir() {
        when:
        inputs.dir('a')

        then:
        inputs.files.files == [treeFile] as Set
    }

    def canRegisterInputProperty() {
        when:
        inputs.property('a', 'value')

        then:
        inputs.properties == [a: 'value']
    }

    def canRegisterInputPropertyUsingAClosure() {
        when:
        inputs.property('a', { 'value' })

        then:
        inputs.properties == [a: 'value']
    }

    def canRegisterInputPropertyUsingACallable() {
        when:
        inputs.property('a', { 'value' } as Callable)

        then:
        inputs.properties == [a: 'value']
    }

    def canRegisterInputPropertyUsingAFileCollection() {
        def files = [new File('file')] as Set

        when:
        inputs.property('a', [getFiles: { files }] as FileCollection)

        then:
        inputs.properties == [a: files]
    }

    def inputPropertyCanBeNestedCallableAndClosure() {
        def files = [new File('file')] as Set
        def fileCollection = [getFiles: { files }] as FileCollection
        def callable = {fileCollection} as Callable

        when:
        inputs.property('a', { callable })

        then:
        inputs.properties == [a: files]
    }

    def canRegisterSourceFile() {
        when:
        inputs.source('file')

        then:
        inputs.sourceFiles.files == ([new File('file')] as Set)
    }

    def canRegisterSourceFiles() {
        when:
        inputs.source('file', 'file2')

        then:
        inputs.sourceFiles.files == ([new File('file'), new File('file2')] as Set)
    }

    def canRegisterSourceDir() {
        when:
        inputs.sourceDir('dir')

        then:
        inputs.sourceFiles.files == [treeFile] as Set
    }

    def sourceFilesAreAlsoInputFiles() {
        when:
        inputs.source('file')

        then:
        inputs.sourceFiles.files == ([new File('file')] as Set)
        inputs.files.files == ([new File('file')] as Set)
    }

    def hasInputsWhenEmptyInputFilesRegistered() {
        when:
        inputs.files([])

        then:
        inputs.hasInputs
        !inputs.hasSourceFiles
    }

    def hasInputsWhenNonEmptyInputFilesRegistered() {
        when:
        inputs.files('a')

        then:
        inputs.hasInputs
        !inputs.hasSourceFiles
    }

    def hasInputsWhenInputPropertyRegistered() {
        when:
        inputs.property('a', 'value')

        then:
        inputs.hasInputs
        !inputs.hasSourceFiles
    }

    def hasInputsWhenEmptySourceFilesRegistered() {
        when:
        inputs.source([])

        then:
        inputs.hasInputs
        inputs.hasSourceFiles
    }

    def hasInputsWhenSourceFilesRegistered() {
        when:
        inputs.source('a')

        then:
        inputs.hasInputs
        inputs.hasSourceFiles
    }

    public void callsTaskStatusNaggerWhenFileMethodCalled() {
        when:
        inputs.file("aFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.file(Object)")
    }

    public void callsTaskStatusNaggerWhenFilesMethodCalled() {
        when:
        inputs.files("aFile", "bFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.files(Object...)")
    }

    public void callsTaskStatusNaggerWhenDirMethodCalled() {
        when:
        inputs.dir("aFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.dir(Object)")
    }

    public void callsTaskStatusNaggerWhenSourceDirMethodCalled() {
        when:
        inputs.sourceDir("aFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.sourceDir(Object)")
    }

    public void callsTaskStatusNaggerWhenSourceMethodCalled() {
        when:
        inputs.source("aFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.source(Object)")
        when:
        inputs.source("aFile", "bFile")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.source(Object...)")
    }

    public void callsTaskStatusNaggerWhenPropertyMethodCalled() {
        when:
        inputs.property("name", "value")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.property(String, Object)")
    }

    public void callsTaskStatusNaggerWhenPropertiesMethodCalled() {
        when:
        inputs.properties(name:"value")
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.properties(Map)")
    }
}