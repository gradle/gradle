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

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.execution.TaskProperties
import org.gradle.api.internal.tasks.properties.DefaultPropertyMetadataStore
import org.gradle.api.internal.tasks.properties.DefaultPropertyWalker
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.caching.internal.tasks.DefaultTaskOutputCachingBuildCacheKeyBuilder
import org.gradle.util.DeferredUtil
import org.gradle.util.UsesNativeServices
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable

import static OutputType.DIRECTORY
import static OutputType.FILE

@UsesNativeServices
class DefaultTaskOutputsTest extends Specification {

    def validBuildCacheKey = new DefaultTaskOutputCachingBuildCacheKeyBuilder().build()

    def taskStatusNagger = Stub(TaskMutator) {
        mutate(_, _) >> { String method, def action ->
            if (action instanceof Runnable) {
                action.run()
            } else if (action instanceof Callable) {
                action.call()
            }
        }
    }
    def resolver = [
        resolve: { String it -> new File(it) },
        resolveFiles: { it ->
            ImmutableFileCollection.of(it.collect { DeferredUtil.unpack(it) }.flatten().collect { new File((String) it) })
        }
    ]   as FileResolver
    def project = Stub(ProjectInternal) {
        getFileFileResolver() >> resolver
    }
    def taskPropertiesWithNoOutputs = Mock(TaskProperties) {
        getOutputFileProperties() >> ImmutableSortedSet.of()
        hasDeclaredOutputs() >> false
    }
    def taskPropertiesWithOutput = Mock(TaskProperties) {
        getOutputFileProperties() >> ImmutableSortedSet.of(Mock(TaskOutputFilePropertySpec) {
            getPropertyName() >> "prop"
        })
        hasDeclaredOutputs() >> true
    }
    def taskPropertiesWithCacheableOutput = Mock(TaskProperties) {
        getOutputFileProperties() >> ImmutableSortedSet.of(Mock(CacheableTaskOutputFilePropertySpec) {
            getPropertyName() >> "prop"
        })
        hasDeclaredOutputs() >> true
    }
    def task = Mock(TaskInternal) {
        getName() >> "task"
        toString() >> "task 'task'"
        getProject() >> project
        getProject() >> project
        getOutputs() >> { outputs }
        getInputs() >> Stub(TaskInputsInternal)
        getDestroyables() >> Stub(TaskDestroyablesInternal)
        getLocalState() >> Stub(TaskLocalStateInternal)
    }

    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs(task, taskStatusNagger, new DefaultPropertyWalker(new DefaultPropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())), new DefaultPropertySpecFactory(task, resolver))

    void hasNoOutputsByDefault() {
        setup:
        assert outputs.files.files.isEmpty()
        assert !outputs.hasOutput
    }

    void outputFileCollectionIsBuiltByTask() {
        setup:
        assert outputs.files.buildDependencies.getDependencies(task).toList() == [task]
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
        outputs.fileProperties*.propertyName == ['$1$1', '$1$2']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
    }

    def "can register unnamed output files with property name"() {
        when: outputs.files("a", "b").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop$1', 'prop$2']
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

    @Issue("https://github.com/gradle/gradle/issues/4085")
    @Unroll
    def "can register more unnamed properties with method #method after properties have been queried"() {
        outputs."$method"("output-1")
        // Trigger naming properties
        outputs.hasOutput
        outputs."$method"("output-2")
        def names = []

        when:
        outputs.visitRegisteredProperties(new PropertyVisitor.Adapter() {
            @Override
            void visitOutputFileProperty(TaskOutputFilePropertySpec property) {
                names += property.propertyName
            }
        })
        then:
        names == ['$1', '$2']

        where:
        method << ["file", "dir", "files", "dirs"]
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

    void getPreviousFilesDelegatesToTaskHistory() {
        def history = Mock(TaskExecutionHistory)
        Set<File> outputFiles = [new File("some-file")] as Set

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
