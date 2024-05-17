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

import com.google.common.collect.ImmutableSet
import groovy.transform.Generated
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.DefaultTypeMetadataStore
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TypeAnnotationHandler
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.reflect.validation.TypeValidationContext

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method

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

    TypeMetadataStore typeMetadataStore = new DefaultTypeMetadataStore(
        [new ThisIsAThingTypeAnnotationHandler()],
        [
            new SimplePropertyAnnotationHandler(TestNested, INPUT, [ItDepends]),
            new SimplePropertyAnnotationHandler(Long, INPUT, [ItDepends, Tint]),
            new SimplePropertyAnnotationHandler(Short, INPUT, [Tint]),
        ],
        [ItDepends, Tint],
        typeAnnotationMetadataStore,
        { annotations -> annotations.get(TYPE)?.annotationType() },
        TestCrossBuildInMemoryCacheFactory.instance(),
        MissingPropertyAnnotationHandler.DO_NOTHING
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
                    it.label("Annotated as invalid thing!")
                }
            }
        }
    }

    private static class SimplePropertyAnnotationHandler extends AbstractPropertyAnnotationHandler {

        SimplePropertyAnnotationHandler(Class<? extends Annotation> annotationType, Kind kind, Collection<Class<? extends Annotation>> allowedModifiers) {
            super(annotationType, kind, ImmutableSet.copyOf(allowedModifiers))
        }

        @Override
        boolean isPropertyRelevant() {
            return true
        }

        @Override
        void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
            // NO OP
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
