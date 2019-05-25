/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyMetadata;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.OPTIONAL;

public class NestedBeanAnnotationHandler implements PropertyAnnotationHandler {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public ImmutableSet<? extends AnnotationCategory> getAllowedModifiers() {
        return ImmutableSet.of(OPTIONAL);
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return !visitor.visitOutputFilePropertiesOnly();
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        Object nested;
        try {
            nested = unpackProvider(value.call());
        } catch (Exception e) {
            visitor.visitInputProperty(propertyName, new InvalidValue(e), false);
            return;
        }
        if (nested != null) {
            context.addNested(propertyName, nested);
        } else if (!propertyMetadata.isAnnotationPresent(Optional.class)) {
            visitor.visitInputProperty(propertyName, new AbsentValue(), false);
        }
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, ParameterValidationContext visitor) {
    }

    @Nullable
    private static Object unpackProvider(@Nullable Object value) {
        // Only unpack one level of Providers, since Provider<Provider<>> is not supported - we don't need two levels of laziness.
        if (value instanceof Provider) {
            return ((Provider<?>) value).get();
        }
        return value;
    }

    private static class InvalidValue implements PropertyValue {
        private final Exception exception;

        public InvalidValue(Exception exception) {
            this.exception = exception;
        }

        @Nullable
        @Override
        public Object call() {
            throw UncheckedException.throwAsUncheckedException(exception);
        }

        @Nullable
        @Override
        public Object getUnprocessedValue() {
            return call();
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            // Ignore
            return TaskDependencyContainer.EMPTY;
        }

        @Override
        public void attachProducer(Task producer) {
            // Ignore
        }

        @Override
        public void maybeFinalizeValue() {
            // Ignore
        }
    }

    private static class AbsentValue implements PropertyValue {
        @Nullable
        @Override
        public Object call() {
            return null;
        }

        @Nullable
        @Override
        public Object getUnprocessedValue() {
            return null;
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            // Ignore
            return TaskDependencyContainer.EMPTY;
        }

        @Override
        public void attachProducer(Task producer) {
            // Ignore
        }

        @Override
        public void maybeFinalizeValue() {
            // Ignore
        }
    }
}
