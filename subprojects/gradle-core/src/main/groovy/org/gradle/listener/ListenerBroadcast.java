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
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.messaging.dispatch.*;

/**
 * <p>Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.</p>
 *
 * <p>Ordering is maintained for events, so that events are delivered to listeners in the order they are generated.
 * Events are delivered to listeners in the order that listeners are added to this broadcaster.</p>
 *
 * @param <T> The listener type.
 */
public class ListenerBroadcast<T> implements StoppableDispatch<MethodInvocation> {
    private final ProxyDispatchAdapter<T> source;
    private final BroadcastDispatch<T> broadcast;
    private final Class<T> type;
    private final StoppableDispatch<MethodInvocation> dispatch;

    public ListenerBroadcast(Class<T> type) {
        this(type, new Transformer<StoppableDispatch<MethodInvocation>>() {
            public StoppableDispatch<MethodInvocation> transform(StoppableDispatch<MethodInvocation> original) {
                return original;
            }
        });
    }

    protected ListenerBroadcast(Class<T> type, Transformer<StoppableDispatch<MethodInvocation>> transformer) {
        this.type = type;
        broadcast = new BroadcastDispatch<T>(type);
        dispatch = transformer.transform(broadcast);
        source = new ProxyDispatchAdapter<T>(type, dispatch);
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
     * Adds the given listener if it is an instance of the listener type.
     *
     * @param listener The listener
     */
    public void maybeAdd(Object listener) {
        if (type.isInstance(listener)) {
            add(type.cast(listener));
        }
    }

    /**
     * Adds a {@link org.gradle.messaging.dispatch.Dispatch} to receive events from this broadcast.
     */
    public void add(Dispatch<MethodInvocation> dispatch) {
        broadcast.add(dispatch);
    }
    
    /**
     * Adds a closure to be notified when the given method is called.
     */
    public void add(String methodName, Closure closure) {
        broadcast.add(methodName, closure);
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
     * Broadcasts the given event to all listeners.
     *
     * @param event The event
     */
    public void dispatch(MethodInvocation event) {
        dispatch.dispatch(event);
    }

    public void stop() {
        dispatch.stop();
    }
}
