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

package org.gradle.workers.internal;

import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry;
import org.gradle.internal.snapshot.impl.WorkSerializationException;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.util.Set;
import java.util.stream.Collectors;

public class DefaultActionExecutionSpecFactory implements ActionExecutionSpecFactory {
    private final IsolatableFactory isolatableFactory;
    private final IsolatableSerializerRegistry serializerRegistry;

    public DefaultActionExecutionSpecFactory(IsolatableFactory isolatableFactory, IsolatableSerializerRegistry serializerRegistry) {
        this.isolatableFactory = isolatableFactory;
        this.serializerRegistry = serializerRegistry;
    }

    @Override
    public <T extends WorkParameters> TransportableActionExecutionSpec newTransportableSpec(IsolatedParametersActionExecutionSpec<T> spec) {
        return new TransportableActionExecutionSpec(
            spec.getImplementationClass().getName(),
            serializerRegistry.serialize(spec.getIsolatedParams()),
            spec.getClassLoaderStructure(),
            spec.getBaseDir(),
            spec.getProjectCacheDir(),
            spec.getAdditionalWhitelistedServices().stream().map(Class::getName).collect(Collectors.toSet())
        );
    }

    @Override
    public <T extends WorkParameters> IsolatedParametersActionExecutionSpec<T> newIsolatedSpec(
        String displayName,
        Class<? extends WorkAction<T>> implementationClass,
        T params,
        WorkerRequirement workerRequirement,
        Set<Class<?>> additionalWhitelistedServices
    ) {
        ClassLoaderStructure classLoaderStructure = workerRequirement instanceof IsolatedClassLoaderWorkerRequirement
            ? ((IsolatedClassLoaderWorkerRequirement) workerRequirement).getClassLoaderStructure()
            : null;

        return new IsolatedParametersActionExecutionSpec<>(
            implementationClass,
            displayName,
            implementationClass.getName(),
            isolatableFactory.isolate(params),
            classLoaderStructure,
            workerRequirement.getWorkerDirectory(),
            workerRequirement.getProjectCacheDir(),
            additionalWhitelistedServices
        );
    }

    @Override
    public <T extends WorkParameters> SimpleActionExecutionSpec<T> newSimpleSpec(IsolatedParametersActionExecutionSpec<T> spec) {
        T params = Cast.uncheckedCast(spec.getIsolatedParams().isolate());
        return new SimpleActionExecutionSpec<>(spec.getImplementationClass(), params, spec.getAdditionalWhitelistedServices());
    }

    @Override
    public <T extends WorkParameters> SimpleActionExecutionSpec<T> newSimpleSpec(TransportableActionExecutionSpec spec) {
        T params = Cast.uncheckedCast(serializerRegistry.deserialize(spec.getSerializedParameters()).isolate());
        Set<Class<?>> additionalWhitelistedServices = spec.getAdditionalWhitelistedServicesClassNames()
            .stream().map(DefaultActionExecutionSpecFactory::fromClassName)
            .collect(Collectors.toSet());
        return new SimpleActionExecutionSpec<>(Cast.uncheckedCast(fromClassName(spec.getImplementationClassName())), params, additionalWhitelistedServices);
    }

    static Class<?> fromClassName(String className) {
        try {
            return ClassLoaderUtils.classFromContextLoader(className);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work.", e);
        }
    }

}
