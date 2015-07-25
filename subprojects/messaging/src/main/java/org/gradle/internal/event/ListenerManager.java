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

/**
 * Unified manager for all listeners for Gradle.  Provides a simple way to find all listeners of a given type in the
 * system.
 *
 * <p>While the methods work with any Object, in general only interfaces should be used as listener types.
 *
 * <p>Implementations are thread-safe: A listener is notified by at most 1 thread at a time, and so do not need to be thread-safe. All listeners
 * of a given type received events in the same order. Listeners can be added and removed at any time.
 */
public interface ListenerManager {
    /**
     * Added a listener.  A single object can implement multiple interfaces, and all interfaces are registered by a
     * single invocation of this method.  There is no order dependency: if a broadcaster has already been made for type
     * T, the listener will be registered with it if <code>(listener instanceof T)</code> returns true.
     *
     * <p>A listener will be used by a single thread at a time, so the listener implementation does not need to be thread-safe.
     *
     * <p>The listener will not receive events that are currently being broadcast from some other thread.
     *
     * @param listener the listener to add.
     */
    void addListener(Object listener);

    /**
     * Removes a listener.  A single object can implement multiple interfaces, and all interfaces are unregistered by a
     * single invocation of this method.  There is no order dependency: if a broadcaster has already been made for type
     * T, the listener will be unregistered with it if <code>(listener instanceof T)</code> returns true.
     *
     * <p>When this method returns, the listener will not be in use and will not receive any further events.
     *
     * @param listener the listener to remove.
     */
    void removeListener(Object listener);

    /**
     * Returns a broadcaster for the given listenerClass. Any method invoked on the broadcaster is forwarded to all registered
     * listeners of the given type. This is done synchronously. Any listener method with a non-void return type will return a null.
     * Exceptions are propagated, and multiple failures are packaged up in a {@link ListenerNotificationException}.
     *
     * <p>A listener is used by a single thread at a time.
     *
     * <p>If there are no registered listeners for the requested type, a broadcaster is returned which does not forward method calls to any listeners.
     * The returned broadcasters are live, that is their list of listeners can be updated by calls to {@link #addListener(Object)} and {@link
     * #removeListener(Object)} after they have been returned.  Broadcasters are also cached, so that repeatedly calling
     * this method with the same listenerClass returns the same broadcaster object.
     *
     * @param listenerClass The type of listener for which to return a broadcaster.
     * @return The broadcaster that forwards method calls to all listeners of the same type that have been (or will be)
     *         registered with this manager.
     */
    <T> T getBroadcaster(Class<T> listenerClass);

    /**
     * Returns a broadcaster for the given listenerClass.  The returned broadcaster will delegate to the canonical
     * broadcaster returned by {@link #getBroadcaster(Class)} for the given listener type.  However, it can also have
     * listeners assigned/removed directly to/from it.  This allows these "anonymous" broadcasters to specialize what
     * listeners receive messages.  Each call creates a new broadcaster, so that client code can create as many "facets"
     * of the listener as they need.  The client code must provide some way for its users to register listeners on the
     * specialized broadcasters.
     *
     * <p>The returned value is not thread-safe.</p>
     *
     * @param listenerClass The type of listener for which to create a broadcaster.
     * @return A broadcaster that forwards method calls to all listeners assigned to it, or of the same type that have
     *         been (or will be) registered with this manager.
     */
    <T> ListenerBroadcast<T> createAnonymousBroadcaster(Class<T> listenerClass);

    /**
     * Uses the given object as a logger. Each listener class has exactly one logger associated with it. Any existing
     * logger for the listener class is discarded. Loggers are otherwise treated the same way as listeners.
     *
     * @param logger The new logger to use.
     */
    void useLogger(Object logger);

    /**
     * Creates a child {@code ListenerManager}. All events broadcast in the child will be received by the listeners
     * registered in the parent. However, the reverse is not true: events broadcast in the parent are not received
     * by the listeners in the children. The child inherits the loggers of its parent, though these can be replaced.
     *
     * @return The child
     */
    ListenerManager createChild();
}
