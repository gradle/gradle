/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.logging.DefaultStandardOutputCapture;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.</p>
 *
 * @param <T> The listener type.
 */
public class ListenerBroadcast<T> {
    private final T source;
    private final Class<T> type;
    private final Map<Object, InvocationHandler> handlers = new LinkedHashMap<Object, InvocationHandler>();
    private final DelegatingInvocationHandler noOpLogger = new DelegatingInvocationHandler() {
        @Override
        public T getDelegate() {
            return null;
        }

        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            return null;
        }
    };
    private DelegatingInvocationHandler logger = noOpLogger;

    public ListenerBroadcast(Class<T> type) {
        this.type = type;
        source = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new BroadcastInvocationHandler()));
    }

    /**
     * Returns the broadcaster. Any method call on this object is broadcast to all listeners.
     *
     * @return The broadcaster.
     */
    public T getSource() {
        return source;
    }

    /**
     * Returns the type of listener to which this class broadcasts.
     *
     * @return The type of the broadcaster.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public void add(T listener) {
        handlers.put(listener, new ListenerInvocationHandler(listener));
    }

    /**
     * Adds the given listener if it is an instance of the listener type.
     *
     * @param listener The listener
     */
    public void maybeAdd(Object listener) {
        if (type.isInstance(listener)) {
            add(type.cast(listener));
        }
    }

    /**
     * Adds a closure to be notified when the given method is called.
     */
    public void add(String methodName, Closure closure) {
        handlers.put(closure, new ClosureInvocationHandler(methodName, closure));
    }

    /**
     * Removes the given listener.
     *
     * @param listener The listener.
     */
    public void remove(Object listener) {
        if (!type.isInstance(listener)) {
            return;
        }
        if (listener.equals(logger.getDelegate())) {
            logger = noOpLogger;
        }
        handlers.remove(listener);
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return String.format("Failed to notify %s.", typeDescription);
    }

    public T setLogger(T logger) {
        T oldLogger = this.logger.getDelegate();
        this.logger = new ListenerInvocationHandler(logger);
        return oldLogger;
    }

    private class BroadcastInvocationHandler implements InvocationHandler {
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
            DefaultStandardOutputCapture standardOutputCapture = null;
            if (getType() != StandardOutputListener.class) {
                standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.QUIET);
                standardOutputCapture.start();
            }
            logger.invoke(null, method, parameters);
            for (InvocationHandler handler : handlers.values()) {
                handler.invoke(null, method, parameters);
            }
            if (standardOutputCapture != null) {
                standardOutputCapture.stop();
            }
            return null;
        }
    }

    private abstract class DelegatingInvocationHandler implements InvocationHandler {
        abstract T getDelegate();
    }

    private class ListenerInvocationHandler extends DelegatingInvocationHandler {
        private final T listener;

        public ListenerInvocationHandler(T listener) {
            this.listener = listener;
        }

        @Override
        T getDelegate() {
            return listener;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            try {
                method.invoke(listener, parameters);
            } catch (InvocationTargetException e) {
                throw new ListenerNotificationException(getErrorMessage(), e.getCause());
            }
            return null;
        }
    }

    private class ClosureInvocationHandler implements InvocationHandler {
        private final String methodName;
        private final Closure closure;

        public ClosureInvocationHandler(String methodName, Closure closure) {
            this.methodName = methodName;
            this.closure = closure;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals(methodName)) {
                if (closure.getMaximumNumberOfParameters() < parameters.length) {
                    parameters = Arrays.asList(parameters).subList(0, closure.getMaximumNumberOfParameters()).toArray();
                }
                try {
                    closure.call(parameters);
                } catch (InvokerInvocationException e) {
                    throw new ListenerNotificationException(getErrorMessage(), e.getCause());
                }
            }
            return null;
        }
    }
}