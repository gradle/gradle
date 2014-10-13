/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.state;

import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ManagedModelElement<T> {

    private final Class<T> type;
    private final ImmutableSortedMap<String, ModelPropertyInstance<?>> properties;
    private final T instance;

    public ManagedModelElement(ModelSchema<T> schema) {
        this.type = schema.getType();
        ImmutableSortedMap.Builder<String, ModelPropertyInstance<?>> builder = ImmutableSortedMap.naturalOrder();
        for (ModelProperty<?> property : schema.getProperties().values()) {
            builder.put(property.getName(), ModelPropertyInstance.of(property));
        }
        this.properties = builder.build();
        this.instance = createInstance();
    }

    public Class<T> getType() {
        return type;
    }

    public <U> ModelPropertyInstance<U> get(Class<U> classType, String propertyName) {
        ModelPropertyInstance<?> modelPropertyInstance = properties.get(propertyName);
        Class<?> modelPropertyType = modelPropertyInstance.getMeta().getType().getRawClass();
        if (!modelPropertyType.equals(classType)) {
            throw new UnexpectedModelPropertyTypeException(propertyName, type, classType, modelPropertyType);
        }
        @SuppressWarnings("unchecked") ModelPropertyInstance<U> cast = (ModelPropertyInstance<U>) modelPropertyInstance;
        return cast;
    }

    ImmutableSortedMap<String, ModelPropertyInstance<?>> getProperties() {
        return properties;
    }

    public T getInstance() {
        return instance;
    }

    private T createInstance() {
        @SuppressWarnings("unchecked") T createdInstance = (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, new ManagedModelElementInvocationHandler());
        return createdInstance;
    }

    private class ManagedModelElementInvocationHandler implements InvocationHandler {

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(method.getReturnType(), propertyName);
            } else {
                setInstanceProperty(method.getParameterTypes()[0], propertyName, args[0]);
                return null;
            }
        }

        private <U> void setInstanceProperty(Class<U> classType, String propertyName, Object value) {
            get(classType, propertyName).set(Cast.cast(classType, value));
        }

        private <U> U getInstanceProperty(Class<U> classType, String propertyName) {
            return get(classType, propertyName).get();
        }
    }
}
