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

package org.gradle.model.internal.core;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.model.internal.manage.binding.ManagedProperty;
import org.gradle.model.internal.type.ModelType;

public class NodeInitializerContext<T> {
    private final ModelType<T> modelType;
    private final Spec<ModelType<?>> constraints;
    private final Optional<PropertyContext> propertyContextOptional;

    private NodeInitializerContext(ModelType<T> modelType, Spec<ModelType<?>> constraints, Optional<PropertyContext> propertyContextOptional) {
        this.modelType = modelType;
        this.constraints = constraints;
        this.propertyContextOptional = propertyContextOptional;
    }

    public static <T> NodeInitializerContext<T> forType(ModelType<T> type) {
        return new NodeInitializerContext<T>(type, Specs.<ModelType<?>>satisfyAll(), Optional.<PropertyContext>absent());
    }

    public static <T> NodeInitializerContext<T> forExtensibleType(ModelType<T> type, Spec<ModelType<?>> constraints) {
        return new NodeInitializerContext<T>(type, constraints, Optional.<PropertyContext>absent());
    }

    public static <T> NodeInitializerContext<T> forProperty(ModelType<T> type, ManagedProperty<?> property, ModelType<?> containingType) {
        return new NodeInitializerContext<T>(type, Specs.<ModelType<?>>satisfyAll(), Optional.of(new PropertyContext(property.getName(), property.getType(), property.isWritable(), property.isDeclaredAsHavingUnmanagedType(), containingType)));
    }

    public ModelType<T> getModelType() {
        return modelType;
    }

    public Spec<ModelType<?>> getConstraints() {
        return constraints;
    }

    public Optional<PropertyContext> getPropertyContextOptional() {
        return propertyContextOptional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeInitializerContext<?> that = (NodeInitializerContext<?>) o;
        return Objects.equal(modelType, that.modelType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modelType);
    }

    public static class PropertyContext {
        private final String name;
        private final ModelType<?> type;
        private final boolean writable;
        private final boolean declaredAsHavingUnmanagedType;
        private final ModelType<?> declaringType;

        private PropertyContext(String name, ModelType<?> type, boolean writable, boolean declaredAsHavingUnmanagedType, ModelType<?> declaringType) {
            this.name = name;
            this.type = type;
            this.writable = writable;
            this.declaredAsHavingUnmanagedType = declaredAsHavingUnmanagedType;
            this.declaringType = declaringType;
        }

        public String getName() {
            return name;
        }

        public ModelType<?> getType() {
            return type;
        }

        public boolean isWritable() {
            return writable;
        }

        public boolean isDeclaredAsHavingUnmanagedType() {
            return declaredAsHavingUnmanagedType;
        }

        public ModelType<?> getDeclaringType() {
            return declaringType;
        }
    }
}
