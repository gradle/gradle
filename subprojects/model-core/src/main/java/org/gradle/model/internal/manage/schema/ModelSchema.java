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

package org.gradle.model.internal.manage.schema;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public class ModelSchema<T> {

    public enum Kind {
        VALUE(false, true), // at the moment we are conflating this with unstructured primitives
        COLLECTION,
        SPECIALIZED_MAP(false, false), // not quite
        STRUCT,
        UNMANAGED(false, false); // some type we know nothing about

        private final boolean isManaged;
        private final boolean isAllowedPropertyTypeOfManagedType;

        Kind() {
            this(true, true);
        }

        Kind(boolean isManaged, boolean isAllowedPropertyTypeOfManagedType) {
            this.isManaged = isManaged;
            this.isAllowedPropertyTypeOfManagedType = isAllowedPropertyTypeOfManagedType;
        }

        public boolean isManaged() {
            return isManaged;
        }

        public boolean isAllowedPropertyTypeOfManagedType() {
            return isAllowedPropertyTypeOfManagedType;
        }
    }

    private final ModelType<T> type;
    private final Kind kind;

    public static <T> ModelSchema<T> value(ModelType<T> type) {
        return new ModelSchema<T>(type, Kind.VALUE);
    }

    public static <T> ModelStructSchema<T> struct(ModelType<T> type, Iterable<ModelProperty<?>> properties, Class<? extends T> managedImpl) {
        return new ModelStructSchema<T>(type, properties, managedImpl);
    }

    public static <T> ModelCollectionSchema<T> collection(ModelType<T> type, ModelType<?> elementType) {
        return new ModelCollectionSchema<T>(type, elementType);
    }

    public static <T> ModelMapSchema<T> specializedMap(ModelType<T> type, ModelType<?> elementType, Class<?> managedImpl) {
        return new ModelMapSchema<T>(type, elementType, managedImpl);
    }

    public static <T> ModelSchema<T> unmanaged(ModelType<T> type) {
        return new ModelSchema<T>(type, Kind.UNMANAGED);
    }

    protected ModelSchema(ModelType<T> type, Kind kind) {
        this.type = type;
        this.kind = kind;
    }

    public ModelType<T> getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return kind.toString().toLowerCase() + " " + type;
    }
}
