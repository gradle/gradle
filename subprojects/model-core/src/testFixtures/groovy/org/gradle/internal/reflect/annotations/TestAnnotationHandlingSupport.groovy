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

package org.gradle.internal.reflect.annotations

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import groovy.transform.Generated
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.DefaultTypeMetadataStore
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TestPropertyTypeResolver
import org.gradle.internal.properties.annotations.TypeAnnotationHandler
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.properties.schema.AbstractInstanceSchema
import org.gradle.internal.properties.schema.AbstractUnresolvedPropertySchema
import org.gradle.internal.properties.schema.DefaultInstanceSchemaExtractor
import org.gradle.internal.properties.schema.InstanceSchema
import org.gradle.internal.properties.schema.InstanceSchemaExtractor
import org.gradle.internal.properties.schema.NestedPropertySchema
import org.gradle.internal.properties.schema.PropertySchemaExtractor
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext
import org.gradle.internal.reflect.validation.TypeValidationContext

import javax.annotation.Nullable
import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method
import java.util.function.Supplier

import static org.gradle.internal.properties.annotations.PropertyAnnotationHandler.Kind.INPUT
import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE

trait TestAnnotationHandlingSupport {

    enum Modifiers implements AnnotationCategory {
        COLOR;

        @Override
        String getDisplayName() {
            name()
        }
    }

    TypeAnnotationMetadataStore typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [ThisIsAThing],
        [(TestNested): TYPE, (Long): TYPE, (Short): TYPE, (Tint): Modifiers.COLOR],
        ["java", "groovy"],
        [Object],
        [Object, GroovyObject],
        [],
        [Ignored, AlsoIgnored],
        { Method method -> method.isAnnotationPresent(Generated) },
        TestCrossBuildInMemoryCacheFactory.instance()
    )

    def propertyHandlers = [
        new TestPropertyAnnotationHandler(TestNested, INPUT, [ItDepends]),
        new TestPropertyAnnotationHandler(Long, INPUT, [ItDepends, Tint]),
        new TestPropertyAnnotationHandler(Short, INPUT, [Tint]),
    ]

    TypeMetadataStore typeMetadataStore = new DefaultTypeMetadataStore(
        [new ThisIsAThingTypeAnnotationHandler()],
        propertyHandlers,
        [ItDepends, Tint],
        typeAnnotationMetadataStore,
        TestPropertyTypeResolver.INSTANCE,
        TestCrossBuildInMemoryCacheFactory.instance()
    )

    InstanceSchemaExtractor<Object, TestInstanceSchema> instanceSchemaExtractor = new DefaultInstanceSchemaExtractor(
        typeMetadataStore,
        TestNested,
        TestOptional,
        { instance -> new TestInstanceSchema.Builder() },
        propertyHandlers
    )

    private static class ThisIsAThingTypeAnnotationHandler implements TypeAnnotationHandler {
        @Override
        Class<? extends Annotation> getAnnotationType() {
            ThisIsAThing
        }

        @Override
        void validateTypeMetadata(Class<?> classWithAnnotationAttached, TypeValidationContext visitor) {
            if (classWithAnnotationAttached.getAnnotation(ThisIsAThing)?.invalid()) {
                visitor.visitTypeProblem {
                    it.withDescription { "Annotated as invalid thing!" }
                }
            }
        }
    }

    private static class TestPropertyAnnotationHandler extends AbstractPropertyAnnotationHandler implements PropertySchemaExtractor<TestInstanceSchema.Builder> {

        TestPropertyAnnotationHandler(Class<? extends Annotation> annotationType, Kind kind, Collection<Class<? extends Annotation>> allowedModifiers) {
            super(annotationType, kind, ImmutableSet.copyOf(allowedModifiers))
        }

        @Override
        boolean isPropertyRelevant() {
            return true
        }

        @Override
        void visitPropertyValue(Annotation propertyAnnotation, String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
            // NO OP
        }

        @Override
        void extractProperty(String qualifiedName, PropertyMetadata metadata, Object parent, TestInstanceSchema.Builder builder) {
            builder.add(new TestPropertySchema(annotationType, qualifiedName, () -> metadata.getPropertyValue(parent)))
        }
    }

    static class TestPropertySchema extends AbstractUnresolvedPropertySchema {

        final Class<? extends Annotation> propertyType

        TestPropertySchema(Class<? extends Annotation> propertyType, String qualifiedName, Supplier<Object> valueResolver) {
            super(qualifiedName, false, valueResolver)
            this.propertyType = propertyType
        }

        @Override
        boolean equals(@Nullable Object o) {
            if (this.is(o)) {
                return true
            }
            if (o == null || getClass() != o.class) {
                return false
            }
            if (!super.equals(o)) {
                return false
            }

            TestPropertySchema that = (TestPropertySchema) o

            if (propertyType != that.propertyType) {
                return false
            }

            return true
        }

        @Override
        int hashCode() {
            int result = super.hashCode()
            result = 31 * result + propertyType.hashCode()
            return result
        }
    }

    static class TestInstanceSchema extends AbstractInstanceSchema {

        final ImmutableList<TestPropertySchema> testProperties

        TestInstanceSchema(
            ReplayingTypeValidationContext validationProblems,
            ImmutableList<NestedPropertySchema> nestedProperties,
            ImmutableList<TestPropertySchema> testProperties) {
            super(validationProblems, nestedProperties)
            this.testProperties = testProperties
        }

        @Override
        boolean equals(@Nullable Object o) {
            if (this.is(o)) {
                return true
            }
            if (o == null || getClass() != o.class) {
                return false
            }
            if (!super.equals(o)) {
                return false
            }

            TestInstanceSchema that = (TestInstanceSchema) o

            if (testProperties != that.testProperties) {
                return false
            }

            return true
        }

        int hashCode() {
            int result = super.hashCode()
            result = 31 * result + testProperties.hashCode()
            return result
        }

        private static class Builder extends InstanceSchema.Builder<TestInstanceSchema> {

            private final ImmutableList.Builder<TestPropertySchema> testProperties = ImmutableList.builder()

            void add(TestPropertySchema property) {
                testProperties.add(property)
            }

            @Override
            protected TestInstanceSchema build(
                ReplayingTypeValidationContext validationProblems,
                ImmutableList<NestedPropertySchema> nestedPropertySchemas
            ) {
                return new TestInstanceSchema(
                    validationProblems,
                    nestedPropertySchemas,
                    toSortedList(testProperties)
                )
            }
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@interface ThisIsAThing {
    boolean invalid() default false;
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Long {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface TestNested {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface TestOptional {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Short {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Tint {
    String value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Ignored {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface AlsoIgnored {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface ItDepends {
}
