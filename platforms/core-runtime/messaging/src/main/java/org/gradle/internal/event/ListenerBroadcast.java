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
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;

/**
 * <p>Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.</p>
 *
 * <p>Ordering is maintained for events, so that events are delivered to listeners in the order they are generated.
 * Events are delivered to listeners in the order that listeners are added to this broadcaster.</p>
 *
 * @param <T> The listener type.
 */
@ThreadSafe
public class ListenerBroadcast<T> implements Dispatch<MethodInvocation> {

    private final Class<T> type;

    @Nullable
    private ProxyDispatchAdapter<T> source;

    private volatile BroadcastDispatch<T> broadcast;

    public ListenerBroadcast(Class<T> type) {
        this.type = type;
        this.broadcast = BroadcastDispatch.empty(type);
    }

    /**
     * Returns the broadcaster. Any method call on this object is broadcast to all listeners.
     *
     * @return The broadcaster.
     */
    public synchronized T getSource() {
        if (source == null) {
            source = new ProxyDispatchAdapter<T>(this, type);
        }
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
     * Returns the number of listeners that are registered with this object.
     */
    public int size() {
        return broadcast.size();
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public synchronized void add(T listener) {
        broadcast = broadcast.add(listener);
    }

    /**
     * Adds the given listeners.
     *
     * @param listeners The listeners
     */
    public synchronized void addAll(Collection<? extends T> listeners) {
        broadcast = broadcast.addAll(listeners);
    }

    /**
     * Adds a {@link Dispatch} to receive events from this broadcast.
     */
    public synchronized void add(Dispatch<MethodInvocation> dispatch) {
        broadcast = broadcast.add(dispatch);
    }

    /**
     * Adds an action to be executed when the given method is called.
     */
    public synchronized void add(String methodName, Action<?> action) {
        broadcast = broadcast.add(methodName, action);
    }

    /**
     * Removes the given listener.
     *
     * @param listener The listener.
     */
    public synchronized void remove(Object listener) {
        broadcast = broadcast.remove(listener);
    }

    /**
     * Removes the given listeners.
     *
     * @param listeners The listeners
     */
    public synchronized void removeAll(Collection<?> listeners) {
        broadcast = broadcast.removeAll(listeners);
    }

    /**
     * Removes all listeners.
     */
    public synchronized void removeAll() {
        broadcast = BroadcastDispatch.empty(type);
    }

    /**
     * Removes all listeners and replaces them with the given listener.
     */
    synchronized void replaceWith(Dispatch<MethodInvocation> dispatch) {
        broadcast = BroadcastDispatch.empty(type).add(dispatch);
    }

    /**
     * Broadcasts the given event to all listeners.
     *
     * @param event The event
     */
    @Override
    public void dispatch(MethodInvocation event) {
        broadcast.dispatch(event);
    }

    public void visitListeners(Action<T> visitor) {
        broadcast.visitListeners(visitor);
    }

    public void visitListenersUntyped(Action<Object> visitor) {
        broadcast.visitListenersUntyped(visitor);
    }

    /**
     * Returns a new {@link ListenerBroadcast} with the same {@link BroadcastDispatch} as this class.
     */
    public ListenerBroadcast<T> copy() {
        ListenerBroadcast<T> result = new ListenerBroadcast<T>(type);
        result.broadcast = this.broadcast;
        return result;
    }
}
