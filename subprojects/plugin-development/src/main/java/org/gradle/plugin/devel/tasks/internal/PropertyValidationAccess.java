/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal;

import com.google.common.base.Equivalence;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Named;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.AbstractPropertyNode;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.api.internal.tasks.properties.TypeMetadataStore;
import org.gradle.api.internal.tasks.properties.TypeScheme;
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Class for easy access to property validation from the validator task.
 */
@NonNullApi
public class PropertyValidationAccess {
    private static final PropertyValidationAccess INSTANCE = new PropertyValidationAccess();

    private final List<TypeScheme> typeSchemes;

    private PropertyValidationAccess() {
        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder().displayName("Global services");
        // Should reuse `GlobalScopeServices` here, however this requires a bunch of stuff in order to discover the plugin service registries
        // For now, re-implement the discovery here
        builder.provider(new Object() {
            @SuppressWarnings("unused")
            void configure(ServiceRegistration registration) {
                registration.add(DefaultListenerManager.class, new DefaultListenerManager(Global.class));
                registration.add(DefaultCrossBuildInMemoryCacheFactory.class);
                // TODO: do we need any factories here?
                registration.add(DefaultManagedFactoryRegistry.class, new DefaultManagedFactoryRegistry());
                registration.add(OutputPropertyRoleAnnotationHandler.class);
                registration.add(DefaultInstantiatorFactory.class);
                List<PluginServiceRegistry> pluginServiceFactories = new DefaultServiceLocator(false, getClass().getClassLoader()).getAll(PluginServiceRegistry.class);
                for (PluginServiceRegistry pluginServiceFactory : pluginServiceFactories) {
                    pluginServiceFactory.registerGlobalServices(registration);
                }
            }
        });
        ServiceRegistry services = builder.build();
        this.typeSchemes = services.getAll(TypeScheme.class);
    }

    @SuppressWarnings("unused")
    public static void collectValidationProblems(Class<?> topLevelBean, TypeValidationContext validationContext) {
        INSTANCE.collectTypeValidationProblems(topLevelBean, validationContext);
    }

    private void collectTypeValidationProblems(Class<?> topLevelBean, TypeValidationContext validationContext) {
        // Skip this for now
        if (topLevelBean.equals(TaskInternal.class)) {
            return;
        }

        TypeMetadataStore metadataStore = null;
        for (TypeScheme typeScheme : typeSchemes) {
            if (typeScheme.appliesTo(topLevelBean)) {
                metadataStore = typeScheme.getMetadataStore();
                break;
            }
        }
        if (metadataStore == null) {
            // Don't know about this type
            return;
        }

        Queue<BeanTypeNode<?>> queue = new ArrayDeque<>();
        BeanTypeNodeFactory nodeFactory = new BeanTypeNodeFactory(metadataStore);
        queue.add(nodeFactory.createRootNode(TypeToken.of(topLevelBean)));

        while (!queue.isEmpty()) {
            BeanTypeNode<?> node = queue.remove();
            node.visit(topLevelBean, validationContext, queue, nodeFactory);
        }
    }

    private static class BeanTypeNodeFactory {
        private final TypeMetadataStore metadataStore;

        public BeanTypeNodeFactory(TypeMetadataStore metadataStore) {
            this.metadataStore = metadataStore;
        }

        public BeanTypeNode<?> createRootNode(TypeToken<?> beanType) {
            return new NestedBeanTypeNode(null, null, beanType, metadataStore.getTypeMetadata(beanType.getRawType()));
        }

        public void createAndAddToQueue(BeanTypeNode<?> parentNode, String propertyName, TypeToken<?> beanType, Queue<BeanTypeNode<?>> queue) {
            if (!parentNode.nodeCreatesCycle(beanType)) {
                queue.add(createChild(parentNode, propertyName, beanType));
            }
        }

        private BeanTypeNode<?> createChild(BeanTypeNode<?> parentNode, String propertyName, TypeToken<?> beanType) {
            Class<?> rawType = beanType.getRawType();
            TypeMetadata typeMetadata = metadataStore.getTypeMetadata(rawType);
            if (!typeMetadata.hasAnnotatedProperties()) {
                if (Map.class.isAssignableFrom(rawType)) {
                    return new MapBeanTypeNode(parentNode, propertyName, Cast.uncheckedNonnullCast(beanType), typeMetadata);
                }
                if (Iterable.class.isAssignableFrom(rawType)) {
                    return new IterableBeanTypeNode(parentNode, propertyName, Cast.uncheckedNonnullCast(beanType), typeMetadata);
                }
            }
            return new NestedBeanTypeNode(parentNode, propertyName, beanType, typeMetadata);
        }
    }

    private abstract static class BeanTypeNode<T> extends AbstractPropertyNode<TypeToken<?>> {

        protected BeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String propertyName, TypeToken<? extends T> beanType, TypeMetadata typeMetadata) {
            super(parentNode, propertyName, typeMetadata);
            this.beanType = beanType;
        }

        public abstract void visit(Class<?> topLevelBean, TypeValidationContext validationContext, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory);

        public boolean nodeCreatesCycle(TypeToken<?> childType) {
            return findNodeCreatingCycle(childType, Equivalence.equals()) != null;
        }

        private final TypeToken<? extends T> beanType;

        protected TypeToken<?> extractNestedType(Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            return PropertyValidationAccess.extractNestedType(beanType, parameterizedSuperClass, typeParameterIndex);
        }

        @Override
        protected TypeToken<?> getNodeValue() {
            return beanType;
        }
    }

    private static class NestedBeanTypeNode extends BeanTypeNode<Object> {

        public NestedBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<?> beanType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, beanType, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, TypeValidationContext validationContext, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            TypeMetadata typeMetadata = getTypeMetadata();
            typeMetadata.visitValidationFailures(getPropertyName(), validationContext);
            for (PropertyMetadata propertyMetadata : typeMetadata.getPropertiesMetadata()) {
                String qualifiedPropertyName = getQualifiedPropertyName(propertyMetadata.getPropertyName());
                if (propertyMetadata.getPropertyType().equals(Nested.class)) {
                    TypeToken<?> beanType = unpackProvider(propertyMetadata.getGetterMethod());
                    nodeFactory.createAndAddToQueue(this, qualifiedPropertyName, beanType, queue);
                }
            }
        }

        private static TypeToken<?> unpackProvider(Method method) {
            Class<?> rawType = method.getReturnType();
            TypeToken<?> genericReturnType = TypeToken.of(method.getGenericReturnType());
            if (Provider.class.isAssignableFrom(rawType)) {
                return PropertyValidationAccess.extractNestedType(Cast.<TypeToken<Provider<?>>>uncheckedNonnullCast(genericReturnType), Provider.class, 0);
            }
            return genericReturnType;
        }
    }

    private static class IterableBeanTypeNode extends BeanTypeNode<Iterable<?>> {

        public IterableBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<Iterable<?>> iterableType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, iterableType, typeMetadata);
        }

        private String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? getQualifiedPropertyName("<name>")
                : getPropertyName() + "*";
        }

        @Override
        public void visit(Class<?> topLevelBean, TypeValidationContext validationContext, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            TypeToken<?> nestedType = extractNestedType(Iterable.class, 0);
            nodeFactory.createAndAddToQueue(this, determinePropertyName(nestedType), nestedType, queue);
        }
    }

    private static class MapBeanTypeNode extends BeanTypeNode<Map<?, ?>> {

        public MapBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<Map<?, ?>> mapType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, mapType, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, TypeValidationContext validationContext, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            TypeToken<?> nestedType = extractNestedType(Map.class, 1);
            nodeFactory.createAndAddToQueue(this, getQualifiedPropertyName("<key>"), nestedType, queue);
        }
    }

    private static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
        ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
        return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
    }
}
