/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.dispatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Adapts from interface T to a {@link Dispatch}
 */
public class ProxyDispatchAdapter<T> {
    private final Class<T> type;
    private final T source;

    public ProxyDispatchAdapter(Dispatch<? super MethodInvocation> dispatch, Class<T> type) {
        this.type = type;
        source = type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                new DispatchingInvocationHandler(type, dispatch)
            )
        );
    }

    public ProxyDispatchAdapter(Dispatch<? super MethodInvocation> dispatch, Class<T> type, Class<?> extraType) {
        this.type = type;
        source = type.cast(
            Proxy.newProxyInstance(
                selectClassLoader(type, extraType),
                new Class<?>[]{type, extraType},
                new DispatchingInvocationHandler(type, dispatch)
            )
        );
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

        @Override
        public Object invoke(Object target, Method method, Object[] parameters) {
            switch (method.getName()) {
                case "equals":
                    Object parameter = parameters[0];
                    if (parameter == null || !Proxy.isProxyClass(parameter.getClass())) {
                        return false;
                    }
                    Object handler = Proxy.getInvocationHandler(parameter);
                    if (!(handler instanceof DispatchingInvocationHandler)) {
                        return false;
                    }

                    DispatchingInvocationHandler otherHandler = (DispatchingInvocationHandler) handler;
                    return otherHandler.type.equals(type) && otherHandler.dispatch == dispatch;
                case "hashCode":
                    return dispatch.hashCode();
                case "toString":
                    return type.getSimpleName() + " broadcast";
                default:
                    dispatch.dispatch(new MethodInvocation(method, parameters));
                    return null;
            }
        }
    }

    private static <T> ClassLoader selectClassLoader(Class<T> type, Class<?> extraType) {
        ClassLoader typeClassLoader = type.getClassLoader();
        ClassLoader candidate = extraType.getClassLoader();
        return candidate != typeClassLoader && candidate != null && isCanLoadType(candidate, type)
            ? candidate
            : typeClassLoader;
    }

    private static <T> boolean isCanLoadType(ClassLoader candidate, Class<T> type) {
        try {
            if (candidate.loadClass(type.getName()) != null) {
                return true;
            }
        } catch (ClassNotFoundException e) {
            // Ignore
        }
        return false;
    }
}
