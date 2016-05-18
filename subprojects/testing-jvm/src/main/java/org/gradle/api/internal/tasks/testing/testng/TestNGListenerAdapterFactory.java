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
package org.gradle.api.internal.tasks.testing.testng;

import com.google.common.base.Objects;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.testng.ISuiteListener;
import org.testng.ITestListener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class TestNGListenerAdapterFactory {
    private final ClassLoader classLoader;

    TestNGListenerAdapterFactory(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ITestListener createAdapter(ITestListener listener) {
        Class<?> testNG6Class = tryLoadClass("org.testng.IConfigurationListener2");
        if (testNG6Class != null) {
            return createProxy(testNG6Class, listener);
        }

        Class<?> testNG5Class = tryLoadClass("org.testng.internal.IConfigurationListener");
        if (testNG5Class != null) {
            return createProxy(testNG5Class, listener);
        }

        throw new UnsupportedOperationException("Neither found interface 'org.testng.IConfigurationListener2' nor interface 'org.testng.internal.IConfigurationListener'. Which version of TestNG are you using?");
    }

    private Class<?> tryLoadClass(String name) {
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private ITestListener createProxy(Class<?> configListenerClass, final ITestListener listener) {
        Class<?>[] interfaces = new Class<?>[]{ITestListener.class, ISuiteListener.class, configListenerClass};
        return (ITestListener) Proxy.newProxyInstance(classLoader, interfaces, new AdaptedListener(listener));
    }

    private static class AdaptedListener implements InvocationHandler {

        private final ITestListener delegate;

        private AdaptedListener(ITestListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Class<?> realReturnType = method.getReturnType();
            Class<?> boxedReturnType = realReturnType;
            if (!realReturnType.equals(void.class) && realReturnType.isPrimitive()) {
                boxedReturnType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(realReturnType);
            }
            if (method.getName().equals("equals") && args != null && args.length == 1) {
                return proxyEquals(proxy, args[0]);
            }
            if (method.getName().equals("hashCode") && args == null) {
                return proxyHashCode(proxy);
            }
            return invoke(delegate.getClass(), delegate, boxedReturnType, method, args);
        }

        private <T, R> R invoke(Class<T> listenerType, Object listener, Class<R> returnType, Method method, Object[] args) {
            T listenerCast = listenerType.cast(listener);
            JavaMethod<T, R> javaMethod = JavaReflectionUtil.method(listenerType, returnType, method.getName(), method.getParameterTypes());
            return javaMethod.invoke(listenerCast, args);
        }

        private boolean proxyEquals(Object proxy, Object other) {
            if (other == null) {
                return false;
            }
            if (proxy == other) {
                return true;
            }
            if (!Proxy.isProxyClass(other.getClass())) {
                return false;
            }
            InvocationHandler otherHandler = Proxy.getInvocationHandler(other);
            if (!(otherHandler instanceof AdaptedListener)) {
                return false;
            }
            AdaptedListener proxyAdapter = (AdaptedListener) Proxy.getInvocationHandler(proxy);
            AdaptedListener otherAdapter = (AdaptedListener) otherHandler;
            return proxyAdapter.getClass().equals(otherHandler.getClass())
                && proxyAdapter.delegate.getClass().equals(otherAdapter.delegate.getClass());
        }

        private int proxyHashCode(Object proxy) {
            AdaptedListener invocationHandler = (AdaptedListener) Proxy.getInvocationHandler(proxy);
            return Objects.hashCode(invocationHandler.getClass(), invocationHandler.delegate.getClass());
        }
    }
}
