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
import java.util.ArrayList;
import java.util.List;

/**
 * A hierarchical {@link ServiceRegistry} implementation.
 *
 * <p>Subclasses can register services by:</p>
 *
 * <ul> <li>Calling {@link #add(org.gradle.api.internal.project.DefaultServiceRegistry.Service)} to register a factory
 * for the service.</li>
 *
 * <li>Calling {@link #add(Class, Object)} to register a service instance.</li>
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
    private final List<Service> services = new ArrayList<Service>();
    private final ServiceRegistry parent;
    private boolean closed;

    public DefaultServiceRegistry() {
        this(null);
    }

    public DefaultServiceRegistry(ServiceRegistry parent) {
        this.parent = parent;
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
                add(new FactoryMethodService(method));
            }
        }
    }

    private void findDecoratorMethods(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getParameterTypes().length == 1
                    && method.getReturnType() != Void.class
                    && method.getParameterTypes()[0].equals(method.getReturnType())) {
                add(new DecoratorMethodService(method));
            }
        }
    }

    protected void add(Service service) {
        services.add(0, service);
    }

    public <T> void add(Class<T> serviceType, final T serviceInstance) {
        add(new FixedInstanceService<T>(serviceType, serviceInstance));
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() method, that
     * method is called to close the service.
     */
    public void close() {
        try {
            new CompositeStoppable(services).stop();
        } finally {
            closed = true;
            services.clear();
        }
    }

    public <T> T get(Class<T> serviceType) throws IllegalArgumentException {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.",
                    serviceType.getSimpleName(), this));
        }

        for (Service service : services) {
            T t = service.getService(serviceType);
            if (t != null) {
                return t;
            }
        }

        if (parent != null) {
            try {
                return parent.get(serviceType);
            } catch (UnknownServiceException e) {
                if (!e.type.equals(serviceType)) {
                    throw e;
                }
                // Ignore
            }
        }

        throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.",
                serviceType.getSimpleName(), this));
    }

    public <T> Factory<? extends T> getFactory(Class<T> type) {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate factory for objects of type %s, as %s has been closed.",
                    type.getSimpleName(), this));
        }

        for (Service service : services) {
            Factory<? extends T> factory = service.getFactory(type);
            if (factory != null) {
                return factory;
            }
        }

        if (parent != null) {
            try {
                return parent.getFactory(type);
            } catch (UnknownServiceException e) {
                if (!e.type.equals(type)) {
                    throw e;
                }
                // Ignore
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

    protected static abstract class Service implements Stoppable {
        final Type serviceType;
        final Class serviceClass;
        Object service;

        Service(Type serviceType) {
            this.serviceType = serviceType;
            serviceClass = toClass(serviceType);
        }

        @Override
        public String toString() {
            return String.format("Service %s", serviceType);
        }

        <T> T getService(Class<T> serviceType) {
            if (!serviceType.isAssignableFrom(this.serviceClass)) {
                return null;
            }
            if (service == null) {
                service = create();
                assert service != null;
            }
            return serviceType.cast(service);
        }

        protected abstract Object create();

        public void stop() {
            try {
                if (service != null) {
                    try {
                        invoke(service.getClass().getMethod("stop"), service);
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                    try {
                        invoke(service.getClass().getMethod("close"), service);
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
            } finally {
                service = null;
            }
        }

        public <T> Factory<? extends T> getFactory(Class<T> elementType) {
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

    private class FactoryMethodService extends Service {
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

    private static class FixedInstanceService<T> extends Service {
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

    private class DecoratorMethodService extends Service {
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
                Class type;
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

    static class UnknownServiceException extends IllegalArgumentException {
        private final Class<?> type;

        UnknownServiceException(Class<?> type, String message) {
            super(message);
            this.type = type;
        }
    }
}
