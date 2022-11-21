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
import org.gradle.api.model.ReplacedBy
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import spock.lang.Specification

import java.lang.annotation.Annotation

class AbstractTypeMetadataWalkerTest extends Specification {
    static final PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Input, InputFile, InputFiles, InputDirectory, Nested, OutputFile, OutputDirectory, OutputFiles, OutputDirectories, Destroys, LocalState
    ]
    static final UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Console, Internal, ReplacedBy
    ]
    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [CustomCacheable],
        ModifierAnnotationCategory.asMap((PROCESSED_PROPERTY_TYPE_ANNOTATIONS + [SearchPath]) as Set<Class<? extends Annotation>>),
        ["java", "groovy"],
        [DefaultTask],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS,
        { false },
        cacheFactory
    )
    def propertyTypeResolver = new DefaultPropertyTypeResolver()
    def metadataStore = new DefaultTypeMetadataStore([], services.getAll(PropertyAnnotationHandler), [Classpath, CompileClasspath], typeAnnotationMetadataStore, propertyTypeResolver, cacheFactory)

    def "should walk a type"() {
        when:
        def inputs = []
        TypeMetadataWalker.typeWalker(metadataStore).walk(TypeToken.of(MyType), new TypeMetadataWalker.PropertyMetadataVisitor<TypeToken<?>>() {
            @Override
            void visitProperty(TypeMetadata declaringType, PropertyMetadata property, String qualifiedName, TypeToken<?> value) {
                inputs.add(["declaringType": declaringType, "property": property, "qualifiedName": qualifiedName, "value": value])
            }
        })

        then:
        inputs.size() == 9
        inputs.collect { it['qualifiedName'] } == ["i1", "i2", "i2.nI2", "i3", "i3.*.nI2", "i4", "i4.<key>.nI2", "i5", "i5.*.*.nI2"]
    }

    def "should walk type instance"() {

    }

    class MyType {
        @Input
        Property<String> i1
        @Nested
        NestedType i2
        @InputFiles
        Set<NestedType> i3
        @InputFiles
        Map<String, NestedType> i4
        @InputFiles
        Set<Set<NestedType>> i5
    }

    class NestedType {
        @Input
        Property<String> nI2
    }
}
