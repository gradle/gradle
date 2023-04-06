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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class AbstractTypeMetadataWalker<T, V extends TypeMetadataWalker.TypeMetadataVisitor<T>> implements TypeMetadataWalker<T, V> {
    private final TypeMetadataStore typeMetadataStore;
    private final Class<? extends Annotation> nestedAnnotation;
    private final Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory;

    private AbstractTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation, Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory) {
        this.typeMetadataStore = typeMetadataStore;
        this.nestedAnnotation = nestedAnnotation;
        this.nestedNodeToQualifiedNameMapFactory = nestedNodeToQualifiedNameMapFactory;
    }

    @Override
    public void walk(T root, V visitor) {
        Class<?> nodeType = resolveType(root);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        visitor.visitRoot(typeMetadata, root);
        Map<T, String> nestedNodesOnPath = nestedNodeToQualifiedNameMapFactory.get();
        nestedNodesOnPath.put(root, "<root>");
        walkChildren(root, typeMetadata, null, visitor, nestedNodesOnPath);
    }

    private void walkNested(T node, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, Map<T, String> nestedNodesWalkedOnPath, boolean isElementOfCollection) {
        Class<?> nodeType = resolveType(node);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        if (Provider.class.isAssignableFrom(nodeType)) {
            walkNestedProvider(node, qualifiedName, propertyMetadata, visitor, isElementOfCollection, child -> walkNested(child, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath, isElementOfCollection));
        } else if (Map.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            walkNestedMap(node, qualifiedName, (name, child) -> walkNested(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath, true));
        } else if (Iterable.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            walkNestedIterable(node, qualifiedName, (name, child) -> walkNested(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath, true));
        } else {
            walkNestedBean(node, typeMetadata, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath);
        }
    }

    private void walkNestedBean(T node, TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, Map<T, String> nestedNodesOnPath) {
        String firstOccurrenceQualifiedName = nestedNodesOnPath.putIfAbsent(node, qualifiedName);
        if (firstOccurrenceQualifiedName != null) {
            onNestedNodeCycle(firstOccurrenceQualifiedName, qualifiedName);
            return;
        }

        visitor.visitNested(typeMetadata, qualifiedName, propertyMetadata, node);
        walkChildren(node, typeMetadata, qualifiedName, visitor, nestedNodesOnPath);
        nestedNodesOnPath.remove(node);
    }

    private void walkChildren(T node, TypeMetadata typeMetadata, @Nullable String parentQualifiedName, V visitor, Map<T, String> nestedNodesOnPath) {
        typeMetadata.getPropertiesMetadata().forEach(propertyMetadata -> {
            if (propertyMetadata.getPropertyType() == nestedAnnotation) {
                walkNestedChild(node, getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName()), propertyMetadata, visitor, child -> walkNested(child, getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName()), propertyMetadata, visitor, nestedNodesOnPath, false));
            } else {
                walkLeaf(node, parentQualifiedName, visitor, propertyMetadata);
            }
        });
    }

    abstract protected void walkLeaf(T node, @Nullable String parentQualifiedName, V visitor, PropertyMetadata propertyMetadata);

    abstract protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName);

    abstract protected void walkNestedProvider(T node, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, boolean isElementOfCollection, Consumer<T> handler);

    abstract protected void walkNestedMap(T node, String qualifiedName, BiConsumer<String, T> handler);

    abstract protected void walkNestedIterable(T node, String qualifiedName, BiConsumer<String, T> handler);

    abstract protected void walkNestedChild(T parent, String childQualifiedName, PropertyMetadata propertyMetadata, V visitor, Consumer<T> handler);

    abstract protected @Nullable T getChild(T parent, PropertyMetadata property);

    abstract protected Class<?> resolveType(T type);

    protected TypeMetadata getTypeMetadata(Class<?> type) {
        return typeMetadataStore.getTypeMetadata(type);
    }

    private static String getQualifiedName(@Nullable String parentPropertyName, String childPropertyName) {
        return parentPropertyName == null
            ? childPropertyName
            : parentPropertyName + "." + childPropertyName;
    }

    private static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
        ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
        return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
    }

    static class InstanceTypeMetadataWalker extends AbstractTypeMetadataWalker<Object, InstanceMetadataVisitor> implements InstanceMetadataWalker {

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
        protected void walkNestedProvider(Object node, String qualifiedName, PropertyMetadata propertyMetadata, InstanceMetadataVisitor visitor, boolean isElementOfCollection, Consumer<Object> handler) {
            walkNestedChild(
                () -> ((Provider<?>) node).getOrNull(),
                qualifiedName,
                propertyMetadata,
                visitor,
                isElementOfCollection,
                handler
            );
        }


        @Override
        protected void walkNestedMap(Object node, String qualifiedName, BiConsumer<String, Object> handler) {
            ((Map<?, ?>) node).forEach((key, value) -> {
                checkNotNull(key, "Null keys in nested map '%s' are not allowed.", qualifiedName);
                String stringKey = key.toString();
                checkNotNullNestedCollectionValue(qualifiedName, stringKey, value);
                handler.accept(stringKey, value);
            });
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void walkNestedIterable(Object node, String qualifiedName, BiConsumer<String, Object> handler) {
            int counter = 0;
            for (Object o : (Iterable<Object>) node) {
                String prefix = o instanceof Named ? ((Named) o).getName() : "";
                String name = prefix + "$" + counter++;
                checkNotNullNestedCollectionValue(qualifiedName, name, o);
                handler.accept(name, o);
            }
        }

        @Override
        protected void walkNestedChild(Object parent, String childQualifiedName, PropertyMetadata propertyMetadata, InstanceMetadataVisitor visitor, Consumer<Object> handler) {
            boolean isElementOfCollection = false;
            walkNestedChild(
                () -> getChild(parent, propertyMetadata),
                childQualifiedName,
                propertyMetadata,
                visitor,
                isElementOfCollection,
                handler
            );
        }

        private void walkNestedChild(Supplier<Object> unpacker, String qualifiedName, PropertyMetadata propertyMetadata, InstanceMetadataVisitor visitor, boolean isElementOfCollection, Consumer<Object> handler) {
            Object value;
            try {
                value = unpacker.get();
            } catch (Exception ex) {
                visitor.visitNestedUnpackingError(qualifiedName, ex);
                return;
            }
            if (value != null) {
                handler.accept(value);
            } else if (isElementOfCollection) {
                throw new IllegalStateException(getNullNestedCollectionValueExceptionMessage(qualifiedName));
            } else {
                TypeToken<?> getterType = propertyMetadata.getDeclaredType();
                TypeMetadata typeMetadata = getTypeMetadata(unpackType(getterType).getRawType());
                visitor.visitNested(typeMetadata, qualifiedName, propertyMetadata, null);
            }
        }

        @Override
        protected void walkLeaf(Object node, @Nullable String parentQualifiedName, InstanceMetadataVisitor visitor, PropertyMetadata propertyMetadata) {
            visitor.visitLeaf(node, getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName()), propertyMetadata);
        }

        @SuppressWarnings("unchecked")
        private static TypeToken<?> unpackType(TypeToken<?> type) {
            while (Provider.class.isAssignableFrom(type.getRawType())) {
                type = extractNestedType((TypeToken<Provider<?>>) type, Provider.class, 0);
            }
            return type;
        }

        @Override
        protected @Nullable Object getChild(Object parent, PropertyMetadata property) {
            return property.getPropertyValue(parent);
        }

        private static void checkNotNullNestedCollectionValue(@Nullable String parentQualifiedName, String name, @Nullable Object value) {
            if (value == null) {
                throw new IllegalStateException(getNullNestedCollectionValueExceptionMessage(getQualifiedName(parentQualifiedName, name)));
            }
        }

        private static String getNullNestedCollectionValueExceptionMessage(String qualifiedName) {
            return String.format("Null value is not allowed for the nested collection property '%s'", qualifiedName);
        }
    }

    static class StaticTypeMetadataWalker extends AbstractTypeMetadataWalker<TypeToken<?>, StaticMetadataVisitor> implements StaticMetadataWalker {
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
        protected void walkNestedProvider(TypeToken<?> node, String qualifiedName, PropertyMetadata propertyMetadata, StaticMetadataVisitor visitor, boolean isElementOfCollection, Consumer<TypeToken<?>> handler) {
            handler.accept(extractNestedType((TypeToken<Provider<?>>) node, Provider.class, 0));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void walkNestedMap(TypeToken<?> node, String qualifiedName, BiConsumer<String, TypeToken<?>> handler) {
            handler.accept(
                "<key>",
                extractNestedType((TypeToken<Map<?, ?>>) node, Map.class, 1));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void walkNestedIterable(TypeToken<?> node, String qualifiedName, BiConsumer<String, TypeToken<?>> handler) {
            TypeToken<?> nestedType = extractNestedType((TypeToken<? extends Iterable<?>>) node, Iterable.class, 0);
            handler.accept(determinePropertyName(nestedType), nestedType);
        }

        @Override
        protected void walkNestedChild(TypeToken<?> parent, String childQualifiedName, PropertyMetadata propertyMetadata, StaticMetadataVisitor visitor, Consumer<TypeToken<?>> handler) {
            handler.accept(getChild(parent, propertyMetadata));
        }

        @Override
        protected void walkLeaf(TypeToken<?> node, @Nullable String parentQualifiedName, StaticMetadataVisitor visitor, PropertyMetadata propertyMetadata) {
        }

        @Override
        protected TypeToken<?> getChild(TypeToken<?> parent, PropertyMetadata property) {
            return property.getDeclaredType();
        }

        private static String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? "<name>"
                : "*";
        }
    }
}
