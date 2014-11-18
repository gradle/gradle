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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.HashSet;

@ThreadSafe
public class ModelSchema<T> {

    public static enum Kind {
        VALUE(false, true), // at the moment we are conflating this with unstructured primitives
        COLLECTION,
        STRUCT, // type is guaranteed to be an interface
        UNMANAGED_STRUCT(false, false), // an interface that is not annotated with @Managed but would otherwise be a valid managed type
        UNMANAGED(false, false); // some type we know nothing about

        private final boolean isManaged;
        private final boolean isAllowedPropertyTypeOfManagedType;

        private Kind() {
            this(true, true);
        }

        private Kind(boolean isManaged, boolean isAllowedPropertyTypeOfManagedType) {
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
    private final ImmutableSortedMap<String, ModelProperty<?>> properties;
    private final HashSet<ModelProperty<?>> declaredProperties;

    public static <T> ModelSchema<T> value(ModelType<T> type) {
        return new ModelSchema<T>(type, Kind.VALUE);
    }

    public static <T> ModelSchema<T> struct(ModelType<T> type, Iterable<ModelProperty<?>> declaredProperties, Iterable<ModelProperty<?>> inheritedProperties) {
        return new ModelSchema<T>(type, Kind.STRUCT, declaredProperties, inheritedProperties);
    }

    public static <T> ModelSchema<T> unmanagedStruct(ModelType<T> type, Iterable<ModelProperty<?>> declaredProperties, Iterable<ModelProperty<?>> inheritedProperties) {
        return new ModelSchema<T>(type, Kind.UNMANAGED_STRUCT, declaredProperties, inheritedProperties);
    }

    public static <T> ModelSchema<T> collection(ModelType<T> type) {
        return new ModelSchema<T>(type, Kind.COLLECTION);
    }

    public static <T> ModelSchema<T> unmanaged(ModelType<T> type) {
        return new ModelSchema<T>(type, Kind.UNMANAGED);
    }

    private ModelSchema(ModelType<T> type, Kind kind, Iterable<ModelProperty<?>> declaredProperties, Iterable<ModelProperty<?>> inheritedProperties) {
        this.type = type;
        this.kind = kind;

        ImmutableSortedMap.Builder<String, ModelProperty<?>> builder = ImmutableSortedMap.naturalOrder();
        Iterable<ModelProperty<?>> properties = Iterables.concat(declaredProperties, inheritedProperties);
        for (ModelProperty<?> property : properties) {
            builder.put(property.getName(), property);
        }
        this.properties = builder.build();
        this.declaredProperties = Sets.newHashSet(declaredProperties);
    }

    private ModelSchema(ModelType<T> type, Kind kind) {
        this(type, kind, Collections.<ModelProperty<?>>emptySet(), Collections.<ModelProperty<?>>emptySet());
    }

    public ModelType<T> getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public ImmutableSortedMap<String, ModelProperty<?>> getProperties() {
        return properties;
    }

    public boolean hasDeclaredProperty(ModelProperty<?> property) {
        return declaredProperties.contains(property);
    }
}
