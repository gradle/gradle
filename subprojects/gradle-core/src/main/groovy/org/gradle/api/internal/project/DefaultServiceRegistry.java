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

import org.gradle.util.UncheckedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A hierarchical {@link ServiceRegistry} implementation. Subclasses can register services by:
 *
 * <ul> <li>Calling {@link #add(org.gradle.api.internal.project.DefaultServiceRegistry.Service)} to register a factory
 * for the service.</li>
 *
 * <li>Calling {@link #add(Class, Object)} to register a service instance.</li>
 *
 * <li>Adding a factory method. A factory method should have a name that starts with 'create', take no parameters, and
 * have a non-void return type. For example, <code>protected SomeService createSomeService() { .... }</code>.</li>
 *
 * <li>Adding a decorator method. A decorator method should have a name that starts with 'decorate', take a single parameter, and a have a non-void return type. 
 *
 *  </ul>
 *
 * <p>Service instances are created on demand. If a service of a given type cannot be located, the registry uses its
 * parent registry, if any, to locate the service.</p>
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
        findFactoryMethods();
        findDecoratorMethods();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void findFactoryMethods() {
        for (Method method : getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.class) {
                add(new FactoryMethodService(method));
            }
        }
    }

    private void findDecoratorMethods() {
        for (Method method : getClass().getDeclaredMethods()) {
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
            for (Service service : services) {
                service.close();
            }
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

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw UncheckedException.asUncheckedException(e.getCause());
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    protected static abstract class Service {
        final Class<?> serviceType;
        Object service;

        Service(Class<?> serviceType) {
            this.serviceType = serviceType;
        }

        <T> T getService(Class<T> serviceType) {
            if (!serviceType.isAssignableFrom(this.serviceType)) {
                return null;
            }
            if (service == null) {
                service = create();
                assert service != null;
            }
            return serviceType.cast(service);
        }

        protected abstract Object create();

        public void close() {
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
    }

    private class FactoryMethodService extends Service {
        private final Method method;

        public FactoryMethodService(Method method) {
            super(method.getReturnType());
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
            super(method.getReturnType());
            this.method = method;
        }

        @Override
        protected Object create() {
            return invoke(method, DefaultServiceRegistry.this, parent.get(method.getParameterTypes()[0]));
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
