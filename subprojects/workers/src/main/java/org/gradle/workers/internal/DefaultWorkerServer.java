/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.reflect.TypeToken;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.workers.WorkerExecution;
import org.gradle.workers.WorkerParameters;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class DefaultWorkerServer implements WorkerProtocol {
    private final ServiceRegistry serviceRegistry;

    @Inject
    public DefaultWorkerServer(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        try {
            Class<? extends WorkerExecution> implementationClass = Cast.uncheckedCast(spec.getImplementationClass());
            Instantiator instantiator = serviceRegistry.get(InstantiatorFactory.class).inject(new ParameterServiceLookup(serviceRegistry, spec.getParameters()));
            WorkerExecution execution = instantiator.newInstance(implementationClass);
            execution.execute();
            if (execution instanceof ProvidesWorkResult) {
                return ((ProvidesWorkResult) execution).getWorkResult();
            } else {
                return DefaultWorkResult.SUCCESS;
            }
        } catch (Throwable t) {
            return new DefaultWorkResult(true, t);
        }
    }

    @Override
    public String toString() {
        return "DefaultWorkerServer{}";
    }

    private static class ParameterServiceLookup implements ServiceLookup {
        private final ServiceLookup delegate;
        private final WorkerParameters parameters;

        public ParameterServiceLookup(ServiceLookup delegate, WorkerParameters parameters) {
            this.delegate = delegate;
            this.parameters = parameters;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            TypeToken<?> serviceTypeToken = TypeToken.of(serviceType);
            if (serviceTypeToken.isSupertypeOf(parameters.getClass())) {
                return parameters;
            }
            return delegate.get(serviceType);
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            return find(serviceType);
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return delegate.get(serviceType, annotatedWith);
        }
    }
}
