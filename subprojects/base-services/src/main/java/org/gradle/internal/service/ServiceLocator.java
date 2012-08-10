/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.service;

import org.gradle.internal.Factory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses the Jar service resource specification to locate service implementations.
 */
public class ServiceLocator extends AbstractServiceRegistry {
    private final ClassLoader classLoader;
    private final Map<Class<?>, Object> implementations = new ConcurrentHashMap<Class<?>, Object>();

    public ServiceLocator(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public <T> T doGet(Class<T> serviceType) throws UnknownServiceException {
        synchronized (implementations) {
            T implementation = serviceType.cast(implementations.get(serviceType));
            if (implementation == null) {
                implementation = getFactory(serviceType).create();
                implementations.put(serviceType, implementation);
            }
            return implementation;
        }
    }

    public <T> ServiceFactory<T> getFactory(final Class<T> serviceType) throws UnknownServiceException {
        ServiceFactory<T> factory = findFactory(serviceType);
        if (factory == null) {
            throw new UnknownServiceException(serviceType, String.format("Could not find meta-data resource 'META-INF/services/%s' for service '%s'.", serviceType.getName(), serviceType.getName()));
        }
        return factory;
    }

    /**
     * Locates a factory for a given service. Returns null when no service implementation is available.
     */
    public <T> ServiceFactory<T> findFactory(Class<T> serviceType) {
        Class<? extends T> implementationClass = findServiceImplementationClass(serviceType);
        if (implementationClass == null) {
            return null;
        }
        return new ServiceFactory<T>(serviceType, implementationClass);
    }

    <T> Class<? extends T> findServiceImplementationClass(Class<T> serviceType) {
        String implementationClassName;
        try {
            implementationClassName = findServiceImplementationClassName(serviceType);
        } catch (Exception e) {
            throw new ServiceLookupException(String.format("Could not determine implementation class for service '%s'.", serviceType.getName()), e);
        }
        if (implementationClassName == null) {
            return null;
        }
        try {
            Class<?> implClass = classLoader.loadClass(implementationClassName);
            if (!serviceType.isAssignableFrom(implClass)) {
                throw new RuntimeException(String.format("Implementation class '%s' is not assignable to service class '%s'.", implementationClassName, serviceType.getName()));
            }
            return implClass.asSubclass(serviceType);
        } catch (Throwable t) {
            throw new ServiceLookupException(String.format("Could not load implementation class '%s' for service '%s'.", implementationClassName, serviceType.getName()), t);
        }
    }

    private String findServiceImplementationClassName(Class<?> serviceType) throws IOException {
        String resourceName = "META-INF/services/" + serviceType.getName();
        URL resource = classLoader.getResource(resourceName);
        if (resource == null) {
            return null;
        }

        InputStream inputStream = resource.openStream();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("#.*", "").trim();
                if (line.length() > 0) {
                    return line;
                }
            }
        } finally {
            inputStream.close();
        }
        throw new RuntimeException(String.format("No implementation class for service '%s' specified in resource '%s'.", serviceType.getName(), resource));
    }

    public static class ServiceFactory<T> implements Factory<T> {
        private final Class<T> serviceType;
        private final Class<? extends T> implementationClass;

        public ServiceFactory(Class<T> serviceType, Class<? extends T> implementationClass) {
            this.serviceType = serviceType;
            this.implementationClass = implementationClass;
        }

        public Class<? extends T> getImplementationClass() {
            return implementationClass;
        }

        public T create() {
            return newInstance();
        }

        public T newInstance(Object... params) {
            Instantiator instantiator = new DirectInstantiator();
            try {
                return instantiator.newInstance(implementationClass, params);
            } catch (ObjectInstantiationException t) {
                throw new RuntimeException(String.format("Could not create an implementation of service '%s'.", serviceType.getName()), t);
            }
        }
    }
}
