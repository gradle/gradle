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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.gradle.util.UncheckedException;

import java.lang.reflect.*;
import java.util.*;

public class ProtocolToModelAdapter {
    public <T, S> T adapt(Class<T> targetType, S protocolObject) {
        return targetType.cast(Proxy.newProxyInstance(targetType.getClassLoader(), new Class<?>[]{targetType}, new InvocationHandlerImpl(protocolObject)));
    }

    private class InvocationHandlerImpl implements InvocationHandler {
        private final Object delegate;
        private final Map<Method, Method> methods = new HashMap<Method, Method>();
        private final Map<String, Object> properties = new HashMap<String, Object>();
        private final Method equalsMethod;
        private final Method hashCodeMethod;

        public InvocationHandlerImpl(Object delegate) {
            this.delegate = delegate;
            try {
                equalsMethod = Object.class.getMethod("equals", Object.class);
                hashCodeMethod = Object.class.getMethod("hashCode");
            } catch (NoSuchMethodException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }

            InvocationHandlerImpl other = (InvocationHandlerImpl) o;
            return delegate.equals(other.delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        public Object invoke(Object target, Method method, Object[] params) throws Throwable {
            if (method.equals(equalsMethod)) {
                Object param = params[0];
                if (param == null || !Proxy.isProxyClass(param.getClass())) {
                    return false;
                }
                InvocationHandler other = Proxy.getInvocationHandler(param);
                return equals(other);
            } else if (method.equals(hashCodeMethod)) {
                return hashCode();
            }

            if (method.getName().matches("get\\w+")) {
                if (properties.containsKey(method.getName())) {
                    return properties.get(method.getName());
                }
                Object value = doInvokeMethod(method, params);
                properties.put(method.getName(), value);
                return value;
            }

            return doInvokeMethod(method, params);
        }

        private Object doInvokeMethod(Method method, Object[] params) throws Throwable {
            Method targetMethod = methods.get(method);
            if (targetMethod == null) {
                targetMethod = findMethod(method);
                methods.put(method, targetMethod);
            }

            Object returnValue;
            try {
                returnValue = targetMethod.invoke(delegate, params);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }

            if (returnValue == null || method.getReturnType().isInstance(returnValue)) {
                return returnValue;
            }

            return convert(returnValue, method.getGenericReturnType());
        }

        private Method findMethod(Method method) {
            Method match;
            try {
                match = delegate.getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException(String.format("Cannot map method %s.%s() to target object of type %s.", method.getDeclaringClass().getSimpleName(), method.getName(), delegate.getClass().getSimpleName()), e);
            }

            LinkedList<Class<?>> queue = new LinkedList<Class<?>>();
            queue.add(delegate.getClass());
            while (!queue.isEmpty()) {
                Class<?> c = queue.removeFirst();
                try {
                    match = c.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                for (Class<?> interfaceType : c.getInterfaces()) {
                    queue.addFirst(interfaceType);
                }
                if (c.getSuperclass() !=null) {
                    queue.addFirst(c.getSuperclass());
                }
            }
            match.setAccessible(true);
            return match;
        }

        private Object convert(Object value, Type targetType) {
            if (targetType instanceof ParameterizedType) {
                ParameterizedType parameterizedTargetType = (ParameterizedType) targetType;
                if (parameterizedTargetType.getRawType().equals(DomainObjectSet.class)) {
                    Type targetElementType = getElementType(parameterizedTargetType);
                    List<Object> convertedElements = new ArrayList<Object>();
                    for (Object element : (Iterable) value) {
                        convertedElements.add(convert(element, targetElementType));
                    }
                    return new ImmutableDomainObjectSet(convertedElements);
                }
            }
            if (targetType instanceof Class) {
                return adapt((Class) targetType, value);
            }
            throw new UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", value.getClass(), targetType));
        }

        private Type getElementType(ParameterizedType type) {
            Type elementType = type.getActualTypeArguments()[0];
            if (elementType instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) elementType;
                return wildcardType.getUpperBounds()[0];
            }
            return elementType;
        }
    }
}
