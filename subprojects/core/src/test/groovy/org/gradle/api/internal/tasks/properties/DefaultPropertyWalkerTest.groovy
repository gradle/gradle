/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties

import groovy.transform.EqualsAndHashCode
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.tasks.DefaultPropertySpecFactory
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultPropertyWalkerTest extends AbstractProjectBuilderSpec {

    def visitor = Mock(PropertyVisitor)

    def "visits properties"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty({ it.propertyName == 'myProperty' && it.value == 'myValue' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFile' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFiles' })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean' && it.value == NestedBean })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.nestedInput' && it.value == 'nested' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'bean.inputDir' })

        1 * visitor.visitOutputFileProperty({ it.propertyName == 'outputFile' && it.value.value.path == 'output' })
        1 * visitor.visitOutputFileProperty({ it.propertyName == 'bean.outputDir' && it.value.value.path == 'outputDir' })

        1 * visitor.visitDestroyableProperty({ it.propertyName == 'destroyed' && it.value.value.path == 'destroyed' })

        1 * visitor.visitLocalStateProperty({ it.propertyName == 'someLocalState' && it.value.value.path == 'localState' })

        0 * _
    }

    static class MyTask extends DefaultTask {

        @Input
        String myProperty = "myValue"

        @InputFile
        File inputFile = new File("some-location")

        @InputFiles
        FileCollection inputFiles = ImmutableFileCollection.of(new File("files"))

        @OutputFile
        File outputFile = new File("output")

        @Nested
        Object bean = new NestedBean()

        @Destroys
        File destroyed = new File('destroyed')

        @LocalState
        File someLocalState = new File('localState')

    }

    static class NestedBean {
        @Input
        String nestedInput = 'nested'

        @InputDirectory
        File inputDir

        @OutputDirectory
        File outputDir = new File('outputDir')
    }

    def "nested bean with null value is detected"() {
        def task = project.tasks.create("myTask", MyTask)
        task.bean = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        1 * visitor.visitInputProperty({ it.propertyName == 'bean' && it.value == null })
    }

    def "cycle in nested inputs is detected"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        def cycle = new Tree(value: "cycle", left: new Tree(value: "left"))
        cycle.right = cycle
        task.nested = new Tree(value: "first", left: new Tree(value: "left"), right: new Tree(value: "deeper", left: cycle, right: new Tree(value: "no-cycle")))

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        IllegalStateException e = thrown(IllegalStateException)
        e.message == "Cycles between nested beans are not allowed. Cycle detected between: 'nested.right.left' and 'nested.right.left.right'."
    }

    def "cycle in nested input and task itself is detected"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        task.nested = new Tree(value: "root", left: task, right: new Tree(value: "right"))

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        IllegalStateException e = thrown(IllegalStateException)
        e.message == "Cycles between nested beans are not allowed. Cycle detected between: '<root>' and 'nested.left'."
    }

    def "nested beans can be equal"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        task.nested = new Tree(value: "root", right: new Tree(value: "right", right: new Tree(value: "right")))

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        noExceptionThrown()
        task.nested.right == task.nested.right.right
    }

    def "nested beans can be re-used"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        def subTree = new Tree(value: "left", left: new Tree(value: "left"), right: new Tree(value: "right"))
        task.nested = new Tree(value: "head", left: subTree, right: new Tree(value: "deeper", left: subTree, right: new Tree(value: "evenDeeper", left: subTree, right: subTree)))

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        noExceptionThrown()
    }

    def "nested iterable beans can have the same names"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        task.nested = [new NamedNestedBean('name', 'value1'), new NamedNestedBean('name', 'value2')]

        when:
        visitProperties(task)

        then:
        _ * visitor.visitNested() >> true
        1 * visitor.visitInputProperty({ it.propertyName == 'nested.name$0'})
        1 * visitor.visitInputProperty({ it.propertyName == 'nested.name$1'})
    }

    def "providers are unpacked"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        task.nested = project.provider { new NestedBean() }

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty({ it.propertyName == "nested" })
        1 * visitor.visitInputProperty({ it.propertyName == "nested.nestedInput" })
        1 * visitor.visitInputFileProperty({ it.propertyName == "nested.inputDir" })
        1 * visitor.visitOutputFileProperty({ it.propertyName == "nested.outputDir" })

        0 * _
    }

    static class NamedNestedBean implements Named {
        @Internal
        final String name
        @Input
        final String value

        NamedNestedBean(String name, String value) {
            this.value = value
            this.name = name
        }
    }

    static class TaskWithNestedObject extends DefaultTask {
        @Nested
        Object nested
    }

    @EqualsAndHashCode(includes = "value")
    static class Tree {
        @Input
        String value

        @Nested Object left
        @Nested Object right
    }

    private visitProperties(TaskInternal task, PropertyAnnotationHandler... annotationHandlers) {
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        new DefaultPropertyWalker(new DefaultTypePropertyMetadataStore(annotationHandlers as List, new TestCrossBuildInMemoryCacheFactory())).visitProperties(specFactory, visitor, task)
    }
}
