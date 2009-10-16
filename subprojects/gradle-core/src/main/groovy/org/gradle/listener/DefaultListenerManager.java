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

import java.util.*;

@SuppressWarnings({"unchecked"})
public class DefaultListenerManager implements ListenerManager {
    private final Set<Object> allListeners = new LinkedHashSet<Object>();
    private final Set<Object> loggers = new LinkedHashSet<Object>();
    private final Map<Class<?>, ListenerBroadcast> broadcasterCache = new HashMap<Class<?>, ListenerBroadcast>();
    private final Object lock = new Object();

    public void addListener(Object listener) {
        synchronized (lock) {
            if (allListeners.add(listener)) {
                for (ListenerBroadcast broadcaster : broadcasterCache.values()) {
                    addToBroadcasterIfTypeMatches(broadcaster, listener);
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
                for (ListenerBroadcast broadcaster : broadcasterCache.values()) {
                    broadcaster.remove(listener);
                }
            }
        }
    }

    public void useLogger(Object logger) {
        synchronized (lock) {
            if (loggers.add(logger)) {
                for (ListenerBroadcast broadcaster : broadcasterCache.values()) {
                    addLoggerToBroadcasterIfTypeMatchers(broadcaster, logger);
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
            ListenerBroadcast<T> broadcaster = broadcasterCache.get(listenerClass);
            if (broadcaster == null) {
                broadcaster = createBroadcaster(listenerClass);
                broadcasterCache.put(listenerClass, broadcaster);
            }

            return broadcaster;
        }
    }

    private <T> ListenerBroadcast<T> createBroadcaster(Class<T> listenerClass) {
        ListenerBroadcast<T> broadcaster = new ListenerBroadcast<T>(listenerClass);
        for (Object listener : allListeners) {
            addToBroadcasterIfTypeMatches(broadcaster, listener);
        }
        for (Object logger : loggers) {
            addLoggerToBroadcasterIfTypeMatchers(broadcaster, logger);
        }

        return broadcaster;
    }

    private void addLoggerToBroadcasterIfTypeMatchers(ListenerBroadcast broadcaster, Object listener) {
        if (broadcaster.getType().isAssignableFrom(listener.getClass())) {
            Object oldLogger = broadcaster.setLogger(listener);
            if (loggers.remove(oldLogger)) {
                removeLogger(oldLogger);
            }
        }
    }

    private void removeLogger(Object oldLogger) {
        for (ListenerBroadcast listenerBroadcast : broadcasterCache.values()) {
            listenerBroadcast.remove(oldLogger);
        }
    }

    private void addToBroadcasterIfTypeMatches(ListenerBroadcast broadcaster, Object listener) {
        if (listener instanceof ClosureListener) {
            ClosureListener closureListener = (ClosureListener) listener;
            if (broadcaster.getType().isAssignableFrom(closureListener.listenerType)) {
                broadcaster.add(closureListener.method, closureListener.closure);
            }
        } else {
            broadcaster.maybeAdd(listener);
        }
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
}
