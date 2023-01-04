/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect.annotations.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.gradle.api.GradleException;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public class DefaultPropertyAnnotationMetadata implements PropertyAnnotationMetadata {
    private final String propertyName;
    private final Method getter;
    private final TypeToken<?> declaredType;
    private final ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory;
    private final ImmutableMap<Class<? extends Annotation>, Annotation> annotationsByType;

    public DefaultPropertyAnnotationMetadata(String propertyName, Method getter, ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory) {
        this.propertyName = propertyName;
        this.getter = getter;
        getter.setAccessible(true);
        this.declaredType = TypeToken.of(getter.getGenericReturnType());
        this.annotationsByCategory = annotationsByCategory;
        this.annotationsByType = collectAnnotationsByType(annotationsByCategory);
    }

    private static ImmutableMap<Class<? extends Annotation>, Annotation> collectAnnotationsByType(ImmutableMap<AnnotationCategory, Annotation> annotations) {
        ImmutableMap.Builder<Class<? extends Annotation>, Annotation> builder = ImmutableMap.builderWithExpectedSize(annotations.size());
        for (Annotation value : annotations.values()) {
            builder.put(value.annotationType(), value);
        }
        return builder.build();
    }

    @Override
    public Method getGetter() {
        return getter;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotationsByType.containsKey(annotationType);
    }

    @Override
    public <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType) {
        return Optional.ofNullable(Cast.uncheckedCast(annotationsByType.get(annotationType)));
    }

    @Override
    public ImmutableMap<AnnotationCategory, Annotation> getAnnotations() {
        return annotationsByCategory;
    }

    @Override
    public int compareTo(@Nonnull PropertyAnnotationMetadata o) {
        return propertyName.compareTo(o.getPropertyName());
    }

    @Override
    public TypeToken<?> getDeclaredType() {
        return declaredType;
    }

    @Nullable
    @Override
    public Object getPropertyValue(Object object) {
        try {
            return getter.invoke(object);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not call %s.%s() on %s", getter.getDeclaringClass().getSimpleName(), getter.getName(), object), e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s / %s()", propertyName, getter.getName());
    }
}
