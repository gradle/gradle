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
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationProgressEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.SubscribableBuildActionRunnerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ToolingApiSubscribableBuildActionRunnerRegistration implements SubscribableBuildActionRunnerRegistration {
    @Override
    public Iterable<BuildOperationListener> createListeners(BuildClientSubscriptions clientSubscriptions, BuildEventConsumer consumer) {
        List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>();
        if (clientSubscriptions.isSendTestProgressEvents()) {
            listeners.add(new ClientForwardingTestOperationListener(consumer, clientSubscriptions));
        }
        if (clientSubscriptions.isSendBuildProgressEvents() || clientSubscriptions.isSendTaskProgressEvents()) {
            BuildOperationListener buildListener = NO_OP;
            if (clientSubscriptions.isSendBuildProgressEvents()) {
                buildListener = new TestIgnoringBuildOperationListener(new ClientForwardingBuildOperationListener(consumer));
            }
            listeners.add(new ClientForwardingTaskOperationListener(consumer, clientSubscriptions, buildListener));
        }
        return listeners;
    }

    private static final BuildOperationListener NO_OP = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        }
    };
}
