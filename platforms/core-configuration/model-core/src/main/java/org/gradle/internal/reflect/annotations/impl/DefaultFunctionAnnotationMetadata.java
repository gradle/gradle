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
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.FunctionAnnotationMetadata;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link FunctionAnnotationMetadata}.
 */
public class DefaultFunctionAnnotationMetadata extends AbstractHasAnnotationMetadata implements FunctionAnnotationMetadata {

    public DefaultFunctionAnnotationMetadata(Method method, ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory) {
        super(method, annotationsByCategory);
    }

    @Override
    public String toString() {
        return getMethod().getName() + "(" + getParameterTypeString() + ")";
    }

    private String getParameterTypeString() {
        return Arrays.stream(getMethod().getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "));
    }

    /**
     * Compares this metadata to another metadata by comparing the method name and the parameter types.
     */
    @Override
    public int compareTo(@Nonnull FunctionAnnotationMetadata o) {
        int result = getMethod().getName().compareTo(o.getMethod().getName());
        if (result == 0) {
            // This is the same method name, compare the number of parameters
            if (getMethod().getParameterCount() != o.getMethod().getParameterCount()) {
                return getMethod().getParameterCount() - o.getMethod().getParameterCount();
            } else {
                // Same method name, same number of parameters, compare the parameter types
                for (int i = 0; i < getMethod().getParameterCount(); i++) {
                    // If the parameters don't match, return the comparison of the first mismatched parameter type name
                    if (getMethod().getParameterTypes()[i] != o.getMethod().getParameterTypes()[i]) {
                        return getMethod().getParameterTypes()[i].getName().compareTo(o.getMethod().getParameterTypes()[i].getName());
                    }
                }
                return 0;
            }
        } else {
            return result;
        }
    }
}
