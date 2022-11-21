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

package org.gradle.internal.properties.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.api.Named;
import org.gradle.api.tasks.Nested;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class AbstractTypeMetadataWalker<T> implements TypeMetadataWalker<T> {
    private final TypeMetadataStore typeMetadataStore;

    private AbstractTypeMetadataWalker(TypeMetadataStore typeMetadataStore) {
        this.typeMetadataStore = typeMetadataStore;
    }

    @Override
    public void walk(T root, PropertyMetadataVisitor<T> visitor) {
        walk(root, null, visitor);
    }

    private void walk(T node, @Nullable String parentPropertyName, PropertyMetadataVisitor<T> visitor) {
        Class<?> nodeType = resolveType(node);
        if (Map.class.isAssignableFrom(nodeType)) {
            handleMap(node, (name, child) -> walk(child, getQualifiedName(parentPropertyName, name), visitor));
        } else if (Iterable.class.isAssignableFrom(nodeType)) {
            handleIterable(node, (name, child) -> walk(child, getQualifiedName(parentPropertyName, name), visitor));
        } else {
            TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
            typeMetadata.getPropertiesMetadata()
                .forEach(propertyMetadata -> {
                    String qualifiedName = getQualifiedName(parentPropertyName, propertyMetadata.getPropertyName());
                    T child = getChild(node, propertyMetadata);
                    Class<?> childType = resolveType(child);
                    visitor.visitProperty(typeMetadata, propertyMetadata, qualifiedName, child);
                    if (propertyMetadata.getPropertyType() == Nested.class) {
                        walk(child, qualifiedName, visitor);
                    } else if (Map.class.isAssignableFrom(childType)) {
                        handleMap(child, (name, mapChild) -> walk(mapChild, getQualifiedName(qualifiedName, name), visitor));
                    } else if (Iterable.class.isAssignableFrom(childType)) {
                        handleIterable(child, (name, iterableChild) -> walk(iterableChild, getQualifiedName(qualifiedName, name), visitor));
                    }
                });
        }
    }

    private static String getQualifiedName(@Nullable String parentPropertyName, String childPropertyName) {
        return parentPropertyName == null
            ? childPropertyName
            : parentPropertyName + "." + childPropertyName;
    }

    abstract protected void handleMap(T node, BiConsumer<String, T> handler);

    abstract protected void handleIterable(T node, BiConsumer<String, T> handler);

    abstract protected Class<?> resolveType(T type);

    abstract protected T getChild(T parent, PropertyMetadata property);

    static class InstanceTypeMetadataWalker extends AbstractTypeMetadataWalker<Object> {
        public InstanceTypeMetadataWalker(TypeMetadataStore typeMetadataStore) {
            super(typeMetadataStore);
        }

        @Override
        protected Class<?> resolveType(Object value) {
            return value.getClass();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleMap(Object node, BiConsumer<String, Object> handler) {
            ((Map<String, Object>) node).forEach(handler);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleIterable(Object node, BiConsumer<String, Object> handler) {
            int counter = 1;
            for (Object o : (Iterable<Object>) node) {
                handler.accept("$" + counter++, o);
            }
        }

        @Override
        protected Object getChild(Object parent, PropertyMetadata property) {
            try {
                return property.getGetterMethod().invoke(parent);
            } catch (Exception e) {
                // TODO Handle this
                throw new RuntimeException(e);
            }
        }
    }

    static class StaticTypeMetadataWalker extends AbstractTypeMetadataWalker<TypeToken<?>> {
        public StaticTypeMetadataWalker(TypeMetadataStore typeMetadataStore) {
            super(typeMetadataStore);
        }

        @Override
        protected Class<?> resolveType(TypeToken<?> type) {
            return type.getRawType();
        }


        @SuppressWarnings("unchecked")
        @Override
        protected void handleMap(TypeToken<?> node, BiConsumer<String, TypeToken<?>> handler) {
            handler.accept(
                "<key>",
                extractNestedType((TypeToken<Map<?, ?>>) node, Map.class, 1));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleIterable(TypeToken<?> node, BiConsumer<String, TypeToken<?>> handler) {
            handler.accept(
                determinePropertyName(node),
                extractNestedType((TypeToken<? extends Iterable<?>>) node, Iterable.class, 0));
        }

        @Override
        protected TypeToken<?> getChild(TypeToken<?> parent, PropertyMetadata property) {
            return TypeToken.of(property.getGetterMethod().getGenericReturnType());
        }

        private static String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? "<name>"
                : "*";
        }

        private static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
            return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
        }
    }
}
