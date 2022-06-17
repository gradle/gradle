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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.properties.DefaultPropertyWalker
import org.gradle.api.internal.tasks.properties.DefaultTypeMetadataStore
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor
import org.gradle.api.internal.tasks.properties.InputFilePropertyType
import org.gradle.api.internal.tasks.properties.InputParameterUtils
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler
import org.gradle.api.provider.Property
import org.gradle.api.tasks.FileNormalizer
import org.gradle.api.tasks.Internal
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import javax.annotation.Nullable
import java.util.concurrent.Callable

class DefaultTaskInputsTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final fileCollectionFactory = TestFiles.fileCollectionFactory(temporaryFolder.testDirectory)

    private def taskStatusNagger = Stub(TaskMutator) {
        mutate(_ as String, _ as Runnable) >> { String method, Runnable action ->
            action.run()
        }
        mutate(_ as String, _ as Callable) >> { String method, Callable<?> action ->
            return action.call()
        }
    }
    def task = Mock(TaskInternal) {
        getName() >> "task"
        toString() >> "task 'task'"
        getInputs() >> { inputs }
        getOutputs() >> Stub(TaskOutputsInternal)
        getDestroyables() >> Stub(TaskDestroyablesInternal)
        getLocalState() >> Stub(TaskLocalStateInternal)
    }
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        [:],
        ["java", "groovy"],
        [],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        [Internal],
        { false },
        cacheFactory
    )
    def walker = new DefaultPropertyWalker(new DefaultTypeMetadataStore([], [new NoOpPropertyAnnotationHandler(Internal)], [], typeAnnotationMetadataStore, cacheFactory))
    private final DefaultTaskInputs inputs = new DefaultTaskInputs(task, taskStatusNagger, walker, fileCollectionFactory)

    def "default values"() {
        expect:
        inputProperties().isEmpty()
        !inputs.hasInputs
        !inputs.hasSourceFiles
        inputs.sourceFiles.empty
    }

    def "can register input file"() {
        when: inputs.file("a")
        then:
        inputFileProperties() == ['$1': "a"]
        inputs.files.files == files("a")
    }

    def "can register input file with property name"() {
        when: inputs.file("a").withPropertyName("prop")
        then:
        inputFileProperties() == ['prop': "a"]
        inputs.files.files == files("a")
    }

    def "can register input files"() {
        when:
        inputs.files("a", "b")
        then:
        inputFileProperties() == ['$1': ["a", "b"]]
        inputs.files.files == files("a", "b")
    }

    def "can register input files with property name"() {
        when: inputs.files("a", "b").withPropertyName("prop")
        then:
        inputFileProperties() == ['prop': ["a", "b"]]
        inputs.files.files == files("a", "b")
    }

    def "can register input dir"() {
        def inputFilePath = "a/input.txt"
        when:
        file(inputFilePath).createFile()
        inputs.dir("a")
        then:
        inputFileProperties() == ['$1': "a"]
        inputs.files.files == files(inputFilePath)
    }

    def "can register input dir with property name"() {
        def inputFilePath = "a/input.txt"
        when:
        file(inputFilePath).createFile()
        inputs.dir("a").withPropertyName("prop")
        then:
        inputFileProperties() == ['prop': "a"]
        inputs.files.files == files(inputFilePath)
    }

    def "cannot register input file with same property name"() {
        inputs.file("a").withPropertyName("alma")
        inputs.file("b").withPropertyName("alma")
        def visitor = new GetInputFilesVisitor(task.toString(), fileCollectionFactory)
        when:
        TaskPropertyUtils.visitProperties(walker, task, visitor)
        visitor.getFileProperties()
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Multiple input file properties with name 'alma'"
    }

    def canRegisterInputProperty() {
        when:
        inputs.property('a', 'value')

        then:
        inputProperties() == [a: 'value']
    }

    def canRegisterInputPropertyUsingAClosure() {
        when:
        inputs.property('a', { 'value' })

        then:
        inputProperties() == [a: 'value']
    }

    def canRegisterInputPropertyUsingACallable() {
        when:
        inputs.property('a', { 'value' } as Callable)

        then:
        inputProperties() == [a: 'value']
    }

    def canRegisterInputPropertyUsingAFileCollection() {
        def files = [new File('file')] as Set

        when:
        inputs.property('a', [getFiles: { files }] as FileCollection)

        then:
        inputProperties() == [a: files]
    }

    def inputPropertyCanBeNestedCallableAndClosure() {
        def files = [new File('file')] as Set
        def fileCollection = [getFiles: { files }] as FileCollection
        def callable = {fileCollection} as Callable

        when:
        inputs.property('a', { callable })

        then:
        inputProperties() == [a: files]
    }

    def "GString input property values are evaluated to avoid serialization issues"() {
        when:
        inputs.property('a', { "hey ${new NotSerializable()}" })

        then:
        inputProperties() == [a: "hey Joe"]
        String.is inputProperties().a.class
    }

    class NotSerializable {
        String toString() { "Joe" }
    }

    def "can register source files"() {
        when: inputs.files("a", "b").withPropertyName("prop")
        then:
        inputs.hasInputs
        !inputs.hasSourceFiles

        when: inputs.files(["s1", "s2"]).skipWhenEmpty()
        then:
        inputs.hasSourceFiles
        inputs.sourceFiles.files == files("s1", "s2")
        inputFileProperties() == ['prop': ["a", "b"], '$1': ["s1", "s2"]]
    }

    def canRegisterSourceFile() {
        when:
        inputs.file('file').skipWhenEmpty()

        then:
        inputs.sourceFiles.files == files('file')
    }

    def canRegisterSourceFiles() {
        when:
        inputs.files('file', 'file2').skipWhenEmpty()

        then:
        inputs.sourceFiles.files == files('file', 'file2')
    }

    def canRegisterSourceDir() {
        def sourceFile = 'dir/source.txt'
        when:
        file(sourceFile).createFile()
        inputs.dir('dir').skipWhenEmpty()

        then:
        inputs.hasSourceFiles
        inputs.sourceFiles.files == files(sourceFile)
    }

    def sourceFilesAreAlsoInputFiles() {
        when:
        inputs.file('file').skipWhenEmpty()

        then:
        inputs.sourceFiles.files == files('file')
        inputFileProperties() == ['$1': 'file']
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
        inputs.files([]).skipWhenEmpty()

        then:
        inputs.hasInputs
        inputs.hasSourceFiles
    }

    def hasInputsWhenSourceFilesRegistered() {
        when:
        inputs.file('a').skipWhenEmpty()

        then:
        inputs.hasInputs
        inputs.hasSourceFiles
    }

    @Issue("https://github.com/gradle/gradle/issues/4085")
    def "can register more unnamed properties with method #method after properties have been queried"() {
        inputs."$method"("input-1")
        // Trigger naming properties
        inputs.hasSourceFiles
        inputs."$method"("input-2")
        def names = []

        when:
        inputs.visitRegisteredProperties(new PropertyVisitor.Adapter() {
            @Override
            void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, DirectorySensitivity emptyDirectorySensitivity, LineEndingSensitivity lineEndingNormalization, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                names += propertyName
            }
        })
        then:
        names == ['$1', '$2']

        where:
        method << ["file", "dir", "files"]
    }

    TestFile file(Object path) {
        temporaryFolder.file(path)
    }

    Set<File> files(Object... paths) {
        paths.collect { file(it) } as Set
    }

    def inputProperties() {
        def visitor = new GetInputPropertiesVisitor()
        TaskPropertyUtils.visitProperties(walker, task, visitor)
        return visitor.properties.collectEntries {[it.propertyName, InputParameterUtils.prepareInputParameterValue(it.value) ] }
    }

    def inputFileProperties() {
        def inputFiles = [:]
        TaskPropertyUtils.visitProperties(walker, task, new PropertyVisitor.Adapter() {
            @Override
            void visitInputFileProperty(
                String propertyName,
                boolean optional,
                boolean skipWhenEmpty,
                DirectorySensitivity emptyDirectorySensitivity,
                LineEndingSensitivity lineEndingNormalization,
                boolean incremental,
                @Nullable Class<? extends FileNormalizer> fileNormalizer,
                PropertyValue value,
                InputFilePropertyType filePropertyType
            ) {
                inputFiles[propertyName] = value.call()
            }
        })
        return inputFiles
    }
}
