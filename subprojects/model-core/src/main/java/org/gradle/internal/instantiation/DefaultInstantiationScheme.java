/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.instantiation;

import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookup;
import sun.reflect.ReflectionFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

class DefaultInstantiationScheme implements InstantiationScheme {
    private final DependencyInjectingInstantiator instantiator;
    private final ConstructorSelector constructorSelector;
    private final Set<Class<? extends Annotation>> injectionAnnotations;

    public DefaultInstantiationScheme(ConstructorSelector constructorSelector, ServiceLookup defaultServices, Set<Class<? extends Annotation>> injectionAnnotations) {
        this.constructorSelector = constructorSelector;
        this.injectionAnnotations = injectionAnnotations;
        this.instantiator = new DependencyInjectingInstantiator(constructorSelector, defaultServices);
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
        return new DefaultInstantiationScheme(constructorSelector, services, injectionAnnotations);
    }

    @Override
    public Instantiator instantiator() {
        return instantiator;
    }

    @Override
    public DeserializationInstantiator deserializationInstantiator() {
        return new DeserializationInstantiator() {
            @Override
            public <T> T newInstance(Class<T> implType, Class<? super T> baseClass) {
                //TODO:instant-execution - use the class generator directly. Also will probably need some caching here
                try {
                    Constructor<?> constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(constructorSelector.forType(implType).getGeneratedClass(), baseClass.getDeclaredConstructor());
                    return implType.cast(constructor.newInstance());
                } catch (InvocationTargetException e) {
                    throw new ObjectInstantiationException(implType, e.getCause());
                } catch (Exception e) {
                    throw new ObjectInstantiationException(implType, e);
                }
            }
        };
    }
}
