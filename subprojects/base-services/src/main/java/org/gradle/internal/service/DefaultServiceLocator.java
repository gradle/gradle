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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uses the Jar service resource specification to locate service implementations.
 */
public class DefaultServiceLocator implements ServiceLocator {
    private final List<ClassLoader> classLoaders;

    public DefaultServiceLocator(ClassLoader... classLoaders) {
        this.classLoaders = Arrays.asList(classLoaders);
    }

    @Override
    public <T> T get(Class<T> serviceType) throws UnknownServiceException {
        return getFactory(serviceType).create();
    }

    @Override
    public <T> List<T> getAll(Class<T> serviceType) throws UnknownServiceException {
        return find(serviceType, false);
    }

    @Override
    public <T> List<T> getAllLenient(Class<T> serviceType) throws UnknownServiceException {
        return find(serviceType, true);
    }

    @Override
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
    @Override
    public <T> ServiceFactory<T> findFactory(Class<T> serviceType) {
        List<ServiceFactory<T>> factories = findFactoriesForServiceType(serviceType, false);
        if (factories.isEmpty()) {
            return null;
        }
        return factories.get(0);
    }

    /**
     * Locates and class load implementation classes for a given service.
     */
    public <T> List<Class<? extends T>> implementationsOf(Class<T> serviceType) {
        return implementationsOf(serviceType, false);
    }

    private <T> List<Class<? extends T>> implementationsOf(Class<T> serviceType, boolean lenient) {
        try {
            return findServiceImplementations(serviceType, lenient);
        } catch (ServiceLookupException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceLookupException(String.format("Could not determine implementation classes for service '%s'.", serviceType.getName()), e);
        }
    }

    private <T> List<T> find(Class<T> serviceType, boolean lenient) {
        List<T> services = new ArrayList<T>();
        List<ServiceFactory<T>> factories = findFactoriesForServiceType(serviceType, lenient);
        for (ServiceFactory<T> factory : factories) {
            T service = factory.create();
            if (service != null) {
                services.add(service);
            }
        }
        return services;
    }

    private <T> List<ServiceFactory<T>> findFactoriesForServiceType(Class<T> serviceType, boolean lenient) {
        return factoriesFor(serviceType, implementationsOf(serviceType, lenient), lenient);
    }

    private <T> List<ServiceFactory<T>> factoriesFor(Class<T> serviceType, List<Class<? extends T>> implementationClasses, boolean lenient) {
        List<ServiceFactory<T>> factories = new ArrayList<ServiceFactory<T>>();
        for (Class<? extends T> implementationClass : implementationClasses) {
            factories.add(new ServiceFactory<T>(serviceType, implementationClass, lenient));
        }
        return factories;
    }

    private <T> List<Class<? extends T>> findServiceImplementations(Class<T> serviceType, boolean lenient) throws IOException {
        String resourceName = "META-INF/services/" + serviceType.getName();
        Set<String> implementationClassNames = new HashSet<String>();
        List<Class<? extends T>> implementations = new ArrayList<Class<? extends T>>();
        for (ClassLoader classLoader : classLoaders) {
            Enumeration<URL> resources = classLoader.getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                List<String> implementationClassNamesFromResource;
                try {
                    implementationClassNamesFromResource = extractImplementationClassNames(resource);
                    if (implementationClassNamesFromResource.isEmpty()) {
                        throw new RuntimeException(String.format("No implementation class for service '%s' specified.", serviceType.getName()));
                    }
                } catch (Throwable e) {
                    if (lenient) {
                        continue;
                    } else {
                        throw new ServiceLookupException(String.format("Could not determine implementation class for service '%s' specified in resource '%s'.", serviceType.getName(), resource), e);
                    }
                }

                for (String implementationClassName : implementationClassNamesFromResource) {
                    if (implementationClassNames.add(implementationClassName)) {
                        try {
                            Class<?> implClass = classLoader.loadClass(implementationClassName);
                            if (!serviceType.isAssignableFrom(implClass)) {
                                throw new RuntimeException(String.format("Implementation class '%s' is not assignable to service class '%s'.", implementationClassName, serviceType.getName()));
                            }
                            implementations.add(implClass.asSubclass(serviceType));
                        } catch (Throwable e) {
                            if (!lenient) {
                                throw new ServiceLookupException(String.format("Could not load implementation class '%s' for service '%s' specified in resource '%s'.", implementationClassName, serviceType.getName(), resource), e);
                            }
                        }
                    }
                }
            }
        }
        return implementations;
    }

    private List<String> extractImplementationClassNames(URL resource) throws IOException {
        InputStream inputStream = resource.openStream();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            List<String> implementationClassNames = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("#.*", "").trim();
                if (line.length() > 0) {
                    implementationClassNames.add(line);
                }
            }
            return implementationClassNames;
        } finally {
            inputStream.close();
        }
    }

    public static class ServiceFactory<T> implements Factory<T> {
        private final Class<T> serviceType;
        private final Class<? extends T> implementationClass;
        private final boolean lenient;

        public ServiceFactory(Class<T> serviceType, Class<? extends T> implementationClass, boolean lenient) {
            this.serviceType = serviceType;
            this.implementationClass = implementationClass;
            this.lenient = lenient;
        }

        public Class<? extends T> getImplementationClass() {
            return implementationClass;
        }

        public T create() {
            return newInstance();
        }

        public T newInstance(Object... params) {
            try {
                return DirectInstantiator.instantiate(implementationClass, params);
            } catch (Throwable t) {
                if (lenient) {
                    return null;
                } else {
                    throw new RuntimeException(String.format("Could not create an implementation of service '%s'.", serviceType.getName()), t);
                }
            }
        }
    }
}
