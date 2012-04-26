/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.dispatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts from interface T to a {@link Dispatch}
 *
 * @param <T>
 */
public class ProxyDispatchAdapter<T> {
    private final Class<T> type;
    private final T source;

    public ProxyDispatchAdapter(Dispatch<? super MethodInvocation> dispatch, Class<T> type, Class<?>... extraTypes) {
        this.type = type;
        List<Class<?>> types = new ArrayList<Class<?>>();
        ClassLoader classLoader = type.getClassLoader();
        types.add(type);
        for (Class<?> extraType : extraTypes) {
            ClassLoader candidate = extraType.getClassLoader();
            if (candidate != classLoader && candidate != null) {
                try {
                    if (candidate.loadClass(type.getName()) != null) {
                        classLoader = candidate;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }
            types.add(extraType);
        }
        source = type.cast(Proxy.newProxyInstance(classLoader, types.toArray(new Class<?>[types.size()]),
                new DispatchingInvocationHandler(type, dispatch)));
    }

    public Class<T> getType() {
        return type;
    }

    public T getSource() {
        return source;
    }

    private static class DispatchingInvocationHandler implements InvocationHandler {
        private final Class<?> type;
        private final Dispatch<? super MethodInvocation> dispatch;

        private DispatchingInvocationHandler(Class<?> type, Dispatch<? super MethodInvocation> dispatch) {
            this.type = type;
            this.dispatch = dispatch;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals("equals")) {
                Object parameter = parameters[0];
                if (parameter == null || !Proxy.isProxyClass(parameter.getClass())) {
                    return false;
                }
                Object handler = Proxy.getInvocationHandler(parameter);
                if (!DispatchingInvocationHandler.class.isInstance(handler)) {
                    return false;
                }

                DispatchingInvocationHandler otherHandler = (DispatchingInvocationHandler) handler;
                return otherHandler.type.equals(type) && otherHandler.dispatch == dispatch;
            }

            if (method.getName().equals("hashCode")) {
                return dispatch.hashCode();
            }
            if (method.getName().equals("toString")) {
                return String.format("%s broadcast", type.getSimpleName());
            }
            dispatch.dispatch(new MethodInvocation(method, parameters));
            return null;
        }
    }
}
