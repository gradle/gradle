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
import org.gradle.api.internal.parameters.NoneParameters;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.FilteringServiceLookup;
import org.gradle.internal.service.InstanceInjectingServiceLookup;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.process.ExecOperations;
import org.gradle.util.internal.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class IsolationScheme<INTERFACE, PARAMS> implements TypeParameterInspection<INTERFACE, PARAMS> {

    // Route the removed InternalProblems type to the service that fails with an actionable error.
    @SuppressWarnings("deprecation")
    private static final Class<?> DEPRECATED_INTERNAL_PROBLEMS = org.gradle.api.problems.internal.InternalProblems.class;

    private static final Collection<Class<?>> DEFAULT_ALLOWED_SERVICES = Arrays.asList(
        ExecOperations.class,
        FileSystemOperations.class,
        ArchiveOperations.class,
        ObjectFactory.class,
        ProviderFactory.class,
        BuildServiceRegistry.class,
        ProblemsInternal.class,
        ManagedObjectRegistry.class,
        DEPRECATED_INTERNAL_PROBLEMS
    );

    private final Class<INTERFACE> interfaceType;
    private final Class<? extends PARAMS> noParamsType;
    private final TypeParameterInspection<INTERFACE, PARAMS> typeParameterInspection;

    public IsolationScheme(Class<INTERFACE> interfaceType, Class<PARAMS> paramsType, Class<? extends PARAMS> noParamsType) {
        this.interfaceType = interfaceType;
        this.noParamsType = noParamsType;
        this.typeParameterInspection = new DefaultTypeParameterInspection<>(interfaceType, paramsType, noParamsType);
    }

    @Override
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        return typeParameterInspection.parameterTypeFor(implementationType);
    }

    @Override
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex) {
        return typeParameterInspection.parameterTypeFor(implementationType, typeArgumentIndex);
    }

    public <P extends PARAMS> P instantiateParameters(Class<P> parametersType, Function<Class<P>, P> instantiator) {
        if (parametersType == noParamsType) {
            return Cast.uncheckedNonnullCast(NoneParameters.singletonOf(Cast.uncheckedNonnullCast(parametersType)));
        }
        return instantiator.apply(parametersType);
    }

    /**
     * Returns the services available for injection into the implementation instance.
     */
    public ServiceLookup servicesForImplementation(
        PARAMS params,
        ServiceLookup allServices,
        Collection<Class<?>> additionalAllowedServices
    ) {
        return new InstanceInjectingServiceLookup(
            Collections.singleton(params),
            new FilteringServiceLookup(
                allServices,
                CollectionUtils.concat(DEFAULT_ALLOWED_SERVICES, additionalAllowedServices),
                FilteringServiceLookup.FilterAction.denyFilteredServices(serviceType -> {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Services of type ");
                    formatter.appendType(serviceType);
                    formatter.append(" are not available for injection into instances of type ");
                    formatter.appendType(interfaceType);
                    formatter.append(".");
                    return formatter.toString();
                })
            )
        );
    }

}
