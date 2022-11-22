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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.tasks.properties.DefaultPropertyTypeResolver
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.lang.annotation.Annotation

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
        TypeMetadataWalker.typeWalker(metadataStore).walk(TypeToken.of(MyType)) { TypeMetadata declaringType, PropertyMetadata property, String qualifiedName, TypeToken<?> value ->
            inputs.add(new CollectedInput(declaringType, property, qualifiedName, value))
        }

        then:
        inputs.collect { it.qualifiedName } == ["i1", "i2", "i2.nI2", "i3", "i3.*.nI2", "i4", "i4.<key>.nI2", "i5", "i5.*.*.nI2"]
    }

    def "should walk type instance"() {
        given:
        def myType = new MyType()
        def nestedType = new NestedType()
        def propertyI1 = TestUtil.propertyFactory().property(String).value("value-i1")
        def propertyNI2 = TestUtil.propertyFactory().property(String).value("value-nI2")
        nestedType.nI2 = propertyNI2
        myType.i1 = propertyI1
        myType.i2 = nestedType
        myType.i3 = [nestedType, nestedType]
        myType.i4 = ["key1": nestedType, "key2": nestedType]
        myType.i5 = [[nestedType]]

        when:
        Map<String, CollectedInput> inputs = [:]
        TypeMetadataWalker.instanceWalker(metadataStore).walk(myType, { TypeMetadata declaringType, PropertyMetadata property, String qualifiedName, Object value ->
            assert !inputs.containsKey(qualifiedName)
            inputs[qualifiedName] = new CollectedInput(declaringType, property, qualifiedName, value)
        })

        then:
        inputs["i1"].value == propertyI1
        inputs["i2"].value == nestedType
        inputs["i2.nI2"].value == propertyNI2
        inputs["i3"].value == [nestedType, nestedType]
        inputs["i3.\$1.nI2"].value == propertyNI2
        inputs["i3.\$2.nI2"].value == propertyNI2
        inputs["i4"].value == ["key1": nestedType, "key2": nestedType]
        inputs["i4.key1.nI2"].value == propertyNI2
        inputs["i4.key2.nI2"].value == propertyNI2
        inputs["i5"].value == [[nestedType]]
        inputs["i5.\$1.\$1.nI2"].value == propertyNI2
    }

    class MyType {
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
    }

    class NestedType {
        @Input
        Property<String> nI2
    }

    class CollectedInput {
        TypeMetadata declaringType
        PropertyMetadata property
        String qualifiedName
        Object value

        CollectedInput(TypeMetadata declaringType, PropertyMetadata property, String qualifiedName, Object value) {
            this.declaringType = declaringType
            this.property = property
            this.qualifiedName = qualifiedName
            this.value = value
        }
    }
}
