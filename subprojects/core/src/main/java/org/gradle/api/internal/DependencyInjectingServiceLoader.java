/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.base.Function;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistry;

import java.util.List;

import static com.google.common.collect.Iterables.transform;

/**
 * Service loader that applies JSR-330 style dependency injection.
 */
public class DependencyInjectingServiceLoader {

    private final ServiceRegistry serviceRegistry;

    public DependencyInjectingServiceLoader(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Lazily instantiates the available service type providers.
     */
    public <T> Iterable<T> load(Class<T> serviceType, ClassLoader classLoader) {
        final Instantiator instantiator = dependencyInjectingInstantiator();
        return transform(
            implementationsOf(serviceType, classLoader),
            new Function<Class<? extends T>, T>() {
                @Override
                public T apply(Class<? extends T> serviceImplementation) {
                    return instantiator.newInstance(serviceImplementation);
                }
            });
    }

    private <T> List<Class<? extends T>> implementationsOf(Class<T> serviceType, ClassLoader classLoader) {
        return new DefaultServiceLocator(classLoader).implementationsOf(serviceType);
    }

    private Instantiator dependencyInjectingInstantiator() {
        return serviceRegistry.get(InstantiatorFactory.class).inject(serviceRegistry);
    }
}
