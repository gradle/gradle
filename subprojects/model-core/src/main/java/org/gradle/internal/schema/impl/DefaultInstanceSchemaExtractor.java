/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.schema.impl;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.schema.InstanceSchema;
import org.gradle.internal.schema.InstanceSchemaExtractor;
import org.gradle.internal.schema.PropertySchema;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.stream.Stream;

public class DefaultInstanceSchemaExtractor implements InstanceSchemaExtractor {

    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;

    public DefaultInstanceSchemaExtractor(TypeAnnotationMetadataStore typeAnnotationMetadataStore) {
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
    }

    @Override
    public InstanceSchema extractSchema(Object instance) {
        TypeAnnotationMetadata instanceMetadata = typeAnnotationMetadataStore.getTypeAnnotationMetadata(instance.getClass());
        ImmutableSortedSet<PropertySchema> properties = extractFromBean(instance, instanceMetadata, null)
            // TODO Do we need to sort here?
            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
        return new DefaultInstanceSchema(instanceMetadata, properties);
    }

    private Stream<PropertySchema> extractFromBean(Object bean, TypeAnnotationMetadata typeMetadata, @Nullable String parentPropertyName) {
        return typeMetadata.getPropertiesAnnotationMetadata().stream()
            .flatMap(propertyAnnotations -> {
                String propertyName = parentPropertyName == null
                    ? propertyAnnotations.getPropertyName()
                    : parentPropertyName + '.' + propertyAnnotations.getPropertyName();
                if (propertyAnnotations.isAnnotationPresent(Nested.class)) {
                    Object nestedBean = getValueOf(bean, propertyAnnotations);
                    if (nestedBean == null) {
                        // TODO Throw when non-optional nested property is null
                        return Stream.empty();
                    }
                    TypeAnnotationMetadata nestedMetadata = typeAnnotationMetadataStore.getTypeAnnotationMetadata(nestedBean.getClass());
                    return extractFromBean(nestedBean, nestedMetadata, propertyName);
                } else {
                    return Stream.of(new DefaultPropertySchema(bean, propertyName, propertyAnnotations));
                }
            });
    }

    private static class DefaultInstanceSchema implements InstanceSchema {
        private final TypeAnnotationMetadata typeMetadata;
        private final ImmutableSortedSet<PropertySchema> properties;

        public DefaultInstanceSchema(TypeAnnotationMetadata typeMetadata, ImmutableSortedSet<PropertySchema> properties) {
            this.typeMetadata = typeMetadata;
            this.properties = properties;
        }

        @Override
        public TypeAnnotationMetadata getTypeMetadata() {
            return typeMetadata;
        }

        @Override
        public Stream<PropertySchema> properties() {
            return properties.stream();
        }
    }

    private static class DefaultPropertySchema implements PropertySchema {
        private final Object bean;
        private final String qualifiedName;
        private final PropertyAnnotationMetadata metadata;

        public DefaultPropertySchema(Object bean, String qualifiedName, PropertyAnnotationMetadata metadata) {
            this.bean = bean;
            this.qualifiedName = qualifiedName;
            this.metadata = metadata;
        }

        @Override
        public String getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public PropertyAnnotationMetadata getMetadata() {
            return metadata;
        }

        @Nullable
        @Override
        public Object getValue() {
            return getValueOf(bean, metadata);
        }

        @Override
        public int compareTo(@Nonnull PropertySchema o) {
            return qualifiedName.compareTo(o.getQualifiedName());
        }
    }

    @Nullable
    private static Object getValueOf(Object bean, PropertyAnnotationMetadata property) {
        try {
            return property.getGetter().invoke(bean);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
