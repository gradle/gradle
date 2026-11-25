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

package org.gradle.internal.properties.bean

import groovy.transform.EqualsAndHashCode
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.properties.bean.TestImplementationResolver
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
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
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.OutputFilePropertyType
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.DefaultTypeMetadataStore
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler
import org.gradle.internal.properties.annotations.TestPropertyTypeResolver
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.reflect.validation.TypeValidationContext
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.internal.service.scopes.ExecutionGlobalServices.IGNORED_METHOD_ANNOTATIONS
import static org.gradle.internal.service.scopes.ExecutionGlobalServices.IGNORED_METHOD_ANNOTATIONS_ALLOWED_MODIFIERS
import static org.gradle.internal.service.scopes.ExecutionGlobalServices.PROPERTY_TYPE_ANNOTATIONS

class DefaultPropertyWalkerTest extends AbstractProjectBuilderSpec {
    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def visitor = Mock(PropertyVisitor)
    def validationContext = Mock(TypeValidationContext)

    def "visits properties"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty('myProperty', { it.call() == 'myValue' }, false)
        1 * visitor.visitInputFileProperty('inputFile', _, _, _, _, _, _, InputFilePropertyType.FILE)
        1 * visitor.visitInputFileProperty('inputFiles', _, _, _, _, _, _, InputFilePropertyType.FILES)
        1 * visitor.visitInputProperty('bean', { it.call().implementationClassIdentifier == NestedBean.name }, false)
        1 * visitor.visitInputProperty('bean.nestedInput', { it.call() == 'nested' }, false)
        1 * visitor.visitInputFileProperty('bean.inputDir', _, _, _, _, _, _, InputFilePropertyType.DIRECTORY)

        1 * visitor.visitOutputFileProperty('outputFile', false, { it.call().path == 'output' }, OutputFilePropertyType.FILE)
        1 * visitor.visitOutputFileProperty('bean.outputDir', false, { it.call().path == 'outputDir' }, OutputFilePropertyType.DIRECTORY)

        1 * visitor.visitDestroyableProperty({ it.call().path == 'destroyed' })

        1 * visitor.visitLocalStateProperty({ it.call().path == 'localState' })

        0 * _
    }

    static abstract class MyTask extends DefaultTask {

        @Input
        String myProperty = "myValue"

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        File inputFile = new File("some-location")

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        FileCollection inputFiles = TestFiles.fixed(new File("files"))

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
        @PathSensitive(PathSensitivity.NONE)
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
        1 * visitor.visitInputProperty('bean', { it.call() == null }, false)
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
        1 * visitor.visitInputProperty('nested.name$0', _, false)
        1 * visitor.visitInputProperty('nested.name$1', _, false)
    }

    def "providers are unpacked"() {
        def task = project.tasks.create("myTask", TaskWithNestedObject)
        task.nested = project.provider { new NestedBean() }

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("nested" , _, false)
        1 * visitor.visitInputProperty("nested.nestedInput", _, false)
        1 * visitor.visitInputFileProperty("nested.inputDir", _, _, _, _, _, _, InputFilePropertyType.DIRECTORY)
        1 * visitor.visitOutputFileProperty("nested.outputDir", false, _, OutputFilePropertyType.DIRECTORY)

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

    static abstract class TaskWithNestedObject extends DefaultTask {
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

    def "lazy-backed value property reads the getter and takes dependencies from the lazy property"() {
        def task = project.tasks.create("myTask", LazyBackedTask)
        task.messageProperty.set("value")
        def context = Mock(TaskDependencyResolveContext)
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, false) >> { args -> value = args[1] }

        when:
        value.taskDependencies.visitDependencies(context)

        then:
        task.getterCalls == 0
        1 * context.add(task.messageProperty)
        0 * context._

        when:
        def result = value.call()

        then:
        result == "value"
        task.getterCalls == 1
    }

    def "lazy-backed property leaves the lazy property changeable after finalizing"() {
        def task = project.tasks.create("myTask", LazyBackedTask)
        task.messageProperty.set("value")
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, false) >> { args -> value = args[1] }

        when:
        value.maybeFinalizeValue()
        task.messageProperty.set("changed")

        then:
        task.messageProperty.get() == "changed"
    }

    def "lazy-backed property whose getter returns the lazy property itself is not finalized"() {
        def task = project.tasks.create("myTask", SelfBackedTask)
        task.lazyMessage.set("value")
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, false) >> { args -> value = args[1] }

        when:
        value.maybeFinalizeValue()
        task.lazyMessage.set("changed")

        then:
        task.lazyMessage.get() == "changed"
    }

    def "lazy-backed property still finalizes a configurable value that is not the lazy property"() {
        def task = project.tasks.create("myTask", OtherConfigurableTask)
        task.other.set("value")
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, false) >> { args -> value = args[1] }

        when:
        value.maybeFinalizeValue()
        task.other.set("changed")

        then:
        thrown(IllegalStateException)
        task.lazyMessage.isPresent() == false
    }

    def "lazy-backed file property keeps the dependencies of an overridden getter"() {
        def task = project.tasks.create("myTask", LazyBackedTask)
        def otherFiles = project.files("other")
        task.otherFiles = otherFiles
        def context = Mock(TaskDependencyResolveContext)
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputFileProperty("files", _, _, _, _, _, _, InputFilePropertyType.FILES) >> { args -> value = args[6] }

        when:
        value.taskDependencies.visitDependencies(context)

        then:
        1 * context.add(otherFiles)
        1 * context.add(task.filesProperty)
        0 * context._

        when:
        value.lazyBackingDependencies.visitDependencies(context)

        then:
        1 * context.add(task.filesProperty)
        0 * context._
    }

    def "only lazy-backed values have lazy backing dependencies"() {
        def task = project.tasks.create("myTask", MyTask)
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty('myProperty', _, false) >> { args -> value = args[1] }

        and:
        value.lazyBackingDependencies.is(TaskDependencyContainer.EMPTY)
    }

    def "lazy-backed property with a Provider getter that returns null still visits the lazy property"() {
        def task = project.tasks.create("myTask", NullProviderLazyBackedTask)
        def context = Mock(TaskDependencyResolveContext)
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, true) >> { args -> value = args[1] }

        when:
        value.taskDependencies.visitDependencies(context)

        then:
        1 * context.add(task.messageProperty)
        0 * context._
    }

    static abstract class NullProviderLazyBackedTask extends DefaultTask {
        private final Property<String> message = project.objects.property(String)

        @Internal
        Property<String> getMessageProperty() {
            return message
        }

        @Input
        @org.gradle.api.tasks.Optional
        @BackedByLazyProperty("messageProperty")
        Provider<String> getMessage() {
            return null
        }
    }

    def "lazy-backed property with a wrong path reports a dependency failure"() {
        def task = project.tasks.create("myTask", BrokenLazyBackedTask)
        def context = Mock(TaskDependencyResolveContext)
        PropertyValue value = null

        when:
        visitProperties(task)

        then:
        _ * visitor.visitOutputFilePropertiesOnly() >> false
        1 * visitor.visitInputProperty("message", _, false) >> { args -> value = args[1] }

        when:
        value.taskDependencies.visitDependencies(context)

        then:
        1 * context.visitFailure({ it instanceof InvalidUserCodeException && it.message.contains("segment 'missing' has no getter getMissing()") })
        0 * context._
    }

    static abstract class LazyBackedTask extends DefaultTask {
        @Internal
        int getterCalls

        @Internal
        FileCollection otherFiles

        private final Property<String> message = project.objects.property(String)
        private final ConfigurableFileCollection files = project.objects.fileCollection()

        @Internal
        Property<String> getMessageProperty() {
            return message
        }

        @Input
        @BackedByLazyProperty("messageProperty")
        String getMessage() {
            getterCalls++
            return message.getOrNull()
        }

        @Internal
        ConfigurableFileCollection getFilesProperty() {
            return files
        }

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        @BackedByLazyProperty("filesProperty")
        FileCollection getFiles() {
            return otherFiles
        }
    }

    static abstract class SelfBackedTask extends DefaultTask {
        private final Property<String> message = project.objects.property(String)

        @Internal
        Property<String> getLazyMessage() {
            return message
        }

        @Input
        @BackedByLazyProperty("lazyMessage")
        Property<String> getMessage() {
            return message
        }
    }

    static abstract class OtherConfigurableTask extends DefaultTask {
        private final Property<String> message = project.objects.property(String)
        private final Property<String> other = project.objects.property(String)

        @Internal
        Property<String> getLazyMessage() {
            return message
        }

        @Internal
        Property<String> getOther() {
            return other
        }

        @Input
        @BackedByLazyProperty("lazyMessage")
        Property<String> getMessage() {
            return other
        }
    }

    static abstract class BrokenLazyBackedTask extends DefaultTask {
        @Input
        @BackedByLazyProperty("missing")
        String getMessage() {
            return "value"
        }
    }

    private visitProperties(TaskInternal task) {
        def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
        def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
            [],
            ModifierAnnotationCategory.asMap(PROPERTY_TYPE_ANNOTATIONS),
            [:],
            ["java", "groovy"],
            [],
            [Object, GroovyObject],
            [ConfigurableFileCollection, Property],
            IGNORED_METHOD_ANNOTATIONS,
            IGNORED_METHOD_ANNOTATIONS_ALLOWED_MODIFIERS,
            { false },
            cacheFactory
        )
        def propertyHandlers = services.getAll(PropertyAnnotationHandler)
        def typeMetadataStore = new DefaultTypeMetadataStore([], propertyHandlers, [PathSensitive, BackedByLazyProperty], [], [], typeAnnotationMetadataStore, TestPropertyTypeResolver.INSTANCE, cacheFactory, MissingPropertyAnnotationHandler.DO_NOTHING)
        new DefaultPropertyWalker(typeMetadataStore, new TestImplementationResolver(), propertyHandlers).visitProperties(task, validationContext, visitor)
    }
}
