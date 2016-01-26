/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspect;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;

/**
 * The schema for an element with properties.
 */
public interface StructSchema<T> extends ModelSchema<T> {
    boolean hasProperty(String name);

    SortedSet<String> getPropertyNames();

    @Nullable
    ModelProperty<?> getProperty(String name);

    Collection<ModelProperty<?>> getProperties();

    Set<WeaklyTypeReferencingMethod<?, ?>> getNonPropertyMethods();

    Set<WeaklyTypeReferencingMethod<?, ?>> getAllMethods();

    boolean hasAspect(Class<? extends ModelSchemaAspect> aspectType);

    <A extends ModelSchemaAspect> A getAspect(Class<A> aspectType);

    Collection<ModelSchemaAspect> getAspects();
}
