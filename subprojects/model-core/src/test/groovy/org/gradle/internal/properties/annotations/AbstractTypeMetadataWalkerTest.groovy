/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.properties.annotations

import com.google.common.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.tasks.properties.DefaultPropertyTypeResolver
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.util.function.Supplier

class AbstractTypeMetadataWalkerTest extends Specification {
    // TODO: Use custom annotation instead `Input`
    static final PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [Input, Nested]
    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        ModifierAnnotationCategory.asMap((PROCESSED_PROPERTY_TYPE_ANNOTATIONS) as Set<Class<? extends Annotation>>),
        ["java", "groovy"],
        [DefaultTask],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        [],
        { false },
        cacheFactory
    )
    def propertyTypeResolver = new DefaultPropertyTypeResolver()
    def metadataStore = new DefaultTypeMetadataStore([], services.getAll(PropertyAnnotationHandler), [], typeAnnotationMetadataStore, propertyTypeResolver, cacheFactory)

    def "should walk a type"() {
        when:
        List<CollectedInput> inputs = []
        TypeMetadataWalker.typeWalker(metadataStore, Nested.class).walk(TypeToken.of(MyTask), new TypeMetadataWalker.NodeMetadataVisitor<TypeToken<?>>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(null, value))
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(qualifiedName, value))
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<TypeToken<?>> value) {
                inputs.add(new CollectedInput(qualifiedName, value.get()))
            }
        })

        then:
        inputs.collect { it.qualifiedName } == [null, "i1", "i2", "i2.nI2", "i3.*", "i3.*.nI2", "i4.<key>", "i4.<key>.nI2", "i5.*.*", "i5.*.*.nI2", "i6.<name>", "i6.<name>.nI3", "i7", "i7.nI2"]
    }

    def "should walk type instance"() {
        given:
        def myType = new MyTask()
        def nestedType = new NestedType()
        def propertyI1 = TestUtil.propertyFactory().property(String).value("value-i1")
        def propertyNI2 = TestUtil.propertyFactory().property(String).value("value-nI2")
        nestedType.nI2 = propertyNI2
        def namedType = new NamedType()
        def propertyNI3 = TestUtil.propertyFactory().property(String).value("value-nI3")
        namedType.nI3 = propertyNI3
        myType.i1 = propertyI1
        myType.i2 = nestedType
        myType.i3 = [nestedType, nestedType]
        myType.i4 = ["key1": nestedType, "key2": nestedType]
        myType.i5 = [[nestedType]]
        myType.i5 = [[nestedType]]
        myType.i6 = [namedType]
        myType.i7 = TestUtil.propertyFactory().property(NestedType).value(nestedType)

        when:
        Map<String, CollectedInput> inputs = [:]
        TypeMetadataWalker.instanceWalker(metadataStore, Nested.class).walk(myType, new TypeMetadataWalker.NodeMetadataVisitor<Object>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, Object value) {
                inputs[null]= new CollectedInput(null, value)
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, Object value) {
                assert !inputs.containsKey(qualifiedName)
                inputs[qualifiedName] = new CollectedInput(qualifiedName, value)
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<Object> value) {
                assert !inputs.containsKey(qualifiedName)
                inputs[qualifiedName] = new CollectedInput(qualifiedName, value.get())
            }
        })

        then:
        inputs[null].value == myType
        inputs["i1"].value == propertyI1
        inputs["i2"].value == nestedType
        inputs["i2.nI2"].value == propertyNI2
        inputs["i3.\$1"].value == nestedType
        inputs["i3.\$2"].value == nestedType
        inputs["i3.\$1.nI2"].value == propertyNI2
        inputs["i3.\$2.nI2"].value == propertyNI2
        inputs["i4.key1"].value ==  nestedType
        inputs["i4.key2"].value ==  nestedType
        inputs["i4.key1.nI2"].value == propertyNI2
        inputs["i4.key2.nI2"].value == propertyNI2
        inputs["i5.\$1.\$1"].value == nestedType
        inputs["i5.\$1.\$1.nI2"].value == propertyNI2
        inputs["i6.\$1"].value == namedType
        inputs["i6.\$1.nI3"].value == propertyNI3
        inputs["i7"].value == nestedType
        inputs["i7.nI2"].value == nestedType.nI2
    }

    def "type walker should handle types with nested cycles"() {
        when:
        List<CollectedInput> inputs = []
        TypeMetadataWalker.typeWalker(metadataStore, Nested.class).walk(TypeToken.of(MyCycleTask), new TypeMetadataWalker.NodeMetadataVisitor<TypeToken<?>>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(null, value))
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(qualifiedName, value))
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<TypeToken<?>> value) {
                inputs.add(new CollectedInput(qualifiedName, value.get()))
            }
        })

        then:
        inputs.collect { it.qualifiedName } == [null, "a", "a.g", "b", "b.g", "c.*", "c.*.g", "d.<key>", "d.<key>.g", "e.*.*", "e.*.*.g"]
    }

    def "instance walker should handle instances with nested cycles"() {
        given:
        def instance = new MyCycleTask()
        instance[propertyWithCycle] = propertyValue

        when:
        List<CollectedInput> inputs = []
        TypeMetadataWalker.instanceWalker(metadataStore, Nested.class).walk(instance, new TypeMetadataWalker.NodeMetadataVisitor<Object>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, Object value) {
                inputs.add(new CollectedInput(null, value))
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, Object value) {
                inputs.add(new CollectedInput(qualifiedName, value))
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<Object> value) {
                inputs.add(new CollectedInput(qualifiedName, value.get()))
            }
        })

        then:
        def exception = thrown(GradleException)
        exception.message == "Cycles between nested beans are not allowed. Cycle detected between: $expectedCycle."

        where:
        propertyWithCycle | propertyValue                                                                         | expectedCycle
        'a'               | CycleType.newInitializedCycle()                                                       | "'a' and 'a.f'"
        'b'               | TestUtil.propertyFactory().property(CycleType).value(CycleType.newInitializedCycle()) | "'b' and 'b.f'"
        'c'               | [CycleType.newInitializedCycle()]                                                     | "'c.\$1' and 'c.\$1.f'"
        'd'               | ['key1': CycleType.newInitializedCycle()]                                             | "'d.key1' and 'd.key1.f'"
        'e'               | [[CycleType.newInitializedCycle()]]                                                   | "'e.\$1.\$1' and 'e.\$1.\$1.f'"
    }

    static class MyTask {
        @Input
        Property<String> i1
        @Nested
        NestedType i2
        @Nested
        List<NestedType> i3
        @Nested
        Map<String, NestedType> i4
        @Nested
        List<List<NestedType>> i5
        @Nested
        List<NamedType> i6
        @Nested
        Property<NestedType> i7
    }

    static class NestedType {
        @Input
        Property<String> nI2
    }

    static class NamedType implements Named {
        @Input
        Property<String> nI3
        @Internal
        String getName() {
            return "namedType"
        }
    }

    static class MyCycleTask {
        @Nested
        CycleType a
        @Nested
        Property<CycleType> b
        @Nested
        List<CycleType> c
        @Nested
        Map<String, CycleType> d
        @Nested
        List<List<CycleType>> e
    }

    static class CycleType {
        @Nested
        CycleType f
        @Input
        CycleType g

        static CycleType newInitializedCycle() {
            def cycleType = new CycleType()
            cycleType.f = cycleType
            cycleType.g = cycleType
            return cycleType
        }
    }

    static class CollectedInput {
        String qualifiedName
        Object value

        CollectedInput(String qualifiedName, Object value) {
            this.qualifiedName = qualifiedName
            this.value = value
        }
    }
}
