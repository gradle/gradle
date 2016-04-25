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

import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.JavaMethod;
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
        return (ITestListener) Proxy.newProxyInstance(classLoader, new Class<?>[]{ITestListener.class, ISuiteListener.class, configListenerClass}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Class<?> realReturnType = method.getReturnType();
                Class<?> boxedReturnType = realReturnType;
                if (!realReturnType.equals(void.class) && realReturnType.isPrimitive()) {
                    boxedReturnType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(realReturnType);
                }
                if (method.getName().equals("equals") && args.length == 1){
                    return proxyEquals(proxy, args[0]);
                }
                if (method.getName().equals("hashCode")){
                    return proxyHashCode(proxy);
                }
                return invoke(listener.getClass(), listener, boxedReturnType, method, args);
            }

            private <T, R> R invoke(Class<T> listenerType, Object listener, Class<R> returnType, Method method, Object[] args) {
                T listenerCast = listenerType.cast(listener);
                JavaMethod<T, R> javaMethod = JavaReflectionUtil.method(listenerType, returnType, method.getName(), method.getParameterTypes());
                return javaMethod.invoke(listenerCast, args);
            }

            @Override
            public boolean equals(Object obj1) {
                return proxyEquals(this, obj1);
            }

            @Override
            public int hashCode() {
                return proxyHashCode(this);
            }

            protected Integer proxyHashCode(Object proxy) {
                return new Integer(System.identityHashCode(proxy));
            }

            protected Boolean proxyEquals(Object proxy, Object other) {
                if(other == null)
                    return Boolean.FALSE;
                String className1 = Proxy.getInvocationHandler(proxy).getClass().toString();
                String className2 = Proxy.isProxyClass(other.getClass())?Proxy.getInvocationHandler(other).getClass().toString():other.getClass().toString();
                return className1.equalsIgnoreCase(className2) ? Boolean.TRUE : Boolean.FALSE;

            }
        });
    }
}
