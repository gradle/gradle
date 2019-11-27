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
import org.gradle.build.event.ProgressListenerRegistry;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.events.task.internal.DefaultTaskStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;

import java.util.Collections;
import java.util.List;

public class DefaultProgressListenerRegistry implements ProgressListenerRegistry {
    private final List<BuildEventListenerFactory> registrations;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;

    public DefaultProgressListenerRegistry(List<BuildEventListenerFactory> registrations, ListenerManager listenerManager, BuildOperationListenerManager buildOperationListenerManager) {
        this.registrations = registrations;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
    }

    @Override
    public void register(Provider<? extends ProgressListener> listenerProvider) {
        // TODO - deliver events asynchronously

        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        ForwardingBuildEventConsumer consumer = new ForwardingBuildEventConsumer(listenerProvider);
        for (BuildEventListenerFactory registration : registrations) {
            Iterable<Object> listeners = registration.createListeners(new BuildEventSubscriptions(Collections.singleton(OperationType.TASK)), consumer);
            builder.addAll(listeners);
            for (Object listener : listeners) {
                listenerManager.addListener(listener);
                if (listener instanceof BuildOperationListener) {
                    buildOperationListenerManager.addListener((BuildOperationListener) listener);
                }
            }
        }

        ImmutableList<Object> allListeners = builder.build();
        if (!allListeners.isEmpty()) {
            listenerManager.addListener(new ListenerCleanup(allListeners, listenerManager, buildOperationListenerManager));
        }
    }

    private static class ForwardingBuildEventConsumer implements BuildEventConsumer {
        private final Provider<? extends ProgressListener> listenerProvider;

        public ForwardingBuildEventConsumer(Provider<? extends ProgressListener> listenerProvider) {
            this.listenerProvider = listenerProvider;
        }

        @Override
        public void dispatch(Object message) {
            // TODO - reuse adapters from tooling api client
            InternalProgressEvent event = (InternalProgressEvent) message;
            if (event.getDescriptor() instanceof InternalTaskDescriptor) {
                InternalTaskDescriptor providerDescriptor = (InternalTaskDescriptor) event.getDescriptor();
                DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
                if (event instanceof InternalOperationStartedProgressEvent) {
                    listenerProvider.get().statusChanged(new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), descriptor));
                } else if (event instanceof InternalOperationFinishedProgressEvent) {
                    // TODO - provide the correct result
                    listenerProvider.get().statusChanged(new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, null));
                }
            }
        }
    }

    private static class ListenerCleanup implements RootBuildLifecycleListener {
        private final List<?> allListeners;
        private final ListenerManager listenerManager;
        private final BuildOperationListenerManager buildOperationListenerManager;

        public ListenerCleanup(List<?> allListeners, ListenerManager listenerManager, BuildOperationListenerManager buildOperationListenerManager) {
            this.allListeners = allListeners;
            this.listenerManager = listenerManager;
            this.buildOperationListenerManager = buildOperationListenerManager;
        }

        @Override
        public void afterStart(GradleInternal gradle) {
        }

        @Override
        public void beforeComplete(GradleInternal gradle) {
            System.out.println("-> UNREGISTER!!");
            for (Object listener : allListeners) {
                listenerManager.removeListener(listener);
                if (listener instanceof BuildOperationListener) {
                    buildOperationListenerManager.removeListener((BuildOperationListener) listener);
                }
            }
            listenerManager.removeListener(this);
        }
    }
}
