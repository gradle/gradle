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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

class GenericArrayTypeWrapper implements GenericArrayType, TypeWrapper {
    private final TypeWrapper componentType;
    private final int hashCode;

    public GenericArrayTypeWrapper(TypeWrapper componentType, int hashCode) {
        this.componentType = componentType;
        this.hashCode = hashCode;
    }

    @Override
    public Type getGenericComponentType() {
        return componentType.unwrap();
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        componentType.collectClasses(builder);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GenericArrayType) {
            GenericArrayType that = (GenericArrayType) o;
            return this == that || getGenericComponentType().equals(that.getGenericComponentType());
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
