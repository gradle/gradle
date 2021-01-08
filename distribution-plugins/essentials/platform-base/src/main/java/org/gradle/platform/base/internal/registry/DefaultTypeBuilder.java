/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.Sets;
import org.gradle.model.internal.manage.schema.ManagedImplSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.Set;

public class DefaultTypeBuilder<T> implements TypeBuilderInternal<T> {
    private final Class<?> markerAnnotation;
    private final ModelSchema<? extends T> schema;
    private Class<?> implementation;
    private final Set<Class<?>> internalViews = Sets.newLinkedHashSet();

    public DefaultTypeBuilder(Class<?> markerAnnotation, ModelSchema<? extends T> schema) {
        this.markerAnnotation = markerAnnotation;
        this.schema = schema;
    }

    @Override
    public TypeBuilderInternal<T> defaultImplementation(Class<?> implementation) {
        if (this.schema instanceof ManagedImplSchema) {
            throw new InvalidModelException(String.format("Method annotated with @%s cannot set default implementation for managed type %s.", markerAnnotation.getSimpleName(), schema.getType().getName()));
        }
        if (this.implementation != null) {
            throw new InvalidModelException(String.format("Method annotated with @%s cannot set default implementation multiple times.", markerAnnotation.getSimpleName()));
        }
        this.implementation = implementation;
        return this;
    }

    @Override
    public Class<?> getDefaultImplementation() {
        return this.implementation;
    }

    @Override
    public TypeBuilder<T> internalView(Class<?> internalView) {
        if (internalViews.contains(internalView)) {
            throw new InvalidModelException(String.format("Internal view '%s' must not be specified multiple times.", internalView.getName()));
        }
        internalViews.add(internalView);
        return this;
    }

    @Override
    public Set<Class<?>> getInternalViews() {
        return internalViews;
    }
}
