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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.Cast;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ProxyDispatchAdapter;
import org.gradle.internal.dispatch.ReflectionDispatch;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.ListenerService;
import org.gradle.internal.service.scopes.ParallelListener;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.StatefulListener;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.util.internal.ArrayUtils.contains;
import static org.gradle.util.internal.CollectionUtils.join;

public class DefaultListenerManager implements ScopedListenerManager {
    private static final List<Class<? extends Annotation>> ANNOTATIONS = ImmutableList.of(StatefulListener.class, ListenerService.class);
    private final Map<Object, ListenerDetails> allListeners = new LinkedHashMap<Object, ListenerDetails>();
    private final Map<Object, ListenerDetails> allLoggers = new LinkedHashMap<Object, ListenerDetails>();
    private final Map<Class<?>, EventBroadcast<?>> broadcasters = new ConcurrentHashMap<Class<?>, EventBroadcast<?>>();
    private final List<Registration> pendingServices = new ArrayList<Registration>();
    private final List<Registration> pendingRegistrations = new ArrayList<Registration>();
    private final Object lock = new Object();
    private final Class<? extends Scope> scope;
    private final DefaultListenerManager parent;

    public DefaultListenerManager(Class<? extends Scope> scope) {
        this(scope, null);
    }

    private DefaultListenerManager(Class<? extends Scope> scope, @Nullable DefaultListenerManager parent) {
        this.scope = scope;
        this.parent = parent;
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotations() {
        return ANNOTATIONS;
    }

    @Nullable
    @Override
    public Class<? extends Annotation> getImplicitAnnotation() {
        return null;
    }

    @Override
    public void whenRegistered(Class<? extends Annotation> annotation, Registration registration) {
        synchronized (lock) {
            if (annotation == ListenerService.class) {
                pendingServices.add(registration);
            } else {
                pendingRegistrations.add(registration);
                for (EventBroadcast<?> broadcast : broadcasters.values()) {
                    if (registrationProvides(broadcast.type, registration)) {
                        broadcast.assertMutable("add listener");
                    }
                }
            }
        }
    }

    private void maybeAddPendingRegistrations(Class<?> type) {
        synchronized (lock) {
            for (Registration registration : pendingServices) {
                addListener(registration.getInstance());
            }
            pendingServices.clear();

            int i = 0;
            while (i < pendingRegistrations.size()) {
                Registration registration = pendingRegistrations.get(i);
                if (registrationProvides(type, registration)) {
                    addListener(registration.getInstance());
                    pendingRegistrations.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    private static boolean registrationProvides(Class<?> type, Registration registration) {
        for (Class<?> declaredType : registration.getDeclaredTypes()) {
            if (type.isAssignableFrom(declaredType)) {
                return true;
            }
        }
        return false;
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
        return new AnonymousListenerBroadcast<T>(listenerClass, getBroadcasterInternal(listenerClass).getDispatch(true));
    }

    private <T> EventBroadcast<T> getBroadcasterInternal(Class<T> listenerClass) {
        synchronized (lock) {
            EventBroadcast<T> broadcaster = Cast.uncheckedCast(broadcasters.get(listenerClass));
            if (broadcaster == null) {
                if (listenerClass.getAnnotation(StatefulListener.class) != null) {
                    broadcaster = new ParallelEventBroadcast<T>(listenerClass);
                } else {
                    broadcaster = new ExclusiveEventBroadcast<T>(listenerClass);
                }

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
        if (!contains(scope.value(), this.scope)) {
            throw new IllegalArgumentException(String.format("Listener type %s with %s cannot be used to generate events in scope '%s'.", listenerClass.getName(), displayScopes(scope.value()), this.scope.getSimpleName()));
        }
    }

    @Override
    public DefaultListenerManager createChild(Class<? extends Scope> scope) {
        return new DefaultListenerManager(scope, this);
    }

    private static String displayScopes(Class<? extends Scope>[] scopes) {
        if (scopes.length == 1) {
            return "service scope '" + scopes[0].getSimpleName() + "'";
        }

        return "service scopes " + join(", ", scopes, new InternalTransformer<String, Class<? extends Scope>>() {
            @Override
            public String transform(Class<? extends Scope> aClass) {
                return "'" + aClass.getSimpleName() + "'";
            }
        });
    }

    /**
     * A broadcaster. Manages all state and registered listener implementations for a given
     * listener interface.
     */
    private abstract class EventBroadcast<T>  {
        protected final Class<T> type;
        private final ListenerDispatch dispatch;
        private final ListenerDispatch dispatchNoLogger;

        private final Set<ListenerDetails> listeners = new LinkedHashSet<ListenerDetails>();

        @Nullable
        private volatile ProxyDispatchAdapter<T> source;

        @Nullable
        private ListenerDetails logger;

        @Nullable
        private Dispatch<MethodInvocation> parentDispatch;

        private ImmutableList<Dispatch<MethodInvocation>> allWithLogger = ImmutableList.of();
        private ImmutableList<Dispatch<MethodInvocation>> allWithNoLogger = ImmutableList.of();

        protected volatile boolean initialized;
        protected final Object initializationLock = new Object();

        EventBroadcast(Class<T> type) {
            this.type = type;
            dispatch = new ListenerDispatch(type, true);
            dispatchNoLogger = new ListenerDispatch(type, false);
            if (parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(true);
                initLoggers();
            }
        }

        Dispatch<MethodInvocation> getDispatch(boolean includeLogger) {
            return includeLogger ? dispatch : dispatchNoLogger;
        }

        T getBroadcaster() {
            if (source == null) {
                synchronized (this) {
                    if (source == null) {
                        source = new ProxyDispatchAdapter<T>(dispatch, type);
                    }
                }
            }
            return source.getSource();
        }

        protected List<Dispatch<MethodInvocation>> getListeners(boolean withLogger) {
            return withLogger ? allWithLogger : allWithNoLogger;
        }

        protected void initLoggers() {
            this.allWithLogger = initAllWithLogger();
            this.allWithNoLogger = initAllWithNoLogger();
        }

        /**
         * Add a listener to this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        public final void maybeAdd(final ListenerDetails listener) {
            if (!type.isInstance(listener.listener)) {
                return;
            }

            assertMutable("add listener");
            doAdd(listener);
        }

        protected void doAdd(final ListenerDetails listener) {
            listeners.add(listener);
        }

        /**
         * Remove a listener from this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        public final void maybeRemove(ListenerDetails listener) {
            if (!type.isInstance(listener.listener)) {
                return;
            }

            assertMutable("remove listener");
            doRemove(listener);
        }

        protected void doRemove(ListenerDetails listener) {
            listeners.remove(listener);
        }

        /**
         * Set the logger for this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        public final void maybeSetLogger(ListenerDetails candidate) {
            if (!type.isInstance(candidate.listener)) {
                return;
            }

            assertMutable("set logger");
            doSetLogger(candidate);
        }

        protected void doSetLogger(ListenerDetails candidate) {
            if (logger == null && parent != null) {
                parentDispatch = parent.getBroadcasterInternal(type).getDispatch(false);
            }
            logger = candidate;
        }

        private ImmutableList<Dispatch<MethodInvocation>> initAllWithNoLogger() {
            if (parentDispatch == null && listeners.isEmpty()) {
                return ImmutableList.of();
            }

            ImmutableList.Builder<Dispatch<MethodInvocation>> dispatchers = ImmutableList.builder();
            if (parentDispatch != null) {
                dispatchers.add(parentDispatch);
            }
            dispatchers.addAll(listeners);
            return dispatchers.build();
        }

        private ImmutableList<Dispatch<MethodInvocation>> initAllWithLogger() {
            if (logger == null && parentDispatch == null && listeners.isEmpty()) {
                return ImmutableList.of();
            }

            ImmutableList.Builder<Dispatch<MethodInvocation>> result = ImmutableList.builder();
            if (logger != null) {
                result.add(logger);
            }
            if (parentDispatch != null) {
                result.add(parentDispatch);
            }
            result.addAll(listeners);
            return result.build();
        }

        protected final void maybeInitialize() {
            if (!initialized) {
                synchronized (initializationLock) {
                    if (!initialized) {
                        maybeAddPendingRegistrations(EventBroadcast.this.type);
                        initLoggers();
                        initialized = true;
                    }
                }
            }
        }

        protected abstract List<Dispatch<MethodInvocation>> startDispatch(boolean includeLogger);

        protected abstract void endDispatch();

        protected abstract void assertMutable(String operation);

        private class ListenerDispatch extends AbstractBroadcastDispatch<T> {

            private final boolean includeLogger;

            ListenerDispatch(Class<T> type, boolean includeLogger) {
                super(type);
                this.includeLogger = includeLogger;
            }

            @Override
            public void dispatch(MethodInvocation invocation) {
                maybeInitialize();

                List<Dispatch<MethodInvocation>> dispatchers = startDispatch(includeLogger);
                try {
                    dispatch(invocation, dispatchers);
                } finally {
                    endDispatch();
                }
            }

        }
    }

    private class ExclusiveEventBroadcast<T> extends EventBroadcast<T> {

        private final ReentrantLock broadcasterLock = new ReentrantLock();
        private final List<Runnable> queuedOperations = new LinkedList<Runnable>();

        public ExclusiveEventBroadcast(Class<T> type) {
            super(type);
        }

        @Override
        protected void doAdd(final ListenerDetails listener) {
            executeNowOrLater(new Runnable() {
                @Override
                public void run() {
                    ExclusiveEventBroadcast.super.doAdd(listener);
                }
            });
        }

        @Override
        protected void doRemove(final ListenerDetails listener) {
            executeNowOrLater(new Runnable() {
                @Override
                public void run() {
                    ExclusiveEventBroadcast.super.doRemove(listener);
                }
            });
        }

        @Override
        protected void doSetLogger(final ListenerDetails candidate) {
            executeNowOrLater(new Runnable() {
                @Override
                public void run() {
                    ExclusiveEventBroadcast.super.doSetLogger(candidate);
                }
            });
        }

        /**
         * Try to execute the given operation now if the broadcast lock is
         * uncontested, otherwise queue it for later.
         */
        private void executeNowOrLater(Runnable operation) {
            if (broadcasterLock.tryLock()) {
                try {
                    operation.run();
                    initLoggers();
                } finally {
                    broadcasterLock.unlock();
                }
            } else {
                synchronized (queuedOperations) {
                    queuedOperations.add(operation);
                }
            }
        }

        @Override
        protected List<Dispatch<MethodInvocation>> startDispatch(boolean includeLogger) {
            if (broadcasterLock.isHeldByCurrentThread()) {
                throw new IllegalStateException(String.format(
                    "Cannot notify listeners of type %s as these listeners are already being notified.",
                    type.getSimpleName()
                ));
            }

            broadcasterLock.lock();

            // Ensure we retrieve listeners while holding lock.
            return getListeners(includeLogger);
        }

        @Override
        protected void endDispatch() {
            try {
                synchronized (queuedOperations) {
                    if (!queuedOperations.isEmpty()) {
                        for (Runnable queuedOperation : queuedOperations) {
                            queuedOperation.run();
                        }
                        initLoggers();
                    }
                }
            } finally {
                broadcasterLock.unlock();
            }
        }

        @Override
        protected void assertMutable(String operation) {
            // Since we perform locking when operating on listeners,
            // the exclusive broadcaster is always mutable.
        }
    }

    /**
     * An {@link EventBroadcast} that allows listeners to be notified in parallel.
     * This is accomplished by forbidding the mutation of the listeners to notify
     * after an event has been broadcast.
     */
    private class ParallelEventBroadcast<T> extends EventBroadcast<T> {

        public ParallelEventBroadcast(Class<T> type) {
            super(type);
        }

        @Override
        protected List<Dispatch<MethodInvocation>> startDispatch(boolean includeLogger) {
            return getListeners(includeLogger);
        }

        @Override
        protected void endDispatch() { }

        @Override
        protected void assertMutable(String operation) {
            synchronized (initializationLock) {
                if (initialized) {
                    throw new IllegalStateException(String.format(
                        "Cannot %s of type %s after events have been broadcast.",
                        operation,
                        type.getSimpleName())
                    );
                }
            }
        }
    }

    /**
     * Holds state about a particular listener implementation. A listener implementation may
     * implement multiple listener interfaces and therefore may receive events from multiple
     * broadcasters.
     */
    private class ListenerDetails implements Dispatch<MethodInvocation> {
        final Object listener;
        final Dispatch<MethodInvocation> dispatch;
        final boolean parallel;
        final AtomicBoolean removed = new AtomicBoolean();
        final ReentrantLock notifyingLock = new ReentrantLock();

        public ListenerDetails(Object listener) {
            this.listener = listener;
            this.dispatch = new ReflectionDispatch(listener);
            this.parallel = JavaReflectionUtil.hasAnnotation(listener.getClass(), ParallelListener.class);
        }

        void disconnect() {
            removed.set(true);
        }

        @Override
        public void dispatch(MethodInvocation message) {
            if (removed.get()) {
                return;
            }

            if (parallel) {
                dispatch.dispatch(message);
                return;
            }

            notifyingLock.lock();
            try {
                dispatch.dispatch(message);
            } finally {
                notifyingLock.unlock();
            }
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
