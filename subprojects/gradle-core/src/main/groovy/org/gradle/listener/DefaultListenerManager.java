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
    private final Map<Class<?>, ListenerBroadcast> broadcasterCache = new HashMap<Class<?>, ListenerBroadcast>();

    public void addListener(Object listener) {
        if (allListeners.add(listener)) {
            addToBroadcasters(listener);
        }
    }

    public void addListener(Class<?> listenerType, String method, Closure listenerClosure) {
        addListener(new ClosureListener(listenerType, method, listenerClosure));
    }

    public void removeListener(Object listener) {
        if (allListeners.remove(listener)) {
            removeFromBroadcasters(listener);
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
        synchronized (broadcasterCache) {
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

        return broadcaster;
    }

    private void addToBroadcasters(Object listener) {
        synchronized (broadcasterCache) {
            for (ListenerBroadcast broadcaster : broadcasterCache.values()) {
                addToBroadcasterIfTypeMatches(broadcaster, listener);
            }
        }
    }

    private void removeFromBroadcasters(Object listener) {
        synchronized (broadcasterCache) {
            for (ListenerBroadcast broadcaster : broadcasterCache.values()) {
                removeFromBroadcasterIfTypeMatches(broadcaster, listener);
            }
        }
    }

    private void addToBroadcasterIfTypeMatches(ListenerBroadcast broadcaster, Object listener) {
        if (listener instanceof ClosureListener) {
            ClosureListener closureListener = (ClosureListener) listener;
            if (broadcaster.getType().isAssignableFrom(closureListener.listenerType)) {
                broadcaster.add(closureListener.method, closureListener.closure);
            }
        } else if (broadcaster.getType().isAssignableFrom(listener.getClass())) {
            broadcaster.add(listener);
        }
    }

    private void removeFromBroadcasterIfTypeMatches(ListenerBroadcast broadcaster, Object listener) {
        if (broadcaster.getType().isAssignableFrom(listener.getClass())) {
            broadcaster.remove(listener);
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
