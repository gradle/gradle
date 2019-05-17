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
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An immutable composite {@link org.gradle.internal.dispatch.Dispatch} implementation. Optimized for a small number of elements, and for infrequent modification.
 */
public abstract class BroadcastDispatch<T> extends AbstractBroadcastDispatch<T> {
    private BroadcastDispatch(Class<T> type) {
        super(type);
    }

    public static <T> BroadcastDispatch<T> empty(Class<T> type) {
        return new EmptyDispatch<T>(type);
    }

    public Class<T> getType() {
        return type;
    }

    public abstract boolean isEmpty();

    public BroadcastDispatch<T> add(Dispatch<MethodInvocation> dispatch) {
        return add(dispatch, dispatch);
    }

    public BroadcastDispatch<T> add(T listener) {
        return add(listener, new ReflectionDispatch(listener));
    }

    public BroadcastDispatch<T> add(String methodName, Action<?> action) {
        assertIsMethod(methodName);
        return add(action, new ActionInvocationHandler(methodName, action));
    }

    abstract BroadcastDispatch<T> add(Object handler, Dispatch<MethodInvocation> dispatch);

    private void assertIsMethod(String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return;
            }
        }
        throw new IllegalArgumentException(String.format("Method %s() not found for listener type %s.", methodName,
                type.getSimpleName()));
    }

    public abstract BroadcastDispatch<T> remove(Object listener);

    public abstract BroadcastDispatch<T> addAll(Collection<? extends T> listeners);

    public abstract BroadcastDispatch<T> removeAll(Collection<?> listeners);

    private static class ActionInvocationHandler implements Dispatch<MethodInvocation> {
        private final String methodName;
        private final Action action;

        ActionInvocationHandler(String methodName, Action action) {
            this.methodName = methodName;
            this.action = action;
        }

        @Override
        public void dispatch(MethodInvocation message) {
            if (message.getMethod().getName().equals(methodName)) {
                action.execute(message.getArguments()[0]);
            }
        }
    }

    private static class EmptyDispatch<T> extends BroadcastDispatch<T> {
        EmptyDispatch(Class<T> type) {
            super(type);
        }

        @Override
        public String toString() {
            return "<empty>";
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public BroadcastDispatch<T> remove(Object listener) {
            return this;
        }

        @Override
        public BroadcastDispatch<T> removeAll(Collection<?> listeners) {
            return this;
        }

        @Override
        BroadcastDispatch<T> add(Object handler, Dispatch<MethodInvocation> dispatch) {
            return new SingletonDispatch<T>(type, handler, dispatch);
        }

        @Override
        public BroadcastDispatch<T> addAll(Collection<? extends T> listeners) {
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            for (T listener : listeners) {
                SingletonDispatch<T> dispatch = new SingletonDispatch<T>(type, listener, new ReflectionDispatch(listener));
                if (!result.contains(dispatch)) {
                    result.add(dispatch);
                }
            }
            if (result.isEmpty()) {
                return this;
            }
            if (result.size() == 1) {
                return result.iterator().next();
            }
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public void dispatch(MethodInvocation message) {
        }
    }

    private static class SingletonDispatch<T> extends BroadcastDispatch<T> {
        private final Object handler;
        private final Dispatch<MethodInvocation> dispatch;

        SingletonDispatch(Class<T> type, Object handler, Dispatch<MethodInvocation> dispatch) {
            super(type);
            this.handler = handler;
            this.dispatch = dispatch;
        }

        @Override
        public String toString() {
            return handler.toString();
        }

        @Override
        public boolean equals(Object obj) {
            SingletonDispatch<T> other = (SingletonDispatch<T>) obj;
            return handler == other.handler || handler.equals(other.handler);
        }

        @Override
        public int hashCode() {
            return handler.hashCode();
        }

        @Override
        BroadcastDispatch<T> add(Object handler, Dispatch<MethodInvocation> dispatch) {
            if (this.handler == handler || this.handler.equals(handler)) {
                return this;
            }
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            result.add(this);
            result.add(new SingletonDispatch<T>(type, handler, dispatch));
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public BroadcastDispatch<T> addAll(Collection<? extends T> listeners) {
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            result.add(this);
            for (T listener : listeners) {
                if (handler == listener || handler.equals(listener)) {
                    continue;
                }
                SingletonDispatch<T> dispatch = new SingletonDispatch<T>(type, listener, new ReflectionDispatch(listener));
                if (!result.contains(dispatch)) {
                    result.add(dispatch);
                }
            }
            if (result.size() == 1) {
                return this;
            }
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public BroadcastDispatch<T> remove(Object listener) {
            if (handler == listener || handler.equals(listener)) {
                return new EmptyDispatch<T>(type);
            }
            return this;
        }

        @Override
        public BroadcastDispatch<T> removeAll(Collection<?> listeners) {
            for (Object listener : listeners) {
                if (handler == listener || handler.equals(listener)) {
                    return new EmptyDispatch<T>(type);
                }
            }
            return this;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void dispatch(MethodInvocation message) {
            dispatch(message, dispatch);
        }
    }

    private static class CompositeDispatch<T> extends BroadcastDispatch<T> {
        private final List<SingletonDispatch<T>> dispatchers;

        CompositeDispatch(Class<T> type, List<SingletonDispatch<T>> dispatchers) {
            super(type);
            this.dispatchers = dispatchers;
        }

        @Override
        public String toString() {
            return dispatchers.toString();
        }

        @Override
        BroadcastDispatch<T> add(Object handler, Dispatch<MethodInvocation> dispatch) {
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            for (SingletonDispatch<T> listener : dispatchers) {
                if (listener.handler == handler || listener.handler.equals(handler)) {
                    return this;
                }
                result.add(listener);
            }
            result.add(new SingletonDispatch<T>(type, handler, dispatch));
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public BroadcastDispatch<T> addAll(Collection<? extends T> listeners) {
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            result.addAll(dispatchers);
            for (T listener : listeners) {
                SingletonDispatch<T> dispatch = new SingletonDispatch<T>(type, listener, new ReflectionDispatch(listener));
                if (!result.contains(dispatch)) {
                    result.add(dispatch);
                }
            }
            if (result.equals(dispatchers)) {
                return this;
            }
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public BroadcastDispatch<T> remove(Object listener) {
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            boolean found = false;
            for (SingletonDispatch<T> dispatch : dispatchers) {
                if (dispatch.handler == listener || dispatch.handler.equals(listener)) {
                    found = true;
                } else {
                    result.add(dispatch);
                }
            }
            if (!found) {
                return this;
            }
            if (result.size() == 1) {
                return result.iterator().next();
            }
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public BroadcastDispatch<T> removeAll(Collection<?> listeners) {
            Set<Object> listenerList = CollectionUtils.toSet(listeners);
            List<SingletonDispatch<T>> result = new ArrayList<SingletonDispatch<T>>();
            for (SingletonDispatch<T> dispatch : this.dispatchers) {
                if (!listenerList.contains(dispatch.handler)) {
                    result.add(dispatch);
                }
            }
            if (result.size() == 0) {
                return new EmptyDispatch<T>(type);
            }
            if (result.size() == 1) {
                return result.iterator().next();
            }
            if (result.equals(this.dispatchers)) {
                return this;
            }
            return new CompositeDispatch<T>(type, result);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void dispatch(MethodInvocation message) {
            dispatch(message, dispatchers.iterator());
        }
    }
}
