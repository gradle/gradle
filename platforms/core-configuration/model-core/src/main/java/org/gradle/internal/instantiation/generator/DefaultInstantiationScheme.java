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
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.instantiation.DeserializationInstantiator;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.service.ServiceLookup;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

class DefaultInstantiationScheme implements InstantiationScheme {
    private final DependencyInjectingInstantiator instantiator;
    private final ConstructorSelector constructorSelector;
    private final Set<Class<? extends Annotation>> injectionAnnotations;
    private final CrossBuildInMemoryCache<Class<?>, ClassGenerator.SerializationConstructor<?>> deserializationConstructorCache;
    private final DeserializationInstantiator deserializationInstantiator;
    private final ClassGenerator classGenerator;

    public DefaultInstantiationScheme(ConstructorSelector constructorSelector, ClassGenerator classGenerator, ServiceLookup defaultServices, Set<Class<? extends Annotation>> injectionAnnotations, CrossBuildInMemoryCacheFactory cacheFactory) {
        this(constructorSelector, classGenerator, defaultServices, injectionAnnotations, cacheFactory.newClassCache());
    }

    private DefaultInstantiationScheme(ConstructorSelector constructorSelector, ClassGenerator classGenerator, ServiceLookup defaultServices, Set<Class<? extends Annotation>> injectionAnnotations, CrossBuildInMemoryCache<Class<?>, ClassGenerator.SerializationConstructor<?>> deserializationConstructorCache) {
        this.classGenerator = classGenerator;
        this.instantiator = new DependencyInjectingInstantiator(constructorSelector, defaultServices);
        this.constructorSelector = constructorSelector;
        this.injectionAnnotations = injectionAnnotations;
        this.deserializationConstructorCache = deserializationConstructorCache;
        this.deserializationInstantiator = new DefaultDeserializationInstantiator(classGenerator, defaultServices, instantiator, deserializationConstructorCache);
    }

    @Override
    public Set<Class<? extends Annotation>> getInjectionAnnotations() {
        return injectionAnnotations;
    }

    @Override
    public <T> InstanceFactory<T> forType(Class<T> type) {
        return instantiator.factoryFor(type);
    }

    @Override
    public InstantiationScheme withServices(ServiceLookup services) {
        return new DefaultInstantiationScheme(constructorSelector, classGenerator, services, injectionAnnotations, deserializationConstructorCache);
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
        private final CrossBuildInMemoryCache<Class<?>, ClassGenerator.SerializationConstructor<?>> constructorCache;

        public DefaultDeserializationInstantiator(ClassGenerator classGenerator, ServiceLookup services, InstanceGenerator nestedGenerator, CrossBuildInMemoryCache<Class<?>, ClassGenerator.SerializationConstructor<?>> constructorCache) {
            this.classGenerator = classGenerator;
            this.services = services;
            this.nestedGenerator = nestedGenerator;
            this.constructorCache = constructorCache;
        }

        @Override
        public <T> T newInstance(Class<T> implType, Class<? super T> baseClass) {
            // TODO - The baseClass can be inferred from the implType, so attach the serialization constructor onto the GeneratedClass rather than parameterizing and caching here
            try {
                ClassGenerator.SerializationConstructor<?> constructor = constructorCache.get(implType, () -> classGenerator.generate(implType).getSerializationConstructor(baseClass));
                return implType.cast(constructor.newInstance(services, nestedGenerator));
            } catch (InvocationTargetException e) {
                throw new ObjectInstantiationException(implType, e.getCause());
            } catch (Exception e) {
                throw new ObjectInstantiationException(implType, e);
            }
        }
    }
}
