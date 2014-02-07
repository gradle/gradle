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

package org.gradle.listener;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ReflectionDispatch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BroadcastDispatch<T> implements Dispatch<MethodInvocation> {
    private final Class<T> type;
    private final Map<Object, Dispatch<MethodInvocation>> handlers = new LinkedHashMap<Object, Dispatch<MethodInvocation>>();

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

    public void removeAll() {
        handlers.clear();
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return String.format("Failed to notify %s.", typeDescription);
    }

    public void dispatch(MethodInvocation invocation) {
        List<Throwable> failures = new ArrayList<Throwable>();
        for (Dispatch<MethodInvocation> handler : new ArrayList<Dispatch<MethodInvocation>>(handlers.values())) {
            try {
                handler.dispatch(invocation);
            } catch (UncheckedException e) {
                failures.add(e.getCause());
            } catch (Throwable t) {
                failures.add(t);
            }
        }
        if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
            throw (RuntimeException) failures.get(0);
        }
        if (!failures.isEmpty()) {
            throw new ListenerNotificationException(getErrorMessage(), failures);
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
