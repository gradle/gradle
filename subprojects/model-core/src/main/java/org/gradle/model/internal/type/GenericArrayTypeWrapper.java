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

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Array;

class GenericArrayTypeWrapper implements TypeWrapper {
    private final TypeWrapper componentType;
    private final int hashCode;

    public GenericArrayTypeWrapper(TypeWrapper componentType, int hashCode) {
        this.componentType = componentType;
        this.hashCode = hashCode;
    }

    @Override
    public Class<?> getRawClass() {
        // This could probably be more efficient
        return Array.newInstance(componentType.getRawClass(), 0).getClass();
    }

    @Override
    public boolean isAssignableFrom(TypeWrapper wrapper) {
        if (wrapper instanceof GenericArrayTypeWrapper) {
            GenericArrayTypeWrapper arrayType = (GenericArrayTypeWrapper) wrapper;
            return componentType.isAssignableFrom(arrayType.componentType);
        }
        return false;
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        componentType.collectClasses(builder);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GenericArrayTypeWrapper) {
            GenericArrayTypeWrapper that = (GenericArrayTypeWrapper) o;
            return this == that || componentType.equals(that.componentType);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getRepresentation(true);
    }

    @Override
    public String getRepresentation(boolean full) {
        return componentType.getRepresentation(full) + "[]";
    }
}
