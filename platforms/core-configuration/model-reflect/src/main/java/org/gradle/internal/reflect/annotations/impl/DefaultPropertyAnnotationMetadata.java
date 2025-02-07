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
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DefaultPropertyAnnotationMetadata extends AbstractHasAnnotationMetadata implements PropertyAnnotationMetadata {
    private final String propertyName;

    public DefaultPropertyAnnotationMetadata(String propertyName, Method getter, ImmutableMap<AnnotationCategory, Annotation> annotationsByCategory) {
        super(getter, annotationsByCategory);
        this.propertyName = propertyName;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public int compareTo(@Nonnull PropertyAnnotationMetadata o) {
        return propertyName.compareTo(o.getPropertyName());
    }

    @Nullable
    @Override
    public Object getPropertyValue(Object object) {
        try {
            return getMethod().invoke(object);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not call %s.%s() on %s", getMethod().getDeclaringClass().getSimpleName(), getMethod().getName(), object), e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s / %s()", propertyName, getMethod().getName());
    }
}
