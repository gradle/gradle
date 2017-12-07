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

package org.gradle.api.internal.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.properties.DefaultPropertiesWalker
import org.gradle.api.internal.tasks.properties.DefaultPropertyMetadataStore
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import java.nio.file.Path

class DefaultPropertiesWalkerTest extends AbstractProjectBuilderSpec {

    def visitor = Mock(PropertyVisitor)

    def "visits properties"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        visitProperties(task)

        then:
        1 * visitor.visitInputProperty({ it.propertyName == 'myProperty' && it.value == 'myValue' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFile' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFiles' })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.class' && it.value == NestedBean.name })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.nestedInput' && it.value == 'nested' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'bean.inputDir' })

        1 * visitor.visitOutputFileProperty({ it.propertyName == 'outputFile' && it.value.value.path == 'output' })
        1 * visitor.visitOutputFileProperty({ it.propertyName == 'bean.outputDir' && it.value.value.path == 'outputDir' })

        1 * visitor.visitDestroyableProperty({ it.propertyName == 'destroyed' && it.value.path == 'destroyed' })

        1 * visitor.visitLocalStateProperty({ it.propertyName == 'someLocalState' && it.value.path == 'localState' })

        0 * _
    }

    static class MyTask extends DefaultTask {

        @Input
        String myProperty = "myValue"

        @InputFile
        File inputFile = new File("some-location")

        @InputFiles
        FileCollection inputFiles = new SimpleFileCollection([new File("files")])

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
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.class' && it.value == null })
    }


    static class TaskWithFileInput extends DefaultTask {
        @Input
        File file

        @Input
        Path filePath

        @Input
        FileCollection fileCollection

        @Input
        FileTree fileTree
    }

    def "warns about @Input being used on File and FileCollection properties"() {
        def visitor = Mock(PropertyVisitor)
        when:
        visitTask(TaskWithFileInput, visitor)

        then:
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'file' has @Input annotation used on property of type $File.name")
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'fileCollection' has @Input annotation used on property of type $FileCollection.name")
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'filePath' has @Input annotation used on property of type $Path.name")
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'fileTree' has @Input annotation used on property of type $FileTree.name")
        4 * visitor.visitInputProperty(_)
        0 * _
    }

    @CacheableTask
    static class CacheableTaskWithoutPathSensitivity extends DefaultTask {
        @InputFile
        File inputFile

        @InputFiles
        FileCollection inputFiles

        @OutputFile
        File outputFile
    }

    def "warns about missing @PathSensitive annotation for @CacheableTask"() {
        def visitor = Mock(PropertyVisitor)

        when:
        visitTask(CacheableTaskWithoutPathSensitivity, visitor)

        then:
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'inputFile' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE")
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'inputFiles' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE")
        2 * visitor.visitInputFileProperty(_)
        1 * visitor.visitOutputFileProperty(_)
        0 * _
    }

    static class TaskWithNonAnnotatedProperty extends DefaultTask {
        FileCollection inputFiles
    }

    def "warns about non-annotated property"() {
        def visitor = Mock(PropertyVisitor)

        when:
        visitTask(TaskWithNonAnnotatedProperty, visitor)

        then:
        1 * visitor.visitValidationMessage(TaskValidationContext.Severity.INFO, "property 'inputFiles' is not annotated with an input or output annotation")
    }

    private visitProperties(TaskInternal task, PropertyAnnotationHandler... annotationHandlers) {
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        new DefaultPropertiesWalker(new DefaultPropertyMetadataStore(annotationHandlers as List)).visitProperties(specFactory, visitor, task)
    }

    private void visitTask(Class taskClass, PropertyVisitor visitor, PropertyAnnotationHandler... annotationHandlers) {
        def task = project.tasks.create(taskClass.simpleName, taskClass)
        def walker = new DefaultPropertiesWalker(new DefaultPropertyMetadataStore(annotationHandlers as List))
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        walker.visitProperties(specFactory, visitor, task)
    }

}
