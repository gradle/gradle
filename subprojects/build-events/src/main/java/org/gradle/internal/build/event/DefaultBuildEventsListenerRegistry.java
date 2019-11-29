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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultBuildEventsListenerRegistry implements BuildEventsListenerRegistry, BuildEventListenerRegistryInternal {
    private final List<BuildEventListenerFactory> factories;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final Set<Provider<?>> subscriptions = new LinkedHashSet<>();
    private final List<Object> listeners = new ArrayList<>();

    public DefaultBuildEventsListenerRegistry(List<BuildEventListenerFactory> factories, ListenerManager listenerManager, BuildOperationListenerManager buildOperationListenerManager) {
        this.factories = factories;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        listenerManager.addListener(new ListenerCleanup());
    }

    @Override
    public List<Provider<?>> getSubscriptions() {
        return ImmutableList.copyOf(subscriptions);
    }

    @Override
    public void subscribe(Provider<? extends OperationCompletionListener> listenerProvider) {
        if (!subscriptions.add(listenerProvider)) {
            return;
        }

        // TODO - deliver events asynchronously
        // TODO - reuse the listeners

        ForwardingBuildEventConsumer consumer = new ForwardingBuildEventConsumer(listenerProvider);
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

    private static class ForwardingBuildEventConsumer implements BuildEventConsumer {
        private final Provider<? extends OperationCompletionListener> listenerProvider;

        public ForwardingBuildEventConsumer(Provider<? extends OperationCompletionListener> listenerProvider) {
            this.listenerProvider = listenerProvider;
        }

        @Override
        public void dispatch(Object message) {
            // TODO - reuse adapters from tooling api client
            InternalProgressEvent event = (InternalProgressEvent) message;
            if (event instanceof DefaultTaskFinishedProgressEvent) {
                DefaultTaskFinishedProgressEvent finishEvent = (DefaultTaskFinishedProgressEvent) event;
                InternalTaskDescriptor providerDescriptor = finishEvent.getDescriptor();
                InternalTaskResult providerResult = finishEvent.getResult();
                DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
                TaskOperationResult result = BuildProgressListenerAdapter.toTaskResult(providerResult);
                listenerProvider.get().onFinish(new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, result));
            }
        }
    }

    private class ListenerCleanup implements RootBuildLifecycleListener {
        @Override
        public void afterStart(GradleInternal gradle) {
        }

        @Override
        public void beforeComplete(GradleInternal gradle) {
            // TODO - maybe make this registry a build scoped service
            for (Object listener : listeners) {
                listenerManager.removeListener(listener);
                if (listener instanceof BuildOperationListener) {
                    buildOperationListenerManager.removeListener((BuildOperationListener) listener);
                }
            }
            listeners.clear();
            subscriptions.clear();
        }
    }
}
