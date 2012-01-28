/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.listener;

import org.gradle.internal.Factory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class LazyCreationProxy<T> {
    private final T source;

    public LazyCreationProxy(Class<T> type, final Factory<? extends T> factory) {
        source = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new LazyInvocationHandler(factory)));
    }

    public T getSource() {
        return source;
    }

    private static class LazyInvocationHandler implements InvocationHandler {
        private Object target;
        private final Factory<?> factory;

        public LazyInvocationHandler(Factory<?> factory) {
            this.factory = factory;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (target == null) {
                target = factory.create();
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
