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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ManagedModelElementInstanceFactory {

    private final ManagedModelElementInstanceStore store = new ManagedModelElementInstanceStore();

    public <T> T create(ManagedModelElement<T> element) {
        Class<T> type = element.getType();
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new ManagedModelElementInvocationHandler(element, store));
        store.add(instance);
        @SuppressWarnings("unchecked") T typedInstance = (T) instance;
        return typedInstance;
    }

    private class ManagedModelElementInvocationHandler implements InvocationHandler {

        private final ManagedModelElement<?> element;
        private final ManagedModelElementInstanceStore store;

        public ManagedModelElementInvocationHandler(ManagedModelElement<?> element, ManagedModelElementInstanceStore store) {
            this.element = element;
            this.store = store;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(method.getReturnType(), propertyName);
            } else if (methodName.startsWith("set")) {
                setInstanceProperty(method.getParameterTypes()[0], propertyName, args[0]);
                return null;
            } else if (methodName.equals("hashCode")) {
                return hashCode();
            }
            throw new Exception("Unexpected method called: " + methodName);
        }

        private <U> void setInstanceProperty(Class<U> classType, String propertyName, Object value) {
            ModelPropertyInstance<U> modelPropertyInstance = element.get(classType, propertyName);
            if (modelPropertyInstance.getMeta().isManaged() && !store.contains(value)) {
                throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", propertyName, element.getType().getName()));
            }
            modelPropertyInstance.set(Cast.cast(classType, value));
        }

        private <U> U getInstanceProperty(Class<U> classType, String propertyName) {
            return element.get(classType, propertyName).get();
        }
    }
}
