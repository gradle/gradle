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

package org.gradle.internal.event;

import org.gradle.api.Action;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ReflectionDispatch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class BroadcastDispatch<T> extends AbstractBroadcastDispatch<T> {
    private final Map<Object, Dispatch<MethodInvocation>> handlers = new LinkedHashMap<Object, Dispatch<MethodInvocation>>();

    public BroadcastDispatch(Class<T> type) {
        super(type);
    }

    public Class<T> getType() {
        return type;
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
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

    @Override
    public void dispatch(MethodInvocation message) {
        Iterator<Dispatch<MethodInvocation>> iterator = new ArrayList<Dispatch<MethodInvocation>>(handlers.values()).iterator();
        dispatch(message, iterator);
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
