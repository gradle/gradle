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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.listener.ListenerNotificationException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class BroadcastDispatch<T> implements StoppableDispatch<MethodInvocation> {
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

    public BroadcastDispatch(Class<T> type) {
        this.type = type;
    }

    public void add(T listener) {
        handlers.put(listener, new ListenerInvocationHandler(listener));
    }

    public void add(String methodName, Closure closure) {
        assertIsMethod(methodName);
        handlers.put(closure, new ClosureInvocationHandler(methodName, closure));
    }

    public void add(String methodName, Action<?> action) {
        assertIsMethod(methodName);
        handlers.put(action, new ActionInvocationHandler(methodName, action));
    }

    private void assertIsMethod(String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return;
            }
        }
        throw new IllegalArgumentException(String.format("Method %s() not found for listener type %s.", methodName,
                type.getSimpleName()));
    }

    public void remove(Object listener) {
        if (listener.equals(logger.getDelegate())) {
            logger = noOpLogger;
        }
        handlers.remove(listener);
    }

    public T setLogger(T logger) {
        T oldLogger = this.logger.getDelegate();
        this.logger = new ListenerInvocationHandler(logger);
        return oldLogger;
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return String.format("Failed to notify %s.", typeDescription);
    }

    public void dispatch(MethodInvocation invocation) {
        try {
            Method method = invocation.getMethod();
            dispatch(method, invocation.getArguments());
        } catch (ListenerNotificationException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new GradleException(throwable);
        }
    }

    private void dispatch(Method method, Object[] parameters) throws Throwable {
        logger.invoke(null, method, parameters);
        for (InvocationHandler handler : new ArrayList<InvocationHandler>(handlers.values())) {
            handler.invoke(null, method, parameters);
        }
    }

    public void stop() {
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
                } catch (Throwable e) {
                    throw new ListenerNotificationException(getErrorMessage(), e);
                }
            }
            return null;
        }
    }

    private class ActionInvocationHandler implements InvocationHandler {
        private final String methodName;
        private final Action action;

        public ActionInvocationHandler(String methodName, Action action) {
            this.methodName = methodName;
            this.action = action;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals(methodName)) {
                try {
                    action.execute(parameters[0]);
                } catch (Throwable e) {
                    throw new ListenerNotificationException(getErrorMessage(), e);
                }
            }
            return null;
        }
    }
}
