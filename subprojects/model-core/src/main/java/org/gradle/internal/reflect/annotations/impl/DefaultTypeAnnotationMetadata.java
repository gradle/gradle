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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

public class DefaultTypeAnnotationMetadata implements TypeAnnotationMetadata {
    private final ImmutableBiMap<Class<? extends Annotation>, Annotation> annotations;
    private final ImmutableSortedSet<PropertyAnnotationMetadata> properties;

    public DefaultTypeAnnotationMetadata(Iterable<? extends Annotation> annotations, Iterable<? extends PropertyAnnotationMetadata> properties) {
        //noinspection ConstantConditions
        this.annotations = ImmutableBiMap.copyOf(Maps.uniqueIndex(annotations, annotation -> annotation.annotationType()));
        this.properties = ImmutableSortedSet.copyOf(properties);
    }

    @Override
    public ImmutableSet<Annotation> getAnnotations() {
        return annotations.values();
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotations.containsKey(annotationType);
    }

    @Override
    public ImmutableSortedSet<PropertyAnnotationMetadata> getPropertiesAnnotationMetadata() {
        return properties;
    }

    @Override
    public void visitValidationFailures(@Nullable String ownerPath, ParameterValidationContext validationContext) {
        properties.forEach(property -> property.visitValidationFailures(ownerPath, validationContext));
    }
}
