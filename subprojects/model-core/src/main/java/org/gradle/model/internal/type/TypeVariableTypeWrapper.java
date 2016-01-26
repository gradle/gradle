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

package org.gradle.model.internal.type;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Nullable;

import java.util.Arrays;

/**
 * Wrapper for a {@link java.lang.reflect.TypeVariable}.
 */
class TypeVariableTypeWrapper implements WildcardWrapper {
    private final String name;
    private final TypeWrapper[] bounds;
    private final int hashCode;

    public TypeVariableTypeWrapper(String name, TypeWrapper[] bounds, int hashCode) {
        this.name = name;
        this.bounds = bounds;
        this.hashCode = hashCode;
    }

    @Override
    public Class<?> getRawClass() {
        if (bounds.length > 0) {
            return bounds[0].getRawClass();
        } else {
            return Object.class;
        }
    }

    @Override
    public boolean isAssignableFrom(TypeWrapper wrapper) {
        return false;
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        for (TypeWrapper bound : bounds) {
            bound.collectClasses(builder);
        }
    }

    @Override
    public String getRepresentation(boolean full) {
        return name;
    }

    public String getName() {
        return name;
    }

    @Override
    public TypeWrapper getUpperBound() {
        return bounds[0];
    }

    @Nullable
    @Override
    public TypeWrapper getLowerBound() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeVariableTypeWrapper)) {
            return false;
        } else {
            TypeVariableTypeWrapper var2 = (TypeVariableTypeWrapper) o;
            return Objects.equal(this.getName(), var2.getName())
                && Arrays.equals(this.bounds, var2.bounds);
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
