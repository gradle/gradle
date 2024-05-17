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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.Types;
import org.gradle.internal.reflect.Types.TypeVisitResult;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.process.ExecOperations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

public class IsolationScheme<IMPLEMENTATION, PARAMS> {
    private final Class<IMPLEMENTATION> interfaceType;
    private final Class<PARAMS> paramsType;
    private final Class<? extends PARAMS> noParamsType;

    public IsolationScheme(Class<IMPLEMENTATION> interfaceType, Class<PARAMS> paramsType, Class<? extends PARAMS> noParamsType) {
        this.interfaceType = interfaceType;
        this.paramsType = paramsType;
        this.noParamsType = noParamsType;
    }

    /**
     * Determines the parameters type for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Nullable
    public <T extends IMPLEMENTATION, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        return parameterTypeFor(implementationType, 0);
    }

    /**
     * Determines the parameters type found at the given type argument index for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Nullable
    public <T extends IMPLEMENTATION, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex) {
        if (implementationType == interfaceType) {
            return null;
        }
        Class<P> parametersType = inferParameterType(implementationType, typeArgumentIndex);
        if (parametersType == paramsType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not create the parameters for ");
            formatter.appendType(implementationType);
            formatter.append(": must use a sub-type of ");
            formatter.appendType(parametersType);
            formatter.append(" as the parameters type. Use ");
            formatter.appendType(noParamsType);
            formatter.append(" as the parameters type for implementations that do not take parameters.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (parametersType == noParamsType) {
            return null;
        }
        return parametersType;
    }

    /**
     * Walk the type hierarchy until we find the interface type and keep track the chain of the type parameters.
     *
     * E.g.: For `interface Baz<T>`, interface `Bar<T extends CharSequence> extends Baz<T>` and `class Foo implements Bar<String>`,
     * we'll have mapping `T extends CharSequence -> String` and `T -> String`.
     *
     * When we come to `Baz<T>`, we can then query the mapping for `T` and get `String`.
     */
    @Nonnull
    private <T extends IMPLEMENTATION, P extends PARAMS> Class<P> inferParameterType(Class<T> implementationType, int typeArgumentIndex) {
        AtomicReference<Type> foundType = new AtomicReference<>();
        Map<Type, Type> collectedTypes = new HashMap<>();
        Types.walkTypeHierarchy(implementationType, type -> {
            for (Type genericInterface : type.getGenericInterfaces()) {
                if (collectTypeParameters(genericInterface, foundType, collectedTypes, typeArgumentIndex)) {
                    return TypeVisitResult.TERMINATE;
                }
            }
            Type genericSuperclass = type.getGenericSuperclass();
            if (collectTypeParameters(genericSuperclass, foundType, collectedTypes, typeArgumentIndex)) {
                return TypeVisitResult.TERMINATE;
            }
            return TypeVisitResult.CONTINUE;
        });

        // Note: we don't handle GenericArrayType here, since
        // we don't support arrays as a type of a Parameter anywhere
        Type type = unwrapTypeVariable(foundType.get());
        return type instanceof Class
            ? Cast.uncheckedNonnullCast(type)
            : type instanceof ParameterizedType
            ? Cast.uncheckedNonnullCast(((ParameterizedType) type).getRawType())
            : Cast.uncheckedNonnullCast(paramsType);
    }

    private boolean collectTypeParameters(Type type, AtomicReference<Type> foundType, Map<Type, Type> collectedTypeParameters, int typeArgumentIndex) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType().equals(interfaceType)) {
                Type parameter = parameterizedType.getActualTypeArguments()[typeArgumentIndex];
                foundType.set(collectedTypeParameters.getOrDefault(parameter, parameter));
                return true;
            }
            Type[] actualTypes = parameterizedType.getActualTypeArguments();
            Type[] typeParameters = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
                Type firstActualInTypeChain = collectedTypeParameters.getOrDefault(actualTypes[i], actualTypes[i]);
                collectedTypeParameters.put(typeParameters[i], firstActualInTypeChain);
            }
        }
        return false;
    }

    private Type unwrapTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            Type nextType;
            Queue<Type> queue = new ArrayDeque<>();
            queue.add(type);
            while ((nextType = queue.poll()) != null) {
                for (Type bound : ((TypeVariable<?>) nextType).getBounds()) {
                    if (bound instanceof TypeVariable) {
                        queue.add(bound);
                    } else if (isAssignableFromType(paramsType, bound)) {
                        return bound;
                    }
                }
            }
        }
        return type;
    }

    private static boolean isAssignableFromType(Class<?> clazz, Type type) {
        return (type instanceof Class && clazz.isAssignableFrom((Class<?>) type))
            || (type instanceof ParameterizedType && clazz.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType()));
    }

    /**
     * Returns the services available for injection into the implementation instance.
     */
    public ServiceLookup servicesForImplementation(@Nullable PARAMS params, ServiceLookup allServices) {
        return servicesForImplementation(params, allServices, Collections.emptyList(), c -> false);
    }

    /**
     * Returns the services available for injection into the implementation instance.
     */
    public ServiceLookup servicesForImplementation(@Nullable PARAMS params, ServiceLookup allServices, Collection<? extends Class<?>> additionalWhiteListedServices, Spec<Class<?>> whiteListPolicy) {
        return new ServicesForIsolatedObject(interfaceType, noParamsType, params, allServices, additionalWhiteListedServices, whiteListPolicy);
    }

    private static class ServicesForIsolatedObject implements ServiceLookup {
        private final Class<?> interfaceType;
        private final Class<?> noParamsType;
        private final Collection<? extends Class<?>> additionalWhiteListedServices;
        private final ServiceLookup allServices;
        private final Object params;
        private final Spec<Class<?>> whiteListPolicy;

        public ServicesForIsolatedObject(
            Class<?> interfaceType,
            Class<?> noParamsType,
            @Nullable Object params,
            ServiceLookup allServices,
            Collection<? extends Class<?>> additionalWhiteListedServices,
            Spec<Class<?>> whiteListPolicy
        ) {
            this.interfaceType = interfaceType;
            this.noParamsType = noParamsType;
            this.additionalWhiteListedServices = additionalWhiteListedServices;
            this.allServices = allServices;
            this.params = params;
            this.whiteListPolicy = whiteListPolicy;
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
                for (Class<?> whiteListedService : additionalWhiteListedServices) {
                    if (serviceClass.isAssignableFrom(whiteListedService)) {
                        return allServices.find(whiteListedService);
                    }
                }
                if (whiteListPolicy.isSatisfiedBy(serviceClass)) {
                    return allServices.find(serviceClass);
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
