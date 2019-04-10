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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

public class DefaultPropertyAnnotationMetadata implements PropertyAnnotationMetadata {
    private final String propertyName;
    private final Method method;
    private final ImmutableMap<AnnotationCategory, Annotation> annotations;
    private final ImmutableList<String> validationProblems;

    public DefaultPropertyAnnotationMetadata(String propertyName, Method method, ImmutableMap<AnnotationCategory, Annotation> annotations, ImmutableList<String> validationProblems) {
        this.propertyName = propertyName;
        this.method = method;
        this.annotations = annotations;
        this.validationProblems = validationProblems;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public Annotation getAnnotation(AnnotationCategory category) {
        return annotations.get(category);
    }

    @Override
    public boolean hasAnnotation(AnnotationCategory category, Class<? extends Annotation> annotationType) {
        Annotation annotation = annotations.get(category);
        return annotation != null && annotation.annotationType().equals(annotationType);
    }

    @Override
    public Map<AnnotationCategory, Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public void visitValidationFailures(@Nullable String ownerPath, ParameterValidationContext validationContext) {
        validationProblems.forEach(validationProblem -> validationContext.visitError(ownerPath, getPropertyName(), validationProblem));
    }

    @Override
    public int compareTo(@NotNull PropertyAnnotationMetadata o) {
        return method.getName().compareTo(o.getMethod().getName());
    }

    @Override
    public String toString() {
        return method.getName();
    }
}
