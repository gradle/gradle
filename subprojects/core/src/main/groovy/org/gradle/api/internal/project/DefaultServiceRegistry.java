/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.internal.Factory;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.util.UncheckedException;

import java.lang.reflect.*;
import java.util.LinkedList;
import java.util.List;

/**
 * A hierarchical {@link ServiceRegistry} implementation.
 *
 * <p>Subclasses can register services by:</p>
 *
 * <li>Calling {@link #add(Class, Object)} to register a service instance.</li>
 *
 * <li>Calling {@link #add(ServiceRegistry)} to register a set of services.</li>
 *
 * <li>Adding a factory method. A factory method should have a name that starts with 'create', take no parameters, and
 * have a non-void return type. For example, <code>protected SomeService createSomeService() { .... }</code>.</li>
 *
 * <li>Adding a decorator method. A decorator method should have a name that starts with 'decorate', take a single
 * parameter, and a have a non-void return type. The before invoking the method, the parameter is located in the parent
 * service registry and then passed to the method.</li>
 *
 * </ul>
 *
 * <p>Service instances are created on demand. {@link #getFactory(Class)} looks for a service instance which implements
 * {@code Factory<T>} where {@code T} is the expected type.</p>.
 *
 * <p>Service registries are arranged in a heirarchy. If a service of a given type cannot be located, the registry uses
 * its parent registry, if any, to locate the service.</p>
 */
public class DefaultServiceRegistry implements ServiceRegistry {
    private final List<Provider> providers = new LinkedList<Provider>();
    private final List<Provider> registeredProviders;
    private final ServiceRegistry parent;
    private boolean closed;

    public DefaultServiceRegistry() {
        this(null);
    }

    public DefaultServiceRegistry(ServiceRegistry parent) {
        this.parent = parent;
        if (parent != null) {
            providers.add(new ParentServices());
        }
        registeredProviders = providers.subList(0, 0);
        for (Class<?> type = getClass(); type != Object.class; type = type.getSuperclass()) {
            findFactoryMethods(type);
            findDecoratorMethods(type);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void findFactoryMethods(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.class) {
                registeredProviders.add(0, new FactoryMethodService(method));
            }
        }
    }

    private void findDecoratorMethods(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getParameterTypes().length == 1
                    && method.getReturnType() != Void.class
                    && method.getParameterTypes()[0].equals(method.getReturnType())) {
                registeredProviders.add(0, new DecoratorMethodService(method));
            }
        }
    }

    /**
     * Adds a set of services to this registry. The given registry is closed when this registry is closed.
     */
    public void add(ServiceRegistry nested) {
        registeredProviders.add(new NestedServices(nested));
    }

    /**
     * Adds a service to this registry. The given object is closed when this registry is closed.
     */
    public <T> void add(Class<T> serviceType, final T serviceInstance) {
        registeredProviders.add(0, new FixedInstanceService<T>(serviceType, serviceInstance));
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() method, that
     * method is called to close the service.
     */
    public void close() {
        try {
            new CompositeStoppable(providers).stop();
        } finally {
            closed = true;
            providers.clear();
        }
    }

    public <T> T get(Class<T> serviceType) throws IllegalArgumentException {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.",
                    serviceType.getSimpleName(), this));
        }

        for (Provider service : providers) {
            T t = service.getService(serviceType);
            if (t != null) {
                return t;
            }
        }

        throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.",
                serviceType.getSimpleName(), this));
    }

    public <T> Factory<T> getFactory(Class<T> type) {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate factory for objects of type %s, as %s has been closed.",
                    type.getSimpleName(), this));
        }

        for (Provider service : providers) {
            Factory<T> factory = service.getFactory(type);
            if (factory != null) {
                return factory;
            }
        }

        throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.",
                type.getSimpleName(), this));
    }

    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw UncheckedException.asUncheckedException(e.getCause());
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    interface Provider extends Stoppable {
        <T> T getService(Class<T> serviceType);

        <T> Factory<T> getFactory(Class<T> type);
    }

    private static abstract class ManagedObjectProvider<T> implements Provider {
        private T instance;

        public T getInstance() {
            if (instance == null) {
                instance = create();
                assert instance != null;
            }
            return instance;
        }

        protected abstract T create();

        public void stop() {
            try {
                if (instance != null) {
                    try {
                        invoke(instance.getClass().getMethod("stop"), instance);
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                    try {
                        invoke(instance.getClass().getMethod("close"), instance);
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
            } finally {
                instance = null;
            }
        }
    }

    private static abstract class SingletonService extends ManagedObjectProvider<Object> {
        final Type serviceType;
        final Class serviceClass;

        SingletonService(Type serviceType) {
            this.serviceType = serviceType;
            serviceClass = toClass(serviceType);
        }

        @Override
        public String toString() {
            return String.format("Service %s", serviceType);
        }

        public <T> T getService(Class<T> serviceType) {
            if (!serviceType.isAssignableFrom(this.serviceClass)) {
                return null;
            }
            return serviceType.cast(getInstance());
        }


        public <T> Factory<T> getFactory(Class<T> elementType) {
            if (!Factory.class.isAssignableFrom(serviceClass)) {
                return null;
            }
            return getFactory(serviceType, elementType);
        }

        private Factory getFactory(Type type, Class elementType) {
            Class c = toClass(type);
            if (!Factory.class.isAssignableFrom(c)) {
                return null;
            }

            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(Factory.class) && parameterizedType.getActualTypeArguments()[0].equals(elementType)) {
                    return getService(Factory.class);
                }
            }

            for (Type interfaceType : c.getGenericInterfaces()) {
                Factory f = getFactory(interfaceType, elementType);
                if (f != null) {
                    return f;
                }
            }

            return null;
        }

        private Class toClass(Type type) {
            if (type instanceof Class) {
                return (Class) type;
            } else {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                return (Class) parameterizedType.getRawType();
            }
        }
    }

    private class FactoryMethodService extends SingletonService {
        private final Method method;

        public FactoryMethodService(Method method) {
            super(method.getGenericReturnType());
            this.method = method;
        }

        @Override
        protected Object create() {
            return invoke(method, DefaultServiceRegistry.this);
        }
    }

    private static class FixedInstanceService<T> extends SingletonService {
        private final T serviceInstance;

        public FixedInstanceService(Class<T> serviceType, T serviceInstance) {
            super(serviceType);
            this.serviceInstance = serviceInstance;
            getService(serviceType);
        }

        @Override
        protected Object create() {
            return serviceInstance;
        }
    }

    private class DecoratorMethodService extends SingletonService {
        private final Method method;

        public DecoratorMethodService(Method method) {
            super(method.getGenericReturnType());
            this.method = method;
        }

        @Override
        protected Object create() {
            Object value;
            if (Factory.class.isAssignableFrom(method.getParameterTypes()[0])) {
                ParameterizedType fatoryType = (ParameterizedType) method.getGenericParameterTypes()[0];
                Type typeArg = fatoryType.getActualTypeArguments()[0];
                Class<?> type;
                if (typeArg instanceof WildcardType) {
                    WildcardType wildcardType = (WildcardType) typeArg;
                    type = (Class) wildcardType.getUpperBounds()[0];
                } else {
                    type = (Class) typeArg;
                }
                value = parent.getFactory(type);
            } else {
                value = parent.get(method.getParameterTypes()[0]);
            }
            return invoke(method, DefaultServiceRegistry.this, value);
        }
    }

    private static class NestedServices extends ManagedObjectProvider<ServiceRegistry> {
        private final ServiceRegistry nested;

        public NestedServices(ServiceRegistry nested) {
            this.nested = nested;
            getInstance();
        }

        @Override
        protected ServiceRegistry create() {
            return nested;
        }

        public <T> Factory<T> getFactory(Class<T> type) {
            try {
                Factory<T> factory = getInstance().getFactory(type);
                assert factory != null;
                assert factory != null : String.format("nested registry returned null for factory type '%s'", type.getName());
                return factory;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(type)) {
                    return null;
                }
                throw e;
            }
        }

        public <T> T getService(Class<T> serviceType) {
            try {
                T service = getInstance().get(serviceType);
                assert service != null : String.format("nested registry returned null for service type '%s'", serviceType.getName());
                return service;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(serviceType)) {
                    return null;
                }
                throw e;
            }
        }
    }

    private class ParentServices implements Provider {
        public <T> Factory<T> getFactory(Class<T> type) {
            try {
                Factory<T> factory = parent.getFactory(type);
                assert factory != null : String.format("parent returned null for factory type '%s'", type.getName());
                return factory;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(type)) {
                    return null;
                }
                throw e;
            }
        }

        public <T> T getService(Class<T> serviceType) {
            try {
                T service = parent.get(serviceType);
                assert service != null : String.format("parent returned null for service type '%s'", serviceType.getName());
                return service;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(serviceType)) {
                    return null;
                }
                throw e;
            }
        }

        public void stop() {
        }
    }
}
