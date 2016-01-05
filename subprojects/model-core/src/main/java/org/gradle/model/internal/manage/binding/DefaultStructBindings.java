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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.StructSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultStructBindings<T> implements StructBindings<T> {
    private final StructSchema<T> publicSchema;
    private final Iterable<StructSchema<?>> internalViewSchemas;
    private final Set<StructSchema<?>> viewSchemas;
    private final StructSchema<?> delegateSchema;

    private final Map<String, ManagedProperty<?>> managedProperties;
    private final Collection<DirectMethodBinding> viewBindings;
    private final Collection<DelegateMethodBinding> delegateBindings;

    protected DefaultStructBindings(
        StructSchema<T> publicSchema,
        Iterable<? extends StructSchema<?>> internalViewSchemas,
        @Nullable StructSchema<?> delegateSchema,

        Map<String, ManagedProperty<?>> managedProperties,
        Iterable<DirectMethodBinding> viewBindings,
        Iterable<DelegateMethodBinding> delegateBindings
    ) {
        this.publicSchema = publicSchema;
        this.internalViewSchemas = ImmutableSet.copyOf(internalViewSchemas);
        this.viewSchemas = ImmutableSet.copyOf(Iterables.concat(Collections.singleton(publicSchema), internalViewSchemas));
        this.delegateSchema = delegateSchema;

        this.managedProperties = ImmutableSortedMap.copyOf(managedProperties, Ordering.natural());
        this.viewBindings = ImmutableSet.copyOf(viewBindings);
        this.delegateBindings = ImmutableSet.copyOf(delegateBindings);
    }

    @Override
    public StructSchema<T> getPublicSchema() {
        return publicSchema;
    }

    @Override
    public Set<StructSchema<?>> getAllViewSchemas() {
        return viewSchemas;
    }

    @Override
    public Iterable<StructSchema<?>> getInternalViewSchemas() {
        return internalViewSchemas;
    }

    @Override
    @Nullable
    public StructSchema<?> getDelegateSchema() {
        return delegateSchema;
    }

    public Map<String, ManagedProperty<?>> getManagedProperties() {
        return managedProperties;
    }

    @Override
    public Collection<DirectMethodBinding> getViewBindings() {
        return viewBindings;
    }

    @Override
    public Collection<DelegateMethodBinding> getDelegateBindings() {
        return delegateBindings;
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
            && Objects.equal(internalViewSchemas, that.internalViewSchemas)
            && Objects.equal(viewSchemas, that.viewSchemas)
            && Objects.equal(delegateSchema, that.delegateSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(publicSchema, internalViewSchemas, viewSchemas, delegateSchema);
    }
}
