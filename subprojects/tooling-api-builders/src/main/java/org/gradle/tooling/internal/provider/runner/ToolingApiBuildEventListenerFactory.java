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

import com.google.common.collect.ImmutableList;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
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
    private final BuildOperationAncestryTracker ancestryTracker;
    private final BuildOperationIdFactory idFactory;
    private final List<OperationResultPostProcessorFactory> postProcessorFactories;

    ToolingApiBuildEventListenerFactory(BuildOperationAncestryTracker ancestryTracker, BuildOperationIdFactory idFactory, List<OperationResultPostProcessorFactory> postProcessorFactories) {
        this.ancestryTracker = ancestryTracker;
        this.idFactory = idFactory;
        this.postProcessorFactories = postProcessorFactories;
    }

    @Override
    public Iterable<Object> createListeners(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        if (!subscriptions.isAnyOperationTypeRequested()) {
            return emptyList();
        }

        ProgressEventConsumer progressEventConsumer = new ProgressEventConsumer(consumer, ancestryTracker);

        List<Object> listeners = new ArrayList<>();

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_OUTPUT)) {
            listeners.add(new ClientForwardingTestOutputOperationListener(progressEventConsumer, idFactory));
        }

        BuildOperationListener buildListener = NO_OP;
        if (subscriptions.isRequested(OperationType.GENERIC)) {
            buildListener = new ClientForwardingBuildOperationListener(progressEventConsumer);
        }

        OperationDependenciesResolver operationDependenciesResolver = new OperationDependenciesResolver();

        PluginApplicationTracker pluginApplicationTracker = new PluginApplicationTracker(ancestryTracker);
        TestTaskExecutionTracker testTaskTracker = new TestTaskExecutionTracker(ancestryTracker);
        ProjectConfigurationTracker projectConfigurationTracker = new ProjectConfigurationTracker(ancestryTracker, pluginApplicationTracker);
        TaskOriginTracker taskOriginTracker = new TaskOriginTracker(pluginApplicationTracker);

        TransformOperationMapper transformOperationMapper = new TransformOperationMapper(operationDependenciesResolver);
        operationDependenciesResolver.addLookup(transformOperationMapper);

        List<OperationResultPostProcessor> postProcessors = new ArrayList<>(postProcessorFactories.size());
        for (OperationResultPostProcessorFactory postProcessorFactory : postProcessorFactories) {
            postProcessors.addAll(postProcessorFactory.createProcessors(subscriptions, consumer));
        }

        TaskOperationMapper taskOperationMapper = new TaskOperationMapper(postProcessors, taskOriginTracker, operationDependenciesResolver);
        operationDependenciesResolver.addLookup(taskOperationMapper);

        ImmutableList<BuildOperationMapper<?, ?>> mappers = ImmutableList.of(
            new FileDownloadOperationMapper(),
            new TestOperationMapper(testTaskTracker),
            new ProjectConfigurationOperationMapper(projectConfigurationTracker),
            taskOperationMapper,
            transformOperationMapper,
            new WorkItemOperationMapper()
        );
        ClientBuildEventGenerator generator = new ClientBuildEventGenerator(progressEventConsumer, subscriptions, mappers, buildListener);
        listeners.add(generator);
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
