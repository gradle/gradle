/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.StructSchema;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DefaultStructBindings<T> implements StructBindings<T> {
    private final StructSchema<T> publicSchema;
    private final Set<StructSchema<?>> declaredViewSchemas;
    private final Set<StructSchema<?>> implementedViewSchemas;
    private final StructSchema<?> delegateSchema;

    private final Map<String, ManagedProperty<?>> managedProperties;
    private final Collection<StructMethodBinding> methodBindings;

    protected DefaultStructBindings(
        StructSchema<T> publicSchema,
        Iterable<? extends StructSchema<?>> declaredViewSchemas,
        Iterable<? extends StructSchema<?>> implementedViewSchemas,
        @Nullable StructSchema<?> delegateSchema,

        Map<String, ManagedProperty<?>> managedProperties,
        Iterable<StructMethodBinding> methodBindings
    ) {
        this.publicSchema = publicSchema;
        this.declaredViewSchemas = ImmutableSet.copyOf(declaredViewSchemas);
        this.implementedViewSchemas = ImmutableSet.copyOf(implementedViewSchemas);
        this.delegateSchema = delegateSchema;

        this.managedProperties = ImmutableSortedMap.copyOf(managedProperties, Ordering.natural());
        this.methodBindings = ImmutableList.copyOf(methodBindings);
    }

    @Override
    public StructSchema<T> getPublicSchema() {
        return publicSchema;
    }

    @Override
    public Set<StructSchema<?>> getDeclaredViewSchemas() {
        return declaredViewSchemas;
    }

    @Override
    public Set<StructSchema<?>> getImplementedViewSchemas() {
        return implementedViewSchemas;
    }

    @Override
    @Nullable
    public StructSchema<?> getDelegateSchema() {
        return delegateSchema;
    }

    @Override
    public Map<String, ManagedProperty<?>> getManagedProperties() {
        return managedProperties;
    }

    @Override
    public ManagedProperty<?> getManagedProperty(String name) {
        return managedProperties.get(name);
    }

    @Override
    public Collection<StructMethodBinding> getMethodBindings() {
        return methodBindings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultStructBindings<?> that = (DefaultStructBindings<?>) o;
        return Objects.equal(publicSchema, that.publicSchema)
            && Objects.equal(declaredViewSchemas, that.declaredViewSchemas)
            && Objects.equal(delegateSchema, that.delegateSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(publicSchema, declaredViewSchemas, delegateSchema);
    }

    @Override
    public String toString() {
        return "StructBindings[" + publicSchema.getType().getDisplayName() + "]";
    }
}
