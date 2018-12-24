/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.internal.instantiation.ConstructorSelector;
import org.gradle.api.internal.instantiation.SelectedConstructor;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.text.TreeFormatter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

/**
 * An {@link Instantiator} that applies dependency injection, delegating to a {@link ConstructorSelector} to decide which constructor to use to create instances.
 */
public class DependencyInjectingInstantiator implements Instantiator {
    private final ClassGenerator classGenerator;
    private final ServiceRegistry services;
    private final ConstructorSelector constructorSelector;

    public DependencyInjectingInstantiator(ConstructorSelector constructorSelector, ClassGenerator classGenerator, ServiceRegistry services) {
        this.classGenerator = classGenerator;
        this.services = services;
        this.constructorSelector = constructorSelector;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        try {
            SelectedConstructor constructor = findConstructor(type, parameters);
            Object[] resolvedParameters = convertParameters(type, constructor, parameters);
            Object instance;
            try {
                instance = classGenerator.newInstance(constructor.getConstructor(), services, this, resolvedParameters);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            return type.cast(instance);
        } catch (Throwable t) {
            throw new ObjectInstantiationException(type, t);
        }
    }

    private SelectedConstructor findConstructor(Class<?> type, Object[] parameters) throws Throwable {
        SelectedConstructor constructor = constructorSelector.forParams(type, parameters);
        if (constructor.getFailure() != null) {
            throw constructor.getFailure();
        }
        return constructor;
    }

    private <T> Object[] convertParameters(Class<T> type, SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        if (parameterTypes.length < parameters.length) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Too many parameters provided for constructor for ");
            formatter.appendType(type);
            formatter.append(String.format(". Expected %s, received %s.", parameterTypes.length, parameters.length));
            throw new IllegalArgumentException(formatter.toString());
        }
        if (parameterTypes.length == parameters.length) {
            // No services to be mixed in
            return verifyParameters(constructor, parameters);
        } else {
            return addServicesToParameters(type, constructor, parameters);
        }
    }

    private <T> Object[] verifyParameters(SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> targetType = parameterTypes[i];
            Object currentParameter = parameters[i];
            if (targetType.isPrimitive()) {
                if (currentParameter == null) {
                    nullPrimitiveType(i, targetType);
                }
                targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
            } else if (currentParameter == null) {
                // Null is ok if the ConstructorSelector says it's ok
                continue;
            }
            if (!targetType.isInstance(currentParameter)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Unable to determine constructor argument #" + (i + 1) + ": value ");
                formatter.appendValue(currentParameter);
                formatter.append(" not assignable to ");
                formatter.appendType(parameterTypes[i]);
                throw new IllegalArgumentException(formatter.toString());
            }
        }
        return parameters;
    }

    private void nullPrimitiveType(int index, Class<?> paramType) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Unable to determine constructor argument #" + (index + 1) + ": null value is not assignable to ");
        formatter.appendType(paramType);
        throw new IllegalArgumentException(formatter.toString());
    }

    private <T> Object[] addServicesToParameters(Class<T> type, SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        Type[] genericTypes = constructor.getConstructor().getGenericParameterTypes();
        Object[] resolvedParameters = new Object[parameterTypes.length];
        int pos = 0;
        for (int i = 0; i < resolvedParameters.length; i++) {
            Class<?> targetType = parameterTypes[i];
            Type serviceType = genericTypes[i];
            if (pos < parameters.length) {
                Object parameter = parameters[pos];
                if (targetType.isPrimitive()) {
                    if (parameter == null) {
                        nullPrimitiveType(i, targetType);
                    }
                    targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
                }
                if (parameter == null || targetType.isInstance(parameter)) {
                    resolvedParameters[i] = parameter;
                    pos++;
                    continue;
                }
            }
            Object service = services.find(serviceType);
            if (service != null) {
                resolvedParameters[i] = service;
                continue;
            }
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Unable to determine constructor argument #" + (i + 1) + ": ");
            if (pos < parameters.length) {
                formatter.append("value ");
                formatter.appendValue(parameters[pos]);
                formatter.append(" is not assignable to ");
                formatter.appendType(parameterTypes[i]);
            } else {
                formatter.append("missing parameter of ");
                formatter.appendType(parameterTypes[i]);
            }
            formatter.append(", or no service of type ");
            formatter.append(serviceType.toString());
            throw new IllegalArgumentException(formatter.toString());
        }

        if (pos != parameters.length) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Unexpected parameter provided for constructor for ");
            formatter.appendType(type);
            throw new IllegalArgumentException(formatter.toString());
        }

        return resolvedParameters;
    }
}
