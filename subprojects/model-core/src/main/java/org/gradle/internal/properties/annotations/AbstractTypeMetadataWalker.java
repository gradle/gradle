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
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class AbstractTypeMetadataWalker<T> implements TypeMetadataWalker<T> {
    private final TypeMetadataStore typeMetadataStore;
    private final Class<? extends Annotation> nestedAnnotation;
    private final Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory;

    private AbstractTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation, Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory) {
        this.typeMetadataStore = typeMetadataStore;
        this.nestedAnnotation = nestedAnnotation;
        this.nestedNodeToQualifiedNameMapFactory = nestedNodeToQualifiedNameMapFactory;
    }

    @Override
    public void walk(T root, TypeMetadataVisitor<T> visitor) {
        Class<?> nodeType = resolveType(root);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        visitor.visitRoot(typeMetadata, root);
        walkChildren(root, typeMetadata, null, visitor, nestedNodeToQualifiedNameMapFactory.get());
    }

    private void walk(T node, String qualifiedName, PropertyMetadata propertyMetadata, TypeMetadataVisitor<T> visitor, Map<T, String> nestedNodesWalkedOnPath) {
        Class<?> nodeType = resolveType(node);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        if (Provider.class.isAssignableFrom(nodeType)) {
            handleProvider(node, child -> walk(child, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath));
        } else if (Map.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            handleMap(node, (name, child) -> walk(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath));
        } else if (Iterable.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            handleIterable(node, (name, child) -> walk(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath));
        } else {
            handleNested(node, typeMetadata, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath);
        }
    }

    private void walkChildren(T node, TypeMetadata typeMetadata, @Nullable String parentQualifiedName, TypeMetadataVisitor<T> visitor, Map<T, String> nestedNodesOnPath) {
        typeMetadata.getPropertiesMetadata().forEach(propertyMetadata -> {
            String childQualifiedName = getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName());
            if (propertyMetadata.getPropertyType() == nestedAnnotation) {
                Optional<T> childOptional = getChild(node, propertyMetadata);
                childOptional.ifPresent(child -> walk(child, childQualifiedName, propertyMetadata, visitor, nestedNodesOnPath));
            } else {
                visitor.visitLeaf(childQualifiedName, propertyMetadata, () -> getChild(node, propertyMetadata).orElse(null));
            }
        });
    }

    private void handleNested(T node, TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeMetadataVisitor<T> visitor, Map<T, String> nestedNodesOnPath) {
        String firstOccurrenceQualifiedName = nestedNodesOnPath.putIfAbsent(node, qualifiedName);
        if (firstOccurrenceQualifiedName != null) {
            onNestedNodeCycle(firstOccurrenceQualifiedName, qualifiedName);
            return;
        }

        visitor.visitNested(typeMetadata, qualifiedName, propertyMetadata, node);
        walkChildren(node, typeMetadata, qualifiedName, visitor, nestedNodesOnPath);
        nestedNodesOnPath.remove(node);
    }

    abstract protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName);

    abstract protected void handleProvider(T node, Consumer<T> handler);

    abstract protected void handleMap(T node, BiConsumer<String, T> handler);

    abstract protected void handleIterable(T node, BiConsumer<String, T> handler);

    abstract protected Class<?> resolveType(T type);

    abstract protected Optional<T> getChild(T parent, PropertyMetadata property);

    private static String getQualifiedName(@Nullable String parentPropertyName, String childPropertyName) {
        return parentPropertyName == null
            ? childPropertyName
            : parentPropertyName + "." + childPropertyName;
    }

    static class InstanceTypeMetadataWalker extends AbstractTypeMetadataWalker<Object> {
        public InstanceTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
            super(typeMetadataStore, nestedAnnotation, IdentityHashMap::new);
        }

        @Override
        protected Class<?> resolveType(Object value) {
            return value.getClass();
        }

        @Override
        protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
            throw new IllegalStateException(String.format("Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.", firstOccurrenceQualifiedName, secondOccurrenceQualifiedName));
        }

        @Override
        protected void handleProvider(Object node, Consumer<Object> handler) {
            handler.accept(((Provider<?>) node).get());
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
        protected Optional<Object> getChild(Object parent, PropertyMetadata property) {
            try {
                return Optional.ofNullable(property.getGetterMethod().invoke(parent));
            } catch (Exception e) {
                // TODO Handle this
                throw new RuntimeException(e);
            }
        }
    }

    static class StaticTypeMetadataWalker extends AbstractTypeMetadataWalker<TypeToken<?>> {
        public StaticTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
            super(typeMetadataStore, nestedAnnotation, HashMap::new);
        }

        @Override
        protected Class<?> resolveType(TypeToken<?> type) {
            return type.getRawType();
        }

        @Override
        protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
            // For Types walk we don't need to do anything on a cycle
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleProvider(TypeToken<?> node, Consumer<TypeToken<?>> handler) {
            handler.accept(extractNestedType((TypeToken<Provider<?>>) node, Provider.class, 0));
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
            TypeToken<?> nestedType = extractNestedType((TypeToken<? extends Iterable<?>>) node, Iterable.class, 0);
            handler.accept(determinePropertyName(nestedType), nestedType);
        }

        @Override
        protected Optional<TypeToken<?>> getChild(TypeToken<?> parent, PropertyMetadata property) {
            return Optional.of(TypeToken.of(property.getGetterMethod().getGenericReturnType()));
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
