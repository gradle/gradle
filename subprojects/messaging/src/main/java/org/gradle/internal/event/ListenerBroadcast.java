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
import org.gradle.internal.dispatch.ProxyDispatchAdapter;

/**
 * <p>Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.</p>
 *
 * <p>Ordering is maintained for events, so that events are delivered to listeners in the order they are generated.
 * Events are delivered to listeners in the order that listeners are added to this broadcaster.</p>
 *
 * @param <T> The listener type.
 */
public class ListenerBroadcast<T> implements Dispatch<MethodInvocation> {
    private final ProxyDispatchAdapter<T> source;
    private final BroadcastDispatch<T> broadcast;
    private final Class<T> type;

    public ListenerBroadcast(Class<T> type) {
        this.type = type;
        broadcast = new BroadcastDispatch<T>(type);
        source = new ProxyDispatchAdapter<T>(broadcast, type);
    }

    /**
     * Returns the broadcaster. Any method call on this object is broadcast to all listeners.
     *
     * @return The broadcaster.
     */
    public T getSource() {
        return source.getSource();
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
     * Returns {@code true} if no listeners are registered with this object.
     *
     * @return {@code true} if no listeners are registered with this object, {@code false} otherwise
     */
    public boolean isEmpty() {
        return broadcast.isEmpty();
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public void add(T listener) {
        broadcast.add(listener);
    }

    /**
     * Adds the given listeners.
     *
     * @param listeners The listeners
     */
    public void addAll(Iterable<? extends T> listeners) {
        for (T listener : listeners) {
            broadcast.add(listener);
        }
    }

    /**
     * Adds a {@link Dispatch} to receive events from this broadcast.
     */
    public void add(Dispatch<MethodInvocation> dispatch) {
        broadcast.add(dispatch);
    }

    /**
     * Adds an action to be executed when the given method is called.
     */
    public void add(String methodName, Action<?> action) {
        broadcast.add(methodName, action);
    }

    /**
     * Removes the given listener.
     *
     * @param listener The listener.
     */
    public void remove(Object listener) {
        broadcast.remove(listener);
    }

    /**
     * Removes the given listeners.
     *
     * @param listeners The listeners
     */
    public void removeAll(Iterable<?> listeners) {
        for (Object listener : listeners) {
            remove(listener);
        }
    }

    /**
     * Removes all listeners.
     */
    public void removeAll() {
        broadcast.removeAll();
    }

    /**
     * Broadcasts the given event to all listeners.
     *
     * @param event The event
     */
    public void dispatch(MethodInvocation event) {
        broadcast.dispatch(event);
    }
}
