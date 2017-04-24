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

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

@UsesNativeServices
class DefaultTaskOutputsTest extends Specification {

    private TaskMutator taskStatusNagger = Stub() {
        mutate(_, _) >> { String method, def action ->
            if (action instanceof Runnable) {
                action.run()
            } else if (action instanceof Callable) {
                action.call()
            }
        }
    }
    def task = Mock(TaskInternal) {
        getName() >> "task"
        toString() >> "task 'task'"
    }
    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs({new File(it)} as FileResolver, task, taskStatusNagger)

    void hasNoOutputsByDefault() {
        setup:
        assert outputs.files.files.isEmpty()
        assert !outputs.hasOutput
    }

    void outputFileCollectionIsBuiltByTask() {
        setup:
        assert outputs.files.buildDependencies.getDependencies(task) == [task] as Set
    }

    def "can register output file"() {
        when: outputs.file("a")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output file with property name"() {
        when: outputs.file("a").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output dir"() {
        when: outputs.file("a")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output dir with property name"() {
        when: outputs.dir("a").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [DIRECTORY]
    }

    def "cannot register output file with same property name"() {
        outputs.file("a").withPropertyName("alma")
        outputs.file("b").withPropertyName("alma")
        when:
        outputs.fileProperties
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Multiple output file properties with name 'alma'"
    }

    def "can register unnamed output files"() {
        when: outputs.files("a", "b")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
    }

    def "can register unnamed output files with property name"() {
        when: outputs.files("a", "b").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
    }

    def "can register named output files"() {
        when: outputs.files("fileA": "a", "fileB": "b")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1.fileA', '$1.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [FILE, FILE]
    }

    @Unroll
    def "can register named #name with property name"() {
        when: outputs."$name"("fileA": "a", "fileB": "b").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop.fileA', 'prop.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [type, type]
        where:
        name    | type
        "files" | FILE
        "dirs"  | DIRECTORY
    }

    @Unroll
    def "can register future named output #name"() {
        when: outputs."$name"({ [one: "a", two: "b"] })
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1.one', '$1.two']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [type, type]
        where:
        name    | type
        "files" | FILE
        "dirs"  | DIRECTORY
    }

    @Unroll
    def "can register future named output #name with property name"() {
        when: outputs."$name"({ [one: "a", two: "b"] }).withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop.one', 'prop.two']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [type, type]
        where:
        name    | type
        "files" | FILE
        "dirs"  | DIRECTORY
    }

    @Unroll
    def "fails when #name registers mapped file with null key"() {
        when:
        outputs."$name"({ [(null): "a"] }).withPropertyName("prop")
        outputs.fileProperties
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Mapped output property 'prop' has null key"
        where:
        name    | type
        "files" | FILE
        "dirs"  | DIRECTORY
    }

    def "error message contains which cacheIf spec failed to evaluate"() {
        outputs.file("someFile")
        outputs.cacheIf("Exception is thrown") { throw new RuntimeException() }

        when:
        outputs.cachingState

        then:
        GradleException e = thrown()
        e.message.contains("Could not evaluate spec for 'Exception is thrown'.")
    }

    def "error message contains which doNotCacheIf spec failed to evaluate"() {
        outputs.file("someFile")
        outputs.cacheIf { true }
        outputs.doNotCacheIf("Exception is thrown") { throw new RuntimeException() }

        when:
        outputs.cachingState

        then:
        GradleException e = thrown()
        e.message.contains("Could not evaluate spec for 'Exception is thrown'.")
    }

    void canRegisterOutputFiles() {
        when:
        outputs.file('a')

        then:
        outputs.files.files == [new File('a')] as Set
    }

    void hasOutputsWhenEmptyOutputFilesRegistered() {
        when:
        outputs.files([])

        then:
        outputs.hasOutput
    }

    void hasOutputsWhenNonEmptyOutputFilesRegistered() {
        when:
        outputs.file('a')

        then:
        outputs.hasOutput
    }

    void hasOutputsWhenUpToDatePredicateRegistered() {
        when:
        outputs.upToDateWhen { false }

        then:
        outputs.hasOutput
    }

    void canSpecifyUpToDatePredicateUsingClosure() {
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

    def "can turn caching on via cacheIf()"() {
        outputs.dir("someDir")

        expect:
        !outputs.cachingState.enabled

        when:
        outputs.cacheIf { true }
        then:
        outputs.cachingState.enabled
    }

    def "can turn caching off via cacheIf()"() {
        outputs.dir("someDir")

        expect:
        !outputs.cachingState.enabled

        when:
        outputs.cacheIf { true }
        then:
        outputs.cachingState.enabled

        when:
        outputs.cacheIf { false }
        then:
        !outputs.cachingState.enabled

        when:
        outputs.cacheIf { true }
        then:
        !outputs.cachingState.enabled
    }

    def "can turn caching off via doNotCacheIf()"() {
        outputs.dir("someDir")

        expect:
        !outputs.cachingState.enabled

        when:
        outputs.doNotCacheIf("test") { false }
        then:
        !outputs.cachingState.enabled

        when:
        outputs.cacheIf { true }
        then:
        outputs.cachingState.enabled

        when:
        outputs.doNotCacheIf("test") { true }
        then:
        !outputs.cachingState.enabled
    }

    def "first reason for not caching is reported"() {
        expect:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "Caching has not been enabled for the task"
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK

        when:
        outputs.cacheIf { true }

        then:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "No outputs declared"
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED

        when:
        outputs.dir("someDir")
        then:
        outputs.cachingState.enabled

        when:
        def taskHistory = Mock(TaskExecutionHistory)
        outputs.setHistory(taskHistory)
        taskHistory.getOverlappingOutputDetection() >> new TaskExecutionHistory.OverlappingOutputs("someProperty", "path/to/outputFile")
        then:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "Outputs created by something else (e.g. 'path/to/outputFile') were found in output property 'someProperty' before executing this task."
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS

        when:
        outputs.setHistory(null)
        outputs.doNotCacheIf("Caching manually disabled") { true }

        then:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "'Caching manually disabled' satisfied"
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED

        when:
        outputs.cacheIf("on CI") { false }

        then:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "'on CI' not satisfied"
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED
    }

    def "report no reason if the task is cacheable"() {
        when:
        outputs.cacheIf { true }
        outputs.dir("someDir")

        then:
        outputs.cachingState.enabled
        outputs.cachingState.disabledReason == null
        outputs.cachingState.disabledReasonCategory == null
    }

    def "disabling caching for plural file outputs is reported"() {
        when:
        outputs.cacheIf { true }
        outputs.files("someFile", "someOtherFile")

        then:
        !outputs.cachingState.enabled
        outputs.cachingState.disabledReason == "Declares multiple output files for the single output property '\$1' via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`"
        outputs.cachingState.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.PLURAL_OUTPUTS
    }

    void getPreviousFilesDelegatesToTaskHistory() {
        TaskExecutionHistory history = Mock()
        FileCollection outputFiles = Mock()

        setup:
        outputs.history = history

        when:
        def f = outputs.previousOutputFiles

        then:
        f == outputFiles
        1 * history.outputFiles >> outputFiles
    }

    void getPreviousFilesFailsWhenNoTaskHistoryAvailable() {
        when:
        outputs.previousOutputFiles

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Task history is currently not available for this task.'
    }
}
