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

import com.google.common.reflect.TypeToken;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.process.ExecOperations;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
        ParameterizedType superType = (ParameterizedType) TypeToken.of(implementationType).getSupertype(interfaceType).getType();
        Class<P> parametersType = Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[typeArgumentIndex]).getRawType());
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
     * Returns the services available for injection into the implementation instance.
     */
    public ServiceLookup servicesForImplementation(@Nullable PARAMS params, ServiceLookup allServices) {
        return new ServicesForIsolatedObject(interfaceType, params, allServices);
    }

    private static class ServicesForIsolatedObject implements ServiceLookup {
        private final Class<?> interfaceType;
        private final ServiceLookup allServices;
        private final Object params;

        public ServicesForIsolatedObject(Class<?> interfaceType, @Nullable Object params, ServiceLookup allServices) {
            this.interfaceType = interfaceType;
            this.allServices = allServices;
            this.params = params;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            if (serviceType instanceof Class) {
                Class<?> serviceClass = (Class) serviceType;
                if (serviceClass.isAssignableFrom(ExecOperations.class)) {
                    return allServices.find(ExecOperations.class);
                }
                if (serviceClass.isAssignableFrom(FileSystemOperations.class)) {
                    return allServices.find(FileSystemOperations.class);
                }
                if (serviceClass.isInstance(params)) {
                    return params;
                }
            }
            return null;
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                notFound(serviceType);
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
