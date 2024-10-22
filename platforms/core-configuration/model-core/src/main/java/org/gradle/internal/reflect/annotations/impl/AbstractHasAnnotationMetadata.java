/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.HasAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Base class for elements that have annotation metadata, such as properties and functions.
 */
public abstract class AbstractHasAnnotationMetadata implements HasAnnotationMetadata {
    protected final Method method;
    protected final TypeToken<?> declaredType;
    protected final ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory;
    protected final ImmutableMap<Class<? extends Annotation>, Annotation> annotationsByType;

    public AbstractHasAnnotationMetadata(Method method, ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory) {
        this.method = method;
        method.setAccessible(true);
        this.declaredType = TypeToken.of(method.getGenericReturnType());
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
    public Method getMethod() {
        return method;
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
    public ImmutableMap<AnnotationCategory, Annotation> getAnnotationsByCategory() {
        return annotationsByCategory;
    }

    @Override
    public TypeToken<?> getDeclaredReturnType() {
        return declaredType;
    }
}
