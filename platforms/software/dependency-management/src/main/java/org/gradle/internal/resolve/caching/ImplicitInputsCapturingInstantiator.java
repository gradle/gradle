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
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

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
 */
@NullMarked
public class ImplicitInputsCapturingInstantiator implements Instantiator {

    private final ServiceLookup delegate;
    private final InstantiatorFactory instantiatorFactory;

    public ImplicitInputsCapturingInstantiator(ServiceLookup delegate, InstantiatorFactory instantiatorFactory) {
        this.delegate = delegate;
        this.instantiatorFactory = instantiatorFactory;
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, @Nullable Object... parameters) throws ObjectInstantiationException {
        return instantiatorFactory.inject(delegate).newInstance(type, parameters);
    }

    public Instantiator capturing(final ImplicitInputRecorder registrar) {
        return new Instantiator() {
            @Override
            public <T> T newInstance(Class<? extends T> type, @Nullable Object... parameters) throws ObjectInstantiationException {
                return instantiatorFactory.inject(capturingRegistry(registrar)).newInstance(type, parameters);
            }
        };
    }

    @Nullable
    public <IN, OUT, SERVICE> ImplicitInputsProvidingService<IN, OUT, SERVICE> findInputCapturingServiceByName(String name) {
        try {
            // TODO: Whenever we allow _user_ services to be injected, this would have to know
            // from which classloader we need to load the service
            return Cast.uncheckedCast(delegate.find(Class.forName(name)));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public ServiceLookup capturingRegistry(ImplicitInputRecorder registrar) {
        return new DefaultCapturingServiceLookup(registrar);
    }

    private class DefaultCapturingServiceLookup implements ServiceLookup {

        private final ImplicitInputRecorder registrar;

        private DefaultCapturingServiceLookup(ImplicitInputRecorder registrar) {
            this.registrar = registrar;
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            return delegate.get(serviceType);
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return delegate.get(serviceType, annotatedWith);
        }

        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            Object service = delegate.find(serviceType);
            if (service instanceof ImplicitInputsProvidingService) {
                return ((ImplicitInputsProvidingService)service).withImplicitInputRecorder(registrar);
            }
            return service;
        }

    }

}
