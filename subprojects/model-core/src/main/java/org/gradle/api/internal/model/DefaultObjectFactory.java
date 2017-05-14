/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.model;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.Named;
import org.gradle.api.model.ObjectFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class DefaultObjectFactory implements ObjectFactory {
    public static final DefaultObjectFactory INSTANCE = new DefaultObjectFactory();

    private final LoadingCache<Class<?>, LoadingCache<String, Object>> values = CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, LoadingCache<String, Object>>() {
        @Override
        public LoadingCache<String, Object> load(final Class<?> type) throws Exception {
            return CacheBuilder.newBuilder().build(new CacheLoader<String, Object>() {
                @Override
                public Object load(String name) throws Exception {
                    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new NamedInvocationHandler(name));
                }
            });
        }
    });

    @Override
    public <T extends Named> T named(final Class<T> type, final String name) {
        return type.cast(values.getUnchecked(type).getUnchecked(name));
    }

    private static class NamedInvocationHandler implements InvocationHandler {
        private final String name;

        NamedInvocationHandler(String name) {
            this.name = name;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getParameterCount() == 0 && (method.getName().equals("getName") || method.getName().equals("toString"))) {
                return name;
            }
            if (method.getParameterCount() == 0 && method.getName().equals("hashCode")) {
                return name.hashCode();
            }
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class && method.getName().equals("equals")) {
                Object parameter = args[0];
                if (parameter == null || !Proxy.isProxyClass(parameter.getClass())) {
                    return false;
                }
                Object handler = Proxy.getInvocationHandler(parameter);
                return handler == this;
            }
            throw new UnsupportedOperationException();
        }
    }
}
