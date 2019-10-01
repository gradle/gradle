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
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class DefaultPropertyAnnotationMetadata implements PropertyAnnotationMetadata {
    private final String propertyName;
    private final Method method;
    private final ImmutableMap<AnnotationCategory, Annotation> annotations;
    private final ImmutableSet<Class<? extends Annotation>> annotationTypes;

    public DefaultPropertyAnnotationMetadata(String propertyName, Method method, ImmutableMap<AnnotationCategory, Annotation> annotations) {
        this.propertyName = propertyName;
        this.method = method;
        this.annotations = annotations;
        this.annotationTypes = collectAnnotationTypes(annotations);
    }

    private static ImmutableSet<Class<? extends Annotation>> collectAnnotationTypes(ImmutableMap<AnnotationCategory, Annotation> annotations) {
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builderWithExpectedSize(annotations.size());
        for (Annotation value : annotations.values()) {
            builder.add(value.annotationType());
        }
        return builder.build();
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotationTypes.contains(annotationType);
    }

    @Override
    public ImmutableMap<AnnotationCategory, Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public int compareTo(@NotNull PropertyAnnotationMetadata o) {
        return method.getName().compareTo(o.getMethod().getName());
    }

    @Override
    public String toString() {
        return String.format("%s / %s()", propertyName, method.getName());
    }
}
