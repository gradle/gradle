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
package org.gradle.listener.dispatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Adapts from interface T to a {@link Dispatch}
 *
 * @param <T>
 */
public class ProxyDispatchAdapter<T> {
    private final Class<T> type;
    private final Dispatch<? super MethodInvocation> dispatch;
    private final T source;

    public ProxyDispatchAdapter(Class<T> type, Dispatch<? super MethodInvocation> dispatch) {
        this.type = type;
        this.dispatch = dispatch;
        source = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new DispatchingInvocationHandler()));
    }

    public Class<T> getType() {
        return type;
    }

    public T getSource() {
        return source;
    }

    private class DispatchingInvocationHandler implements InvocationHandler {
        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals("equals")) {
                return parameters[0] != null && Proxy.isProxyClass(parameters[0].getClass())
                        && Proxy.getInvocationHandler(parameters[0]) == this;
            }
            if (method.getName().equals("hashCode")) {
                return hashCode();
            }
            if (method.getName().equals("toString")) {
                return String.format("%s broadcast", type.getSimpleName());
            }
            dispatch.dispatch(new MethodInvocation(method, parameters));
            return null;
        }
    }
}
