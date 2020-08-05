/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public class ToolingApiBuildEventListenerFactory implements BuildEventListenerFactory {
    private final BuildOperationIdFactory idFactory;
    private final List<OperationResultPostProcessorFactory> postProcessorFactories;

    ToolingApiBuildEventListenerFactory(BuildOperationIdFactory idFactory, List<OperationResultPostProcessorFactory> postProcessorFactories) {
        this.idFactory = idFactory;
        this.postProcessorFactories = postProcessorFactories;
    }

    @Override
    public Iterable<Object> createListeners(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        if (!subscriptions.isAnyOperationTypeRequested()) {
            return emptyList();
        }
        BuildOperationParentTracker parentTracker = new BuildOperationParentTracker();
        ProgressEventConsumer progressEventConsumer = new ProgressEventConsumer(consumer, parentTracker);
        List<Object> listeners = new ArrayList<>();
        listeners.add(parentTracker);
        if (subscriptions.isRequested(OperationType.TEST)) {
            BuildOperationListener buildListener = new ClientForwardingTestOperationListener(progressEventConsumer, subscriptions);
            if (subscriptions.isRequested(OperationType.TEST_OUTPUT)) {
                buildListener = new ClientForwardingTestOutputOperationListener(buildListener, progressEventConsumer, idFactory);
            }
            listeners.add(buildListener);
        }
        if (subscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM, OperationType.TASK, OperationType.PROJECT_CONFIGURATION, OperationType.TRANSFORM)) {
            BuildOperationListener buildListener = NO_OP;
            if (subscriptions.isRequested(OperationType.GENERIC)) {
                buildListener = new TestIgnoringBuildOperationListener(new ClientForwardingBuildOperationListener(progressEventConsumer));
            }
            if (subscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM)) {
                buildListener = new ClientForwardingWorkItemOperationListener(progressEventConsumer, subscriptions, buildListener);
            }
            OperationDependenciesResolver operationDependenciesResolver = new OperationDependenciesResolver();
            if (subscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM, OperationType.TRANSFORM)) {
                ClientForwardingTransformOperationListener transformOperationListener = new ClientForwardingTransformOperationListener(progressEventConsumer, subscriptions, buildListener, operationDependenciesResolver);
                operationDependenciesResolver.addLookup(transformOperationListener);
                buildListener = transformOperationListener;
            }
            PluginApplicationTracker pluginApplicationTracker = new PluginApplicationTracker(parentTracker);
            if (subscriptions.isAnyRequested(OperationType.PROJECT_CONFIGURATION, OperationType.TASK)) {
                listeners.add(pluginApplicationTracker);
            }
            if (subscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM, OperationType.TRANSFORM, OperationType.TASK)) {
                TaskOriginTracker taskOriginTracker = new TaskOriginTracker(pluginApplicationTracker);
                if (subscriptions.isAnyRequested(OperationType.TASK)) {
                    listeners.add(taskOriginTracker);
                }

                List<OperationResultPostProcessor> postProcessors = new ArrayList<>(postProcessorFactories.size());
                for (OperationResultPostProcessorFactory postProcessorFactory : postProcessorFactories) {
                    postProcessors.addAll(postProcessorFactory.createProcessors(subscriptions, consumer));
                }
                listeners.addAll(postProcessors);
                OperationResultPostProcessor postProcessor = new CompositeOperationResultPostProcessor(postProcessors);

                ClientForwardingTaskOperationListener taskOperationListener = new ClientForwardingTaskOperationListener(progressEventConsumer, subscriptions, buildListener, postProcessor, taskOriginTracker, operationDependenciesResolver);
                operationDependenciesResolver.addLookup(taskOperationListener);
                buildListener = taskOperationListener;
            }
            listeners.add(new ClientForwardingProjectConfigurationOperationListener(progressEventConsumer, subscriptions, buildListener, parentTracker, pluginApplicationTracker));
        }
        return listeners;
    }

    private static final BuildOperationListener NO_OP = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        }
    };
}
