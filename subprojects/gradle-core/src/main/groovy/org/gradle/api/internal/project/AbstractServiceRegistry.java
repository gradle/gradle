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

import java.util.List;
import java.util.ArrayList;

public class AbstractServiceRegistry implements ServiceRegistry {
    private final List<Service> services = new ArrayList<Service>();
    private final ServiceRegistry parent;

    public AbstractServiceRegistry() {
        this(null);
    }

    public AbstractServiceRegistry(ServiceRegistry parent) {
        this.parent = parent;
    }

    protected void add(Service service) {
        services.add(0, service);
    }

    protected <T> void add(Class<T> serviceType, final T serviceInstance) {
        add(new Service(serviceType) {
            @Override
            protected Object create() {
                return serviceInstance;
            }
        });
    }

    public <T> T get(Class<T> serviceType) throws IllegalArgumentException {
        for (Service service : services) {
            T t = service.getService(serviceType);
            if (t != null) {
                return t;
            }
        }

        if (parent != null) {
            return parent.get(serviceType);
        }

        throw new IllegalArgumentException(String.format("No service of type %s available.",
                serviceType.getSimpleName()));
    }

    protected abstract class Service {
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
    }
}
