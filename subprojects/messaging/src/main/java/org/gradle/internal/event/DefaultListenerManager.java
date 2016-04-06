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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ProxyDispatchAdapter;
import org.gradle.internal.dispatch.ReflectionDispatch;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"unchecked"})
public class DefaultListenerManager implements ListenerManager {
    private final Map<Object, ListenerDetails> allListeners = new LinkedHashMap<Object, ListenerDetails>();
    private final Map<Object, ListenerDetails> allLoggers = new LinkedHashMap<Object, ListenerDetails>();
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
            if (!allListeners.containsKey(listener)) {
                ListenerDetails details = new ListenerDetails(listener);
                allListeners.put(listener, details);
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeAdd(details);
                }
            }
        }
    }

    public void removeListener(Object listener) {
        synchronized (lock) {
            ListenerDetails details = allListeners.remove(listener);
            if (details != null) {
                details.disconnect();
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeRemove(details);
                }
                details.untilNotInUse(Thread.currentThread());
            }
        }
    }

    public void useLogger(Object logger) {
        synchronized (lock) {
            if (!allLoggers.containsKey(logger)) {
                ListenerDetails details = new ListenerDetails(logger);
                allLoggers.put(logger, details);
                for (EventBroadcast<?> broadcaster : broadcasters.values()) {
                    broadcaster.maybeSetLogger(details);
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

    public ListenerManager createChild() {
        return new DefaultListenerManager(this);
    }

    private class EventBroadcast<T> {
        private final Class<T> type;
        private final ProxyDispatchAdapter<T> source;
        private final ListenerDispatch dispatch;
        private final ListenerDispatch dispatchNoLogger;

        // The following state is protected by lock
        private final Set<ListenerDetails> listeners = new LinkedHashSet<ListenerDetails>();
        private ListenerDetails logger;
        private Dispatch<MethodInvocation> parentDispatch;
        private Thread owner;

        EventBroadcast(Class<T> type) {
            this.type = type;
            dispatch = new ListenerDispatch(type, true);
            dispatchNoLogger = new ListenerDispatch(type, false);
            if (parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(true);
            }
            source = new ProxyDispatchAdapter<T>(dispatch, type);
        }

        Dispatch<MethodInvocation> getDispatch(boolean includeLogger) {
            return includeLogger ? dispatch : dispatchNoLogger;
        }

        T getBroadcaster() {
            return source.getSource();
        }

        // Must be holding lock
        void maybeAdd(ListenerDetails listener) {
            if (type.isInstance(listener.listener)) {
                listeners.add(listener);
            }
        }

        // Must be holding lock
        void maybeRemove(ListenerDetails listener) {
            listeners.remove(listener);
            // Another thread may be using listener
        }

        // Must be holding lock
        void maybeSetLogger(ListenerDetails candidate) {
            if (type.isInstance(candidate.listener)) {
                if (logger == null && parent != null) {
                    parentDispatch = parent.getBroadcasterInternal(type).getDispatch(false);
                }
                logger = candidate;
            }
        }

        private List<Dispatch<MethodInvocation>> startNotification(boolean includeLogger) {
            synchronized (lock) {
                // Mark this listener type as being notified
                while (owner != null) {
                    if (owner == Thread.currentThread()) {
                        throw new IllegalStateException(String.format("Cannot notify listeners of type %s as these listeners are already being notified.", type.getSimpleName()));
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                owner = Thread.currentThread();

                // Take a snapshot while holding lock
                List<Dispatch<MethodInvocation>> dispatchers = new ArrayList<Dispatch<MethodInvocation>>(listeners.size() + 2);
                if (includeLogger && logger != null) {
                    dispatchers.add(logger);
                }
                if (parentDispatch != null) {
                    dispatchers.add(parentDispatch);
                }
                for (ListenerDetails listener : listeners) {
                    listener.startNotification(owner);
                    dispatchers.add(listener);
                }
                return dispatchers;
            }
        }

        private void endNotification(List<Dispatch<MethodInvocation>> dispatchers) {
            synchronized (lock) {
                for (Dispatch<MethodInvocation> dispatcher : dispatchers) {
                    if (dispatcher instanceof ListenerDetails) {
                        ListenerDetails listener = (ListenerDetails) dispatcher;
                        listener.endNotification(owner);
                    }
                }
                owner = null;
                lock.notifyAll();
            }
        }

        private class ListenerDispatch extends AbstractBroadcastDispatch<T> {
            private final boolean includeLogger;

            public ListenerDispatch(Class<T> type, boolean includeLogger) {
                super(type);
                this.includeLogger = includeLogger;
            }

            @Override
            public void dispatch(MethodInvocation invocation) {
                List<Dispatch<MethodInvocation>> dispatchers = startNotification(includeLogger);
                try {
                    dispatch(invocation, dispatchers.iterator());
                } finally {
                    endNotification(dispatchers);
                }
            }
        }
    }

    private class ListenerDetails implements Dispatch<MethodInvocation> {
        final Object listener;
        final Dispatch<MethodInvocation> dispatch;
        final AtomicBoolean removed = new AtomicBoolean();

        // Protected by lock
        Thread owner;

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

        // Must be holding lock
        public void startNotification(Thread owner) {
            untilNotInUse(owner);
            this.owner = owner;
        }

        // Must be holding lock
        public void untilNotInUse(Thread expectedOwner) {
            while (this.owner != null && this.owner != expectedOwner) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        // Must be holding lock
        public void endNotification(Thread owner) {
            if (this.owner != owner && this.owner != null) {
                throw new IllegalStateException("Unexpected owner for listener.");
            }
            this.owner = null;
            lock.notifyAll();
        }
    }
}
