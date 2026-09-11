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

package org.gradle.internal.instantiation.generator;

import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.cache.Cache;
import org.gradle.cache.internal.ClassCacheFactory;
import org.gradle.internal.instantiation.DeserializationInstantiator;
import org.gradle.internal.instantiation.InjectedServicesPolicy;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.generator.ClassGenerator.SerializationConstructor;
import org.gradle.internal.service.ServiceLookup;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

class DefaultInstantiationScheme implements InstantiationScheme {
    private final DependencyInjectingInstantiator instantiator;
    private final ConstructorSelector constructorSelector;
    private final Set<Class<? extends Annotation>> injectionAnnotations;
    private final Cache<Class<?>, SerializationConstructor<?>> deserializationConstructorCache;
    private final DeserializationInstantiator deserializationInstantiator;
    private final ClassGenerator classGenerator;
    private final ServiceLookup services;
    private final InjectedServicesInspector injectedServicesInspector;
    @Nullable
    private final InjectedServicesPolicy injectedServicesPolicy;

    public DefaultInstantiationScheme(
        ConstructorSelector constructorSelector,
        ClassGenerator classGenerator,
        ServiceLookup services,
        Set<Class<? extends Annotation>> injectionAnnotations,
        ClassCacheFactory cacheFactory
    ) {
        this(constructorSelector, classGenerator, services, injectionAnnotations, cacheFactory.newClassCache());
    }

    public DefaultInstantiationScheme(
        ConstructorSelector constructorSelector,
        ClassGenerator classGenerator,
        ServiceLookup services,
        Set<Class<? extends Annotation>> injectionAnnotations,
        Cache<Class<?>, SerializationConstructor<?>> deserializationConstructorCache
    ) {
        this(constructorSelector, classGenerator, services, injectionAnnotations, deserializationConstructorCache, null);
    }

    private DefaultInstantiationScheme(
        ConstructorSelector constructorSelector,
        ClassGenerator classGenerator,
        ServiceLookup services,
        Set<Class<? extends Annotation>> injectionAnnotations,
        Cache<Class<?>, SerializationConstructor<?>> deserializationConstructorCache,
        @Nullable InjectedServicesPolicy injectedServicesPolicy
    ) {
        this.classGenerator = classGenerator;
        this.services = services;
        this.instantiator = new DependencyInjectingInstantiator(constructorSelector, services);
        this.constructorSelector = constructorSelector;
        this.injectionAnnotations = injectionAnnotations;
        this.deserializationConstructorCache = deserializationConstructorCache;
        this.deserializationInstantiator = new DefaultDeserializationInstantiator(classGenerator, services, instantiator, deserializationConstructorCache);
        this.injectedServicesInspector = new InjectedServicesInspector(constructorSelector);
        this.injectedServicesPolicy = injectedServicesPolicy;
    }

    @Override
    public Set<Class<? extends Annotation>> getInjectionAnnotations() {
        return injectionAnnotations;
    }

    @Override
    public <T> InstanceFactory<T> forType(Class<T> type) {
        InstanceFactory<T> factory = instantiator.factoryFor(type);
        if (injectedServicesPolicy != null) {
            injectedServicesPolicy.declaredInjectedServices(type, injectedServicesInspector.declaredInjectedServices(type, classGenerator.generate(type)));
        }
        return factory;
    }

    @Override
    public InstantiationScheme withServices(ServiceLookup services) {
        return new DefaultInstantiationScheme(constructorSelector, classGenerator, services, injectionAnnotations, deserializationConstructorCache, injectedServicesPolicy);
    }

    @Override
    public InstantiationScheme withInjectedServicesPolicy(InjectedServicesPolicy policy) {
        return new DefaultInstantiationScheme(constructorSelector, classGenerator, services, injectionAnnotations, deserializationConstructorCache, policy);
    }

    @Override
    public InstanceGenerator instantiator() {
        return instantiator;
    }

    @Override
    public DeserializationInstantiator deserializationInstantiator() {
        return deserializationInstantiator;
    }

    private static class DefaultDeserializationInstantiator implements DeserializationInstantiator {
        private final ClassGenerator classGenerator;
        private final ServiceLookup services;
        private final InstanceGenerator nestedGenerator;
        private final Cache<Class<?>, SerializationConstructor<?>> constructorCache;

        public DefaultDeserializationInstantiator(ClassGenerator classGenerator, ServiceLookup services, InstanceGenerator nestedGenerator, Cache<Class<?>, SerializationConstructor<?>> constructorCache) {
            this.classGenerator = classGenerator;
            this.services = services;
            this.nestedGenerator = nestedGenerator;
            this.constructorCache = constructorCache;
        }

        @Override
        public <T> Class<? extends T> getGeneratedType(Class<T> implType) {
            return classGenerator.generate(implType).getGeneratedClass();
        }

        @Override
        public <T> T newInstance(Class<T> implType, Class<? super T> baseClass) {
            // TODO - The baseClass can be inferred from the implType, so attach the serialization constructor onto the GeneratedClass rather than parameterizing and caching here
            try {
                SerializationConstructor<?> constructor = serializationConstructorFor(implType, baseClass);
                return implType.cast(constructor.newInstance(services, nestedGenerator));
            } catch (InvocationTargetException e) {
                throw new ObjectInstantiationException(implType, e.getCause());
            } catch (Exception e) {
                throw new ObjectInstantiationException(implType, e);
            }
        }

        private <T> SerializationConstructor<?> serializationConstructorFor(Class<T> implType, Class<? super T> baseClass) {
            return constructorCache.get(
                implType,
                () -> classGenerator.generate(implType).getSerializationConstructor(baseClass)
            );
        }
    }
}
