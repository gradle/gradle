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

import org.gradle.api.Transformer;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;

/**
 * An {@link Instantiator} that applies JSR-330 style dependency injection.
 */
public class DependencyInjectingInstantiator implements Instantiator {

    private final ServiceRegistry services;
    private final CrossBuildInMemoryCache<Class<?>, CachedConstructor> constructorCache;
    private final ClassGenerator classGenerator;

    public DependencyInjectingInstantiator(ServiceRegistry services, CrossBuildInMemoryCache<Class<?>, CachedConstructor> constructorCache) {
        this.classGenerator = new ClassGenerator() {
            @Override
            public <T> Class<? extends T> generate(Class<T> type) {
                return type;
            }
        };
        this.services = services;
        this.constructorCache = constructorCache;
    }

    public DependencyInjectingInstantiator(ClassGenerator classGenerator, ServiceRegistry services, CrossBuildInMemoryCache<Class<?>, CachedConstructor> constructorCache) {
        this.classGenerator = classGenerator;
        this.services = services;
        this.constructorCache = constructorCache;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        try {
            Constructor<?> constructor = findConstructor(type);
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

    private <T> Constructor<?> findConstructor(final Class<? extends T> type) throws Throwable {
        CachedConstructor cached = constructorCache.get(type, new Transformer<CachedConstructor, Class<?>>() {
            @Override
            public CachedConstructor transform(Class<?> aClass) {
                try {
                    validateType(type);
                    Class<? extends T> implClass = classGenerator.generate(type);
                    Constructor<?> constructor = InjectUtil.selectConstructor(implClass, type);
                    constructor.setAccessible(true);
                    return CachedConstructor.of(constructor);
                } catch (Throwable e) {
                    return CachedConstructor.of(e);
                }
            }
        });
        if (cached.error != null) {
            throw cached.error;
        }
        return cached.constructor;
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

    private static <T> void validateType(Class<T> type) {
        if (type.isInterface() || type.isAnnotation() || type.isEnum()) {
            throw new IllegalArgumentException(String.format("Type %s is not a class.", type.getName()));
        }
        if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is a non-static inner class.", type.getName()));
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is an abstract class.", type.getName()));
        }
    }

    static class CachedConstructor {
        private final Constructor<?> constructor;
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
