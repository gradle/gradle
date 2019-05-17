/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching;

import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.UnknownServiceException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

/**
 * An instantiator which is responsible for allowing the capture of implicit
 * inputs provided by injected services. For this to be possible, a capturing
 * instantiator "session" must be created before the instance is created. This
 * must be done by calling the {@link #capturing(ImplicitInputRecorder)} method
 * which provides a registrar which will record implicit inputs.
 *
 * Not all services have to be capturing. Only services implementing the
 * {@link ImplicitInputsProvidingService} interface are declaring the inputs they generate.
 *
 * If recording inputs is not required, the {@link #newInstance(Class, Object...)}
 * method can still be called, in which case it creates a non capturing instance.
 *
 */
public class ImplicitInputsCapturingInstantiator implements Instantiator {
    private final ServiceRegistry serviceRegistry;
    private final InstantiatorFactory factory;

    public ImplicitInputsCapturingInstantiator(ServiceRegistry serviceRegistry, InstantiatorFactory factory) {
        this.serviceRegistry = serviceRegistry;
        this.factory = factory;
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
        return factory.inject(serviceRegistry).newInstance(type, parameters);
    }

    public Instantiator capturing(final ImplicitInputRecorder registrar) {
        return new Instantiator() {
            @Override
            public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
                return factory.inject(capturingRegistry(registrar)).newInstance(type, parameters);
            }
        };
    }

    public <IN, OUT, SERVICE> ImplicitInputsProvidingService<IN, OUT, SERVICE> findInputCapturingServiceByName(String name) {
        try {
            // TODO: Whenever we allow _user_ services to be injected, this would have to know
            // from which classloader we need to load the service
            return Cast.uncheckedCast(serviceRegistry.find(Class.forName(name)));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public ServiceRegistry capturingRegistry(ImplicitInputRecorder registrar) {
        return new DefaultCapturingServicesRegistry(registrar);
    }

    private class DefaultCapturingServicesRegistry implements ServiceRegistry {
        private final ImplicitInputRecorder registrar;

        private DefaultCapturingServicesRegistry(ImplicitInputRecorder registrar) {
            this.registrar = registrar;
        }

        @Override
        public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
            return serviceRegistry.get(serviceType);
        }

        @Override
        public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
            return serviceRegistry.getAll(serviceType);
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            return serviceRegistry.get(serviceType);
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return serviceRegistry.get(serviceType, annotatedWith);
        }

        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            Object service = serviceRegistry.find(serviceType);
            if (ImplicitInputsProvidingService.class.isInstance(service)) {
                return ((ImplicitInputsProvidingService)service).withImplicitInputRecorder(registrar);
            }
            return service;
        }

        @Override
        public <T> Factory<T> getFactory(Class<T> type) throws UnknownServiceException, ServiceLookupException {
            return serviceRegistry.getFactory(type);
        }

        @Override
        public <T> T newInstance(Class<T> type) throws UnknownServiceException, ServiceLookupException {
            return serviceRegistry.newInstance(type);
        }
    }
}
