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
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ManagedProxyFactory {

    public <T> T createProxy(ModelElementState state, ClassLoader classLoader, Class<?>... interfaces) {
        StateBackedInvocationHandler invocationHandler = new StateBackedInvocationHandler(state);
        return Cast.uncheckedCast(Proxy.newProxyInstance(classLoader, interfaces, invocationHandler));
    }

    private static class StateBackedInvocationHandler implements InvocationHandler {

        private final ModelElementState state;

        public StateBackedInvocationHandler(ModelElementState state) {
            this.state = state;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(ModelType.returnType(method), propertyName);
            } else if (methodName.startsWith("set")) {
                setInstanceProperty(ModelType.paramType(method, 0), propertyName, args[0]);
                return null;
            } else if (methodName.equals("hashCode")) {
                return hashCode();
            }
            throw new Exception("Unexpected method called: " + methodName);
        }

        private <U> void setInstanceProperty(ModelType<U> type, String name, Object value) {
            state.set(type, name, Cast.<U>uncheckedCast(value));
        }

        private <U> U getInstanceProperty(ModelType<U> type, String name) {
            return state.get(type, name);
        }
    }
}
