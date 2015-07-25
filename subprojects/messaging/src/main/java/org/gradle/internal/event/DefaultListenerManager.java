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

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ReflectionDispatch;

import java.util.*;

@SuppressWarnings({"unchecked"})
public class DefaultListenerManager implements ListenerManager {
    private final Set<Object> allListeners = new LinkedHashSet<Object>();
    private final Set<Object> allLoggers = new LinkedHashSet<Object>();
    private final Map<Class<?>, EventBroadcast> broadcasters = new HashMap<Class<?>, EventBroadcast>();
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
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeAdd(listener);
                }
            }
        }
    }

    public void removeListener(Object listener) {
        synchronized (lock) {
            if (allListeners.remove(listener)) {
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeRemove(listener);
                }
            }
        }
    }

    public void useLogger(Object logger) {
        synchronized (lock) {
            if (allLoggers.add(logger)) {
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeSetLogger(logger);
                }
            }
        }
    }

    public <T> T getBroadcaster(Class<T> listenerClass) {
        return getBroadcasterInternal(listenerClass).getBroadcaster();
    }

    public <T> ListenerBroadcast<T> createAnonymousBroadcaster(Class<T> listenerClass) {
        ListenerBroadcast<T> broadcast = new ListenerBroadcast(listenerClass);
        broadcast.add(getBroadcasterInternal(listenerClass).getDispatch(true));
        return broadcast;
    }

    private <T> EventBroadcast<T> getBroadcasterInternal(Class<T> listenerClass) {
        synchronized (lock) {
            EventBroadcast<T> broadcaster = broadcasters.get(listenerClass);
            if (broadcaster == null) {
                broadcaster = new EventBroadcast<T>(listenerClass);
                broadcasters.put(listenerClass, broadcaster);
                for (Object listener : allListeners) {
                    broadcaster.maybeAdd(listener);
                }
                for (Object logger : allLoggers) {
                    broadcaster.maybeSetLogger(logger);
                }
            }
            return broadcaster;
        }
    }

    public ListenerManager createChild() {
        return new DefaultListenerManager(this);
    }

    private class EventBroadcast<T> {
        private final Object lock = new Object();
        private final Class<T> type;
        private final ProxyDispatchAdapter<T> source;
        private final ListenerDispatch dispatch;
        private final ListenerDispatch dispatchNoLogger;
        private final List<Dispatch<MethodInvocation>> dispatchers = new ArrayList<Dispatch<MethodInvocation>>();
        private final List<Dispatch<MethodInvocation>> dispatchersNoLogger = new ArrayList<Dispatch<MethodInvocation>>();
        private final Map<Object, Dispatch<MethodInvocation>> listeners = new LinkedHashMap<Object, Dispatch<MethodInvocation>>();
        private Dispatch<MethodInvocation> logger;
        private Dispatch<MethodInvocation> parentDispatch;

        EventBroadcast(Class<T> type) {
            this.type = type;
            dispatch = new ListenerDispatch(type, dispatchers);
            dispatchNoLogger = new ListenerDispatch(type, dispatchersNoLogger);
            if (parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(true);
            }
            rebuild();
            source = new ProxyDispatchAdapter<T>(dispatch, type);
        }

        Dispatch<MethodInvocation> getDispatch(boolean includeLogger) {
            return includeLogger ? dispatch : dispatchNoLogger;
        }

        T getBroadcaster() {
            return source.getSource();
        }

        void maybeAdd(Object listener) {
            if (type.isInstance(listener)) {
                listeners.put(listener, new ReflectionDispatch(listener));
                rebuild();
            }
        }

        void maybeRemove(Object listener) {
            if (listeners.remove(listener) != null) {
                rebuild();
            }
        }

        void maybeSetLogger(Object candidate) {
            if (type.isInstance(candidate)) {
                if (logger == null && parent != null) {
                    parentDispatch = parent.getBroadcasterInternal(type).getDispatch(false);
                }
                logger = new ReflectionDispatch(candidate);
                rebuild();
            }
        }

        private void rebuild() {
            dispatchers.clear();
            dispatchersNoLogger.clear();
            if (logger != null) {
                dispatchers.add(logger);
            }
            if (parentDispatch != null) {
                dispatchers.add(parentDispatch);
                dispatchersNoLogger.add(parentDispatch);
            }
            dispatchers.addAll(listeners.values());
            dispatchersNoLogger.addAll(listeners.values());
        }

        private class ListenerDispatch extends AbstractBroadcastDispatch<T> {
            private final List<Dispatch<MethodInvocation>> dispatchers;

            public ListenerDispatch(Class<T> type, List<Dispatch<MethodInvocation>> dispatchers) {
                super(type);
                this.dispatchers = dispatchers;
            }

            @Override
            public void dispatch(MethodInvocation invocation) {
                synchronized (lock) {
                    super.dispatch(invocation);
                }
            }

            @Override
            protected List<Dispatch<MethodInvocation>> getHandlers() {
                return dispatchers;
            }
        }
    }
}
