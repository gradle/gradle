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
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.SubscribableBuildActionRunnerRegistration;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public class ToolingApiSubscribableBuildActionRunnerRegistration implements SubscribableBuildActionRunnerRegistration {

    private final OperationResultPostProcessor operationResultPostProcessor;

    ToolingApiSubscribableBuildActionRunnerRegistration(OperationResultPostProcessor operationResultPostProcessor) {
        this.operationResultPostProcessor = operationResultPostProcessor;
    }

    @Override
    public Iterable<BuildOperationListener> createListeners(BuildClientSubscriptions clientSubscriptions, BuildEventConsumer consumer) {
        if (!clientSubscriptions.isAnyOperationTypeRequested()) {
            return emptyList();
        }
        BuildOperationParentTracker parentTracker = new BuildOperationParentTracker();
        ProgressEventConsumer progressEventConsumer = new ProgressEventConsumer(consumer, parentTracker);
        List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>();
        listeners.add(parentTracker);
        if (clientSubscriptions.isRequested(OperationType.TEST)) {
            listeners.add(new ClientForwardingTestOperationListener(progressEventConsumer, clientSubscriptions));
        }
        if (clientSubscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM, OperationType.TASK)) {
            BuildOperationListener buildListener = NO_OP;
            if (clientSubscriptions.isRequested(OperationType.GENERIC)) {
                buildListener = new TestIgnoringBuildOperationListener(new ClientForwardingBuildOperationListener(progressEventConsumer));
            }
            if (clientSubscriptions.isAnyRequested(OperationType.GENERIC, OperationType.WORK_ITEM)) {
                buildListener = new ClientForwardingWorkItemOperationListener(progressEventConsumer, clientSubscriptions, buildListener);
            }
            listeners.add(new ClientForwardingTaskOperationListener(progressEventConsumer, clientSubscriptions, buildListener, operationResultPostProcessor));
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
