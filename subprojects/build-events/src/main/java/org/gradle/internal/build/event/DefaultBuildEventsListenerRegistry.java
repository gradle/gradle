/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event;

import com.google.common.collect.ImmutableList;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.util.CollectionUtils;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultBuildEventsListenerRegistry implements BuildEventsListenerRegistry, BuildEventListenerRegistryInternal {
    private final List<BuildEventListenerFactory> factories;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final Map<Provider<?>, ForwardingBuildEventConsumer> subscriptions = new LinkedHashMap<>();
    private final List<Object> listeners = new ArrayList<>();
    private final ExecutorFactory executorFactory;

    public DefaultBuildEventsListenerRegistry(List<BuildEventListenerFactory> factories, ListenerManager listenerManager,
                                              BuildOperationListenerManager buildOperationListenerManager, ExecutorFactory executorFactory) {
        this.factories = factories;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.executorFactory = executorFactory;
        listenerManager.addListener(new ListenerCleanup());
    }

    @Override
    public List<Provider<?>> getSubscriptions() {
        return ImmutableList.copyOf(subscriptions.keySet());
    }

    @Override
    public void subscribe(Provider<? extends OperationCompletionListener> listenerProvider) {
        if (subscriptions.containsKey(listenerProvider)) {
            return;
        }
        // TODO - deliver events asynchronously
        // TODO - reuse the listeners

        ForwardingBuildEventConsumer consumer = new ForwardingBuildEventConsumer(listenerProvider, executorFactory);
        subscriptions.put(listenerProvider, consumer);

        BuildEventSubscriptions eventSubscriptions = new BuildEventSubscriptions(Collections.singleton(OperationType.TASK));
        for (BuildEventListenerFactory registration : factories) {
            Iterable<Object> listeners = registration.createListeners(eventSubscriptions, consumer);
            CollectionUtils.addAll(this.listeners, listeners);
            for (Object listener : listeners) {
                listenerManager.addListener(listener);
                if (listener instanceof BuildOperationListener) {
                    buildOperationListenerManager.addListener((BuildOperationListener) listener);
                }
            }
        }
    }

    private static class ForwardingBuildEventConsumer implements BuildEventConsumer, Closeable {
        private static final Object END = new Object();
        private final Provider<? extends OperationCompletionListener> listenerProvider;
        private final ManagedExecutor executor;
        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        private final AtomicReference<Exception> failure = new AtomicReference<>();

        public ForwardingBuildEventConsumer(Provider<? extends OperationCompletionListener> listenerProvider, ExecutorFactory executorFactory) {
            this.listenerProvider = listenerProvider;
            this.executor = executorFactory.create("build event listener");
            executor.submit(this::run);
        }

        private void run() {
            while (true) {
                Object next;
                try {
                    next = events.take();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (next == END) {
                    return;
                }
                // TODO - reuse adapters from tooling api client
                InternalProgressEvent event = (InternalProgressEvent) next;
                if (event instanceof DefaultTaskFinishedProgressEvent) {
                    DefaultTaskFinishedProgressEvent providerEvent = (DefaultTaskFinishedProgressEvent) event;
                    InternalTaskDescriptor providerDescriptor = providerEvent.getDescriptor();
                    InternalTaskResult providerResult = providerEvent.getResult();
                    DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
                    TaskOperationResult result = BuildProgressListenerAdapter.toTaskResult(providerResult);
                    DefaultTaskFinishEvent finishEvent = new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, result);
                    try {
                        listenerProvider.get().onFinish(finishEvent);
                    } catch (Exception e) {
                        failure.set(e);
                        break;
                    }
                }
            }
            // A failure has happened. Drain the queue and complete without waiting. There should no more messages added to the queue
            // as the dispatch method will see the failure
            events.clear();
        }

        @Override
        public void dispatch(Object message) {
            if (failure.get() == null) {
                events.add(message);
            }
            // else, the handler thread is no longer handling messages so discard it
        }

        @Override
        public void close() {
            events.add(END);
            executor.stop(60, TimeUnit.SECONDS);
            Exception failure = this.failure.get();
            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }
    }

    private class ListenerCleanup extends BuildAdapter {
        @Override
        public void buildFinished(BuildResult result) {
            // TODO - maybe make this registry a build scoped service
            try {
                for (Object listener : listeners) {
                    listenerManager.removeListener(listener);
                    if (listener instanceof BuildOperationListener) {
                        buildOperationListenerManager.removeListener((BuildOperationListener) listener);
                    }
                }
                CompositeStoppable.stoppable(subscriptions.values()).stop();
            } finally {
                listeners.clear();
                subscriptions.clear();
            }
        }
    }
}
