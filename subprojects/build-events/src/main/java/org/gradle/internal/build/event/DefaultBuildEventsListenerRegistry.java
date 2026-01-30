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
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.MultiProducerSingleConsumerProcessor;
import org.gradle.internal.concurrent.Stoppable;
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
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class DefaultBuildEventsListenerRegistry implements BuildEventsListenerRegistry, BuildEventListenerRegistryInternal {

    private final UserCodeApplicationContext applicationContext;
    private final BuildEventListenerFactory factory;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    @GuardedBy("subscriptions")
    private final Map<Provider<?>, ListenerSubscription> subscriptions = new LinkedHashMap<>();

    public DefaultBuildEventsListenerRegistry(
        UserCodeApplicationContext applicationContext,
        BuildEventListenerFactory factory,
        ListenerManager listenerManager,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.applicationContext = applicationContext;
        this.factory = factory;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        listenerManager.addListener(new ListenerCleanup());
    }

    @Override
    public List<Subscription> getSubscriptions() {
        synchronized (subscriptions) {
            return subscriptions.entrySet().stream()
                .map(entry -> new Subscription(entry.getValue().getRegistrationPoint(), entry.getKey()))
                .collect(toImmutableList());
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
        ForwardingBuildOperationListener subscription = new ForwardingBuildOperationListener(getCurrentContext(), listenerProvider);
        processIfBuildService(listenerProvider);
        buildOperationListenerManager.addListener(subscription);
        return subscription;
    }

    @Override
    public void onTaskCompletion(Provider<? extends OperationCompletionListener> listenerProvider) {
        addSubscriptionIfNotExists(listenerProvider, this::makeTaskCompletionSubscription);
    }

    private ForwardingBuildEventConsumer makeTaskCompletionSubscription(Provider<? extends OperationCompletionListener> listenerProvider) {
        ForwardingBuildEventConsumer subscription = new ForwardingBuildEventConsumer(getCurrentContext(), listenerProvider);
        processIfBuildService(listenerProvider);

        for (Object listener : subscription.getListeners()) {
            listenerManager.addListener(listener);
            if (listener instanceof BuildOperationListener) {
                buildOperationListenerManager.addListener((BuildOperationListener) listener);
            }
        }
        return subscription;
    }

    private <T> void addSubscriptionIfNotExists(Provider<T> listenerProvider, Function<Provider<T>, ? extends ListenerSubscription> factoryFunction) {
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
        ListenerSubscription subscription;
        synchronized (subscriptions) {
            subscription = subscriptions.remove(listenerProvider);
        }
        if (subscription != null) {
            subscription.getListeners().forEach(this::unsubscribe);
            subscription.stop();
        }
    }

    private void unsubscribeAll() {
        Collection<ListenerSubscription> subscribed;

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

    @Nullable
    private UserCodeSource getCurrentContext() {
        UserCodeApplicationContext.Application current = applicationContext.current();
        return current != null ? current.getSource() : null;
    }

    public interface ListenerSubscription extends Stoppable {

        @Nullable UserCodeSource getRegistrationPoint();

        List<Object> getListeners();

    }

    private static class ForwardingBuildOperationListener implements ListenerSubscription, BuildOperationListener {

        private final @Nullable UserCodeSource registrationPoint;
        private final MultiProducerSingleConsumerProcessor<Pair<BuildOperationDescriptor, OperationFinishEvent>> processor;
        private final Provider<? extends BuildOperationListener> listenerProvider;

        public ForwardingBuildOperationListener(
            @Nullable UserCodeSource registrationPoint,
            Provider<? extends BuildOperationListener> listenerProvider
        ) {
            this.registrationPoint = registrationPoint;
            this.listenerProvider = listenerProvider;
            this.processor = new MultiProducerSingleConsumerProcessor<>("build event listener", this::handle);
            processor.start();
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            processor.maybeSubmit(Pair.of(buildOperation, finishEvent));
        }

        private void handle(Pair<BuildOperationDescriptor, OperationFinishEvent> message) {
            listenerProvider.get().finished(message.left, message.right);
        }

        @Override
        public @Nullable UserCodeSource getRegistrationPoint() {
            return registrationPoint;
        }

        @Override
        public List<Object> getListeners() {
            return ImmutableList.of(this);
        }

        @Override
        public void stop() {
            processor.stop(Duration.ofMinutes(1));
        }

    }

    private class ForwardingBuildEventConsumer implements ListenerSubscription, BuildEventConsumer {

        private final @Nullable UserCodeSource registrationPoint;
        private final MultiProducerSingleConsumerProcessor<DefaultTaskFinishedProgressEvent> processor;
        private final Provider<? extends OperationCompletionListener> listenerProvider;
        private final ImmutableList<Object> listeners;

        public ForwardingBuildEventConsumer(
            @Nullable UserCodeSource registrationPoint,
            Provider<? extends OperationCompletionListener> listenerProvider
        ) {
            this.registrationPoint = registrationPoint;
            this.listenerProvider = listenerProvider;
            BuildEventSubscriptions eventSubscriptions = new BuildEventSubscriptions(Collections.singleton(OperationType.TASK));
            // TODO - share these listeners here and with the tooling api client, where possible
            listeners = ImmutableList.copyOf(factory.createListeners(eventSubscriptions, this));
            this.processor = new MultiProducerSingleConsumerProcessor<>("build event listener", this::handle);
            processor.start();
        }

        @Override
        public void dispatch(Object message) {
            if (message instanceof DefaultTaskFinishedProgressEvent) {
                processor.maybeSubmit((DefaultTaskFinishedProgressEvent) message);
            }
        }

        private void handle(DefaultTaskFinishedProgressEvent providerEvent) {
            // TODO - reuse adapters from tooling api client
            InternalTaskDescriptor providerDescriptor = providerEvent.getDescriptor();
            InternalTaskResult providerResult = providerEvent.getResult();
            DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
            TaskOperationResult result = BuildProgressListenerAdapter.toTaskResult(providerResult);
            DefaultTaskFinishEvent finishEvent = new DefaultTaskFinishEvent(providerEvent.getEventTime(), providerEvent.getDisplayName(), descriptor, result);
            listenerProvider.get().onFinish(finishEvent);
        }

        @Override
        public @Nullable UserCodeSource getRegistrationPoint() {
            return registrationPoint;
        }

        @Override
        public List<Object> getListeners() {
            return listeners;
        }

        @Override
        public void stop() {
            processor.stop(Duration.ofMinutes(1));
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
