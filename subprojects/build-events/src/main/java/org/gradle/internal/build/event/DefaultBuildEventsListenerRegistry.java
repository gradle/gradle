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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.internal.RegisteredBuildServiceProvider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class DefaultBuildEventsListenerRegistry implements BuildEventsListenerRegistry, BuildEventListenerRegistryInternal {
    private final BuildEventListenerFactory factory;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    @GuardedBy("subscriptions")
    private final Map<Provider<?>, AbstractListener<?>> subscriptions = new LinkedHashMap<>();
    private final ExecutorFactory executorFactory;

    public DefaultBuildEventsListenerRegistry(
        BuildEventListenerFactory factory,
        ListenerManager listenerManager,
        BuildOperationListenerManager buildOperationListenerManager,
        ExecutorFactory executorFactory
    ) {
        this.factory = factory;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.executorFactory = executorFactory;
        listenerManager.addListener(new ListenerCleanup());
    }

    @Override
    public List<Provider<?>> getSubscriptions() {
        synchronized (subscriptions) {
            return ImmutableList.copyOf(subscriptions.keySet());
        }
    }

    @Override
    public void subscribe(Provider<?> provider) {
        ProviderInternal<?> providerInternal = Providers.internal(provider);
        if (OperationCompletionListener.class.isAssignableFrom(providerInternal.getType())) {
            onTaskCompletion(Cast.uncheckedCast(provider));
        } else {
            onOperationCompletion(Cast.uncheckedCast(provider));
        }
    }

    @Override
    public void onOperationCompletion(Provider<? extends BuildOperationListener> listenerProvider) {
        addSubscriptionIfNotExists(listenerProvider, this::makeBuildOperationSubscription);
    }

    private ForwardingBuildOperationListener makeBuildOperationSubscription(Provider<? extends BuildOperationListener> listenerProvider) {
        ForwardingBuildOperationListener subscription = new ForwardingBuildOperationListener(listenerProvider, executorFactory);
        processIfBuildService(listenerProvider);
        buildOperationListenerManager.addListener(subscription);
        return subscription;
    }

    @Override
    public void onTaskCompletion(Provider<? extends OperationCompletionListener> listenerProvider) {
        addSubscriptionIfNotExists(listenerProvider, this::makeTaskCompletionSubscription);
    }

    private ForwardingBuildEventConsumer makeTaskCompletionSubscription(Provider<? extends OperationCompletionListener> listenerProvider) {
        ForwardingBuildEventConsumer subscription = new ForwardingBuildEventConsumer(listenerProvider, executorFactory);
        processIfBuildService(listenerProvider);

        for (Object listener : subscription.getListeners()) {
            listenerManager.addListener(listener);
            if (listener instanceof BuildOperationListener) {
                buildOperationListenerManager.addListener((BuildOperationListener) listener);
            }
        }
        return subscription;
    }

    private <T> void addSubscriptionIfNotExists(Provider<T> listenerProvider, Function<Provider<T>, ? extends AbstractListener<?>> factoryFunction) {
        synchronized (subscriptions) {
            subscriptions.computeIfAbsent(listenerProvider, Cast.uncheckedNonnullCast(factoryFunction));
        }
    }

    private void processIfBuildService(Provider<?> listenerProvider) {
        if (listenerProvider instanceof RegisteredBuildServiceProvider<?, ?>) {
            RegisteredBuildServiceProvider<?, ?> serviceProvider = Cast.uncheckedCast(listenerProvider);
            serviceProvider.beforeStopping(this::unsubscribeProvider);
            serviceProvider.keepAlive();
        }
    }

    private void unsubscribeProvider(Provider<?> listenerProvider) {
        AbstractListener<?> subscription;
        synchronized (subscriptions) {
            subscription = subscriptions.remove(listenerProvider);
        }
        if (subscription != null) {
            subscription.getListeners().forEach(this::unsubscribe);
            subscription.close();
        }
    }

    private void unsubscribeAll() {
        Collection<AbstractListener<?>> subscribed;

        synchronized (subscriptions) {
            subscribed = ImmutableList.copyOf(subscriptions.values());
            subscriptions.clear();
        }

        subscribed.stream()
            .flatMap(it -> it.getListeners().stream())
            .forEach(this::unsubscribe);
        CompositeStoppable.stoppable(subscribed).stop();
    }

    private void unsubscribe(Object listener) {
        listenerManager.removeListener(listener);
        if (listener instanceof BuildOperationListener) {
            buildOperationListenerManager.removeListener((BuildOperationListener) listener);
        }
    }

    private static abstract class AbstractListener<T> implements Closeable {
        private static final Object END = new Object();
        private final ManagedExecutor executor;
        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        private final AtomicReference<Exception> failure = new AtomicReference<>();

        public AbstractListener(ExecutorFactory executorFactory) {
            this.executor = executorFactory.create("build event listener");
            Future<?> ignored = executor.submit(this::run);
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
                try {
                    handle(Cast.uncheckedNonnullCast(next));
                } catch (Exception e) {
                    failure.set(e);
                    break;
                }
            }
            // A failure has happened. Drain the queue and complete without waiting. There should no more messages added to the queue
            // as the dispatch method will see the failure
            events.clear();
        }

        protected abstract void handle(T message);

        public abstract List<Object> getListeners();

        protected void queue(T message) {
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

    private static class ForwardingBuildOperationListener extends AbstractListener<Pair<BuildOperationDescriptor, OperationFinishEvent>> implements BuildOperationListener {
        private final Provider<? extends BuildOperationListener> listenerProvider;

        public ForwardingBuildOperationListener(Provider<? extends BuildOperationListener> listenerProvider, ExecutorFactory executorFactory) {
            super(executorFactory);
            this.listenerProvider = listenerProvider;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            queue(Pair.of(buildOperation, finishEvent));
        }

        @Override
        protected void handle(Pair<BuildOperationDescriptor, OperationFinishEvent> message) {
            listenerProvider.get().finished(message.left, message.right);
        }

        @Override
        public List<Object> getListeners() {
            return ImmutableList.of(this);
        }
    }

    private class ForwardingBuildEventConsumer extends AbstractListener<DefaultTaskFinishedProgressEvent> implements BuildEventConsumer {
        private final Provider<? extends OperationCompletionListener> listenerProvider;
        private final ImmutableList<Object> listeners;

        public ForwardingBuildEventConsumer(Provider<? extends OperationCompletionListener> listenerProvider, ExecutorFactory executorFactory) {
            super(executorFactory);
            this.listenerProvider = listenerProvider;
            BuildEventSubscriptions eventSubscriptions = new BuildEventSubscriptions(Collections.singleton(OperationType.TASK));
            // TODO - share these listeners here and with the tooling api client, where possible
            listeners = ImmutableList.copyOf(factory.createListeners(eventSubscriptions, this));
        }

        @Override
        public void dispatch(Object message) {
            if (message instanceof DefaultTaskFinishedProgressEvent) {
                queue((DefaultTaskFinishedProgressEvent) message);
            }
        }

        @Override
        protected void handle(DefaultTaskFinishedProgressEvent providerEvent) {
            // TODO - reuse adapters from tooling api client
            InternalTaskDescriptor providerDescriptor = providerEvent.getDescriptor();
            InternalTaskResult providerResult = providerEvent.getResult();
            DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
            TaskOperationResult result = BuildProgressListenerAdapter.toTaskResult(providerResult);
            DefaultTaskFinishEvent finishEvent = new DefaultTaskFinishEvent(providerEvent.getEventTime(), providerEvent.getDisplayName(), descriptor, result);
            listenerProvider.get().onFinish(finishEvent);
        }

        @Override
        public List<Object> getListeners() {
            return listeners;
        }
    }

    private class ListenerCleanup extends BuildAdapter {
        @SuppressWarnings("deprecation")
        @Override
        public void buildFinished(BuildResult result) {
            // TODO - maybe make the registry a build scoped service
            if (!((GradleInternal) result.getGradle()).isRootBuild()) {
                // Stop only when the root build completes
                return;
            }
            unsubscribeAll();
        }
    }
}
