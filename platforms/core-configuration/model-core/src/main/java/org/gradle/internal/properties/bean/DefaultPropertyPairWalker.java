/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.properties.bean;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.declarative.dsl.model.annotations.NestedRestricted;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.annotations.InstancePairTypeMetadataWalker;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;

import javax.annotation.Nullable;

/**
 * Walks the properties of a pair of objects, visiting each pair of properties with the provided {@link PropertyPairVisitor}.  The walker
 * will visit all properties of the common type of the two objects, including nested properties annotated with {@link NestedRestricted}.
 * Null properties will be visited, but the visitor will be passed null for the property value.  If either instance of a
 * {@link NestedRestricted} property is null, the visitor will not be called for the nested properties of that property (even if one
 * instance is non-null).
 */
public class DefaultPropertyPairWalker implements PropertyPairWalker {
    private final InstancePairTypeMetadataWalker walker;

    public DefaultPropertyPairWalker(TypeMetadataStore typeMetadataStore) {
        this.walker = InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, NestedRestricted.class);
    }

    @Override
    public <T, L extends T, R extends T> void visitPropertyPairs(Class<T> commonType, L left, R right, PropertyPairVisitor visitor) {
        InstancePairTypeMetadataWalker.InstancePair<T> roots = InstancePairTypeMetadataWalker.InstancePair.of(commonType, left, right);
        walker.walk(roots, new PropertyPairMetadataVisitor(visitor));
    }

    private static class PropertyPairMetadataVisitor implements InstancePairTypeMetadataWalker.InstancePairMetadataVisitor {
        private final PropertyPairVisitor pairVisitor;

        public PropertyPairMetadataVisitor(PropertyPairVisitor pairVisitor) {
            this.pairVisitor = pairVisitor;
        }

        @Override
        public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable InstancePairTypeMetadataWalker.InstancePair<?> pair) {

        }

        @Override
        public void visitNestedUnpackingError(String qualifiedName, Exception e) {
            throw new RuntimeException("Failed to query value for nested property: " + qualifiedName, e);
        }

        @Override
        public void visitLeaf(InstancePairTypeMetadataWalker.InstancePair<?> parent, String qualifiedName, PropertyMetadata propertyMetadata) {
            Class<?> type = propertyMetadata.getDeclaredType().getRawType();
            if (parent.getLeft() != null && parent.getRight() != null) {
                if (Property.class.isAssignableFrom(type)) {
                    Property<?> left = Cast.uncheckedCast(propertyMetadata.getPropertyValue(parent.getLeft()));
                    Provider<?> right = Cast.uncheckedCast(propertyMetadata.getPropertyValue(parent.getRight()));
                    pairVisitor.visitPropertyTypePair(left, Cast.uncheckedCast(right));
                }
            }
        }

        @Override
        public void visitRoot(TypeMetadata typeMetadata, InstancePairTypeMetadataWalker.InstancePair<?> value) {

        }
    }
}
