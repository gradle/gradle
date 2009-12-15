/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

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
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void findFactoryMethods() {
        for (Method method : getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("create") && method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.class) {
                add(new FactoryMethodService(method));
            }
        }
    }

    protected void add(Service service) {
        services.add(0, service);
    }

    public <T> void add(Class<T> serviceType, final T serviceInstance) {
        add(new FixedInstanceService<T>(serviceType, serviceInstance));
    }

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
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }

        throw new IllegalArgumentException(String.format("No service of type %s available in %s.",
                serviceType.getSimpleName(), this));
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new GradleException(e);
        } catch (Exception e) {
            throw new GradleException(e);
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
                    invoke(service.getClass().getMethod("close"), service);
                }
            } catch (NoSuchMethodException e) {
                // ignore
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
}
