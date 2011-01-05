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
import org.gradle.listener.ListenerNotificationException;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class BroadcastDispatch<T> implements StoppableDispatch<MethodInvocation> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastDispatch.class);
    private final Class<T> type;
    private final Map<Object, Dispatch<MethodInvocation>> handlers
            = new LinkedHashMap<Object, Dispatch<MethodInvocation>>();

    public BroadcastDispatch(Class<T> type) {
        this.type = type;
    }

    public Class<T> getType() {
        return type;
    }

    public void add(Dispatch<MethodInvocation> dispatch) {
        handlers.put(dispatch, dispatch);
    }

    public void add(T listener) {
        handlers.put(listener, new ReflectionDispatch(listener));
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
        handlers.remove(listener);
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return String.format("Failed to notify %s.", typeDescription);
    }

    public void dispatch(MethodInvocation invocation) {
        try {
            ExceptionTrackingListener tracker = new ExceptionTrackingListener(LOGGER);
            for (Dispatch<MethodInvocation> handler : new ArrayList<Dispatch<MethodInvocation>>(handlers.values())) {
                try {
                    handler.dispatch(invocation);
                } catch (UncheckedException e) {
                    tracker.execute(e.getCause());
                } catch (Throwable t) {
                    tracker.execute(t);
                }
            }
            tracker.stop();
        } catch (Throwable t) {
            throw new ListenerNotificationException(getErrorMessage(), t);
        }
    }

    public void stop() {
    }

    private class ClosureInvocationHandler implements Dispatch<MethodInvocation> {
        private final String methodName;
        private final Closure closure;

        public ClosureInvocationHandler(String methodName, Closure closure) {
            this.methodName = methodName;
            this.closure = closure;
        }

        public void dispatch(MethodInvocation message) {
            if (message.getMethod().getName().equals(methodName)) {
                Object[] parameters = message.getArguments();
                if (closure.getMaximumNumberOfParameters() < parameters.length) {
                    parameters = Arrays.asList(parameters).subList(0, closure.getMaximumNumberOfParameters()).toArray();
                }
                closure.call(parameters);
            }
        }
    }

    private class ActionInvocationHandler implements Dispatch<MethodInvocation> {
        private final String methodName;
        private final Action action;

        public ActionInvocationHandler(String methodName, Action action) {
            this.methodName = methodName;
            this.action = action;
        }

        public void dispatch(MethodInvocation message) {
            if (message.getMethod().getName().equals(methodName)) {
                action.execute(message.getArguments()[0]);
            }
        }
    }
}
