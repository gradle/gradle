/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.isolated;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;

public class IsolationScheme<INTERFACE, PARAMS> implements TypeParameterInspection<INTERFACE, PARAMS> {
    private final Class<INTERFACE> interfaceType;
    private final Class<? extends PARAMS> noParamsType;
    private final TypeParameterInspection<INTERFACE, PARAMS> typeParameterInspection;

    public IsolationScheme(Class<INTERFACE> interfaceType, Class<PARAMS> paramsType, Class<? extends PARAMS> noParamsType) {
        this.interfaceType = interfaceType;
        this.noParamsType = noParamsType;
        this.typeParameterInspection = new DefaultTypeParameterInspection<>(interfaceType, paramsType, noParamsType);
    }

    /**
     * Determines the parameters type for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Override
    @Nullable
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        return typeParameterInspection.parameterTypeFor(implementationType);
    }

    /**
     * Determines the parameters type found at the given type argument index for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Override
    @Nullable
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex) {
        return typeParameterInspection.parameterTypeFor(implementationType, typeArgumentIndex);
    }

    /**
     * Returns the services available for injection into the implementation instance.
     */
    public ServiceLookup servicesForImplementation(
        @Nullable PARAMS params,
        ServiceLookup allServices,
        Collection<? extends Class<?>> additionalWhiteListedServices
    ) {
        return new ServicesForIsolatedObject(interfaceType, noParamsType, params, allServices, additionalWhiteListedServices);
    }

    private static class ServicesForIsolatedObject implements ServiceLookup {
        private final Class<?> interfaceType;
        private final Class<?> noParamsType;
        private final Collection<? extends Class<?>> additionalWhiteListedServices;
        private final ServiceLookup allServices;
        private final @Nullable Object params;

        public ServicesForIsolatedObject(
            Class<?> interfaceType,
            Class<?> noParamsType,
            @Nullable Object params,
            ServiceLookup allServices,
            Collection<? extends Class<?>> additionalWhiteListedServices
        ) {
            this.interfaceType = interfaceType;
            this.noParamsType = noParamsType;
            this.additionalWhiteListedServices = additionalWhiteListedServices;
            this.allServices = allServices;
            this.params = params;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            if (serviceType instanceof Class) {
                Class<?> serviceClass = Cast.uncheckedNonnullCast(serviceType);
                if (serviceClass.isInstance(params)) {
                    return params;
                }
                if (serviceClass.isAssignableFrom(noParamsType)) {
                    throw new ServiceLookupException(String.format("Cannot query the parameters of an instance of %s that takes no parameters.", interfaceType.getSimpleName()));
                }
                if (serviceClass.isAssignableFrom(ExecOperations.class)) {
                    return allServices.find(ExecOperations.class);
                }
                if (serviceClass.isAssignableFrom(FileSystemOperations.class)) {
                    return allServices.find(FileSystemOperations.class);
                }
                if (serviceClass.isAssignableFrom(ArchiveOperations.class)) {
                    return allServices.find(ArchiveOperations.class);
                }
                if (serviceClass.isAssignableFrom(ObjectFactory.class)) {
                    return allServices.find(ObjectFactory.class);
                }
                if (serviceClass.isAssignableFrom(ProviderFactory.class)) {
                    return allServices.find(ProviderFactory.class);
                }
                if (serviceClass.isAssignableFrom(BuildServiceRegistry.class)) {
                    return allServices.find(BuildServiceRegistry.class);
                }
                if (serviceClass.isAssignableFrom(InternalProblems.class)) {
                    return allServices.find(InternalProblems.class);
                }
                if (serviceClass.isAssignableFrom(ManagedObjectRegistry.class)) {
                    return allServices.find(ManagedObjectRegistry.class);
                }
                for (Class<?> whiteListedService : additionalWhiteListedServices) {
                    if (serviceClass.isAssignableFrom(whiteListedService)) {
                        return allServices.find(whiteListedService);
                    }
                }
            }
            return null;
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                return notFound(serviceType);
            }
            return result;
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return notFound(serviceType);
        }

        private Object notFound(Type serviceType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Services of type ");
            formatter.appendType(serviceType);
            formatter.append(" are not available for injection into instances of type ");
            formatter.appendType(interfaceType);
            formatter.append(".");
            throw new UnknownServiceException(serviceType, formatter.toString());
        }
    }
}
