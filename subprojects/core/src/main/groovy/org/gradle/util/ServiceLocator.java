/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.internal.Instantiator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Uses the Jar service resource specification to locate service implementations.
 */
public class ServiceLocator {
    /**
     * Locates an implementation for a given service. Returns null when no service implementation is available.
     */
    public <T> T findServiceImplementation(Class<T> serviceType, ClassLoader classLoader, Object... params) {
        try {
            Class<? extends T> implementationClass = findServiceImplementationClass(serviceType, classLoader);
            if (implementationClass == null) {
                return null;
            }
            Instantiator instantiator = new DirectInstantiator();
            return instantiator.newInstance(implementationClass, params);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("Could not create an implementation of service '%s'.", serviceType.getName()), t);
        }
    }

    /**
     * Locates an implementation for a given service. Does not return null.
     */
    public <T> T getServiceImplementation(Class<T> serviceType, ClassLoader classLoader, Object... params) {
        T implementation = findServiceImplementation(serviceType, classLoader, params);
        if (implementation == null) {
            throw new RuntimeException(String.format("No implementation class specified for service '%s'.", serviceType.getName()));
        }
        return implementation;
    }

    /**
     * Locates an implementation class for a given service. Returns null when no service implementation is available.
     */
    public <T> Class<? extends T> findServiceImplementationClass(Class<T> serviceType, ClassLoader classLoader) {
        String implementationClassName;
        try {
            implementationClassName = findServiceImplementationClassName(serviceType, classLoader);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not determine implementation class for service '%s'.", serviceType.getName()), e);
        }
        try {
            if (implementationClassName == null) {
                return null;
            }
            Class<?> implClass = classLoader.loadClass(implementationClassName);
            if (!serviceType.isAssignableFrom(implClass)) {
                throw new RuntimeException(String.format("Implementation class '%s' is not assignable to service class '%s'.", implementationClassName, serviceType.getName()));
            }
            return implClass.asSubclass(serviceType);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("Could not load implementation class '%s' for service '%s'.", implementationClassName, serviceType.getName()), t);
        }
    }

    private String findServiceImplementationClassName(Class<?> serviceType, ClassLoader classLoader) throws IOException {
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
}
