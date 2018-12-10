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

package org.gradle.api.internal;

import org.gradle.api.internal.instantiation.ConstructorSelector;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * An {@link Instantiator} that applies dependency injection, delegating to a {@link ConstructorSelector} to decide which constructor to use to create instances.
 */
public class DependencyInjectingInstantiator implements Instantiator {
    private final ServiceRegistry services;
    private final ConstructorSelector constructorSelector;

    public DependencyInjectingInstantiator(ConstructorSelector constructorSelector, ServiceRegistry services) {
        this.services = services;
        this.constructorSelector = constructorSelector;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        try {
            Constructor<?> constructor = findConstructor(type, parameters);
            Object[] resolvedParameters = convertParameters(type, constructor, parameters, null);
            try {
                Object instance = constructor.newInstance(resolvedParameters);
                if (instance instanceof WithServiceRegistry) {
                    ((WithServiceRegistry) instance).setServices(services);
                }
                return type.cast(instance);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (Throwable t) {
            throw new ObjectInstantiationException(type, t);
        }
    }

    private <T> Constructor<?> findConstructor(Class<? extends T> type, Object[] parameters) throws Throwable {
        CachedConstructor constructor = constructorSelector.forParams(type, parameters);
        if (constructor.error != null) {
            throw constructor.error;
        }
        return constructor.constructor;
    }

    private <T> Object[] convertParameters(Class<T> type, Constructor<?> constructor, Object[] parameters, List<Object> injectedServices) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length < parameters.length) {
            throw new IllegalArgumentException(String.format("Too many parameters provided for constructor for class %s. Expected %s, received %s.", type.getName(), parameterTypes.length, parameters.length));
        }
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Object[] resolvedParameters = new Object[parameterTypes.length];
        int pos = 0;
        for (int i = 0; i < resolvedParameters.length; i++) {
            Class<?> targetType = parameterTypes[i];
            if (targetType.isPrimitive()) {
                targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
            }
            Type serviceType = genericTypes[i];
            Object currentParameter;
            if (pos < parameters.length && targetType.isInstance(parameters[pos])) {
                currentParameter = parameters[pos];
                pos++;
            } else {
                currentParameter = services.find(serviceType);
                if (currentParameter != null && injectedServices != null) {
                    injectedServices.add(currentParameter);
                }
            }

            if (currentParameter != null) {
                resolvedParameters[i] = currentParameter;
            } else {
                StringBuilder builder = new StringBuilder(String.format("Unable to determine %s argument #%s:", type.getName(), i + 1));
                if (pos < parameters.length) {
                    builder.append(String.format(" value %s not assignable to type %s", parameters[pos], parameterTypes[i]));
                } else {
                    builder.append(String.format(" missing parameter value of type %s", parameterTypes[i]));
                }
                builder.append(String.format(", or no service of type %s", serviceType));
                throw new IllegalArgumentException(builder.toString());
            }
        }
        if (pos != parameters.length) {
            throw new IllegalArgumentException(String.format("Unexpected parameter provided for constructor for class %s.", type.getName()));
        }
        return resolvedParameters;
    }

    public static class CachedConstructor {
        @Nullable
        private final Constructor<?> constructor;
        @Nullable
        private final Throwable error;

        private CachedConstructor(Constructor<?> constructor, Throwable error) {
            this.constructor = constructor;
            this.error = error;
        }

        public static CachedConstructor of(Constructor<?> ctor) {
            return new CachedConstructor(ctor, null);
        }

        public static CachedConstructor of(Throwable err) {
            return new CachedConstructor(null, err);
        }
    }

    /**
     * An internal interface that can be used by code generators/proxies to indicate that
     * they require a service registry.
     */
    public interface WithServiceRegistry {
        void setServices(ServiceRegistry services);
    }
}
