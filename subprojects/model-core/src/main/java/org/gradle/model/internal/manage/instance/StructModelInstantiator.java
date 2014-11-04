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

package org.gradle.model.internal.manage.instance;

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class StructModelInstantiator implements ModelInstantiatorStrategy {

    public <T> T newInstance(ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator instantiator) {
        if (schema.getKind().equals(ModelSchema.Kind.STRUCT)) {
            Class<T> concreteType = schema.getType().getConcreteClass();
            ManagedModelElement<T> element = new ManagedModelElement<T>(schema);
            ManagedModelElementInvocationHandler invocationHandler = new ManagedModelElementInvocationHandler(element, schemaStore, instantiator);
            return Cast.uncheckedCast(Proxy.newProxyInstance(concreteType.getClassLoader(), new Class<?>[]{concreteType, ManagedInstance.class}, invocationHandler));
        } else {
            return null;
        }

    }

    private static class ManagedModelElementInvocationHandler implements InvocationHandler {

        private final ManagedModelElement<?> element;
        private final ModelSchemaStore schemaStore;
        private final ModelInstantiator instantiator;

        private ManagedModelElementInvocationHandler(ManagedModelElement<?> element, ModelSchemaStore schemaStore, ModelInstantiator instantiator) {
            this.element = element;
            this.schemaStore = schemaStore;
            this.instantiator = instantiator;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(ModelType.of(method.getGenericReturnType()), propertyName);
            } else if (methodName.startsWith("set")) {
                setInstanceProperty(ModelType.of(method.getGenericParameterTypes()[0]), propertyName, args[0]);
                return null;
            } else if (methodName.equals("hashCode")) {
                return hashCode();
            }
            throw new Exception("Unexpected method called: " + methodName);
        }

        private <U> void setInstanceProperty(ModelType<U> propertyType, String propertyName, Object value) {
            ModelPropertyInstance<U> modelPropertyInstance = element.get(propertyType, propertyName);
            ModelSchema<U> propertySchema = schemaStore.getSchema(propertyType);
            if (propertySchema.getKind().equals(ModelSchema.Kind.STRUCT) && !ManagedInstance.class.isInstance(value)) {
                throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", propertyName, element.getType()));
            }
            modelPropertyInstance.set(Cast.cast(propertyType.getConcreteClass(), value));
        }

        private <U> U getInstanceProperty(ModelType<U> propertyType, String propertyName) {
            ModelPropertyInstance<U> propertyInstance = element.get(propertyType, propertyName);
            U value = propertyInstance.get();
            if (value == null && !propertyInstance.getMeta().isWritable()) {
                value = instantiator.newInstance(schemaStore.getSchema(propertyType));
                propertyInstance.set(value);
            }

            return value;
        }
    }

}
