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

import org.gradle.internal.Cast;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ProxyDispatchAdapter;
import org.gradle.internal.dispatch.ReflectionDispatch;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultListenerManager implements ListenerManager {
    private final Map<Object, ListenerDetails> allListeners = new LinkedHashMap<Object, ListenerDetails>();
    private final Map<Object, ListenerDetails> allLoggers = new LinkedHashMap<Object, ListenerDetails>();
    private final Map<Class<?>, EventBroadcast<?>> broadcasters = new ConcurrentHashMap<Class<?>, EventBroadcast<?>>();
    private final Object lock = new Object();
    private final Class<? extends Scope> scope;
    private final DefaultListenerManager parent;

    public DefaultListenerManager(Class<? extends Scope> scope) {
        this(scope, null);
    }

    private DefaultListenerManager(Class<? extends Scope> scope, DefaultListenerManager parent) {
        this.scope = scope;
        this.parent = parent;
    }

    @Override
    public void addListener(Object listener) {
        ListenerDetails details = null;
        synchronized (lock) {
            if (!allListeners.containsKey(listener)) {
                details = new ListenerDetails(listener);
                allListeners.put(listener, details);
            }
        }
        if (details != null) {
            details.useAsListener();
        }
    }

    @Override
    public void removeListener(Object listener) {
        ListenerDetails details;
        synchronized (lock) {
            details = allListeners.remove(listener);
            if (details != null) {
                details.disconnect();
            }
        }
        if (details != null) {
            details.remove();
        }
    }

    @Override
    public void useLogger(Object logger) {
        ListenerDetails details = null;
        synchronized (lock) {
            if (!allLoggers.containsKey(logger)) {
                details = new ListenerDetails(logger);
                allLoggers.put(logger, details);
            }
        }
        if (details != null) {
            details.useAsLogger();
        }
    }

    @Override
    public <T> boolean hasListeners(Class<T> listenerClass) {
        EventBroadcast<T> broadcaster = getBroadcasterInternal(listenerClass);
        return !broadcaster.listeners.isEmpty();
    }

    @Override
    public <T> T getBroadcaster(Class<T> listenerClass) {
        assertCanBroadcast(listenerClass);
        return getBroadcasterInternal(listenerClass).getBroadcaster();
    }

    @Override
    public <T> AnonymousListenerBroadcast<T> createAnonymousBroadcaster(Class<T> listenerClass) {
        assertCanBroadcast(listenerClass);
        AnonymousListenerBroadcast<T> broadcast = new AnonymousListenerBroadcast<T>(listenerClass);
        broadcast.add(getBroadcasterInternal(listenerClass).getDispatch(true));
        return broadcast;
    }

    private <T> EventBroadcast<T> getBroadcasterInternal(Class<T> listenerClass) {
        synchronized (lock) {
            EventBroadcast<T> broadcaster = Cast.uncheckedCast(broadcasters.get(listenerClass));
            if (broadcaster == null) {
                broadcaster = new EventBroadcast<T>(listenerClass);
                broadcasters.put(listenerClass, broadcaster);
                for (ListenerDetails listener : allListeners.values()) {
                    broadcaster.maybeAdd(listener);
                }
                for (ListenerDetails logger : allLoggers.values()) {
                    broadcaster.maybeSetLogger(logger);
                }
            }
            return broadcaster;
        }
    }

    private <T> void assertCanBroadcast(Class<T> listenerClass) {
        EventScope scope = listenerClass.getAnnotation(EventScope.class);
        if (scope == null) {
            throw new IllegalArgumentException(String.format("Listener type %s is not annotated with @EventScope.", listenerClass.getName()));
        }
        if (!scope.value().equals(this.scope)) {
            throw new IllegalArgumentException(String.format("Listener type %s with scope %s cannot be used to generate events in scope %s.", listenerClass.getName(), scope.value().getSimpleName(), this.scope.getSimpleName()));
        }
    }

    @Override
    public ListenerManager createChild(Class<? extends Scope> scope) {
        return new DefaultListenerManager(scope, this);
    }

    /**
     * Manages the listeners and state for a given listener type
     */
    private class EventBroadcast<T> implements Dispatch<MethodInvocation> {
        private final Class<T> type;
        private final ListenerDispatch dispatch;
        private final ListenerDispatch dispatchNoLogger;

        private volatile ProxyDispatchAdapter<T> source;
        private final Set<ListenerDetails> listeners = new LinkedHashSet<ListenerDetails>();
        private final List<Runnable> queuedOperations = new LinkedList<Runnable>();
        private final ReentrantLock broadcasterLock = new ReentrantLock();
        private ListenerDetails logger;
        private Dispatch<MethodInvocation> parentDispatch;
        private List<Dispatch<MethodInvocation>> allWithLogger = Collections.emptyList();
        private List<Dispatch<MethodInvocation>> allWithNoLogger = Collections.emptyList();

        EventBroadcast(Class<T> type) {
            this.type = type;
            dispatch = new ListenerDispatch(type, true);
            dispatchNoLogger = new ListenerDispatch(type, false);
            if (parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(true);
                invalidateDispatchCache();
            }
        }

        @Override
        public void dispatch(MethodInvocation message) {
            dispatch.dispatch(message);
        }

        Dispatch<MethodInvocation> getDispatch(boolean includeLogger) {
            return includeLogger ? dispatch : dispatchNoLogger;
        }

        T getBroadcaster() {
            if (source == null) {
                synchronized (this) {
                    if (source == null) {
                        source = new ProxyDispatchAdapter<T>(this, type);
                    }
                }
            }
            return source.getSource();
        }

        private void invalidateDispatchCache() {
            ensureAllWithLoggerInitialized();
            ensureAllWithoutLoggerInitialized();
        }

        void maybeAdd(final ListenerDetails listener) {
            if (type.isInstance(listener.listener)) {
                if (broadcasterLock.tryLock()) {
                    try {
                        listeners.add(listener);
                        invalidateDispatchCache();
                    } finally {
                        broadcasterLock.unlock();
                    }
                } else {
                    synchronized (queuedOperations) {
                        queuedOperations.add(new Runnable() {
                            @Override
                            public void run() {
                                listeners.add(listener);
                            }
                        });
                    }
                }
            }
        }

        void maybeRemove(final ListenerDetails listener) {
            if (broadcasterLock.tryLock()) {
                try {
                    if (listeners.remove(listener)) {
                        invalidateDispatchCache();
                    }
                } finally {
                    broadcasterLock.unlock();
                }
            } else {
                synchronized (queuedOperations) {
                    queuedOperations.add(new Runnable() {
                        @Override
                        public void run() {
                            listeners.remove(listener);
                        }
                    });
                }
            }
        }

        void maybeSetLogger(final ListenerDetails candidate) {
            if (type.isInstance(candidate.listener)) {
                if (broadcasterLock.tryLock()) {
                    try {
                        doSetLogger(candidate);
                        invalidateDispatchCache();
                    } finally {
                        broadcasterLock.unlock();
                    }
                } else {
                    synchronized (queuedOperations) {
                        queuedOperations.add(new Runnable() {
                            @Override
                            public void run() {
                                doSetLogger(candidate);
                            }
                        });
                    }
                }
            }
        }

        private void doSetLogger(ListenerDetails candidate) {
            if (logger == null && parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(false);
            }
            logger = candidate;
        }

        private List<Dispatch<MethodInvocation>> startNotification(boolean includeLogger) {
            takeOwnership();

            // Take a snapshot while holding lock
            List<Dispatch<MethodInvocation>> result = includeLogger ? allWithLogger : allWithNoLogger;
            doStartNotification(result);
            return result;
        }

        private void doStartNotification(List<Dispatch<MethodInvocation>> result) {
            for (Dispatch<MethodInvocation> dispatch : result) {
                if (dispatch instanceof ListenerDetails) {
                    ListenerDetails listenerDetails = (ListenerDetails) dispatch;
                    listenerDetails.startNotification();
                }
            }
        }

        private void ensureAllWithoutLoggerInitialized() {
            if (parentDispatch == null && listeners.isEmpty()) {
                allWithNoLogger = Collections.emptyList();
            } else {
                List<Dispatch<MethodInvocation>> dispatchers = new ArrayList<Dispatch<MethodInvocation>>();
                if (parentDispatch != null) {
                    dispatchers.add(parentDispatch);
                }
                dispatchers.addAll(listeners);
                allWithNoLogger = dispatchers;
            }
        }

        private void ensureAllWithLoggerInitialized() {
            if (logger == null && parentDispatch == null && listeners.isEmpty()) {
                allWithLogger = Collections.emptyList();
            } else {
                allWithLogger = buildAllWithLogger();
            }
        }

        private void takeOwnership() {
            // Mark this listener type as being notified
            if (broadcasterLock.isHeldByCurrentThread()) {
                throw new IllegalStateException(String.format("Cannot notify listeners of type %s as these listeners are already being notified.", type.getSimpleName()));
            }

            broadcasterLock.lock();
        }

        private List<Dispatch<MethodInvocation>> buildAllWithLogger() {
            List<Dispatch<MethodInvocation>> result = new ArrayList<Dispatch<MethodInvocation>>();
            if (logger != null) {
                result.add(logger);
            }
            if (parentDispatch != null) {
                result.add(parentDispatch);
            }
            result.addAll(listeners);
            return result;
        }

        private void endNotification(List<Dispatch<MethodInvocation>> dispatchers) {
            for (Dispatch<MethodInvocation> dispatcher : dispatchers) {
                if (dispatcher instanceof ListenerDetails) {
                    ListenerDetails listener = (ListenerDetails) dispatcher;
                    listener.endNotification();
                }
            }
            try {
                synchronized (queuedOperations) {
                    if (!queuedOperations.isEmpty()) {
                        for (Runnable queuedOperation : queuedOperations) {
                            queuedOperation.run();
                        }
                        invalidateDispatchCache();
                    }
                }
            } finally {
                broadcasterLock.unlock();
            }
        }

        private class ListenerDispatch extends AbstractBroadcastDispatch<T> {
            private final boolean includeLogger;

            ListenerDispatch(Class<T> type, boolean includeLogger) {
                super(type);
                this.includeLogger = includeLogger;
            }

            @Override
            public void dispatch(MethodInvocation invocation) {
                List<Dispatch<MethodInvocation>> dispatchers = startNotification(includeLogger);
                try {
                    if (!dispatchers.isEmpty()) {
                        dispatch(invocation, dispatchers.iterator());
                    }
                } finally {
                    endNotification(dispatchers);
                }
            }
        }
    }

    /**
     * Holds state about a particular listener
     */
    private class ListenerDetails implements Dispatch<MethodInvocation> {
        final Object listener;
        final Dispatch<MethodInvocation> dispatch;
        final AtomicBoolean removed = new AtomicBoolean();
        final ReentrantLock notifyingLock = new ReentrantLock();

        public ListenerDetails(Object listener) {
            this.listener = listener;
            this.dispatch = new ReflectionDispatch(listener);
        }

        void disconnect() {
            removed.set(true);
        }

        @Override
        public void dispatch(MethodInvocation message) {
            if (!removed.get()) {
                dispatch.dispatch(message);
            }
        }

        void startNotification() {
            notifyingLock.lock();
        }

        void endNotification() {
            notifyingLock.unlock();
        }

        void remove() {
            // block until the listener has finished notifying.
            notifyingLock.lock();
            try {
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeRemove(this);
                }
            } finally {
                notifyingLock.unlock();
            }
        }

        void useAsLogger() {
            for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                broadcaster.maybeSetLogger(this);
            }
        }

        void useAsListener() {
            for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                broadcaster.maybeAdd(this);
            }
        }
    }
}
