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

package org.gradle.internal.service;

import org.gradle.internal.Factory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

public abstract class AbstractServiceRegistry implements ServiceRegistry {
    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        if (serviceType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) serviceType;
            if (parameterizedType.getRawType().equals(Factory.class)) {
                Type typeArg = parameterizedType.getActualTypeArguments()[0];
                if (typeArg instanceof Class) {
                    return getFactory((Class) typeArg);
                }
                if (typeArg instanceof WildcardType) {
                    WildcardType wildcardType = (WildcardType) typeArg;
                    if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                        if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                            return getFactory((Class<Object>) wildcardType.getLowerBounds()[0]);
                        }
                    }
                    if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                        if (wildcardType.getUpperBounds()[0] instanceof Class) {
                            return getFactory((Class<Object>) wildcardType.getUpperBounds()[0]);
                        }
                    }
                }
            }
        }
        if (serviceType instanceof Class) {
            return get((Class) serviceType);
        }
        throw new UnsupportedOperationException(String.format("Cannot locate service of type %s yet.", serviceType));
    }

    public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
        if (serviceType.equals(Factory.class)) {
            throw new IllegalArgumentException("Cannot locate service of raw type Factory. Use getFactory() or get(Type) instead.");
        }
        if (serviceType.isArray()) {
            throw new IllegalArgumentException(String.format("Cannot locate service of array type %s[].", serviceType.getComponentType().getSimpleName()));
        }
        if (serviceType.isAnnotation()) {
            throw new IllegalArgumentException(String.format("Cannot locate service of annotation type @%s.", serviceType.getSimpleName()));
        }
        return doGet(serviceType);
    }

    protected abstract <T> T doGet(Class<T> serviceType);

    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

}
