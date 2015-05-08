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
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultTaskOutputsTest extends Specification {

    private TaskMutator taskStatusNagger = Stub() {
        mutate(_, _) >> { String method, Runnable action -> action.run() }
    }
    private final TaskInternal task = [toString: {'task'}] as TaskInternal
    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs({new File(it)} as FileResolver, task, taskStatusNagger)

    public void hasNoOutputsByDefault() {
        setup:
        assert outputs.files.files.isEmpty()
        assert !outputs.hasOutput
    }

    public void outputFileCollectionIsBuiltByTask() {
        setup:
        assert outputs.files.buildDependencies.getDependencies(task) == [task] as Set
    }

    public void canRegisterOutputFiles() {
        when:
        outputs.files('a')

        then:
        outputs.files.files == [new File('a')] as Set
    }

    public void hasOutputsWhenEmptyOutputFilesRegistered() {
        when:
        outputs.files([])

        then:
        outputs.hasOutput
    }

    public void hasOutputsWhenNonEmptyOutputFilesRegistered() {
        when:
        outputs.files('a')

        then:
        outputs.hasOutput
    }

    public void hasOutputsWhenUpToDatePredicateRegistered() {
        when:
        outputs.upToDateWhen { false }

        then:
        outputs.hasOutput
    }

    public void canSpecifyUpToDatePredicateUsingClosure() {
        boolean upToDate = false

        when:
        outputs.upToDateWhen { upToDate }

        then:
        !outputs.upToDateSpec.isSatisfiedBy(task)

        when:
        upToDate = true

        then:
        outputs.upToDateSpec.isSatisfiedBy(task)
    }

    public void getPreviousFilesDelegatesToTaskHistory() {
        TaskExecutionHistory history = Mock()
        FileCollection outputFiles = Mock()

        setup:
        outputs.history = history

        when:
        def f = outputs.previousFiles

        then:
        f == outputFiles
        1 * history.outputFiles >> outputFiles
    }

    public void getPreviousFilesFailsWhenNoTaskHistoryAvailable() {
        when:
        outputs.previousFiles

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Task history is currently not available for this task.'
    }
}