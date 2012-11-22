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

import groovy.lang.Closure;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ReflectionDispatch;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked"})
public class DefaultListenerManager implements ListenerManager {
    private final Set<Object> allListeners = new LinkedHashSet<Object>();
    private final Set<Object> allLoggers = new LinkedHashSet<Object>();
    private final Map<Class<?>, ListenerBroadcast> broadcasters = new HashMap<Class<?>, ListenerBroadcast>();
    private final Map<Class<?>, LoggerDispatch> loggers = new HashMap<Class<?>, LoggerDispatch>();
    private final Map<Class<?>, BroadcastDispatch> dispatchers = new HashMap<Class<?>, BroadcastDispatch>();
    private final Object lock = new Object();
    private final DefaultListenerManager parent;

    public DefaultListenerManager() {
        this(null);
    }

    private DefaultListenerManager(DefaultListenerManager parent) {
        this.parent = parent;
    }

    public void addListener(Object listener) {
        synchronized (lock) {
            if (allListeners.add(listener)) {
                for (BroadcastDispatch<?> broadcaster : dispatchers.values()) {
                    maybeAddToDispatcher(broadcaster, listener);
                }
            }
        }
    }

    public void addListener(Class<?> listenerType, String method, Closure listenerClosure) {
        addListener(new ClosureListener(listenerType, method, listenerClosure));
    }

    public void removeListener(Object listener) {
        synchronized (lock) {
            if (allListeners.remove(listener)) {
                for (BroadcastDispatch<?> broadcaster : dispatchers.values()) {
                    broadcaster.remove(listener);
                }
            }
        }
    }

    public void useLogger(Object logger) {
        synchronized (lock) {
            if (allLoggers.add(logger)) {
                for (LoggerDispatch dispatch : loggers.values()) {
                    dispatch.maybeSetLogger(logger);
                }
            }
        }
    }

    public <T> T getBroadcaster(Class<T> listenerClass) {
        return getBroadcasterInternal(listenerClass).getSource();
    }

    public <T> ListenerBroadcast<T> createAnonymousBroadcaster(Class<T> listenerClass) {
        ListenerBroadcast<T> broadcast = new ListenerBroadcast(listenerClass);
        broadcast.add(getBroadcasterInternal(listenerClass).getSource());
        return broadcast;
    }

    private <T> ListenerBroadcast<T> getBroadcasterInternal(Class<T> listenerClass) {
        synchronized (lock) {
            ListenerBroadcast<T> broadcaster = broadcasters.get(listenerClass);
            if (broadcaster == null) {
                broadcaster = new ListenerBroadcast<T>(listenerClass);
                broadcaster.add(getLogger(listenerClass));
                broadcaster.add(getDispatcher(listenerClass));
                if (parent != null) {
                    broadcaster.add(parent.getDispatcher(listenerClass));
                }
                broadcasters.put(listenerClass, broadcaster);
            }

            return broadcaster;
        }
    }

    private <T> BroadcastDispatch<T> getDispatcher(Class<T> listenerClass) {
        synchronized (lock) {
            BroadcastDispatch<T> dispatcher = dispatchers.get(listenerClass);
            if (dispatcher == null) {
                dispatcher = new BroadcastDispatch<T>(listenerClass);
                dispatchers.put(listenerClass, dispatcher);
                for (Object listener : allListeners) {
                    maybeAddToDispatcher(dispatcher, listener);
                }
            }
            return dispatcher;
        }
    }

    private LoggerDispatch getLogger(Class<?> listenerClass) {
        synchronized (lock) {
            LoggerDispatch dispatch = loggers.get(listenerClass);
            if (dispatch == null) {
                dispatch = new LoggerDispatch(listenerClass, parent == null ? null : parent.getLogger(listenerClass));
                for (Object logger : allLoggers) {
                    dispatch.maybeSetLogger(logger);
                }
                loggers.put(listenerClass, dispatch);
            }
            return dispatch;
        }
    }

    private void maybeAddToDispatcher(BroadcastDispatch broadcaster, Object listener) {
        if (listener instanceof ClosureListener) {
            ClosureListener closureListener = (ClosureListener) listener;
            if (broadcaster.getType().isAssignableFrom(closureListener.listenerType)) {
                broadcaster.add(new ClosureBackedMethodInvocationDispatch(closureListener.method, closureListener.closure));
            }
        } else if (broadcaster.getType().isInstance(listener)) {
            broadcaster.add(listener);
        }
    }

    public ListenerManager createChild() {
        return new DefaultListenerManager(this);
    }

    private static class ClosureListener {
        final Class<?> listenerType;
        final String method;
        final Closure closure;

        private ClosureListener(Class<?> listenerType, String method, Closure closure) {
            this.listenerType = listenerType;
            this.method = method;
            this.closure = closure;
        }
    }

    private static class LoggerDispatch implements Dispatch<MethodInvocation> {
        private final Class<?> type;
        private Dispatch<MethodInvocation> dispatch;

        private LoggerDispatch(Class<?> type, LoggerDispatch parentDispatch) {
            this.type = type;
            this.dispatch = parentDispatch;
        }

        public void dispatch(MethodInvocation message) {
            if (dispatch != null) {
                dispatch.dispatch(message);
            }
        }

        public void maybeSetLogger(Object logger) {
            if (type.isInstance(logger)) {
                dispatch = new ReflectionDispatch(logger);
            }
        }
    }
}
