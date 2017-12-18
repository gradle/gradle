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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.properties.DefaultPropertyMetadataStore
import org.gradle.api.internal.tasks.properties.DefaultPropertyWalker
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.caching.internal.DefaultBuildCacheHasher
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultPropertyWalkerTest extends AbstractProjectBuilderSpec {

    def visitor = Mock(PropertyVisitor)
    def classloaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> new DefaultBuildCacheHasher().putString("classloader").hash()
    }

    def "visits properties"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        visitProperties(task)

        then:
        1 * visitor.visitInputProperty({ it.propertyName == 'myProperty' && it.value == 'myValue' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFile' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFiles' })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.class' && it.value == NestedBean.name })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.$$implementation$$' })
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

    private visitProperties(TaskInternal task, PropertyAnnotationHandler... annotationHandlers) {
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        new DefaultPropertyWalker(new DefaultPropertyMetadataStore(annotationHandlers as List), classloaderHasher).visitProperties(specFactory, visitor, task)
    }
}
